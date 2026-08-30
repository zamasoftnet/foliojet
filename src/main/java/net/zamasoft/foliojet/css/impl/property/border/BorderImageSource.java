package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code border-image-source: none | <image>} です。 */
public final class BorderImageSource extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderImageSource();

	private BorderImageSource() {
		super("border-image-source");
	}

	public static Value get(CSSStyle style) {
		return style.get(INFO);
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (token == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		final Value value = BackgroundImage.parseLayer(ua, uri, token);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
