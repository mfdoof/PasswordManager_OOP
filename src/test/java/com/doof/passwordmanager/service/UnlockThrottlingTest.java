package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.testutil.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UnlockThrottlingTest {

    private static final String RIGHT = "MasterPass1";
    private static final String WRONG = "WrongPass1";

    @TempDir
    Path tmp;

    private MutableClock clock;
    private VaultService vault;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(1_700_000_000_000L);
        vault = newVaultService();
        vault.createVault(RIGHT.toCharArray());
        vault.lockVault();
    }

    /** A fresh service over the same file, like restarting the app. */
    private VaultService newVaultService() {
        ConnectionManager cm = ConnectionManager.open(tmp.resolve("vault.db"), w -> {});
        return new VaultService(new JdbcVaultMetadataDao(cm), VaultServiceTest.FAST, clock);
    }

    @Test
    void delayScheduleDoublesAndCapsAt30s() {
        assertEquals(0, VaultService.delayAfter(0));
        assertEquals(1_000, VaultService.delayAfter(1));
        assertEquals(2_000, VaultService.delayAfter(2));
        assertEquals(4_000, VaultService.delayAfter(3));
        assertEquals(8_000, VaultService.delayAfter(4));
        assertEquals(16_000, VaultService.delayAfter(5));
        assertEquals(30_000, VaultService.delayAfter(6));
        assertEquals(30_000, VaultService.delayAfter(100));
        assertEquals(30_000, VaultService.delayAfter(Integer.MAX_VALUE));
    }

    @Test
    void eachFailureGrowsTheDelayUntilTheCap() {
        long[] expected = {1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000};
        for (int i = 0; i < expected.length; i++) {
            InvalidPasswordException e = assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
            assertEquals(i + 1, e.getFailedAttempts());
            assertEquals(expected[i], e.getRetryAfterMs());
            assertEquals(expected[i], vault.unlockStatus().getRetryAfterMs());
            clock.advance(expected[i]);
        }
        assertFalse(vault.isUnlocked());
    }

    @Test
    void attemptsDuringTheWaitAreRefusedWithoutCounting() {
        assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
        assertThrows(InvalidPasswordException.class, () -> { clock.advance(1_000); vault.unlockVault(WRONG.toCharArray()); });
        // Now 2 failures: must wait 2s.

        clock.advance(1_999);
        UnlockThrottledException e = assertThrows(UnlockThrottledException.class, () -> vault.unlockVault(WRONG.toCharArray()));
        assertEquals(2, e.getFailedAttempts());
        assertEquals(1, e.getRetryAfterMs());

        // Even the right password is refused during the wait.
        assertThrows(UnlockThrottledException.class, () -> vault.unlockVault(RIGHT.toCharArray()));
        assertFalse(vault.isUnlocked());
        assertEquals(2, vault.unlockStatus().getFailedAttempts(), "refused attempts must not count");

        clock.advance(1);
        vault.unlockVault(RIGHT.toCharArray());
        assertTrue(vault.isUnlocked());
    }

    @Test
    void refusedAttemptStillWipesThePassword() {
        assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
        char[] pw = RIGHT.toCharArray();
        assertThrows(UnlockThrottledException.class, () -> vault.unlockVault(pw));
        assertArrayEquals(new char[pw.length], pw);
    }

    @Test
    void counterPersistsAcrossRestart() {
        for (int i = 0; i < 3; i++) {
            assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
            clock.advance(VaultService.delayAfter(i + 1));
        }
        assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));  // 4th: 8s wait

        VaultService restarted = newVaultService();
        UnlockStatus status = restarted.unlockStatus();
        assertEquals(4, status.getFailedAttempts());
        assertEquals(8_000, status.getRetryAfterMs());
        assertThrows(UnlockThrottledException.class, () -> restarted.unlockVault(RIGHT.toCharArray()));

        clock.advance(8_000);
        InvalidPasswordException e = assertThrows(InvalidPasswordException.class, () -> restarted.unlockVault(WRONG.toCharArray()));
        assertEquals(5, e.getFailedAttempts());
        assertEquals(16_000, e.getRetryAfterMs());
    }

    @Test
    void successResetsTheCounter() {
        assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
        clock.advance(1_000);
        assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
        clock.advance(2_000);

        vault.unlockVault(RIGHT.toCharArray());
        assertEquals(0, vault.unlockStatus().getFailedAttempts());
        assertFalse(vault.unlockStatus().isThrottled());

        vault.lockVault();
        InvalidPasswordException e = assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
        assertEquals(1, e.getFailedAttempts(), "count restarts from 1 after a success");
        assertEquals(1_000, e.getRetryAfterMs());

        assertEquals(1, newVaultService().unlockStatus().getFailedAttempts(), "reset was persisted, then one new failure");
    }

    @Test
    void clockMovedBackwardsStillRequiresTheFullDelay() {
        assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(WRONG.toCharArray()));
        clock.advance(-60_000);
        assertEquals(1_000, vault.unlockStatus().getRetryAfterMs());
        assertThrows(UnlockThrottledException.class, () -> vault.unlockVault(RIGHT.toCharArray()));
    }

    @Test
    void noFailuresMeansNoThrottling() {
        UnlockStatus status = vault.unlockStatus();
        assertEquals(0, status.getFailedAttempts());
        assertFalse(status.isThrottled());
        vault.unlockVault(RIGHT.toCharArray());
        assertTrue(vault.isUnlocked());
    }
}
