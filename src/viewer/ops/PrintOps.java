package viewer.ops;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import viewer.PdfSession;

import javax.swing.*;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/** Sends the open PDF to a printer. */
public final class PrintOps extends Ops {

    public PrintOps(PdfSession session, Component parent) { super(session, parent); }

    public void printPdf() {
        if (!requireDocument()) return;
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            if (!job.printDialog()) return;
            // Print a private copy on a background thread; the dialog above was the only UI part.
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    try (PDDocument doc = session.openCopy()) {
                        job.setPageable(new PDFPageable(doc));
                        job.print();
                    }
                    return null;
                }

                @Override protected void done() {
                    try {
                        get();
                        info("Print job sent.", "Print");
                    } catch (Exception ex) {
                        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                        error(t instanceof PrinterException ? "Printing failed" : "Could not print", t);
                    }
                }
            }.execute();
        } catch (Exception e) {
            error("Printing failed", e);
        }
    }
}
