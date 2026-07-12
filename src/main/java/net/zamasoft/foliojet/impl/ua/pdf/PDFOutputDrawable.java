package net.zamasoft.foliojet.impl.ua.pdf;

import java.io.IOException;

import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/**
 * A zero-size drawable that runs a page-output action at the point it is
 * reached in the paint sequence. Used to emit interactive PDF objects
 * (annotations, form fields) in document order so that, in a tagged document,
 * they nest inside the structure element open at that position rather than at
 * the document root. Emits no visible output and is a no-op for non-PDF output.
 *
 * @author MIYABE Tatsuhiko
 */
final class PDFOutputDrawable implements Drawable {

	/** An action performed against the page output during painting. */
	interface Action {
		void run(PDFPageOutput out) throws IOException;
	}

	private final Action action;

	PDFOutputDrawable(final Action action) {
		this.action = action;
	}

	@Override
	public void draw(final GC gc, final double x, final double y) throws GraphicsException {
		if (gc instanceof PDFGC pdfgc && pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput out) {
			try {
				this.action.run(out);
			} catch (IOException e) {
				throw new GraphicsException(e);
			}
		}
	}
}
