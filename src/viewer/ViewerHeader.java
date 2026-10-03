package viewer;

import ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/** Top strip of the viewer: file name + details on the left, actions on the right. */
final class ViewerHeader extends JPanel {

    final JButton open = Ui.button("Open PDF");
    final JButton organize = Ui.button("Organize");
    final JButton star = Ui.button("Star");
    final JButton reload = Ui.button("Reload");
    private final Ui.Header header = Ui.header("Viewer", "Open a PDF to get started");

    ViewerHeader() {
        super(new BorderLayout(Ui.GAP, 0));
        setOpaque(false);
        add(header, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(open);
        actions.add(organize);
        actions.add(star);
        actions.add(reload);
        add(actions, BorderLayout.EAST);
        setDocumentActionsEnabled(false);
    }

    void showFile(File f, int pages) {
        header.title.setText(f.getName());
        header.subtitle.setText(pages + (pages == 1 ? " Page" : " Pages") + " \u2022 " + size(f.length()));
        setDocumentActionsEnabled(true);
    }

    void showNothing() {
        header.title.setText("Viewer");
        header.subtitle.setText("Open a PDF to get started");
        setDocumentActionsEnabled(false);
    }

    void setStarred(boolean starred) { star.setText(starred ? "Unstar" : "Star"); }

    private void setDocumentActionsEnabled(boolean on) {
        organize.setEnabled(on);
        star.setEnabled(on);
        reload.setEnabled(on);
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
