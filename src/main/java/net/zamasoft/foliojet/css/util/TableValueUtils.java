package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.BorderCollapseValue;
import net.zamasoft.foliojet.css.value.CaptionSideValue;
import net.zamasoft.foliojet.css.value.EmptyCellsValue;
import net.zamasoft.foliojet.css.value.TableLayoutValue;
import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public final class TableValueUtils {
	private TableValueUtils() {
		// unused
	}

	/**
	 * caption-side を値に変換します。SPEC CSS2 17.4.1
	 */
	public static Value toCaptionSide(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "top":
				return CaptionSideValue.TOP_VALUE;
			case "bottom":
				return CaptionSideValue.BOTTOM_VALUE;
			case "before":
				return CaptionSideValue.BEFORE_VALUE;
			case "after":
				return CaptionSideValue.AFTER_VALUE;
			}
		}
		return null;
	}

	/**
	 * table-layout を値に変換します。(CSS2 17.5.2)
	 */
	public static Value toTableLayout(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "auto":
				return TableLayoutValue.AUTO_VALUE;
			case "fixed":
				return TableLayoutValue.FIXED_VALUE;
			}
		}
		return null;
	}

	/**
	 * border-collapse を値に変換します。(CSS2 17.6)
	 */
	public static Value toBorderCollapse(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "collapse":
				return BorderCollapseValue.COLLAPSE_VALUE;
			case "separate":
				return BorderCollapseValue.SEPARATE_VALUE;
			}
		}
		return null;
	}

	/**
	 * empty-cells を値に変換します。(CSS2 17.6.1)
	 */
	public static Value toEmptyCells(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "show":
				return EmptyCellsValue.SHOW_VALUE;
			case "hide":
				return EmptyCellsValue.HIDE_VALUE;
			}
		}
		return null;
	}
}
