package net.zamasoft.foliojet.css.impl.property.column;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.css3.ColumnFillValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class ColumnFill extends KeywordPropertyInfo {

	public static final PrimitivePropertyInfo INFO = new ColumnFill();

	public static byte get(CSSStyle style) {
		ColumnFillValue value = (ColumnFillValue) style.get(INFO);
		return value.getColumnFill();
	}

	protected ColumnFill() {
		super("-cssj-column-fill", false, ColumnFillValue.BALANCE_VALUE, Map.of("auto", ColumnFillValue.AUTO_VALUE,
				"balance", ColumnFillValue.BALANCE_VALUE));
	}

}