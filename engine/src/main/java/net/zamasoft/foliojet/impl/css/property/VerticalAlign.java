package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
import net.zamasoft.foliojet.style.box.content.AbsoluteVerticalAlignPolicy;
import net.zamasoft.foliojet.style.box.content.CSSVerticalAlignPolicy;
import net.zamasoft.foliojet.style.box.content.FractionalVerticalAlignPolicy;
import net.zamasoft.foliojet.style.box.content.VerticalAlignPolicy;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

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