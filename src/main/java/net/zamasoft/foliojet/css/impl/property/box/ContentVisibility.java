package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.ContentVisibilityValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * content-visibilityプロパティです(css-contain-2、2026-08-10)。
 * 適用のされ方は{@link ContentVisibilityValue}参照。
 *
 * @author MIYABE Tatsuhiko
 */
public class ContentVisibility extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ContentVisibility();

	public static byte get(CSSStyle style) {
		ContentVisibilityValue value = (ContentVisibilityValue) style.get(INFO);
		return value.getContentVisibility();
	}

	protected ContentVisibility() {
		super("content-visibility");
	}

	public Value getDefault(CSSStyle style) {
		return ContentVisibilityValue.VISIBLE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("visible")) {
				return ContentVisibilityValue.VISIBLE_VALUE;
			} else if (ident.equals("hidden")) {
				return ContentVisibilityValue.HIDDEN_VALUE;
			} else if (ident.equals("auto")) {
				return ContentVisibilityValue.AUTO_VALUE;
			}
		}
		throw new PropertyException();
	}
}
