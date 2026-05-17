package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.css3.ColumnFillValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnFill.java 1552 2018-04-26 01:43:24Z miyabe $
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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("auto")) {
				return ColumnFillValue.AUTO_VALUE;
			} else if (ident.equals("balance")) {
				return ColumnFillValue.BALANCE_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}