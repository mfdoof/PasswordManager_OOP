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

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

SSL/TLS Connection Requirement

This application uses an SSL/TLS-secured connection to communicate with the MySQL database.
Before running the program, make sure that SSL is enabled in your local MySQL server. When SSL is active, all data transferred between the application and the database is encrypted, ensuring that sensitive information such as account credentials remains protected.

Most MySQL installations already include built-in support for SSL/TLS. If you are setting up a new MySQL instance, you can enable SSL using the default configuration or by generating the required certificates during installation. Each user can create their own local certificates — there is no need to share any certificate files between systems.

The application is already configured to use SSL by default through the connection URL specified in ConnectionManager.java. The connection string includes the parameter sslMode=VERIFY_IDENTITY, which enforces secure communication and verifies the identity of the MySQL server. No additional setup is required within the application code; as long as your MySQL server has SSL enabled, the connection will automatically be encrypted and verified.

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
