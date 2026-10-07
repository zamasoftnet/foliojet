package net.zamasoft.foliojet.formatter;

import jp.cssj.cti2.TranscoderException;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.MultiDocumentOutput;
import net.zamasoft.zstream.resolver.Source;

/**
 * A formatter that accepts multiple documents (EPUB spine items) and lays out
 * each as an independent unit (2026-09-02).
 *
 * <p>
 * If the output is {@link MultiDocumentOutput}, {@code DirectSession} calls this interface,
 * which drives the passes (structure scan → intermediate → final) <b>for each item</b>.
 * Otherwise, the existing {@link Formatter#format} feeds all items sequentially to a single UA.
 * </p>
 */
public interface MultiDocumentFormatter extends Formatter {
	/**
	 * Lays out each document as an independent unit.
	 *
	 * @param source    The entire input (EPUB)
	 * @param ua        The parent UA. Opens a child for each item through {@link MultiDocumentOutput#openDocument}
	 *                  to process that item
	 * @param passCount {@code processing.pass-count}
	 */
	void formatDocuments(Source source, MultiDocumentOutput ua, int passCount)
			throws AbortException, TranscoderException;
}
