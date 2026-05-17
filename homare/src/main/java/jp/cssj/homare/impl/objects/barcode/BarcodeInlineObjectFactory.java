package jp.cssj.homare.impl.objects.barcode;

import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.css.InlineObject;
import jp.cssj.homare.css.InlineObjectFactory;

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
