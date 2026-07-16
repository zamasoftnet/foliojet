package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public interface PageGenerator {
	
	public UserAgent getUserAgent();

	public PageBreakMode getPageSide();

	public PageBox nextPage();

	public void drawPage(PageBox page) throws GraphicsException;

	/**
	 * ソースアンカー(Params.sourceIndex)が本流セグメント窓内の該当要素の
	 * Start イベントを指しているかを検査します(M6b の診断用)。
	 * セグメント窓を持たない実装は常に true を返します。
	 *
	 * @param sourceIndex 窓内のイベント位置
	 * @param element     期待される要素
	 * @return アンカーが整合していれば true
	 */
	public default boolean verifySourceAnchor(int sourceEpoch, int sourceIndex,
			net.zamasoft.foliojet.css.CSSElement element) {
		return true;
	}
}
