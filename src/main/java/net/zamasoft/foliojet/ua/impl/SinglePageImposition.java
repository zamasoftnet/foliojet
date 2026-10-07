package net.zamasoft.foliojet.ua.impl;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.ua.props.OutputAutoRotate;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import net.zamasoft.foliojet.layout.imposition.AbstractImposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.imposition.PagePlacement;
import net.zamasoft.pdfg2d.gc.imposition.PrinterMarks;
import net.zamasoft.pdfg2d.gc.imposition.Trims;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/**
 * Implements one-up imposition. Delegates placement calculations and crop mark drawing to
 * pdfg2d's {@link PagePlacement} / {@link PrinterMarks}.
 */
public class SinglePageImposition extends AbstractImposition {
	protected GC gc;

	/** State started by nextPage() and restored by closePage(). */
	protected GC.State gcState;

	protected double actualPageWidth, actualPageHeight;

	public SinglePageImposition(UserAgent ua) {
		super(ua);
	}

	private Trims trims() {
		// pdfg2d's PrinterMarks uses the top, bottom, left, and right trim margins and bleed.
		return new Trims(this.trimTop, this.trimRight, this.trimBottom, this.trimLeft, this.cuttingMargin);
	}

	private PagePlacement.Align alignValue() {
		switch (this.align) {
		case FALSE:
			return PagePlacement.Align.CENTER;
		case TRUE:
			return PagePlacement.Align.FIT_TO_PAPER;
		case PRESERVE_ASPECT_RATIO:
			return PagePlacement.Align.PRESERVE_ASPECT_RATIO;
		default:
			throw new IllegalStateException();
		}
	}

	private PagePlacement.AutoRotate autoRotateValue() {
		switch (this.autoRotate) {
		case NONE:
			return PagePlacement.AutoRotate.NONE;
		case CONTENT:
			return PagePlacement.AutoRotate.CONTENT;
		case PAPER:
			return PagePlacement.AutoRotate.PAPER;
		default:
			throw new IllegalStateException();
		}
	}

	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		final Trims trims = this.trims();
		// Imposition uses the finished size (inset from the print area by trimInset).
		// If trimInset is 0, this is the print area itself; the default values stay unchanged here.
		final PagePlacement placement = PagePlacement.compute(this.paperWidth, this.paperHeight, this.getTrimWidth(),
				this.getTrimHeight(), trims, this.alignValue(), this.autoRotateValue());
		this.actualPageWidth = placement.actualPageWidth();
		this.actualPageHeight = placement.actualPageHeight();

		// AUTO_ROTATE_CONTENT rotates the content while keeping the configured paper orientation.
		if (this.autoRotate == OutputAutoRotate.CONTENT) {
			this.gc = this.ua.nextPage(this.paperWidth, this.paperHeight);
		} else {
			this.gc = this.ua.nextPage(placement.actualPaperWidth(), placement.actualPaperHeight());
		}
		this.gcState = this.gc.begin();

		if (placement.rotateContent()) {
			AffineTransform at = AffineTransform.getRotateInstance(-Math.PI / 2.0);
			at.translate(-placement.actualPaperWidth(), 0);
			this.gc.transform(at);
		}

		this.gc.transform(AffineTransform.getTranslateInstance(placement.centerX(), placement.centerY()));

		// Write the finished bounds (TrimBox) and the bounds including bleed (BleedBox) to the PDF
		// (2026-08-30, user report E-6). Always set them, regardless of crop marks,
		// so the imposition process can identify the trim line programmatically.
		this.setPageBoxes(placement);

