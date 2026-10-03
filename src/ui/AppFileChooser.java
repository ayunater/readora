package ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * A {@link JFileChooser} that opens inside Readora's own themed dialog
 * window (same title bar, icon and padding as every other dialog) instead
 * of the stock one. It is a drop-in replacement: the same
 * {@code showOpenDialog / showSaveDialog / setFileFilter / ...} calls work.
 * The browsing area itself follows the active theme (rounded controls,
 * theme colours).
 */
public class AppFileChooser extends JFileChooser {

    public AppFileChooser() { super(); init(); }
    public AppFileChooser(File dir) { super(dir); init(); }
    public AppFileChooser(String dir) { super(dir); init(); }

    private void init() {
        setPreferredSize(new Dimension(780, 470));
    }

    @Override
    protected JDialog createDialog(Component parent) {
        String title = getDialogTitle();
        if (title == null) title = getUI().getDialogTitle(this);
        JDialog d = Dialogs.shell(parent, title);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));
        root.add(this, BorderLayout.CENTER);
        d.setContentPane(root);
        Dialogs.onEscape(d, this::cancelSelection);
        d.pack();
        d.setLocationRelativeTo(d.getOwner());
        return d;
    }
}
