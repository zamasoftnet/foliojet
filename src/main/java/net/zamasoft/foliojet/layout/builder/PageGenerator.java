package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public interface PageGenerator {
	
	public UserAgent getUserAgent();

	public PageBreakMode getPageSide();

	public PageBox nextPage();

	/**
	 * 現在のページ名です(名前付きページN2。null=無名)。
	 */
	public default String getPageName() {
		return null;
	}

	/**
	 * 次に生成されるページからのページ名を設定します(名前付きページN2。
	 * scratch計測等のページ概念を持たない実装ではno-op)。
	 */
	public default void setPageName(String pageName) {
	}

	/**
	 * ページを出力します。
	 *
	 * <p>
	 * <b>何も描かないページは出力されません</b>(css-break-3 §4.4、
	 * 2026-07-28)。落とされたページは番号も面(recto/verso)も消費しない
	 * ため、呼び出し側がページの並びを数えているなら返り値を見る必要が
	 * あります。
	 * </p>
	 *
	 * @param page     確定したページ
	 * @param lastPage このページが<b>文書の最後</b>か(2026-07-29新設)。
	 *                 何も描かないページを落としてよいかの判定に使う——
	 *                 最後でなければ後続の内容があるので落としてよいが、
	 *                 最後なら落とすと0ページのPDFになりうる
	 * @param closedByForcedBreak このページを閉じたのが<b>強制改ページ</b>か。
	 *                 先頭要素の{@code page-break-before:always}のように、
	 *                 作者が要求した結果としての白紙は残す
	 * @return 実際に出力したなら true、何も描かないので落としたなら false
	 */
	public boolean drawPage(PageBox page, boolean lastPage, boolean closedByForcedBreak) throws GraphicsException;

	/**
	 * レイアウトソースログを返します(M6b v3)。持たない実装は null。
	 */
	public default net.zamasoft.foliojet.layout.fragment.LayoutSource getLayoutSource() {
		return null;
	}

	/**
	 * live テキストパイプラインの配達済みソース文字終端を返します
	 * (M6b v3)。切断段落の尾部再生はここで打ち切る(それ以降は
	 * live のバッファが供給するため、再生すると二重になる)。
	 */
	public default int getDeliveredCharEnd() {
		return Integer.MAX_VALUE;
	}

	/**
	 * レイアウトソースログを水位で刈り込みます(M6b v3)。
	 * watermark が Long.MAX_VALUE の場合は全て(開いている StartBlock を
	 * 除く)破棄してよいことを意味します。
	 */
	public default void compactLayoutSource(long watermark) {
	}
}
