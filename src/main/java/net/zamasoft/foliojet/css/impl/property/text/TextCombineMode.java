package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextCombineValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Internal property carrying the type of tate-chu-yoko (2026-08-11).
 *
 * <p>
 * Set when the {@code -cssj-text-combine} shorthand (including its standard-name alias {@code
 * text-combine-upright}) expands. Not intended for authors to write directly,
 * but kept parseable like other internal properties
 * (values are {@code none}/{@code horizontal}/{@code all}).
 * Not inherited; tate-chu-yoko applies only to the specified element.
 * </p>
 *
 * @see net.zamasoft.foliojet.css.value.TextCombineValue
 *
 * @author MIYABE Tatsuhiko
 */
public class TextCombineMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextCombineMode();

	public static byte get(CSSStyle style) {
		TextCombineValue value = (TextCombineValue) style.get(INFO);
		return value.getTextCombine();
	}

	protected TextCombineMode() {
		super("-cssj-text-combine-mode");
	}

	public Value getDefault(CSSStyle style) {
		return TextCombineValue.NONE_VALUE;
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
			if (ident.equals("none")) {
				return TextCombineValue.NONE_VALUE;
			} else if (ident.equals("horizontal")) {
				return TextCombineValue.HORIZONTAL_VALUE;
			} else if (ident.equals("all")) {
				return TextCombineValue.ALL_VALUE;
			}
		}
		throw new PropertyException();
	}
}
