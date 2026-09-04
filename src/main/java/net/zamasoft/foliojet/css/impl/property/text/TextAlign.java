package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.TextValueUtils;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextAlign extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new TextAlign();

	public static byte get(CSSStyle style) {
		TextAlignValue value = (TextAlignValue) style.get(INFO);
		return TextValueUtils.toTextAlignParam(value, style);
	}

	protected TextAlign() {
		super("text-align");
	}

	public Value getDefault(CSSStyle style) {
		return TextAlignValue.START_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		TextAlignValue textAlign = (TextAlignValue)value;
		if (textAlign != TextAlignValue.MATCH_PARENT_VALUE) {
			return textAlign;
		}
		CSSStyle parent = style.getParentStyle();
		if (parent == null) {
			return TextAlignValue.START_VALUE;
		}
		textAlign = (TextAlignValue)parent.get(TextAlign.INFO);
		switch (textAlign.getTextAlign()) {
		case TextAlignValue.START:
			return TextValueUtils.usesLegacyRtlAlignment(parent)
					? TextAlignValue.RIGHT_VALUE : TextAlignValue.LEFT_VALUE;
		case TextAlignValue.END:
			return TextValueUtils.usesLegacyRtlAlignment(parent)
					? TextAlignValue.LEFT_VALUE : TextAlignValue.RIGHT_VALUE;
		default:
			return textAlign;
		}
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
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
