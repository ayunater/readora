package viewer.ops;

import org.apache.pdfbox.pdmodel.PDDocument;
import ui.Ui;
import viewer.PdfSession;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Full-text search. All hits are highlighted on the pages at once; picking
 * a result in the list jumps to it and marks it as the current hit.
 */
public final class SearchOps extends Ops {

    private final BiConsumer<List<HighlightArea>, HighlightArea> showHits;
    private final java.util.function.Consumer<HighlightArea> jumpTo;

    public SearchOps(PdfSession session, Component parent,
                     BiConsumer<List<HighlightArea>, HighlightArea> showHits,
                     java.util.function.Consumer<HighlightArea> jumpTo) {
        super(session, parent);
        this.showHits = showHits;
        this.jumpTo = jumpTo;
    }

    public void search() {
        if (!requireDocument()) return;
        String q = JOptionPane.showInputDialog(parent, "Enter text to search", "Search Text",
                JOptionPane.PLAIN_MESSAGE);
        if (q == null) return;
        q = q.trim();
        if (q.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Search text cannot be empty.");
            return;
        }
        final String query = q;
        background("Searching document...", () -> {
            try (PDDocument doc = session.openCopy()) {
                SearchStripper s = new SearchStripper(query);
                s.getText(doc);
                return s.getResults();
            }
        }, results -> present(query, results), "Search failed");
    }

    /** Removes search highlights from the page view. */
    public void clearHighlights() { showHits.accept(List.of(), null); }

    private void present(String query, List<SearchResult> results) {
        if (results.isEmpty()) {
            showHits.accept(List.of(), null);
            info("No results found for \"" + query + "\".", "Search Text");
            return;
        }
        List<HighlightArea> all = new ArrayList<>();
        for (SearchResult r : results) all.add(r.getHighlight());
        showHits.accept(all, all.get(0));
        jumpTo.accept(all.get(0));

        DefaultListModel<SearchResult> model = new DefaultListModel<>();
        results.forEach(model::addElement);
        JList<SearchResult> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.addListSelectionListener(e -> {
            SearchResult r = list.getSelectedValue();
            if (!e.getValueIsAdjusting() && r != null) {
                showHits.accept(all, r.getHighlight());
                jumpTo.accept(r.getHighlight());
            }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(560, 320));

        // Non-modal, so the page behind stays visible and scrollable while you step through hits.
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dlg = new JDialog(owner, results.size() + " result(s) for \"" + query + "\"", Dialog.ModalityType.MODELESS);
        JButton close = Ui.button("Close");
        close.addActionListener(e -> dlg.dispose());
        dlg.add(sp, BorderLayout.CENTER);
        dlg.add(Ui.buttonBar(close), BorderLayout.SOUTH);
        ((JComponent) dlg.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }
}
