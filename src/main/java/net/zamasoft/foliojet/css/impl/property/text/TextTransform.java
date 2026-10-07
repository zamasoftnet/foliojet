package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.TextTransformValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextTransform extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextTransform();

	public static byte get(CSSStyle style) {
		TextTransformValue value = (TextTransformValue) style.get(INFO);
		return value.getTextTransform();
	}

	protected TextTransform() {
		super("text-transform");
	}

	public Value getDefault(CSSStyle style) {
		return TextTransformValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	/**
	 * {@code none | [capitalize | uppercase | lowercase] || full-width} (css-text-3;
	 * {@code full-width} added 2026-10-06; {@code full-size-kana} and {@code math-auto} are unsupported).
	 */
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("none")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextTransformValue.NONE_VALUE;
		}
		byte cased = AbstractTextParams.TEXT_TRANSFORM_NONE;
		boolean fullWidth = false;
		while (tokens.hasNext()) {
			if (cased == AbstractTextParams.TEXT_TRANSFORM_NONE && tokens.eat("capitalize")) {
				cased = AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE;
			} else if (cased == AbstractTextParams.TEXT_TRANSFORM_NONE && tokens.eat("uppercase")) {
				cased = AbstractTextParams.TEXT_TRANSFORM_UPPERCASE;
			} else if (cased == AbstractTextParams.TEXT_TRANSFORM_NONE && tokens.eat("lowercase")) {
				cased = AbstractTextParams.TEXT_TRANSFORM_LOWERCASE;
			} else if (!fullWidth && tokens.eat("full-width")) {
				fullWidth = true;
			} else {
				throw new PropertyException();
			}
		}
		if (cased == AbstractTextParams.TEXT_TRANSFORM_NONE && !fullWidth) {
			throw new PropertyException();
		}
		return TextTransformValue
				.of((byte) (cased | (fullWidth ? AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH : 0)));
	}

}