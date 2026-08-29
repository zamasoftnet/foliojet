package net.zamasoft.foliojet.layout.imposition;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.props.OutputAutoRotate;
import net.zamasoft.foliojet.ua.props.OutputFitToPaper;

/**
 * 面付けを行うインターフェースです。
 * 
 * <p>
 * PagedMediaと異なり、複数のページに並行して描画することはできません。
 * PDF上の同じページに複数のページを面付けするため、nextPageは複数の呼び出しに対して同じグラフィックコンテキストを返すことがあるためです。
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 */
public interface Imposition {
	/**
	 * 綴じ方向を返します。
	 * 
	 * @return
	 */
	public BoundSide getBoundSide();

	/**
	 * 綴じ方向を設定します。
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
	 * 印刷面の外周のうち、<b>塗り足しとして扱う帯の幅</b>です
	 * (2026-08-29、利用者報告B-3)。ここが0でなければ、仕上り線は
	 * 印刷面の外周からこの幅だけ内側にあるとみなします——つまり
	 * 塗り足し込みで作られた既存データを、CSSを書き換えずに
	 * トンボ付きで出力できます。
	 */
	public double getTrimInset();

	/** @see #getTrimInset() */
	public void setTrimInset(double trimInset);

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
