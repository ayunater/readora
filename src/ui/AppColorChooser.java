package ui;

import javax.swing.*;
import java.awt.*;

/** Colour picker shown in Readora's own dialog (replaces {@code JColorChooser.showDialog}). */
public final class AppColorChooser {

    private AppColorChooser() { }

    /** @return the chosen colour, or {@code null} if cancelled */
    public static Color pick(Component parent, String title, Color initial) {
        JColorChooser chooser = new JColorChooser(initial != null ? initial : Color.WHITE);
        chooser.setPreviewPanel(new JPanel());           // the swatch/RGB tabs already show the colour
        int r = Dialogs.showConfirmDialog(parent, chooser, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? chooser.getColor() : null;
    }
}
