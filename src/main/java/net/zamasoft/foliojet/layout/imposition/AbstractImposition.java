package net.zamasoft.foliojet.layout.imposition;

import java.text.MessageFormat;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputAutoRotate;
import net.zamasoft.foliojet.ua.props.OutputFitToPaper;
import net.zamasoft.foliojet.ua.props.OutputPrintMode;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;
import net.zamasoft.foliojet.ua.BoundSide;

public abstract class AbstractImposition implements Imposition {
	protected final UserAgent ua;

	protected int pageNumber = 0;

	protected BoundSide boundSide = BoundSide.LEFT;

	protected OutputPrintMode printMode;

	protected OutputFitToPaper align = OutputFitToPaper.FALSE;

	protected OutputAutoRotate autoRotate = OutputAutoRotate.NONE;

	/** Crop marks (corner marks). */
	protected boolean crop = false;

	/** Cross marks (center marks). */
	protected boolean cross = false;

	/** Trim allowance. */
	protected double trimTop = 1.0 * PDFUtils.POINTS_PER_CM;
	protected double trimRight = 1.0 * PDFUtils.POINTS_PER_CM;
	protected double trimLeft = 1.0 * PDFUtils.POINTS_PER_CM;
	protected double trimBottom = 1.0 * PDFUtils.POINTS_PER_CM;

	/** Cutting margin within the trim allowance. */
	protected double cuttingMargin = PDFUtils.CUTTING_MARGIN_MM * PDFUtils.POINTS_PER_MM;

	/**
	 * Width of the band along the printable area's perimeter treated as bleed
	 * (2026-08-29, user report B-3; default 0 means the printable area is the finished size).
	 */
	protected double trimInset = 0;

	/** Spine width. */
	protected double spineWidth = 0;

	/** Page width. */
	protected double pageWidth = (PDFUtils.PAPER_A4_WIDTH_MM * PDFUtils.POINTS_PER_MM);

	/** Page height. */
	protected double pageHeight = (PDFUtils.PAPER_A4_HEIGHT_MM * PDFUtils.POINTS_PER_MM);

	/** Paper width. */
	protected double paperWidth;

	/** Paper height. */
	protected double paperHeight;

	/** Marginal note. */
	protected MessageFormat note = null;

	/** Clipping */
	protected boolean clip = true;

	public AbstractImposition(UserAgent ua) {
		assert ua != null;
		this.ua = ua;
		this.paperWidth = this.pageHeight;
		this.paperHeight = this.pageHeight;
		this.printMode = UAProps.OUTPUT_PRINT_MODE.get(ua);
	}

	public CSSElement nextPageSide() {
		final CSSElement pageElement = this.getNextPageSide(this.ua.getPassContext().getPageSide());
		this.ua.getPassContext().setPageSide(pageElement);
		return pageElement;
	}

	@Override
	public CSSElement getNextPageSide(CSSElement pageElement) {
		switch (this.printMode) {
		case DOUBLE_SIDE:
		case LEFT_SIDE:
		case RIGHT_SIDE:
			// Double-sided
			if (this.getBoundSide() == BoundSide.LEFT) {
				// Left binding
				if (pageElement == null) {
					pageElement = CSSElement.PAGE_FIRST_RIGHT;
				} else if (pageElement == CSSElement.PAGE_FIRST_RIGHT) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				} else if (pageElement == CSSElement.PAGE_LEFT_EVEN) {
					pageElement = CSSElement.PAGE_RIGHT_ODD;
				} else if (pageElement == CSSElement.PAGE_RIGHT_ODD) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				}
			} else {
				// Right binding
				if (pageElement == null) {
					pageElement = CSSElement.PAGE_FIRST_LEFT;
				} else if (pageElement == CSSElement.PAGE_FIRST_LEFT) {
					pageElement = CSSElement.PAGE_RIGHT_EVEN;
				} else if (pageElement == CSSElement.PAGE_RIGHT_EVEN) {
					pageElement = CSSElement.PAGE_LEFT_ODD;
				} else if (pageElement == CSSElement.PAGE_LEFT_ODD) {
					pageElement = CSSElement.PAGE_RIGHT_EVEN;
				}
			}
			break;

		case SINGLE_SIDE:
			// Single-sided
			if (pageElement == null) {
				pageElement = CSSElement.PAGE_SINGLE_FIRST;
			} else {
				pageElement = CSSElement.PAGE_SINGLE;
			}
			break;

