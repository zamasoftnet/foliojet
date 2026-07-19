package net.zamasoft.foliojet.impl.css.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RelativeSizeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;

/**
 * @author MIYABE Tatsuhiko
 */
public class FontSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontSize();

	public static double get(CSSStyle style) {
		return ((AbsoluteLengthValue) style.get(INFO)).getLength();
	}

	protected FontSize() {
		super("font-size");
	}

	public Value getDefault(CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		return AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.MEDIUM));
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		CSSStyle parentStyle = style.getParentStyle();
		if (parentStyle == null) {
			parentStyle = style;
		}
		if (value instanceof PercentageValue percentage) {
			double fontSize = FontSize.get(parentStyle);
			return AbsoluteLengthValue.create(parentStyle.getUserAgent(), percentage.getRatio() * fontSize);
		}
		if (value instanceof CalcLengthValue calc) {
			// calc()が絶対長さと割合を混在させた場合(例: calc(1px + 50%))。
			// font-sizeの%は(width/height等と違いレイアウト時ではなく)親の
			// font-sizeを基準に今ここで解決できるため、PercentageValueと
			// 同じ扱いにしてAbsoluteLengthValueへ完全に還元する。
			double fontSize = FontSize.get(parentStyle);
			return AbsoluteLengthValue.create(parentStyle.getUserAgent(),
					calc.getAbsolute() + calc.getRatio() * fontSize);
		}
		if (value instanceof RelativeSizeValue relativeSize) {
			UserAgent ua = parentStyle.getUserAgent();
			double fontSize = FontSize.get(parentStyle);
			switch (relativeSize.getRelativeSize()) {
			case RelativeSizeValue.LARGER:
				return AbsoluteLengthValue.create(ua, ua.getLargerFontSize(fontSize));
			case RelativeSizeValue.SMALLER:
				return AbsoluteLengthValue.create(ua, ua.getSmallerFontSize(fontSize));
			default:
				throw new IllegalStateException();
			}
		}
		if (value instanceof RelativeLengthValue relative) {
			// font-size自体の相対長さは親のフォントサイズを基準にする
			return relative.toAbsoluteLength(parentStyle);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = FontValueUtils.toFontSize(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}