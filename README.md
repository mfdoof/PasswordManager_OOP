# PasswordManager_OOP
Password Manager (Java OOP + JDBC + MySQL)
A secure Password Manager built using Java (OOP), Swing, and MySQL, designed to demonstrate clean architecture, modular design, and strong data encryption practices. This project implements AES-GCM encryption, PBKDF2 password hashing, and JDBC-based persistence, complete with a simple but functional graphical user interface.

Overview
The Password Manager securely stores user credentials in a local MySQL database, protected by a master password. All stored account passwords are encrypted using AES-GCM, with a session key derived through PBKDF2 from the master password.
Features include: - Vault creation and unlock system - AES-GCM encryption/decryption - PBKDF2-based password hashing - MySQL database integration via JDBC - Object-oriented modular structure - Swing UI (Unlock, Create Vault, Dashboard)

Setup
Requirements: Java 17+, MySQL 8+, MySQL Connector/J (included).
Create the database using: mysql -u root -p < db/schema.sql
Configure database credentials in ConnectionManager.java.
create a dedicated MySQL user for the app.

Run
1. Open the project in IntelliJ IDEA. 2. Run com.doof.passwordmanager.app.Main. 3. On first launch: create or unlock the master vault. 4. Access the dashboard after unlocking.

