package viewer;

import ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

/** Bottom bar of the viewer: page navigation, zoom and fit buttons. */
final class ViewerControls extends JPanel {

    final JToggleButton pages = new JToggleButton("Pages", true);
    final JButton prev = Ui.squareButton("\u25C0", "Previous page");
    final JButton next = Ui.squareButton("\u25B6", "Next page");
    final JTextField pageField = new JTextField("1", 3);
    final JLabel pageTotal = new JLabel("/ 0");
    final JButton zoomOut = Ui.squareButton("\u2212", "Zoom out");
    final JButton zoomIn = Ui.squareButton("+", "Zoom in");
    final JTextField zoomField = new JTextField("100%", 5);
    final JButton fitWidth = Ui.button("Fit Width");
    final JButton fitPage = Ui.button("Fit Page");

    ViewerControls() {
        super(new FlowLayout(FlowLayout.CENTER, 10, 0));
        setOpaque(false);
        Ui.styleButton(pages);
        for (JTextField f : new JTextField[]{pageField, zoomField}) {
            Ui.styleField(f);
            f.setHorizontalAlignment(SwingConstants.CENTER);
            f.setPreferredSize(new Dimension(f == pageField ? 64 : 76, Ui.CONTROL_H));
        }
        pageTotal.setFont(pageField.getFont());

        add(pages);
        add(Box.createHorizontalStrut(10));
        add(prev);
        add(pageField);
        add(pageTotal);
        add(next);
        add(Box.createHorizontalStrut(10));
        add(zoomOut);
        add(zoomField);
        add(zoomIn);
        add(Box.createHorizontalStrut(10));
        add(fitWidth);
        add(fitPage);
        setEnabledAll(false);
    }

    void setEnabledAll(boolean on) {
        for (Component c : getComponents()) c.setEnabled(on);
    }

    void showPage(int zeroBased, int total) {
        pageField.setText(String.valueOf(zeroBased + 1));
        pageTotal.setText("/ " + total);
    }

    void showZoom(double zoom) { zoomField.setText(Math.round(zoom * 100) + "%"); }

    /** Wires the page box so Enter jumps to the typed page. */
    void onGoto(IntConsumer go) {
        pageField.addActionListener(e -> {
            try { go.accept(Integer.parseInt(pageField.getText().trim()) - 1); }
            catch (NumberFormatException ex) { /* ignore garbage */ }
        });
    }
}
