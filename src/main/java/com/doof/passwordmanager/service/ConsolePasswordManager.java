package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.JdbcAccountDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.dao.AccountDao;
import com.doof.passwordmanager.dao.VaultMetadataDao;

import java.util.List;
import java.util.Scanner;
import java.util.Arrays;

public class ConsolePasswordManager {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        VaultMetadataDao vaultDao = new JdbcVaultMetadataDao();
        VaultService vaultService = new VaultService(vaultDao);
        AccountDao accountDao = new JdbcAccountDao();
        AccountService accountService = new AccountService(accountDao, vaultService);

        while (true) {
            printMenu();
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> createVault(vaultService);
                    case "2" -> unlockVault(vaultService);
                    case "3" -> addAccount(accountService);
                    case "4" -> showAccounts(accountService);
                    case "5" -> lockVault(vaultService);
                    case "6" -> {
                        System.out.println("Goodbye!");
                        vaultService.lockVault();
                        return;
                    }
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
        System.out.println("3) Add account");
        System.out.println("4) Show accounts");
        System.out.println("5) Lock vault");
        System.out.println("6) Exit");
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

    private static void addAccount(AccountService accountService) {
        System.out.print("Enter email: ");
        String email = scanner.nextLine();
        System.out.print("Enter website: ");
        String website = scanner.nextLine();
        System.out.print("Enter password: ");
        char[] pass = scanner.nextLine().toCharArray();

        int id = accountService.addAccount(email, website, pass);
        Arrays.fill(pass, '\0');
        System.out.println("Account added (ID: " + id + ")");
    }

    private static void showAccounts(AccountService accountService) {
        List<AccountService.AccountView> list = accountService.getAllAccountsDecrypted();
        if (list.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        System.out.println("\nStored accounts:");
        for (AccountService.AccountView v : list) {
            System.out.printf("ID: %d | Email: %s | Website: %s | Password: %s%n",
                    v.getId(), v.getEmail(), v.getWebsite(), new String(v.getPassword()));
            Arrays.fill(v.getPassword(), '\0');
        }
    }

    private static void lockVault(VaultService vaultService) {
        vaultService.lockVault();
        System.out.println("Vault locked.");
    }
}
