package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.NormalValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-line-height"> line-
 * height 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: LineHeight.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class LineHeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineHeight();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		switch (value.getValueType()) {
		case Value.TYPE_REAL:
			return ((RealValue) value).getReal() * FontSize.get(style);
		case Value.TYPE_NORMAL:
			return style.getUserAgent().getNormalLineHeight() * FontSize.get(style);
		default:
			return ((AbsoluteLengthValue) value).getLength();
		}
	}

	protected LineHeight() {
		super("line-height");
	}

	public Value getDefault(CSSStyle style) {
		return NormalValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		switch (value.getValueType()) {
		case Value.TYPE_NORMAL:
		case Value.TYPE_REAL:
			return value;
		case Value.TYPE_PERCENTAGE:
			return AbsoluteLengthValue.create(style.getUserAgent(),
					((PercentageValue) value).getRatio() * FontSize.get(style));
		default:
			return ValueUtils.emExToAbsoluteLength(value, style);
		}
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