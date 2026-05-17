package jp.cssj.homare.impl.css.property.ext;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.NoneValue;
import jp.cssj.homare.css.value.StringValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJRegeneratable.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJRegeneratable extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJRegeneratable();

	public static String get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_NONE) {
			return null;
		}
		return ((StringValue) value).getString();
	}

	private CSSJRegeneratable() {
		super("-cssj-regeneratable");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			return InheritValue.INHERIT_VALUE;
		}
		final Value name;
		{
			short luType = lu.getLexicalUnitType();
			switch (luType) {
			case LexicalUnit.SAC_IDENT:
				String ident = lu.getStringValue().toLowerCase();
				if (ident.equals("none")) {
					name = NoneValue.NONE_VALUE;
				} else {
					name = new StringValue(lu.getStringValue());
				}
				break;

			case LexicalUnit.SAC_STRING_VALUE:
				name = new StringValue(lu.getStringValue());
				break;

			default:
				throw new PropertyException();
			}
		}
		return name;
	}
}