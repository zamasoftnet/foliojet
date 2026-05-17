package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.AutoValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.RectValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Clip.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Clip extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Clip();

	public static RectValue get(CSSStyle style) {
		Value value = style.get(INFO);
		return value.getValueType() == Value.TYPE_AUTO ? null : (RectValue) value;
	}

	private Clip() {
		super("clip");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return AutoValue.AUTO_VALUE;
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_RECT_FUNCTION) {
			LengthValue top, left, bottom, right;
			lu = lu.getParameters();

			if (ValueUtils.isAuto(lu)) {
				top = AbsoluteLengthValue.ZERO;
			} else {
				top = ValueUtils.toLength(ua, lu);
			}
			if (top == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			lu = lu.getNextLexicalUnit().getNextLexicalUnit();
			if (ValueUtils.isAuto(lu)) {
				left = AbsoluteLengthValue.ZERO;
			} else {
				left = ValueUtils.toLength(ua, lu);
			}
			if (left == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			lu = lu.getNextLexicalUnit().getNextLexicalUnit();
			if (ValueUtils.isAuto(lu)) {
				bottom = AbsoluteLengthValue.ZERO;
			} else {
				bottom = ValueUtils.toLength(ua, lu);
			}
			if (bottom == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			lu = lu.getNextLexicalUnit().getNextLexicalUnit();
			if (ValueUtils.isAuto(lu)) {
				right = AbsoluteLengthValue.ZERO;
			} else {
				right = ValueUtils.toLength(ua, lu);
			}
			if (right == null) {
				throw new PropertyException(String.valueOf(lu));
			}

			final RectValue rect = new RectValue(top, left, bottom, right);
			return rect;
		}
		throw new PropertyException(String.valueOf(lu));
	}

}