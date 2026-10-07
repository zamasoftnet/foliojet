package net.zamasoft.foliojet.layout.imposition;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.props.OutputAutoRotate;
import net.zamasoft.foliojet.ua.props.OutputFitToPaper;

/**
 * An interface for imposition.
 *
 * <p>
 * Unlike PagedMedia, it does not allow concurrent drawing on multiple pages.
 * Since multiple pages are imposed on the same PDF page, multiple calls to nextPage may return the
 * same graphics context.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface Imposition {
	/**
	 * Returns the binding side.
	 *
	 * @return
	 */
	public BoundSide getBoundSide();

	/**
	 * Sets the binding side.
	 *
	 * @param boundSide
	 */
	public void setBoundSide(BoundSide boundSide);

	public OutputFitToPaper getAlign();

	public void setAlign(OutputFitToPaper align);

	public OutputAutoRotate getAutoRotate();

	public void setAutoRotate(OutputAutoRotate autoRotate);

	public double getTrimTop();

	public double getTrimRight();

	public double getTrimBottom();

	public double getTrimLeft();

	/**
	 * Sets the trim allowance widths.
	 *
	 * @param trimTop
	 * @param trimRight
	 * @param trimBottom
	 * @param trimLeft
	 */
	public void setTrims(double trimTop, double trimRight, double trimBottom, double trimLeft);

	/**
	 * Returns the cutting margin width.
	 *
	 * @return
	 */
	public double getCuttingMargin();

	/**
	 * The <b>width of the band treated as bleed</b> along the printable area's perimeter
	 * (2026-08-29, user report B-3). When nonzero, the trim line is considered inset from the printable area's
	 * perimeter by this width. This lets existing data created with bleed be output with crop marks
	 * without changing the CSS.
	 */
	public double getTrimInset();

	/** @see #getTrimInset() */
	public void setTrimInset(double trimInset);

	/**
	 * Sets the cutting margin width.
	 *
	 * @param cuttingMargin
	 */
	public void setCuttingMargin(double cuttingMargin);

	/**
	 * Returns the spine width.
	 *
	 * @return
	 */
	public double getSpineWidth();

	/**
	 * Sets the spine width.
	 *
	 * @param spineWidth
	 */
	public void setSpineWidth(double spineWidth);

	public void fitPaperWidth();

	public double getPaperWidth();

	/**
	 * Sets the paper width.
	 *
	 * @param paperWidth
	 */
	public void setPaperWidth(double paperWidth);

	public void fitPaperHeight();

	public double getPaperHeight();

	/**
	 * Sets the paper height.
	 *
	 * @param paperHeight
	 */
	public void setPaperHeight(double paperHeight);

	public String getNote();

	/**
	 * Sets the note printed in the crop-mark area.
	 *
	 * @param note
	 */
	public void setNote(String note);

	public boolean isCrop();

	/**
	 * Sets whether to draw crop marks (corner marks).
	 *
	 * @param crop
	 */
	public void setCrop(boolean crop);

	public boolean isCross();

	/**
	 * Sets whether to clip the printable area.
	 *
	 * @param clip
	 */
	public void setClip(boolean clip);

	public boolean isClip();

	/**
	 * Sets whether to draw cross marks (center marks).
	 *
	 * @param cross
	 */
	public void setCross(boolean cross);

	/**
	 * Sets the page width.
	 *
	 * @param width
	 */
	public void setPageWidth(double width);

	/**
	 * Sets the page height.
	 *
	 * @param height
	 */
	public void setPageHeight(double height);

	public double getPageWidth();

	public double getPageHeight();

	public GC nextPage() throws GraphicsException;

	public CSSElement nextPageSide();

	/** Finds the next side under the current imposition rules. Does not update the UA's side or page number. */
	public CSSElement getNextPageSide(CSSElement pageElement);

	public void closePage() throws GraphicsException;

	public void finish() throws GraphicsException;
}
