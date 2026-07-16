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

	/**
	 * ソースアンカーの指す閉じた部分木をソースイベントから再駆動します
	 * (M6b segment-restyle)。改ページ残余のうち「丸ごと次ページへ移動した
	 * 閉じた要素」をボックス再生の代わりに再スタイル+再レイアウトします。
	 * セグメント窓を持たない実装は常に false(非対応)を返します。
	 *
	 * @param sourceEpoch アンカーの窓世代
	 * @param sourceIndex 窓内の Start イベント位置
	 * @param element     期待される要素(整合検査)
	 * @return 再駆動した場合 true。false なら呼び出し側がボックス再生で
	 *         フォールバックする
	 */
	public default boolean replaySubtree(int sourceEpoch, int sourceIndex,
			net.zamasoft.foliojet.css.CSSElement element) {
		return false;
	}

	/**
	 * 切断された段落の尾部(charOffset 以降)をソースイベントから
	 * 再駆動します(M6b Phase B)。chainElement は段落を含む開いている
	 * チェーン要素(アンカー解決は要素同一性で行い、窓世代に依存しない)。
	 * endIndex が負なら窓末尾まで。非対応実装は false を返します。
	 */
	public default boolean replayTextTail(net.zamasoft.foliojet.css.CSSElement chainElement, int charOffset,
			int endEpoch, int endIndex) {
		return false;
	}

	/**
	 * レイアウトソースログを返します(M6b v3)。持たない実装は null。
	 */
	public default net.zamasoft.foliojet.layout.fragment.LayoutSource getLayoutSource() {
		return null;
	}

	/**
	 * レイアウトソースログを水位で刈り込みます(M6b v3)。
	 * watermark が Long.MAX_VALUE の場合は全て(開いている StartBlock を
	 * 除く)破棄してよいことを意味します。
	 */
	public default void compactLayoutSource(long watermark) {
	}
}
