package net.zamasoft.foliojet.impl.objects.mathml;

import javax.xml.parsers.ParserConfigurationException;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.InlineObjectFactory;

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