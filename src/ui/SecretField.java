package ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;

/** A password field with a show/hide eye button. Hands out {@code char[]}, which callers should wipe after use. */
public final class SecretField extends JPanel {

    private final JPasswordField field = new JPasswordField();
    private final JToggleButton eye = new JToggleButton(Ui.icon("eye", 16));
    private final char hidden;

    public SecretField(int columns) {
        super(new BorderLayout(6, 0));
        setOpaque(false);
        field.setColumns(columns);
        Ui.styleField(field);
        hidden = field.getEchoChar();
        eye.setFocusPainted(false);
        eye.setToolTipText("Show or hide the password");
        eye.putClientProperty("FlatLaf.style", "arc:" + Ui.CONTROL_ARC);
        eye.setPreferredSize(new Dimension(Ui.CONTROL_H, Ui.CONTROL_H));
        eye.addActionListener(e -> setRevealed(eye.isSelected()));
        add(field, BorderLayout.CENTER);
        add(eye, BorderLayout.EAST);
    }

    public JPasswordField field() { return field; }

    public char[] getChars() { return field.getPassword(); }

    public void setChars(char[] c) { field.setText(new String(c)); }

    public void clear() {
        char[] c = field.getPassword();
        Arrays.fill(c, '\0');
        field.setText("");
    }

    public void setRevealed(boolean show) {
        field.setEchoChar(show ? (char) 0 : hidden);
        eye.setSelected(show);
        eye.setIcon(Ui.icon(show ? "eye-off" : "eye", 16));
    }

    public void addChangeListener(Runnable r) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        });
    }

    @Override public void requestFocus() { field.requestFocus(); }
    @Override public boolean requestFocusInWindow() { return field.requestFocusInWindow(); }
}
