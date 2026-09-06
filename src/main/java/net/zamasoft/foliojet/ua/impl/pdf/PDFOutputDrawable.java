package net.zamasoft.foliojet.ua.impl.pdf;

import java.io.IOException;

import net.zamasoft.foliojet.layout.draw.PageOutputDrawable;
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
final class PDFOutputDrawable implements PageOutputDrawable {

	/** An action performed against the page output during painting. */
	interface Action {
		void run(PDFPageOutput out) throws IOException;
	}

	private final Action action;
	/** D7 が描画時点の注釈・フォーム属性を比較するための値。遅延構築物も共有する。 */
	private final Object[] digestValues;

	PDFOutputDrawable(final Action action, final Object[] digestValues) {
		this.action = action;
		this.digestValues = digestValues;
	}

	@Override
	public void draw(final GC gc, final double x, final double y) throws GraphicsException {
		if (net.zamasoft.foliojet.layout.util.DelegatingGC.unwrap(gc) instanceof PDFGC pdfgc
				&& pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput out) {
			try {
				this.action.run(out);
			} catch (IOException e) {
				throw new GraphicsException(e);
			}
		}
	}
}
