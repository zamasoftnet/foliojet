package net.zamasoft.foliojet.impl.css.property.box;

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
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-vertical-align">
 * vertical-align 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class VerticalAlign extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new VerticalAlign();

	public static VerticalAlignPolicy getForInline(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof AbsoluteLengthValue length) {
			return new AbsoluteVerticalAlignPolicy(length.getLength());
		}
		if (value instanceof PercentageValue percentage) {
			return new FractionalVerticalAlignPolicy(percentage.getRatio());
		}
		if (value instanceof VerticalAlignValue) {
			return (VerticalAlignPolicy) value;
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	public static byte getForTableCell(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof VerticalAlignValue va) {
			switch (va.getVerticalAlignType()) {
			case CSSVerticalAlignPolicy.TOP:
				return Types.VERTICAL_ALIGN_START;
			case CSSVerticalAlignPolicy.MIDDLE:
				return Types.VERTICAL_ALIGN_MIDDLE;
			case CSSVerticalAlignPolicy.BOTTOM:
				return Types.VERTICAL_ALIGN_END;
			default:
				return Types.VERTICAL_ALIGN_BASELINE;
			}
		}
		if (value instanceof AbsoluteLengthValue || value instanceof PercentageValue) {
			return Types.VERTICAL_ALIGN_BASELINE;
		}
		throw new IllegalStateException(String.valueOf(value));
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

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
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
		}
		Value value = ValueUtils.toLength(ua, lu);
		if (value == null) {
			return ValueUtils.toPercentage(lu);
		}
		return value;
	}

}