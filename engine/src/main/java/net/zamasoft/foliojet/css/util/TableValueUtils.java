package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.value.BorderCollapseValue;
import net.zamasoft.foliojet.css.value.CaptionSideValue;
import net.zamasoft.foliojet.css.value.EmptyCellsValue;
import net.zamasoft.foliojet.css.value.TableLayoutValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TableValueUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class TableValueUtils {
	private TableValueUtils() {
		// unused
	}

	/**
	 * caption-side を値に変換します。SPEC CSS2 17.4.1
	 * 
	 * @param lu
	 * @return
	 */
	public static Value toCaptionSide(LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("top")) {
				return CaptionSideValue.TOP_VALUE;
			} else if (ident.equals("bottom")) {
				return CaptionSideValue.BOTTOM_VALUE;
			} else if (ident.equals("before")) {
				return CaptionSideValue.BEFORE_VALUE;
			} else if (ident.equals("after")) {
				return CaptionSideValue.AFTER_VALUE;
			}

		default:
			return null;
		}
	}

	/**
	 * table-layout を値に変換します。(CSS2 17.5.2)
	 * 
	 * @param lu
	 * @return
	 */
	public static Value toTableLayout(LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("auto")) {
				return TableLayoutValue.AUTO_VALUE;
			} else if (ident.equals("fixed")) {
				return TableLayoutValue.FIXED_VALUE;
			}
		default:
			return null;
		}
	}

	/**
	 * border-collapse を値に変換します。(CSS2 17.6)
	 * 
	 * @param lu
	 * @return
	 */
	public static Value toBorderCollapse(LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("collapse")) {
				return BorderCollapseValue.COLLAPSE_VALUE;
			} else if (ident.equals("separate")) {
				return BorderCollapseValue.SEPARATE_VALUE;
			}
		default:
			return null;
		}
	}

	/**
	 * empty-cells を値に変換します。(CSS2 17.6.1)
	 * 
	 * @param lu
	 * @return
	 */
	public static Value toEmptyCells(LexicalUnit lu) {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("show")) {
				return EmptyCellsValue.SHOW_VALUE;
			} else if (ident.equals("hide")) {
				return EmptyCellsValue.HIDE_VALUE;
			}
		default:
			return null;
		}
	}
}