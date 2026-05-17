package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.PercentageValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.VerticalAlignValue;
import jp.cssj.homare.style.box.content.AbsoluteVerticalAlignPolicy;
import jp.cssj.homare.style.box.content.CSSVerticalAlignPolicy;
import jp.cssj.homare.style.box.content.FractionalVerticalAlignPolicy;
import jp.cssj.homare.style.box.content.VerticalAlignPolicy;
import jp.cssj.homare.style.box.params.Types;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-vertical-align">
 * vertical-align 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: VerticalAlign.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class VerticalAlign extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new VerticalAlign();

	public static VerticalAlignPolicy getForInline(CSSStyle style) {
		Value value = style.get(INFO);
		switch (value.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			return new AbsoluteVerticalAlignPolicy(((AbsoluteLengthValue) value).getLength());

		case Value.TYPE_PERCENTAGE:
			return new FractionalVerticalAlignPolicy(((PercentageValue) value).getRatio());

		case Value.TYPE_VERTICAL_ALIGN:
			return (VerticalAlignPolicy) value;
		default:
			throw new IllegalStateException();
		}
	}

	public static byte getForTableCell(CSSStyle style) {
		Value value = style.get(INFO);
		switch (value.getValueType()) {
		case Value.TYPE_VERTICAL_ALIGN:
			CSSVerticalAlignPolicy va = (CSSVerticalAlignPolicy) value;
			switch (va.getVerticalAlignType()) {
			case CSSVerticalAlignPolicy.TOP:
				return Types.VERTICAL_ALIGN_START;
			case CSSVerticalAlignPolicy.MIDDLE:
				return Types.VERTICAL_ALIGN_MIDDLE;
			case CSSVerticalAlignPolicy.BOTTOM:
				return Types.VERTICAL_ALIGN_END;
			}

		case Value.TYPE_ABSOLUTE_LENGTH:
		case Value.TYPE_PERCENTAGE:
			return Types.VERTICAL_ALIGN_BASELINE;
		default:
			throw new IllegalStateException();
		}
	}

	protected VerticalAlign() {
		super("vertical-align");
	}

	public Value getDefault(CSSStyle style) {
		return VerticalAlignValue.BASELINE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("baseline")) {
				return VerticalAlignValue.BASELINE_VALUE;
			} else if (ident.equals("middle")) {
				return VerticalAlignValue.MIDDLE_VALUE;
			} else if (ident.equals("sub")) {
				return VerticalAlignValue.SUB_VALUE;
			} else if (ident.equals("super")) {
				return VerticalAlignValue.SUPER_VALUE;
			} else if (ident.equals("text-top")) {
				return VerticalAlignValue.TEXT_TOP_VALUE;
			} else if (ident.equals("text-bottom")) {
				return VerticalAlignValue.TEXT_BOTTOM_VALUE;
			} else if (ident.equals("top")) {
				return VerticalAlignValue.TOP_VALUE;
			} else if (ident.equals("bottom")) {
				return VerticalAlignValue.BOTTOM_VALUE;
			}
			throw new PropertyException();

		default:
			Value value = ValueUtils.toLength(ua, lu);
			if (value == null) {
				return ValueUtils.toPercentage(lu);
			}
			return value;
		}
	}

}