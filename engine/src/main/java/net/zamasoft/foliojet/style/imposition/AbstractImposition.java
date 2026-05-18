package net.zamasoft.foliojet.style.imposition;

import java.text.MessageFormat;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPrintMode;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.pdf.util.PDFUtils;

public abstract class AbstractImposition implements Imposition {
	protected final UserAgent ua;

	protected int pageNumber = 0;

	protected byte boundSide = BOUND_SIDE_LEFT;

	protected byte printMode;

	protected byte align = ALIGN_CENTER;

	protected byte autoRotate = AUTO_ROTATE_NONE;

	/** クロップマーク(コーナートンボ)。 */
	protected boolean crop = false;

	/** クロスマーク(センタートンボ)。 */
	protected boolean cross = false;

	/** 断ちしろ。 */
	protected double trimTop = 1.0 * PDFUtils.POINTS_PER_CM;
	protected double trimRight = 1.0 * PDFUtils.POINTS_PER_CM;
	protected double trimLeft = 1.0 * PDFUtils.POINTS_PER_CM;
	protected double trimBottom = 1.0 * PDFUtils.POINTS_PER_CM;

	/** 断ちしろのうちドブの幅。 */
	protected double cuttingMargin = PDFUtils.CUTTING_MARGIN_MM * PDFUtils.POINTS_PER_MM;

	/** 背表紙幅。 */
	protected double spineWidth = 0;

	/** ページ幅。 */
	protected double pageWidth = (PDFUtils.PAPER_A4_WIDTH_MM * PDFUtils.POINTS_PER_MM);

	/** ページ高さ。 */
	protected double pageHeight = (PDFUtils.PAPER_A4_HEIGHT_MM * PDFUtils.POINTS_PER_MM);

	/** 用紙幅。 */
	protected double paperWidth;

	/** 用紙高さ。 */
	protected double paperHeight;

	/** 欄外記述。 */
	protected MessageFormat note = null;

	/** クリッピング */
	protected boolean clip = true;

	public AbstractImposition(UserAgent ua) {
		assert ua != null;
		this.ua = ua;
		this.paperWidth = this.pageHeight;
		this.paperHeight = this.pageHeight;
		this.printMode = (byte) UAProps.OUTPUT_PRINT_MODE.getCode(ua);
	}

	public CSSElement nextPageSide() {
		CSSElement pageElement = this.ua.getPassContext().getPageSide();
		switch (this.printMode) {
		case OutputPrintMode.DOUBLE_SIDE:
		case OutputPrintMode.LEFT_SIDE:
		case OutputPrintMode.RIGHT_SIDE:
			// 両面
			if (this.getBoundSide() == Imposition.BOUND_SIDE_LEFT) {
				// 左綴じ
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
				// 右綴じ
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

		case OutputPrintMode.SINGLE_SIDE:
			// 片面
			if (pageElement == null) {
				pageElement = CSSElement.PAGE_SINGLE_FIRST;
			} else {
				pageElement = CSSElement.PAGE_SINGLE;
			}
			break;

		default:
			throw new IllegalStateException();
		}
		this.ua.getPassContext().setPageSide(pageElement);
		return pageElement;
	}

	public final byte getBoundSide() {
		return this.boundSide;
	}

	public final void setBoundSide(byte boundSide) {
		this.boundSide = boundSide;
		switch (this.printMode) {
		case OutputPrintMode.DOUBLE_SIDE:
		case OutputPrintMode.LEFT_SIDE:
		case OutputPrintMode.RIGHT_SIDE:
			// 両面
			if (this.getBoundSide() == Imposition.BOUND_SIDE_LEFT) {
				// 横書き
				this.ua.setBoundSide(UserAgent.BOUND_SIDE_LEFT);
			} else {
				// 縦書き
				this.ua.setBoundSide(UserAgent.BOUND_SIDE_RIGHT);
			}
			break;

		case OutputPrintMode.SINGLE_SIDE:
			break;

		default:
			throw new IllegalStateException();
		}
	}

	public final byte getAlign() {
		return this.align;
	}

	public final void setAlign(byte align) {
		this.align = align;
	}

	public final byte getAutoRotate() {
		return this.autoRotate;
	}

	public final void setAutoRotate(byte autoRotate) {
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
		this.paperWidth = this.pageWidth + this.trimLeft + this.trimRight;
	}

	public final double getPageHeight() {
		return this.pageHeight;
	}

	public final void setPageHeight(double pageHeight) {
		this.pageHeight = pageHeight;
	}

	public void fitPaperHeight() {
		this.paperHeight = this.pageHeight + this.trimTop + this.trimBottom;
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
