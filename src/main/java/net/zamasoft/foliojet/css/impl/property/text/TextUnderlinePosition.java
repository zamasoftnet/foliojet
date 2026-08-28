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
 * {@code text-underline-position}(css-text-decoration-3 §2.7)です
 * (2026-08-29新設。従来は無視リストにあった)。
 *
 * <p>
 * {@code auto | [ from-font | under ] || [ left | right ]}。描画へ効くのは
 * {@code under}(横書きで下線をディセントの下へ置く)と、縦書きでの
 * {@code right}(下線を文字の右側へ置く)。{@code from-font}はフォントの
 * 下線位置を取れないため{@code auto}と同じ、{@code left}は縦書きの既定側
 * なので{@code auto}と同じ。継承する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TextUnderlinePosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextUnderlinePosition();

	/** 解析結果。{@code under}と側({@code left}/{@code right}/なし)を別々に持つ。 */
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
	 * レイアウトへ渡す位置({@code AbstractTextParams.UNDERLINE_POSITION_*})。
	 * 縦書きだけに意味がある{@code right}を優先し、次に{@code under}。
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
			// from-font 単独は auto と同じ
			return KeywordValue.AUTO;
		}
		return new Position(under, side);
	}
}
