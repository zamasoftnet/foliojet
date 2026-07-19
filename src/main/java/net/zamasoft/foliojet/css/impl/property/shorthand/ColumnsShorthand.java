package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.impl.property.column.ColumnCount;
import net.zamasoft.foliojet.css.impl.property.column.ColumnWidth;

/**
 * @author MIYABE Tatsuhiko
 */
public class ColumnsShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new ColumnsShorthand();

	protected ColumnsShorthand() {
		super("-cssj-columns");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final CssToken lu = tokens.next();
		final CssToken lu2 = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (ValueUtils.isAuto(lu)) {
			if (lu2 == null || ValueUtils.isAuto(lu2)) {
				primitives.set(ColumnWidth.INFO, KeywordValue.AUTO);
				primitives.set(ColumnCount.INFO, IntegerValue.create(1));
				return;
			}
			if (lu2 instanceof CssToken.Num num2 && num2.integer()) {
				primitives.set(ColumnWidth.INFO, KeywordValue.AUTO);
				primitives.set(ColumnCount.INFO, IntegerValue.create(num2.intValue()));
				return;
			}
			LengthValue value = ValueUtils.toLength(ua, lu2);
			if (value != null) {
				primitives.set(ColumnWidth.INFO, value);
				primitives.set(ColumnCount.INFO, IntegerValue.create(1));
				return;
			}
			throw new PropertyException();
		}
		if (lu instanceof CssToken.Num num && num.integer()) {
			if (lu2 == null || ValueUtils.isAuto(lu2)) {
				primitives.set(ColumnWidth.INFO, KeywordValue.AUTO);
				primitives.set(ColumnCount.INFO, IntegerValue.create(num.intValue()));
				return;
			}
			LengthValue value = ValueUtils.toLength(ua, lu2);
			if (value != null) {
				primitives.set(ColumnWidth.INFO, value);
				primitives.set(ColumnCount.INFO, IntegerValue.create(num.intValue()));
				return;
			}
			throw new PropertyException();
		}
		LengthValue value = ValueUtils.toLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		if (lu2 == null || ValueUtils.isAuto(lu2)) {
			primitives.set(ColumnWidth.INFO, value);
			primitives.set(ColumnCount.INFO, IntegerValue.create(1));
			return;
		}
		if (lu2 instanceof CssToken.Num num2 && num2.integer()) {
			primitives.set(ColumnWidth.INFO, value);
			primitives.set(ColumnCount.INFO, IntegerValue.create(num2.intValue()));
			return;
		}
		throw new PropertyException();
	}

}