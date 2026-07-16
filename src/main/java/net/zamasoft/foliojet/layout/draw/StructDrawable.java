package net.zamasoft.foliojet.layout.draw;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/**
 * A zero-size drawable that opens or closes a tagged-PDF structure element as
 * it is reached in the paint sequence. Inserted around a box's content in
 * document order so that the marked content drawn between a begin and its
 * matching end attaches to the right structure element. It emits no visible
 * output and is a no-op for non-PDF or untagged output.
 *
 * @author MIYABE Tatsuhiko
 */
public final class StructDrawable implements Drawable {

	private static final StructDrawable END = new StructDrawable(null, null);

	/** The structure role to open, or {@code null} for the end marker. */
	private final String role;

	/** Table-header scope ({@code "Row"}/{@code "Column"}), or {@code null}. */
	private final String scope;

	private StructDrawable(final String role, final String scope) {
		this.role = role;
		this.scope = scope;
	}

	/**
	 * Returns a marker that opens a structure element of the given role.
	 *
	 * @param role the structure type (e.g. {@code "H1"}, {@code "P"})
	 * @return the begin marker
	 */
	public static StructDrawable begin(final String role) {
		return new StructDrawable(role, null);
	}

	/**
	 * Returns a marker that opens a table-header ({@code TH}) with a cell scope.
	 *
	 * @param role  the structure type (typically {@code "TH"})
	 * @param scope the header scope ({@code "Row"}, {@code "Column"},
	 *              {@code "Both"})
	 * @return the begin marker
	 */
	public static StructDrawable begin(final String role, final String scope) {
		return new StructDrawable(role, scope);
	}

	/**
	 * Returns the marker that closes the innermost open structure element.
	 *
	 * @return the end marker
	 */
	public static StructDrawable end() {
		return END;
	}

	@Override
	public void draw(final GC gc, final double x, final double y) throws GraphicsException {
		if (gc instanceof PDFGC pdfgc && pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput page) {
			if (this.role == null) {
				page.endStructElement();
			} else if (this.scope != null) {
				page.beginStructElement(this.role, this.scope);
			} else {
				page.beginStructElement(this.role);
			}
		}
	}
}
