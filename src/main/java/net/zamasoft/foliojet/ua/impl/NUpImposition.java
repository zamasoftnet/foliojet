package net.zamasoft.foliojet.ua.impl;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import net.zamasoft.foliojet.layout.imposition.AbstractImposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputNUpOrder;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.imposition.PrinterMarks;
import net.zamasoft.pdfg2d.gc.imposition.Trims;

/**
 * N-up imposition implementation. Scales and places multiple logical pages in a grid on one physical sheet.
 * <p>
 * With automatic paper size (fitPaper), the sheet has the size of "logical page + trim margins,"
 * giving typical printer N-up behavior with each page reduced to 1/N.
 * With an explicit paper size, builds the grid inside its trim margins.
 * Chooses grid rows/columns to maximize the scale factor for the aspect ratio of the sheet's first page.
 * </p>
 */
public class NUpImposition extends AbstractImposition {
	private final int pagesPerSheet;

	private final OutputNUpOrder order;

	private GC gc;

	/** Sheet-wide state (saved at sheet start, restored at sheet end). */
	private GC.State sheetState;

	/** Per-logical-page state. */
	private GC.State pageState;

	/** Next cell index within the sheet. */
	private int cell;

	private int sheetNumber;

	private int rows, cols;

	private double montageWidth, montageHeight;

	public NUpImposition(UserAgent ua, int pagesPerSheet, OutputNUpOrder order) {
		super(ua);
		assert pagesPerSheet >= 2;
		this.pagesPerSheet = pagesPerSheet;
		this.order = order;
	}

	public GC nextPage() throws GraphicsException {
		++this.pageNumber;
		if (this.gc == null) {
			this.nextSheet();
		}
		final double cellWidth = this.montageWidth / this.cols;
		final double cellHeight = this.montageHeight / this.rows;
		final int index = this.cell++;
		final int col, row;
		switch (this.order) {
		case HORIZONTAL_REVERSE:
			row = index / this.cols;
			col = this.cols - 1 - index % this.cols;
			break;
		case VERTICAL:
			col = index / this.rows;
			row = index % this.rows;
			break;
		case VERTICAL_REVERSE:
			col = this.cols - 1 - index / this.rows;
			row = index % this.rows;
			break;
		default:
			row = index / this.cols;
			col = index % this.cols;
			break;
		}
		final double cellX = col * cellWidth;
		final double cellY = row * cellHeight;

		this.pageState = this.gc.begin();
		if (this.clip) {
			this.gc.clip(new Rectangle2D.Double(cellX, cellY, cellWidth, cellHeight));
		}
		// Scale to fit the cell while preserving aspect ratio, and center.
		// Fit the finished size; bleed (trimInset) extends outside it
		// and the cell clip prevents it from spilling into neighboring cells
		final double trimWidth = this.getTrimWidth();
		final double trimHeight = this.getTrimHeight();
		double scale = Math.min(cellWidth / trimWidth, cellHeight / trimHeight);
		if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
			scale = 1;
		}
		this.gc.transform(AffineTransform.getTranslateInstance(cellX + (cellWidth - scale * trimWidth) / 2.0,
				cellY + (cellHeight - scale * trimHeight) / 2.0));
		if (scale != 1) {
			this.gc.transform(AffineTransform.getScaleInstance(scale, scale));
		}
		if (this.trimInset != 0) {
			// Return the origin to the top-left of the print area (content coordinate system)
			this.gc.transform(AffineTransform.getTranslateInstance(-this.trimInset, -this.trimInset));
		}
		return this.gc;
	}

	private void nextSheet() throws GraphicsException {
		++this.sheetNumber;
		// Finalize the grid using paper/page dimensions at the sheet's first page
		this.montageWidth = this.paperWidth - this.trimLeft - this.trimRight;
		this.montageHeight = this.paperHeight - this.trimTop - this.trimBottom;
		this.chooseGrid();

		this.gc = this.ua.nextPage(this.paperWidth, this.paperHeight);
		this.sheetState = this.gc.begin();

		// Treat the entire area inside trim margins as the montage, so no centering translation is needed
		final Trims trims = new Trims(this.trimTop, this.trimRight, this.trimBottom, this.trimLeft,
				this.cuttingMargin);
		if (this.crop) {
			PrinterMarks.drawCrop(this.gc, this.montageWidth, this.montageHeight, trims);
		}
		if (this.cross) {
			PrinterMarks.drawCross(this.gc, this.montageWidth, this.montageHeight, trims);
		}
		PrinterMarks.drawSpine(this.gc, this.montageWidth, this.montageHeight, trims, this.spineWidth);
		if (this.note != null) {
			String text = this.note.format(new Object[] { String.valueOf(this.sheetNumber) });
			PrinterMarks.drawNote(this.gc, this.ua.getDefaultFontPolicy().asFontPolicyList(), text,
					this.montageWidth, trims);
		}

		if (this.trimLeft != 0 || this.trimTop != 0) {
			this.gc.transform(AffineTransform.getTranslateInstance(this.trimLeft, this.trimTop));
		}
		this.cell = 0;
	}

	/**
	 * Determines grid rows/columns. Chooses the factor pair that maximizes the scale factor
	 * for the aspect ratio of the sheet's first page.
	 */
	private void chooseGrid() {
		double best = -1;
		for (int c = 1; c <= this.pagesPerSheet; ++c) {
			if (this.pagesPerSheet % c != 0) {
				continue;
			}
			final int r = this.pagesPerSheet / c;
			final double scale = Math.min((this.montageWidth / c) / this.pageWidth,
					(this.montageHeight / r) / this.pageHeight);
			if (scale > best) {
				best = scale;
				this.cols = c;
				this.rows = r;
			}
		}
	}

	public void closePage() throws GraphicsException {
		this.pageState.close();
		this.pageState = null;
		if (this.cell >= this.pagesPerSheet) {
			this.closeSheet();
		}
	}

	public void finish() throws GraphicsException {
		// Close a sheet ending with a partial set of pages
		if (this.gc != null) {
			this.closeSheet();
		}
	}

	private void closeSheet() throws GraphicsException {
		this.sheetState.close();
		try {
			this.ua.closePage(this.gc);
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			this.gc = null;
			this.sheetState = null;
		}
	}
}