		default:
			throw new IllegalStateException();
		}
		return pageElement;
	}

	public final BoundSide getBoundSide() {
		return this.boundSide;
	}

	public final void setBoundSide(BoundSide boundSide) {
		this.boundSide = boundSide;
		switch (this.printMode) {
		case DOUBLE_SIDE:
		case LEFT_SIDE:
		case RIGHT_SIDE:
			// Double-sided
			if (this.getBoundSide() == BoundSide.LEFT) {
				// Horizontal writing
				this.ua.setBoundSide(BoundSide.LEFT);
			} else {
				// Vertical writing
				this.ua.setBoundSide(BoundSide.RIGHT);
			}
			break;

		case SINGLE_SIDE:
			break;

		default:
			throw new IllegalStateException();
		}
	}

	public final OutputFitToPaper getAlign() {
		return this.align;
	}

	public final void setAlign(OutputFitToPaper align) {
		this.align = align;
	}

	public final OutputAutoRotate getAutoRotate() {
		return this.autoRotate;
	}

	public final void setAutoRotate(OutputAutoRotate autoRotate) {
		this.autoRotate = autoRotate;
	}

	public final double getTrimTop() {
		return this.trimTop;
	}

	public final double getTrimRight() {
		return this.trimRight;
	}

	public final double getTrimBottom() {
		return this.trimBottom;
	}

	public final double getTrimLeft() {
		return this.trimLeft;
	}

	public final void setTrims(double trimTop, double trimRight, double trimBottom, double trimLeft) {
		this.trimTop = trimTop;
		this.trimRight = trimRight;
		this.trimBottom = trimBottom;
		this.trimLeft = trimLeft;
	}

	public final double getCuttingMargin() {
		return this.cuttingMargin;
	}

	public final void setCuttingMargin(double cuttingMargin) {
		this.cuttingMargin = cuttingMargin;
	}

	public final double getTrimInset() {
		return this.trimInset;
	}

	public final void setTrimInset(double trimInset) {
		this.trimInset = trimInset;
	}

	/**
	 * The finished width. Inset by {@link #getTrimInset()} from the printable area:
	 * crop marks are drawn here, and the paper size adds the trim allowance to this size.
	 */
	public final double getTrimWidth() {
		return Math.max(0, this.pageWidth - this.trimInset * 2.0);
	}

	/** @see #getTrimWidth() */
	public final double getTrimHeight() {
		return Math.max(0, this.pageHeight - this.trimInset * 2.0);
	}

	public final double getSpineWidth() {
		return this.spineWidth;
	}

	public final void setSpineWidth(double spineWidth) {
		this.spineWidth = spineWidth;
	}

	public final double getPageWidth() {
		return this.pageWidth;
	}

	public final void setPageWidth(double pageWidth) {
		this.pageWidth = pageWidth;
	}

	public void fitPaperWidth() {
		this.paperWidth = this.getTrimWidth() + this.trimLeft + this.trimRight;
	}

	public final double getPageHeight() {
		return this.pageHeight;
	}

	public final void setPageHeight(double pageHeight) {
		this.pageHeight = pageHeight;
	}

	public void fitPaperHeight() {
		this.paperHeight = this.getTrimHeight() + this.trimTop + this.trimBottom;
	}

	public final double getPaperWidth() {
		return this.paperWidth;
	}

	public final void setPaperWidth(double paperWidth) {
		this.paperWidth = paperWidth;
	}

	public final double getPaperHeight() {
		return this.paperHeight;
	}

	public final void setPaperHeight(double paperHeight) {
		this.paperHeight = paperHeight;
	}

	public final String getNote() {
		if (this.note == null) {
			return null;
		}
		return this.note.toPattern();
	}

	public final void setNote(String note) {
		if (note == null) {
			this.note = null;
			return;
		}
		this.note = new MessageFormat(note);
	}

	public final boolean isCrop() {
		return this.crop;
	}

	public final void setCrop(boolean crop) {
		this.crop = crop;
	}

	public final boolean isCross() {
		return this.cross;
	}

	public final void setCross(boolean cross) {
		this.cross = cross;
	}

	public final boolean isClip() {
		return this.clip;
	}

	public final void setClip(boolean clip) {
		this.clip = clip;
	}

	public void finish() throws GraphicsException {
		// ignore
	}
}
