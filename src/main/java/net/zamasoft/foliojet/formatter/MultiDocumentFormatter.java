package net.zamasoft.foliojet.formatter;

import jp.cssj.cti2.TranscoderException;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.MultiDocumentOutput;
import net.zamasoft.zstream.resolver.Source;

/**
 * 入力が複数の文書(EPUBのspine項目)からなり、それぞれを独立した単位として
 * 組めるフォーマッタです(2026-09-02)。
 *
 * <p>
 * 出力側が{@link MultiDocumentOutput}なら{@code DirectSession}はこちらを
 * 呼び、パス駆動(構造走査→中間→最終)は<b>項目ごとに</b>この中で回す。
 * そうでなければ従来の{@link Formatter#format}が1つのUAへ全項目を順に流す。
 * </p>
 */
public interface MultiDocumentFormatter extends Formatter {
	/**
	 * 各文書を独立した単位として組みます。
	 *
	 * @param source    入力全体(EPUB)
	 * @param ua        親のUA。項目ごとに{@link MultiDocumentOutput#openDocument}で
	 *                  子を開く
	 * @param passCount {@code processing.pass-count}
	 */
	void formatDocuments(Source source, MultiDocumentOutput ua, int passCount)
			throws AbortException, TranscoderException;
}
