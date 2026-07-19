package net.zamasoft.foliojet.css.impl.property.column;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.ColumnFillValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class ColumnFill extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new ColumnFill();

	public static byte get(CSSStyle style) {
		ColumnFillValue value = (ColumnFillValue) style.get(INFO);
		return value.getColumnFill();
	}

	protected ColumnFill() {
		super("-cssj-column-fill");
	}

	public Value getDefault(CSSStyle style) {
		return ColumnFillValue.BALANCE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("auto")) {
				return ColumnFillValue.AUTO_VALUE;
			} else if (ident.equals("balance")) {
				return ColumnFillValue.BALANCE_VALUE;
			}
		}
		throw new PropertyException();
	}

}