package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnsShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnsShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new ColumnsShorthand();

	protected ColumnsShorthand() {
		super("-cssj-columns");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		LexicalUnit lu2 = lu.getNextLexicalUnit();
		if (lu2 != null && lu2.getNextLexicalUnit() != null) {
			throw new PropertyException();
		}
		if (ValueUtils.isAuto(lu)) {
			if (lu2 == null || ValueUtils.isAuto(lu2)) {
				primitives.set(ColumnWidth.INFO, AutoValue.AUTO_VALUE);
				primitives.set(ColumnCount.INFO, IntegerValue.create(1));
				return;
			}
			if (lu2.getLexicalUnitType() == LexicalUnit.SAC_INTEGER) {
				primitives.set(ColumnWidth.INFO, AutoValue.AUTO_VALUE);
				primitives.set(ColumnCount.INFO, IntegerValue.create(lu2.getIntegerValue()));
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
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INTEGER) {
			if (lu2 == null || ValueUtils.isAuto(lu2)) {
				primitives.set(ColumnWidth.INFO, AutoValue.AUTO_VALUE);
				primitives.set(ColumnCount.INFO, IntegerValue.create(lu.getIntegerValue()));
				return;
			}
			LengthValue value = ValueUtils.toLength(ua, lu2);
			if (value != null) {
				primitives.set(ColumnWidth.INFO, value);
				primitives.set(ColumnCount.INFO, IntegerValue.create(lu.getIntegerValue()));
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
		if (lu2.getLexicalUnitType() == LexicalUnit.SAC_INTEGER) {
			primitives.set(ColumnWidth.INFO, value);
			primitives.set(ColumnCount.INFO, IntegerValue.create(lu2.getIntegerValue()));
			return;
		}
		throw new PropertyException();
	}

}