package com.doof.passwordmanager.app;

import com.doof.passwordmanager.service.EntryService.EntrySummary;
import com.doof.passwordmanager.service.ApplicationConnector;
import com.doof.passwordmanager.service.InvalidPasswordException;
import com.doof.passwordmanager.service.UnlockThrottledException;
import com.doof.passwordmanager.util.InputValidator;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dashboard {

    private final ApplicationConnector connector;
    private JFrame frame;
    private JTable entriesTable;
    private EntryTableModel tableModel;
    private JButton refreshButton;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton lockButton;
    private JButton changePasswordButton;
    private JTextField searchField;

    private static final int REVEAL_MS = 10_000;
    private static final int CLIPBOARD_CLEAR_MS = 20_000;
    private static final String MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    // Passwords currently shown, by entry id. Decrypted on "Show", wiped on re-mask/refresh/close.
    private final Map<Long, char[]> revealed = new HashMap<>();

    private Runnable onClose;

    public Dashboard(ApplicationConnector connector) {
        this.connector = connector;
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("DOOF - Dashboard");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(900, 600);
            frame.setLocationRelativeTo(null);

            JPanel root = new JPanel(new BorderLayout(8, 8));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchField = new JTextField(24);
            refreshButton = new JButton("Refresh");
            addButton = new JButton("Add");
            editButton = new JButton("Edit");
            deleteButton = new JButton("Delete");
            lockButton = new JButton("Lock");
            changePasswordButton = new JButton("Change password");

            top.add(new JLabel("Search:"));
            top.add(searchField);
            top.add(refreshButton);
            top.add(addButton);
            top.add(editButton);
            top.add(deleteButton);
            top.add(lockButton);
            top.add(changePasswordButton);
            root.add(top, BorderLayout.NORTH);

            tableModel = new EntryTableModel();
            entriesTable = new JTable(tableModel);
            entriesTable.setRowHeight(28);

            entriesTable.getColumnModel().getColumn(2).setCellRenderer(new MaskedPasswordRenderer());
            entriesTable.getColumnModel().getColumn(2).setCellEditor(new MaskToggleEditor(new JCheckBox()));

            root.add(new JScrollPane(entriesTable), BorderLayout.CENTER);

            frame.setContentPane(root);
            frame.setVisible(true);

            refreshButton.addActionListener(e -> refresh());
            addButton.addActionListener(e -> showAddDialog());
            editButton.addActionListener(e -> showEditDialog());
            deleteButton.addActionListener(e -> deleteSelected());
            lockButton.addActionListener(e -> {
                if (connector != null) connector.lockVault();
                close();
            });

            changePasswordButton.addActionListener(e -> showChangePasswordDialog());
            searchField.addActionListener(e -> searchAndRefresh());

            refresh();
        });
    }

    public void close() {
        if (frame != null) {
            SwingUtilities.invokeLater(() -> {
                hideAllRevealed();
                frame.dispose();
                if (onClose != null) onClose.run();
            });
        }
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    private void searchAndRefresh() {
        String q = searchField.getText();
        SwingUtilities.invokeLater(() -> refreshWithQuery(q));
    }

    public void refresh() {
        refreshWithQuery(null);
    }

    private void refreshWithQuery(String query) {
        new SwingWorker<List<EntrySummary>, Void>() {
            @Override
            protected List<EntrySummary> doInBackground() throws Exception {
                if (connector == null) return new ArrayList<>();
                if (query == null || query.trim().isEmpty()) {
                    return connector.listEntries();
                } else {
                    return connector.search(query, 200, 0);
                }
            }

            @Override
            protected void done() {
                try {
                    List<EntrySummary> list = get();
                    hideAllRevealed();
                    tableModel.setFromSummaries(list);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Failed to load entries: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showAddDialog() {
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        JTextField websiteField = new JTextField();
        JTextField emailField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        p.add(new JLabel("Website")); p.add(websiteField);
        p.add(new JLabel("Username")); p.add(emailField);
        p.add(new JLabel("Password")); p.add(passwordField);

        int option = JOptionPane.showConfirmDialog(frame, p, "Add entry", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            char[] pwd = passwordField.getPassword();
            String email = emailField.getText().trim();
            String website = websiteField.getText().trim();
            try {
                InputValidator.validateUsername(email);
                InputValidator.validateWebsite(website);
                InputValidator.validatePassword(pwd);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                Arrays.fill(pwd, '\0');
                return;
            }

            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    if (connector == null) return false;
                    try {
                        connector.addEntry(website, email, pwd, null);
                        return true;
                    } finally {
                        Arrays.fill(pwd, '\0');
                    }
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            refresh();
                        } else {
                            JOptionPane.showMessageDialog(frame, "Failed to add entry.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(frame, "Failed to add entry: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    private void showEditDialog() {
        int row = entriesTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(frame, "Select a row to edit.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = entriesTable.convertRowIndexToModel(row);
        EntryRow er = tableModel.getRow(modelRow);

        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        JTextField websiteField = new JTextField(er.website);
        JTextField emailField = new JTextField(er.email);
        JPasswordField passwordField = new JPasswordField();
        p.add(new JLabel("Website")); p.add(websiteField);
        p.add(new JLabel("Username")); p.add(emailField);
        p.add(new JLabel("New Password (leave blank to keep)")); p.add(passwordField);

        int option = JOptionPane.showConfirmDialog(frame, p, "Edit entry", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            char[] pwd = passwordField.getPassword();
            String email = emailField.getText().trim();
            String website = websiteField.getText().trim();
            try {
                InputValidator.validateUsername(email);
                InputValidator.validateWebsite(website);
                if (pwd != null && pwd.length > 0) InputValidator.validatePassword(pwd);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                Arrays.fill(pwd, '\0');
                return;
            }

            final char[] finalPwd = (pwd != null && pwd.length > 0) ? pwd : null;
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    if (connector == null) return false;
                    try {
                        connector.updateEntry(er.id, website, email, finalPwd, null);
                        return true;
                    } finally {
                        if (finalPwd != null) Arrays.fill(finalPwd, '\0');
                    }
                }

                @Override
                protected void done() {
                    try {
                        if (get()) refresh();
                        else JOptionPane.showMessageDialog(frame, "Failed to update entry.", "Error", JOptionPane.ERROR_MESSAGE);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(frame, "Failed to update entry: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    private void deleteSelected() {
        int row = entriesTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(frame, "Select a row to delete.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = entriesTable.convertRowIndexToModel(row);
        EntryRow er = tableModel.getRow(modelRow);
        int opt = JOptionPane.showConfirmDialog(frame, "Delete entry for " + er.website + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (opt != JOptionPane.YES_OPTION) return;

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                if (connector == null) return false;
                try {
                    connector.deleteEntry(er.id);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    if (get()) refresh();
                    else JOptionPane.showMessageDialog(frame, "Failed to delete entry.", "Error", JOptionPane.ERROR_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Failed to delete entry: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showChangePasswordDialog() {
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        JPasswordField currentField = new JPasswordField();
        JPasswordField newField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();
        p.add(new JLabel("Current master password")); p.add(currentField);
        p.add(new JLabel("New master password")); p.add(newField);
        p.add(new JLabel("Confirm new password")); p.add(confirmField);

        int option = JOptionPane.showConfirmDialog(frame, p, "Change master password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        char[] current = currentField.getPassword();
        char[] next = newField.getPassword();
        char[] confirm = confirmField.getPassword();
        if (option != JOptionPane.OK_OPTION) {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirm, '\0');
            return;
        }
        try {
            if (!Arrays.equals(next, confirm)) {
                throw new IllegalArgumentException("New passwords do not match.");
            }
            InputValidator.validatePassword(next);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            return;
        } finally {
            Arrays.fill(confirm, '\0');
        }

        changePasswordButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                if (connector == null) return "Application not initialized.";
                try {
                    connector.changeMasterPassword(current, next);  // wipes both arrays
                    return null;
                } catch (InvalidPasswordException e) {
                    return "Current password is incorrect (" + e.getFailedAttempts() + " failed attempts). "
                            + "Try again in " + (e.getRetryAfterMs() + 999) / 1000 + "s.";
                } catch (UnlockThrottledException e) {
                    return e.getMessage();
                } catch (Exception e) {
                    return "Failed to change master password; the old password still works.";
                } finally {
                    Arrays.fill(current, '\0');
                    Arrays.fill(next, '\0');
                }
            }

            @Override
            protected void done() {
                changePasswordButton.setEnabled(true);
                try {
                    String error = get();
                    if (error == null) {
                        JOptionPane.showMessageDialog(frame, "Master password changed.", "Done", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(frame, error, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Failed to change master password.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static class EntryRow {
        final long id;
        final String website;
        final String email;

        EntryRow(long id, String website, String email) {
            this.id = id;
            this.website = website;
            this.email = email;
        }
    }

    private class EntryTableModel extends AbstractTableModel {
        private final String[] cols = {"Website", "Username", "Password"};
        private final List<EntryRow> rows = new ArrayList<>();

        void setFromSummaries(List<EntrySummary> summaries) {
            rows.clear();
            for (EntrySummary e : summaries) {
                rows.add(new EntryRow(e.getId(), e.getSite(), e.getUser()));
            }
            fireTableDataChanged();
        }

        EntryRow getRow(int modelRow) { return rows.get(modelRow); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EntryRow r = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return r.website;
                case 1: return r.email;
                case 2: return "";
                default: return "";
            }
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return columnIndex == 2; }
    }

    private class MaskedPasswordRenderer extends JPanel implements TableCellRenderer {
        private final JLabel lbl = new JLabel();
        private final JButton btn = new JButton("Show");

        MaskedPasswordRenderer() {
            setLayout(new BorderLayout());
            lbl.setBorder(BorderFactory.createEmptyBorder(0,6,0,6));
            btn.setFocusable(false);
            btn.setMargin(new Insets(2,6,2,6));
            add(lbl, BorderLayout.CENTER);
            add(btn, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            EntryRow er = tableModel.getRow(modelRow);
            char[] shown = revealed.get(er.id);
            lbl.setText(shown != null ? new String(shown) : MASK);
            return this;
        }
    }

    private class MaskToggleEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JLabel lbl = new JLabel();
        private final JButton btn = new JButton("Show");
        private int currentModelRow = -1;

        MaskToggleEditor(JCheckBox checkBox) {
            lbl.setBorder(BorderFactory.createEmptyBorder(0,6,0,6));
            btn.setFocusable(false);
            btn.setMargin(new Insets(2,6,2,6));
            btn.addActionListener(this);
            panel.add(lbl, BorderLayout.CENTER);
            panel.add(btn, BorderLayout.EAST);
        }

        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentModelRow = table.convertRowIndexToModel(row);
            EntryRow er = tableModel.getRow(currentModelRow);
            char[] shown = revealed.get(er.id);
            lbl.setText(shown != null ? new String(shown) : MASK);
            btn.setText(shown != null ? "Hide" : "Show");
            return panel;
        }

        @Override public Object getCellEditorValue() { return ""; }

        @Override public void actionPerformed(ActionEvent e) {
            stopCellEditing();
            if (currentModelRow < 0) return;
            final long id = tableModel.getRow(currentModelRow).id;
            if (revealed.containsKey(id)) {
                hideRevealed(id);
            } else if (connector != null) {
                try {
                    // Decrypt on demand; the service wipes its copy when this returns.
                    char[] pw = connector.withPassword(id, char[]::clone);
                    revealed.put(id, pw);
                    copyToClipboard(new String(pw));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Failed to reveal password: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Timer remask = new Timer(REVEAL_MS, ev -> hideRevealed(id));
                remask.setRepeats(false);
                remask.start();
                Timer clear = new Timer(CLIPBOARD_CLEAR_MS, ev -> clearClipboard());
                clear.setRepeats(false);
                clear.start();
            }
            tableModel.fireTableDataChanged();
        }
    }

    private void hideRevealed(long id) {
        char[] pw = revealed.remove(id);
        if (pw != null) {
            Arrays.fill(pw, '\0');
            if (tableModel != null) tableModel.fireTableDataChanged();
        }
    }

    private void hideAllRevealed() {
        for (char[] pw : revealed.values()) Arrays.fill(pw, '\0');
        revealed.clear();
    }

    private void copyToClipboard(String s) {
        try {
            StringSelection sel = new StringSelection(s);
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(sel, null);
        } catch (Exception ignored) {}
    }

    private void clearClipboard() {
        try {
            StringSelection sel = new StringSelection("");
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(sel, null);
        } catch (Exception ignored) {}
    }
}