		// Draw crop marks and page numbers.
		this.drawMarks(trims);
		if (this.note != null) {
			String text = this.note.format(new Object[] { String.valueOf(this.pageNumber) });
			PrinterMarks.drawNote(this.gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), text,
					this.actualPageWidth, trims);
		}

		// Offset for crop marks. If trimInset is set, offset further so the top-left of
		// the print area lies trimInset outside the trim line.
		// The origin is always the top-left of the print area (the content coordinate system).
		final double ox = this.trimLeft - this.trimInset;
		final double oy = this.trimTop - this.trimInset;
		if (ox != 0 || oy != 0) {
			this.gc.transform(AffineTransform.getTranslateInstance(ox, oy));
		}

		// Clipping region. The origin is the top-left of the print area, so if trimInset is set,
		// shift -cuttingMargin, measured from the trim line, right and down by that amount.
		// Without this, **only the bottom-right bleed is clipped**.
		// (Observed: on a 156 pt wide sheet, the left 5 pt strip appeared but the right strip disappeared.)
		double bgX = this.trimInset - this.cuttingMargin;
		double bgY = this.trimInset - this.cuttingMargin;
		double bgW = this.getTrimWidth() + this.cuttingMargin * 2.0;
		double bgH = this.getTrimHeight() + this.cuttingMargin * 2.0;

		switch (this.align) {
		case FALSE: {
			// Clip the drawable area.
			if (this.clip) {
				this.gc.clip(new Rectangle2D.Double(bgX, bgY, bgW, bgH));
			}
		}
			break;
		case TRUE:
		case PRESERVE_ASPECT_RATIO: {
			double hscale = placement.hscale();
			double vscale = placement.vscale();

			// Clip the drawable area.
			if (this.clip) {
				this.gc.clip(new Rectangle2D.Double(bgX, bgY, bgW * hscale, bgH * vscale));
			}

			// Scale to fit the page.
			if (hscale != 0 && vscale != 0) {
				this.gc.transform(AffineTransform.getScaleInstance(hscale, vscale));
			}
		}
			break;
		default:
			throw new IllegalArgumentException();
		}

		return this.gc;
	}

	public void closePage() throws GraphicsException {
		this.gcState.close();
		try {
			this.ua.closePage(this.gc);
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.gc = null;
			this.gcState = null;
		}
	}

	/**
	 * Sets the finished bounds as {@code TrimBox} and the bounds including bleed as {@code BleedBox}
	 * on the PDF page (2026-08-30, user report E-6).
	 *
	 * <p>
	 * Without these, imposition and printing workflows could not identify the trim line programmatically,
	 * so users had to add them afterward with tools such as PyMuPDF.
	 * Pass coordinates with a <b>top-left origin</b>, as required by
	 * {@link net.zamasoft.pdfg2d.pdf.PDFPageOutput}
	 * (the writer converts them to PDF's bottom-left origin).
	 * </p>
	 *
	 * <p>
	 * Do not set them when {@code output.auto-rotate} rotates the content for placement,
	 * because the mapping between paper and content coordinates is not straightforward.
	 * PDF 1.3 and earlier cannot have TrimBox/BleedBox, so silently skip those versions as well.
	 * </p>
	 */
	private void setPageBoxes(final PagePlacement placement) {
		if (placement.rotateContent()) {
			return;
		}
		if (!(net.zamasoft.foliojet.layout.util.DelegatingGC.unwrap(this.gc) instanceof PDFGC pdfgc)
				|| !(pdfgc.getPDFGraphicsOutput() instanceof PDFPageOutput out)) {
			return;
		}
		final double trimWidth = this.getTrimWidth(), trimHeight = this.getTrimHeight();
		if (!(trimWidth > 0 && trimHeight > 0)) {
			return;
		}
		final double paperWidth = placement.actualPaperWidth(), paperHeight = placement.actualPaperHeight();
		final double trimX = placement.centerX() + this.trimLeft;
		final double trimY = placement.centerY() + this.trimTop;
		final Rectangle2D trim = new Rectangle2D.Double(trimX, trimY, trimWidth, trimHeight);
		// Bleed extends outside the finished bounds by the bleed margin. Clamp it to the paper bounds.
		final double bleedX = Math.max(0, trimX - this.cuttingMargin);
		final double bleedY = Math.max(0, trimY - this.cuttingMargin);
		final Rectangle2D bleed = new Rectangle2D.Double(bleedX, bleedY,
				Math.min(paperWidth, trimX + trimWidth + this.cuttingMargin) - bleedX,
				Math.min(paperHeight, trimY + trimHeight + this.cuttingMargin) - bleedY);
		try {
			out.setBleedBox(bleed);
			out.setTrimBox(trim);
		} catch (final UnsupportedOperationException e) {
			// PDF 1.3 or earlier: finished bounds cannot be represented, so do nothing.
		}
	}

	protected final void drawMarks(Trims trims) throws GraphicsException {
		// Crop marks
		if (this.crop) {
			PrinterMarks.drawCrop(this.gc, this.actualPageWidth, this.actualPageHeight, trims);
		}
		if (this.cross) {
			PrinterMarks.drawCross(this.gc, this.actualPageWidth, this.actualPageHeight, trims);
		}

		// Spine
		PrinterMarks.drawSpine(this.gc, this.actualPageWidth, this.actualPageHeight, trims, this.spineWidth);
	}
}
