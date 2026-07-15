package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.TextValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.TextAlign;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextAlignLast extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new TextAlignLast();

	public static byte get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.AUTO) {
			final byte align = TextAlign.get(style);
			switch (align) {
			case TextAlignValue.JUSTIFY:
				value = TextAlignValue.START_VALUE;
				break;
			default:
				value = style.get(TextAlign.INFO);
				break;
			}
		}
		return TextValueUtils.toTextAlignParam((TextAlignValue)value, style);
	}

	protected TextAlignLast() {
		super("-cssj-text-align-last");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			return KeywordValue.AUTO;
		}
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			TextAlignValue value = TextValueUtils.toTextAlign(ident);
			if (value != null) {
				return value;
			}
		}
		throw new PropertyException();
	}

}