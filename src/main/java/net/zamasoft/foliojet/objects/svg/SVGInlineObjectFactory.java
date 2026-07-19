package net.zamasoft.foliojet.objects.svg;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.InlineObjectFactory;

public class SVGInlineObjectFactory implements InlineObjectFactory {
	public static final String URI = "http://www.w3.org/2000/svg";

	public boolean match(CSSElement key) {
		return URI.equals(key.uri);
	}

	public InlineObject createInlineObject() {
		return new SVGInlineObject();
	}
}