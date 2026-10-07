package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.util.List;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * A layer (group image) retained as an SVG fragment (2026-08-29).
 *
 * <p>
 * Layers with opacity, filter, or mix-blend-mode become transparency groups in PDF and rasters
 * in Java2D, but browser-rendered SVG only needs a {@code <g>} wrapper, keeping the contents
 * as vectors. {@code DirectPagedSVGGC.createGroupImage} writes layer contents to a separate
 * buffer, and {@code finish()} turns it into this image.
 * Drawing wraps it in {@code <g transform opacity filter style>} and inserts it.
 * </p>
 *
 * <p>
 * Text within the layer (text positions in page JSON) is recorded in layer coordinates;
 * when the drawing position is known, transform and transfer it to the page ({@link #textRuns}).
 * </p>
 */
final class SVGFragmentImage implements Image {
	private final String svg;
	private final double width, height;
	private final List<PagedSVGResources.TextRun> textRuns;

	SVGFragmentImage(final String svg, final double width, final double height,
			final List<PagedSVGResources.TextRun> textRuns) {
		this.svg = svg;
		this.width = width;
		this.height = height;
		this.textRuns = textRuns;
	}

	String svg() {
		return this.svg;
	}

	/** Text runs recorded in layer coordinates. */
	List<PagedSVGResources.TextRun> textRuns() {
		return this.textRuns;
	}

	@Override
	public double getWidth() {
		return this.width;
	}

	@Override
	public double getHeight() {
		return this.height;
	}

	/** SVG fragments can only be placed in SVG. */
	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		throw new UnsupportedOperationException("SVG fragment can only be drawn to the SVG writer");
	}

	@Override
	public String getAltString() {
		return null;
	}
}
