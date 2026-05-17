package jp.cssj.homare.impl.objects.svg;

import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.css.InlineObject;
import jp.cssj.homare.css.InlineObjectFactory;

public class SVGInlineObjectFactory implements InlineObjectFactory {
	public static final String URI = "http://www.w3.org/2000/svg";

	public boolean match(CSSElement key) {
		return URI.equals(key.uri);
	}

	public InlineObject createInlineObject() {
		return new SVGInlineObject();
	}
}