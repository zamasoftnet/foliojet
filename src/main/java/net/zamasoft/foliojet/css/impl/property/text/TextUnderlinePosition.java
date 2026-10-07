package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-underline-position} (css-text-decoration-3 §2.7)
 * (added 2026-08-29; previously on the ignore list).
 *
 * <p>
 * {@code auto | [ from-font | under ] || [ left | right ]}.
 * Rendering is affected by {@code under} (places underlines below the descent in horizontal writing)
 * and {@code right} in vertical writing (places underlines to the right of the text).
 * {@code from-font} equals {@code auto} because the font's underline position is unavailable;
 * {@code left} equals {@code auto} because it is the default side in vertical writing.
 * Inherited.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextUnderlinePosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextUnderlinePosition();

	/** Parsed result. Stores {@code under} separately from the side ({@code left}/{@code right}/absent). */
	private record Position(boolean under, byte side) implements Value {
		public String toString() {
			final StringBuilder s = new StringBuilder();
			if (this.under) {
				s.append("under");
			}
			if (this.side != AbstractTextParams.UNDERLINE_POSITION_AUTO) {
				if (s.length() > 0) {
					s.append(' ');
				}
				s.append(this.side == AbstractTextParams.UNDERLINE_POSITION_LEFT ? "left" : "right");
			}
			return s.length() == 0 ? "auto" : s.toString();
		}
	}

	/**
	 * Position passed to layout ({@code AbstractTextParams.UNDERLINE_POSITION_*}).
	 * Prioritizes {@code right}, which matters only in vertical writing, then {@code under}.
	 */
	public static byte get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value instanceof Position position) {
			if (position.side() == AbstractTextParams.UNDERLINE_POSITION_RIGHT) {
				return AbstractTextParams.UNDERLINE_POSITION_RIGHT;
			}
			if (position.under()) {
				return AbstractTextParams.UNDERLINE_POSITION_UNDER;
			}
			return position.side();
		}
		return AbstractTextParams.UNDERLINE_POSITION_AUTO;
	}

	private TextUnderlinePosition() {
		super("text-underline-position");
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
		boolean under = false, fromFont = false;
		byte side = AbstractTextParams.UNDERLINE_POSITION_AUTO;
		int count = 0;
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (!(token instanceof CssToken.Ident ident)) {
				throw new PropertyException();
			}
			++count;
			switch (ident.lower()) {
			case "auto":
				if (count != 1 || tokens.hasNext()) {
					throw new PropertyException();
				}
				return KeywordValue.AUTO;
			case "under":
				if (under || fromFont) {
					throw new PropertyException();
				}
				under = true;
				break;
			case "from-font":
				if (under || fromFont) {
					throw new PropertyException();
				}
				fromFont = true;
				break;
			case "left":
			case "right":
				if (side != AbstractTextParams.UNDERLINE_POSITION_AUTO) {
					throw new PropertyException();
				}
				side = ident.is("left") ? AbstractTextParams.UNDERLINE_POSITION_LEFT
						: AbstractTextParams.UNDERLINE_POSITION_RIGHT;
				break;
			default:
				throw new PropertyException();
			}
		}
		if (count == 0) {
			throw new PropertyException();
		}
		if (!under && side == AbstractTextParams.UNDERLINE_POSITION_AUTO) {
			// from-font alone is equivalent to auto.
			return KeywordValue.AUTO;
		}
		return new Position(under, side);
	}
}
