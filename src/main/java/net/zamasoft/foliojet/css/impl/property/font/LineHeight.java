package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-line-height"> line-
 * height 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class LineHeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineHeight();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof RealValue real) {
			return real.getReal() * FontSize.get(style);
		}
		if (value == KeywordValue.NORMAL) {
			return style.getUserAgent().getNormalLineHeight() * FontSize.get(style);
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	protected LineHeight() {
		super("line-height");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == KeywordValue.NORMAL || value instanceof RealValue) {
			return value;
		}
		if (value instanceof PercentageValue percentage) {
			return AbsoluteLengthValue.create(style.getUserAgent(), percentage.getRatio() * FontSize.get(style));
		}
		if (value instanceof CalcLengthValue calc) {
			// calc()が絶対長さと割合を混在させた場合(例: calc(50% + 10pt))。
			// line-heightの%はfont-size同様、親ではなく自要素のfont-sizeを
			// 基準に今ここで解決できるため、PercentageValueと同じ扱いにして
			// AbsoluteLengthValueへ完全に還元する。
			return AbsoluteLengthValue.create(style.getUserAgent(),
					calc.getAbsolute() + calc.getRatio() * FontSize.get(style));
		}
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value lineHeight = BoxValueUtils.toLineHeight(ua, lu);
		if (lineHeight == null) {
			throw new PropertyException();
		}
		return lineHeight;
	}

}