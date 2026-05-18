package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.TextValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.TextAlign;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextAlignLast.java 1640 2023-10-04 03:06:26Z miyabe $
 */
public class TextAlignLast extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new TextAlignLast();

	public static byte get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_AUTO) {
			final byte align = TextAlign.get(style);
			switch (align) {
			case TextAlignValue.JUSTIFY:
				value = TextAlignValue.START_VALUE;
				break;
			default:
				value = style.get(TextAlign.INFO);
				break;
			}
		}
		return TextValueUtils.toTextAlignParam((TextAlignValue)value, style);
	}

	protected TextAlignLast() {
		super("-cssj-text-align-last");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return AutoValue.AUTO_VALUE;
		}
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			TextAlignValue value = TextValueUtils.toTextAlign(ident);
			if (value != null) {
				return value;
			}

		default:
			throw new PropertyException();
		}
	}

}