package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.TextTransformValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextTransform.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextTransform extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextTransform();

	public static byte get(CSSStyle style) {
		TextTransformValue value = (TextTransformValue) style.get(INFO);
		return value.getTextTransform();
	}

	protected TextTransform() {
		super("text-transform");
	}

	public Value getDefault(CSSStyle style) {
		return TextTransformValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("none")) {
				return TextTransformValue.NONE_VALUE;
			} else if (ident.equals("capitalize")) {
				return TextTransformValue.CAPITALIZE_VALUE;
			} else if (ident.equals("uppercase")) {
				return TextTransformValue.UPPERCASE_VALUE;
			} else if (ident.equals("lowercase")) {
				return TextTransformValue.LOWERCASE_VALUE;
			}
		}
		throw new PropertyException();
	}

}