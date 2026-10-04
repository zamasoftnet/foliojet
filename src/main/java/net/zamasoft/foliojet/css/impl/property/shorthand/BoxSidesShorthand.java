package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 4 辺のショートハンド {@code margin} / {@code padding} / {@code border-width} / {@code border-style} /
 * {@code border-color} / {@code inset} です。1〜4 個の値を上・右・下・左へ配る(1 個は全辺、2 個は上下・左右、3 個は
 * 上・左右・下)。全体キーワードは基底の{@link #longhands()}で受ける。
 *
 * <p>
 * 2026-10-04 まで 6 つのクラスに同じ配り方の写しがあり、届かない{@code inherit}の分岐と、全体キーワードの扱いの
 * 3 通りの流儀(border-color だけが自前で受けていた)を持っていた。論理側の{@link LogicalBoxShorthand}と同じ形にした。
 * 5 個以上の値は不正(以前は 5 個目以降を黙って無視していた)。
 * </p>
 *
 * <p>
 * {@code inset}(css-logical §4.4)は論理軸ではなく物理辺へ展開する(仕様どおり)。未対応だと
 * {@code inset:0; margin:auto}の絶対配置センタリング(実地で頻出の中央寄せイディオム)が丸ごと落ち、要素が
 * 静的位置(左上)へ張り付く(asahi.comの動画再生アイコンが左上へ寄った、2026-08-27)。
 * </p>
 */
public final class BoxSidesShorthand extends AbstractShorthandPropertyInfo {
	/** 値の型ごとの読み方。 */
	@FunctionalInterface
	private interface Reader {
		Value read(UserAgent ua, CssToken token) throws PropertyException;
	}

	public static final ShorthandPropertyInfo MARGIN = new BoxSidesShorthand("margin", Margin.TOP, Margin.RIGHT,
			Margin.BOTTOM, Margin.LEFT, BoxValueUtils::toMarginWidth);
	public static final ShorthandPropertyInfo PADDING = new BoxSidesShorthand("padding", Padding.TOP, Padding.RIGHT,
			Padding.BOTTOM, Padding.LEFT, BoxValueUtils::toPositiveLength);
	public static final ShorthandPropertyInfo BORDER_WIDTH = new BoxSidesShorthand("border-width", BorderWidth.TOP,
			BorderWidth.RIGHT, BorderWidth.BOTTOM, BorderWidth.LEFT, BorderValueUtils::toBorderWidth);
	public static final ShorthandPropertyInfo BORDER_STYLE = new BoxSidesShorthand("border-style", BorderStyle.TOP,
			BorderStyle.RIGHT, BorderStyle.BOTTOM, BorderStyle.LEFT, (ua, token) -> BorderValueUtils.toBorderStyle(token));
	public static final ShorthandPropertyInfo BORDER_COLOR = new BoxSidesShorthand("border-color", BorderColor.TOP,
			BorderColor.RIGHT, BorderColor.BOTTOM, BorderColor.LEFT, BoxSidesShorthand::toBorderColor);
	public static final ShorthandPropertyInfo INSET = new BoxSidesShorthand("inset", Inset.TOP, Inset.RIGHT,
			Inset.BOTTOM, Inset.LEFT, BoxValueUtils::toMarginWidth);

	private final PrimitivePropertyInfo[] sides;
	private final Reader reader;

	private BoxSidesShorthand(final String name, final PrimitivePropertyInfo top, final PrimitivePropertyInfo right,
			final PrimitivePropertyInfo bottom, final PrimitivePropertyInfo left, final Reader reader) {
		super(name);
		this.sides = new PrimitivePropertyInfo[] { top, right, bottom, left };
		this.reader = reader;
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return this.sides.clone();
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		final Value[] values = new Value[4];
		int n = 0;
		do {
			final Value value = this.reader.read(ua, tokens.next());
			if (value == null) {
				throw new PropertyException();
			}
			values[n++] = value;
		} while (n < 4 && tokens.hasNext());
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		final Value top = values[0];
		final Value right = n > 1 ? values[1] : top;
		final Value bottom = n > 2 ? values[2] : top;
		final Value left = n > 3 ? values[3] : right;
		primitives.set(this.sides[0], top);
		primitives.set(this.sides[1], right);
		primitives.set(this.sides[2], bottom);
		primitives.set(this.sides[3], left);
	}

	private static Value toBorderColor(final UserAgent ua, final CssToken token) {
		if (ColorValueUtils.isTransparent(token)) {
			return KeywordValue.TRANSPARENT;
		}
		// currentcolor は DEFAULT 番兵(2026-08-29)
		return ColorValueUtils.toColorOrCurrent(ua, token);
	}
}
