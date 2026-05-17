package jp.cssj.homare.impl.objects.mathml;

import javax.xml.parsers.ParserConfigurationException;

import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.css.InlineObject;
import jp.cssj.homare.css.InlineObjectFactory;

public class MathMLInlineObjectFactory implements InlineObjectFactory {
	public static final String URI = "http://www.w3.org/1998/Math/MathML";

	public boolean match(CSSElement key) {
		CSSElement ce = (CSSElement) key;
		return URI.equals(ce.uri);
	}

	public InlineObject createInlineObject() {
		try {
			return new MathMLInlineObject();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}
}