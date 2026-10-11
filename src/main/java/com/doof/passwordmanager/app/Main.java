package com.doof.passwordmanager.app;

import com.doof.passwordmanager.service.ApplicationConnector;
import com.doof.passwordmanager.util.InputValidator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;

public class Main {

    private final ApplicationConnector appConnector;
    private final Runnable onUnlockSuccess;

    private JFrame frame;
    private JPanel rootPanel;
    private CardLayout cardLayout;

    private static final String CARD_UNLOCK = "card.unlock";
    private static final String CARD_SETUP = "card.setup";

    private JPasswordField unlockPasswordField;
    private JButton unlockButton;
    private JLabel unlockErrorLabel;

    private JPasswordField setupPasswordField;
    private JPasswordField setupConfirmField;
    private JButton createButton;
    private JButton cancelButton;
    private JLabel setupErrorLabel;

    private int failureCount = 0;
    private boolean isProcessing = false;

    private static final int FAILURE_THRESHOLD = 5;
    private static final int INITIAL_LOCKOUT_SECONDS = 30;

    public Main(ApplicationConnector appConnector, Runnable onUnlockSuccess) {
        this.appConnector = appConnector;
        this.onUnlockSuccess = onUnlockSuccess;
    }

    public void show() {
        initFrame();
        initUnlockCard();
        initSetupCard();
        frame.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            boolean initialized;
            try {
                initialized = appConnector != null && appConnector.isVaultInitialized();
            } catch (Exception e) {
                initialized = false;
            }
            cardLayout.show(rootPanel, initialized ? CARD_UNLOCK : CARD_SETUP);
        });
    }

    private void initFrame() {
        frame = new JFrame("DOOF - Password Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);

        rootPanel = new JPanel();
        cardLayout = new CardLayout();
        rootPanel.setLayout(cardLayout);
        frame.add(rootPanel);
    }

    private void initUnlockCard() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel logo = createLabel("DOOF", 48, Font.BOLD);
        JLabel title = createLabel("UNLOCK VAULT", 28, Font.PLAIN, Color.GRAY);
        JLabel subtitle = createLabel("Enter Password", 16, Font.PLAIN);

        unlockPasswordField = createPasswordField();
        unlockButton = createButton("Unlock");
        unlockErrorLabel = createErrorLabel();

        panel.add(Box.createVerticalGlue());
        panel.add(logo);
        panel.add(Box.createVerticalStrut(8));
        panel.add(title);
        panel.add(Box.createVerticalStrut(20));
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(12));
        panel.add(unlockPasswordField);
        panel.add(Box.createVerticalStrut(18));
        panel.add(unlockButton);
        panel.add(Box.createVerticalStrut(10));
        panel.add(unlockErrorLabel);
        panel.add(Box.createVerticalGlue());

        unlockButton.addActionListener(e -> handleUnlockAction());
        unlockPasswordField.addActionListener(e -> handleUnlockAction());

        rootPanel.add(panel, CARD_UNLOCK);
    }

    private void initSetupCard() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel logo = createLabel("DOOF", 48, Font.BOLD);
        JLabel title = createLabel("CREATE MASTER PASSWORD", 22, Font.PLAIN, Color.GRAY);
        JLabel hint = createLabel("Use a long passphrase (min 8 chars, upper+lower+digit)", 12, Font.PLAIN);

        setupPasswordField = createPasswordField();
        setupConfirmField = createPasswordField();
        createButton = createButton("Create");
        cancelButton = new JButton("Cancel");
        cancelButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        setupErrorLabel = createErrorLabel();

        createButton.setEnabled(false);

        panel.add(Box.createVerticalGlue());
        panel.add(logo);
        panel.add(Box.createVerticalStrut(8));
        panel.add(title);
        panel.add(Box.createVerticalStrut(8));
        panel.add(hint);
        panel.add(Box.createVerticalStrut(12));
        panel.add(setupPasswordField);
        panel.add(Box.createVerticalStrut(16));
        panel.add(setupConfirmField);
        panel.add(Box.createVerticalStrut(18));
        panel.add(createButton);
        panel.add(Box.createVerticalStrut(6));
        panel.add(cancelButton);
        panel.add(Box.createVerticalStrut(8));
        panel.add(setupErrorLabel);
        panel.add(Box.createVerticalGlue());

        DocumentListener docListener = new SimpleDocumentListener() {
            @Override public void changed() { validateSetupFields(); }
        };
        setupPasswordField.getDocument().addDocumentListener(docListener);
        setupConfirmField.getDocument().addDocumentListener(docListener);

        createButton.addActionListener(e -> handleCreateVaultAction());
        cancelButton.addActionListener(e -> frame.dispose());

        rootPanel.add(panel, CARD_SETUP);
    }

    private JLabel createLabel(String text, int size, int style) { return createLabel(text, size, style, Color.BLACK); }
    private JLabel createLabel(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        label.setForeground(color);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private JPasswordField createPasswordField() {
        JPasswordField field = new JPasswordField(20);
        field.setMaximumSize(new Dimension(320, 34));
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        field.setAlignmentX(Component.CENTER_ALIGNMENT);
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setBorder(UIManager.getBorder("TextField.border"));
        field.setBackground(Color.WHITE);
        return field;
    }

    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        return btn;
    }

    private JLabel createErrorLabel() {
        JLabel lbl = new JLabel("", SwingConstants.CENTER);
        lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lbl.setForeground(Color.RED);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    private void validateSetupFields() {
        char[] pass = setupPasswordField.getPassword();
        char[] confirm = setupConfirmField.getPassword();
        try {
            if (pass == null || confirm == null || pass.length == 0 || confirm.length == 0) {
                createButton.setEnabled(false);
                setupErrorLabel.setText("");
                return;
            }
            if (!Arrays.equals(pass, confirm)) {
                createButton.setEnabled(false);
                setupErrorLabel.setText("Passwords do not match.");
                return;
            }
            try {
                InputValidator.validatePassword(pass);
                createButton.setEnabled(true);
                setupErrorLabel.setText("");
            } catch (Exception ex) {
                createButton.setEnabled(false);
                setupErrorLabel.setText(ex.getMessage());
            }
        } finally {
            if (pass != null) Arrays.fill(pass, '\0');
            if (confirm != null) Arrays.fill(confirm, '\0');
        }
    }

    private void handleUnlockAction() {
        if (isProcessing) return;
        char[] password = unlockPasswordField.getPassword();
        if (password == null || password.length == 0) {
            unlockErrorLabel.setText("Please enter your password.");
            return;
        }
        unlockErrorLabel.setText("");
        setProcessing(true);

        new SwingWorker<Boolean, Void>() {
            private String error;
            @Override protected Boolean doInBackground() {
                try {
                    if (appConnector == null) {
                        error = "Application not initialized.";
                        return false;
                    }
                    appConnector.unlockVault(password);
                    return true;
                } catch (RuntimeException e) {
                    error = e.getMessage() != null && e.getMessage().contains("Invalid")
                            ? "Incorrect master password."
                            : "Unable to unlock vault.";
                    return false;
                } finally {
                    if (password != null) Arrays.fill(password, '\0');
                }
            }

            @Override protected void done() {
                try {
                    if (get()) {
                        failureCount = 0;
                        SwingUtilities.invokeLater(() -> {
                            frame.setVisible(false);
                            Dashboard d = new Dashboard(appConnector);
                            d.setOnClose(() -> SwingUtilities.invokeLater(() -> {
                                unlockPasswordField.setText("");
                                unlockErrorLabel.setText("");
                                cardLayout.show(rootPanel, CARD_UNLOCK);
                                frame.setVisible(true);
                            }));
                            d.show();
                        });
                    } else {
                        failureCount++;
                        unlockErrorLabel.setText(error);
                        if (failureCount >= FAILURE_THRESHOLD) startLockout(INITIAL_LOCKOUT_SECONDS);
                    }
                } catch (Exception ignored) {}
                setProcessing(false);
            }
        }.execute();
    }

    private void handleCreateVaultAction() {
        if (isProcessing) return;
        char[] pass = setupPasswordField.getPassword();
        char[] confirm = setupConfirmField.getPassword();
        if (pass == null || confirm == null || pass.length == 0 || confirm.length == 0) {
            setupErrorLabel.setText("Please enter and confirm your password.");
            return;
        }
        if (!Arrays.equals(pass, confirm)) {
            setupErrorLabel.setText("Passwords do not match.");
            return;
        }

        setProcessing(true);
        new SwingWorker<Boolean, Void>() {
            private String error;
            @Override protected Boolean doInBackground() {
                try {
                    if (appConnector == null) {
                        error = "Application not initialized.";
                        return false;
                    }
                    appConnector.createVault(pass);
                    return true;
                } catch (RuntimeException e) {
                    error = "Unable to create vault.";
                    return false;
                } finally {
                    if (pass != null) Arrays.fill(pass, '\0');
                    if (confirm != null) Arrays.fill(confirm, '\0');
                }
            }

            @Override protected void done() {
                try {
                    if (get()) {
                        SwingUtilities.invokeLater(() -> {
                            frame.setVisible(false);
                            Dashboard d = new Dashboard(appConnector);
                            d.setOnClose(() -> SwingUtilities.invokeLater(() -> {
                                // reset unlock fields for a clean state
                                unlockPasswordField.setText("");
                                unlockErrorLabel.setText("");
                                cardLayout.show(rootPanel, CARD_UNLOCK);
                                frame.setVisible(true);
                            }));
                            d.show();
                        });
                    } else {
                        setupErrorLabel.setText(error);
                    }
                } catch (Exception ignored) {}
                setProcessing(false);
            }
        }.execute();
    }

    private void startLockout(int seconds) {
        setProcessing(true);
        unlockErrorLabel.setText("Too many attempts. Try again in " + seconds + "s");
        Timer timer = new Timer(1000, null);
        final int[] remaining = {seconds};
        timer.addActionListener(e -> {
            remaining[0]--;
            unlockErrorLabel.setText("Too many attempts. Try again in " + remaining[0] + "s");
            if (remaining[0] <= 0) {
                timer.stop();
                failureCount = 0;
                setProcessing(false);
                unlockErrorLabel.setText("");
            }
        });
        timer.start();
    }

    private void setProcessing(boolean processing) {
        isProcessing = processing;
        if (unlockButton != null) unlockButton.setEnabled(!processing);
        if (createButton != null) createButton.setEnabled(!processing);
        if (cancelButton != null) cancelButton.setEnabled(!processing);
        if (unlockPasswordField != null) unlockPasswordField.setEnabled(!processing);
        if (setupPasswordField != null) setupPasswordField.setEnabled(!processing);
        if (setupConfirmField != null) setupConfirmField.setEnabled(!processing);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ApplicationConnector connector = null;
            try {
                connector = AppBootstrap.createProductionConnector();
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(null, "Warning: Database connection failed. You can still view the UI.\n\n" + t.getMessage(), "Database Warning", JOptionPane.WARNING_MESSAGE);
            }

            Main main = new Main(connector, null);
            main.show();
        });
    }

    private abstract static class SimpleDocumentListener implements DocumentListener {
        public abstract void changed();
        @Override public void insertUpdate(DocumentEvent e) { changed(); }
        @Override public void removeUpdate(DocumentEvent e) { changed(); }
        @Override public void changedUpdate(DocumentEvent e) { changed(); }
    }
}
