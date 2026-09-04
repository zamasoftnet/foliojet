package net.zamasoft.foliojet.layout.draw;

import net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.pdf.StructureRef;

/** A text drawable whose visual leaves belong to one logical bidi line. */
public interface LogicalTextDrawable extends Drawable {
	LogicalLineEmission getLogicalLineEmission();

	String getLineVisualText();

	void drawLogicalText(GC gc, double x, double y, StructureRef structRef, LineTextScope lineScope)
			throws GraphicsException;
}
