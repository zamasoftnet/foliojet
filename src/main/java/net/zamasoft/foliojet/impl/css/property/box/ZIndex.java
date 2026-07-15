package net.zamasoft.foliojet.impl.css.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class ZIndex extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ZIndex();

	public static int getValue(CSSStyle style) {
		Value value = style.get(INFO);
		return ((IntegerValue) value).getInteger();
	}

	public static byte getType(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.AUTO || CSSPosition.get(style) == PositionValue.STATIC) {
			return Params.Z_INDEX_AUTO;
		}
		return Params.Z_INDEX_SPECIFIED;
	}

	private ZIndex() {
		super("z-index");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("auto")) {
				return IntegerValue.ZERO;
			}
		} else if (lu instanceof CssToken.Num num && num.integer()) {
			return IntegerValue.create(num.intValue());
		}
		throw new PropertyException();
	}

}