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
