package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.NormalValue;
import jp.cssj.homare.css.value.PercentageValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.style.box.params.Length;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: LetterSpacing.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class LetterSpacing extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LetterSpacing();

	public static Length get(CSSStyle style) {
		Value value = style.get(INFO);
		switch (value.getValueType()) {
		case Value.TYPE_NORMAL:
			return Length.create(0, Length.TYPE_ABSOLUTE);
		case Value.TYPE_PERCENTAGE:
			return Length.create(((PercentageValue) value).getRatio(), Length.TYPE_RELATIVE);
		case Value.TYPE_ABSOLUTE_LENGTH:
			return Length.create(((AbsoluteLengthValue) value).getLength(), Length.TYPE_ABSOLUTE);
		default:
			throw new IllegalStateException();
		}
	}

	protected LetterSpacing() {
		super("letter-spacing");
	}

	public Value getDefault(CSSStyle style) {
		return NormalValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isNormal(lu)) {
			return NormalValue.NORMAL_VALUE;
		}
		final Value value;
		value = ValueUtils.toLength(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}