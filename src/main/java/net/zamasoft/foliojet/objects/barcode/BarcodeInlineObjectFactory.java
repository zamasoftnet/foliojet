package net.zamasoft.foliojet.objects.barcode;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.InlineObjectFactory;

/**
 * バーコードオブジェクトです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class BarcodeInlineObjectFactory implements InlineObjectFactory {
	private static final String BARCODE_URI = "http://barcode4j.krysalis.org/ns";

	public boolean match(CSSElement key) {
		CSSElement ce = (CSSElement) key;
		return ce.uri.equals(BARCODE_URI);
	}

	public InlineObject createInlineObject() {
		return new BarcodeInlineObject();
	}
}
