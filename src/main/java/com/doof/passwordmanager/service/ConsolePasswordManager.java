package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.JdbcEntryDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.dao.EntryDao;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.db.ConnectionManager;

import java.util.List;
import java.util.Scanner;
import java.util.Arrays;

public class ConsolePasswordManager {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        ConnectionManager cm = ConnectionManager.open(ConnectionManager.resolveVaultPath(args));
        VaultMetadataDao vaultDao = new JdbcVaultMetadataDao(cm);
        VaultService vaultService = new VaultService(vaultDao);
        EntryDao entryDao = new JdbcEntryDao(cm);
        EntryService entryService = new EntryService(entryDao, vaultService);

        while (true) {
            printMenu();
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> createVault(vaultService);
                    case "2" -> unlockVault(vaultService);
                    case "3" -> addEntry(entryService);
                    case "4" -> showEntries(entryService);
                    case "5" -> lockVault(vaultService);
                    case "6" -> {
                        System.out.println("Goodbye!");
                        vaultService.lockVault();
                        return;
                    }
                    case "7" -> revealPassword(entryService);
                    default -> System.out.println("Invalid option. Try again.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n=== Password Manager Test ===");
        System.out.println("1) Create vault");
        System.out.println("2) Unlock vault");
        System.out.println("3) Add entry");
        System.out.println("4) List entries");
        System.out.println("5) Lock vault");
        System.out.println("6) Exit");
        System.out.println("7) Reveal password");
        System.out.print("> ");
    }

    private static void createVault(VaultService vaultService) {
        if (vaultService.isVaultInitialized()) {
            System.out.println("Vault already exists.");
            return;
        }
        System.out.print("Enter master password: ");
        char[] master = scanner.nextLine().toCharArray();

        vaultService.createVault(master);
        Arrays.fill(master, '\0');
        System.out.println("Vault created successfully.");
    }

    private static void unlockVault(VaultService vaultService) {
        if (!vaultService.isVaultInitialized()) {
            System.out.println("Vault not initialized yet.");
            return;
        }
        System.out.print("Enter master password: ");
        char[] master = scanner.nextLine().toCharArray();

        vaultService.unlockVault(master);
        Arrays.fill(master, '\0');
        System.out.println("Vault unlocked!");
    }

    private static void addEntry(EntryService entryService) {
        System.out.print("Enter email: ");
        String user = scanner.nextLine();
        System.out.print("Enter website: ");
        String site = scanner.nextLine();
        System.out.print("Enter password: ");
        char[] pass = scanner.nextLine().toCharArray();

        long id = entryService.addEntry(site, user, pass, null);
        System.out.println("Entry added (ID: " + id + ")");
    }

    private static void showEntries(EntryService entryService) {
        List<EntryService.EntrySummary> list = entryService.listEntries();
        if (list.isEmpty()) {
            System.out.println("No entries found.");
            return;
        }
        System.out.println("\nStored entries:");
        for (EntryService.EntrySummary e : list) {
            System.out.printf("ID: %d | Email: %s | Website: %s | Password: ********%n",
                    e.getId(), e.getUser(), e.getSite());
        }
    }

    private static void revealPassword(EntryService entryService) {
        System.out.print("Entry ID: ");
        long id = Long.parseLong(scanner.nextLine().trim());
        entryService.withPassword(id, pw -> {
            System.out.print("Password: ");
            System.out.println(pw);
            return null;
        });
    }

    private static void lockVault(VaultService vaultService) {
        vaultService.lockVault();
        System.out.println("Vault locked.");
    }
}
