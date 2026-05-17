package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractCompositePrimitivePropertyInfo;
import jp.cssj.homare.css.property.CompositeProperty.Entry;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderSpacing.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderSpacing extends AbstractCompositePrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_H = new BorderSpacing();
	public static final PrimitivePropertyInfo INFO_V = new BorderSpacing();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_H, INFO_V };

	public static double getHorizontal(CSSStyle style) {
		AbsoluteLengthValue h = (AbsoluteLengthValue) style.get(INFO_H);
		return h.getLength();
	}

	public static double getVertical(CSSStyle style) {
		AbsoluteLengthValue v = (AbsoluteLengthValue) style.get(INFO_V);
		return v.getLength();
	}

	protected BorderSpacing() {
		super("border-spacing");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return true;
	}

	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}

	protected Entry[] parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			return new Entry[] { new Entry(BorderSpacing.INFO_H, InheritValue.INHERIT_VALUE),
					new Entry(BorderSpacing.INFO_V, InheritValue.INHERIT_VALUE) };
		}
		LengthValue h = ValueUtils.toLength(ua, lu);
		lu = lu.getNextLexicalUnit();
		LengthValue v;
		if (lu == null) {
			v = h;
		} else {
			v = ValueUtils.toLength(ua, lu);
		}
		if (h != null && v != null) {
			return new Entry[] { new Entry(BorderSpacing.INFO_H, h), new Entry(BorderSpacing.INFO_V, v) };
		}
		throw new PropertyException();
	}

}