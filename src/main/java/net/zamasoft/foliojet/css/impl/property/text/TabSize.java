package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code tab-size}です(css-text-3 §3.2、2026-08-29新設)。
 *
 * <p>
 * {@code <number [0,∞]> | <length [0,∞]>}。継承、既定8。数値は空白文字
 * (U+0020)の送り幅の倍数、長さはそのままタブ幅になる。タブ位置は行頭
 * からタブ幅の整数倍({@code TextBuilder.control})。仕様の数値は
 * 「空白の送り+letter-spacing+word-spacing」だが、本実装は空白の
 * 送り幅だけを掛ける(近似)。
 * </p>
 *
 * <p>
 * 2026-08-29より前は固定24pt(既定フォント12pt×2文字分)だった。
 * 既定8の空白幅倍は、和文既定の空白幅(12pt→3pt前後)では24ptと同程度。
 * </p>
 */
public class TabSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TabSize();

	private static final RealValue DEFAULT = RealValue.create(8);

	/** 値が空白幅の倍数({@code <number>})か。 */
	public static boolean isMultiple(final CSSStyle style) {
		return style.get(INFO) instanceof RealValue;
	}

	/** 倍数なら倍率、長さなら絶対長さ(pt)。 */
	public static double get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value instanceof RealValue real) {
			return real.getReal();
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	protected TabSize() {
		super("tab-size");
	}

	public Value getDefault(final CSSStyle style) {
		return DEFAULT;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (token instanceof CssToken.Num num) {
			if (num.value() < 0) {
				throw new PropertyException();
			}
			return RealValue.create(num.value());
		}
		final Value length = ValueUtils.toLength(ua, token);
		if (length == null || (length instanceof AbsoluteLengthValue abs && abs.getLength() < 0)) {
			throw new PropertyException();
		}
		return length;
	}
}
