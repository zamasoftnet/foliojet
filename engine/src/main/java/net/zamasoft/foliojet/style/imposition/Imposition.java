package net.zamasoft.foliojet.style.imposition;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * 面付けを行うインターフェースです。
 * 
 * <p>
 * PagedMediaと異なり、複数のページに並行して描画することはできません。
 * PDF上の同じページに複数のページを面付けするため、nextPageは複数の呼び出しに対して同じグラフィックコンテキストを返すことがあるためです。
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Imposition.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public interface Imposition {
	/**
	 * 左綴じ。
	 */
	public static final byte BOUND_SIDE_LEFT = UserAgent.BOUND_SIDE_LEFT;

	/**
	 * 右綴じ。
	 */
	public static final byte BOUND_SIDE_RIGHT = UserAgent.BOUND_SIDE_RIGHT;

	/**
	 * 中央配置。
	 */
	public static final byte ALIGN_CENTER = 1;

	/**
	 * 用紙にあわせて拡大。
	 */
	public static final byte ALIGN_FIT_TO_PAPER = 2;

	/**
	 * アスペクト比維持拡大
	 */
	public static final byte ALIGN_PRESERVE_ASPECT_RATIO = 3;

	/**
	 * 回転なし。
	 */
	public static final byte AUTO_ROTATE_NONE = 1;

	/**
	 * 内容を回転。
	 */
	public static final byte AUTO_ROTATE_CONTENT = 2;

	/**
	 * 用紙を回転。
	 */
	public static final byte AUTO_ROTATE_PAPER = 3;

	/**
	 * 綴じ方向を返します。
	 * 
	 * @return
	 */
	public byte getBoundSide();

	/**
	 * 綴じ方向を設定します。
	 * 
	 * @param boundSide
	 */
	public void setBoundSide(byte boundSide);

	public byte getAlign();

	public void setAlign(byte align);

	public byte getAutoRotate();

	public void setAutoRotate(byte autoRotate);

	public double getTrimTop();

	public double getTrimRight();

	public double getTrimBottom();

	public double getTrimLeft();

	/**
	 * 断ち代の幅を設定します。
	 * 
	 * @param trimTop
	 * @param trimRight
	 * @param trimBottom
	 * @param trimLeft
	 */
	public void setTrims(double trimTop, double trimRight, double trimBottom, double trimLeft);

	/**
	 * ドブの幅を返します。
	 * 
	 * @return
	 */
	public double getCuttingMargin();

	/**
	 * ドブの幅を設定します。
	 * 
	 * @param cuttingMargin
	 */
	public void setCuttingMargin(double cuttingMargin);

	/**
	 * 背表紙の幅を返します。
	 * 
	 * @return
	 */
	public double getSpineWidth();

	/**
	 * 背表紙の幅を設定します。
	 * 
	 * @param spineWidth
	 */
	public void setSpineWidth(double spineWidth);

	public void fitPaperWidth();

	public double getPaperWidth();

	/**
	 * 用紙幅を設定します。
	 * 
	 * @param paperWidth
	 */
	public void setPaperWidth(double paperWidth);

	public void fitPaperHeight();

	public double getPaperHeight();

	/**
	 * 用紙高さを設定します。
	 * 
	 * @param paperHeight
	 */
	public void setPaperHeight(double paperHeight);

	public String getNote();

	/**
	 * トンボの部分に印刷される注釈を設定します。
	 * 
	 * @param note
	 */
	public void setNote(String note);

	public boolean isCrop();

	/**
	 * クロップマーク(コーナートンボ)の有無を設定します。
	 * 
	 * @param crop
	 */
	public void setCrop(boolean crop);

	public boolean isCross();

	/**
	 * 印刷面をクリッピングするかどうかを設定します。
	 * 
	 * @param clip
	 */
	public void setClip(boolean clip);

	public boolean isClip();

	/**
	 * クロスマーク(センタートンボ)の有無を設定します。
	 * 
	 * @param cross
	 */
	public void setCross(boolean cross);

	/**
	 * ページ幅を設定します。
	 * 
	 * @param width
	 */
	public void setPageWidth(double width);

	/**
	 * ページ高さを設定します。
	 * 
	 * @param height
	 */
	public void setPageHeight(double height);

	public double getPageWidth();

	public double getPageHeight();

	public GC nextPage() throws GraphicsException;

	public CSSElement nextPageSide();

	public void closePage() throws GraphicsException;

	public void finish() throws GraphicsException;
}
