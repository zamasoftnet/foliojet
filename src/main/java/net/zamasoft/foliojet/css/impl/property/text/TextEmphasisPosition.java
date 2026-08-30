package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextEmphasisPositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-emphasis-position}(css-text-decor-3)です。
 * {@code [ over | under ] && [ right | left ]}を順不同で受け付け、
 * 省略された成分は初期値の{@code over right}で補います。
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextEmphasisPosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextEmphasisPosition();

	public static TextEmphasisPositionValue get(final CSSStyle style) {
		return (TextEmphasisPositionValue) style.get(INFO);
	}

	private TextEmphasisPosition() {
		super("text-emphasis-position");
	}

	public Value getDefault(final CSSStyle style) {
		return TextEmphasisPositionValue.OVER_RIGHT;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		boolean under = false, left = false;
		boolean linePositionSet = false, sideSet = false;
		int count = 0;
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (!(token instanceof CssToken.Ident ident)) {
				throw new PropertyException();
			}
			++count;
			switch (ident.lower()) {
			case "over":
			case "under":
				if (linePositionSet) {
					throw new PropertyException();
				}
				linePositionSet = true;
				under = ident.is("under");
				break;
			case "right":
			case "left":
				if (sideSet) {
					throw new PropertyException();
				}
				sideSet = true;
				left = ident.is("left");
				break;
			default:
				throw new PropertyException();
			}
		}
		if (count == 0) {
			throw new PropertyException();
		}
		if (under) {
			return left ? TextEmphasisPositionValue.UNDER_LEFT : TextEmphasisPositionValue.UNDER_RIGHT;
		}
		return left ? TextEmphasisPositionValue.OVER_LEFT : TextEmphasisPositionValue.OVER_RIGHT;
	}
}
