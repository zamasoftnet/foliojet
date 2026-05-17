package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.BoxValueUtils;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.NormalValue;
import jp.cssj.homare.css.value.PercentageValue;
import jp.cssj.homare.css.value.RealValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value lineHeight = BoxValueUtils.toLineHeight(ua, lu);
		if (lineHeight == null) {
			throw new PropertyException();
		}
		return lineHeight;
	}

}