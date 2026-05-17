package jp.cssj.homare.css.util;

import jp.cssj.homare.css.value.PageBreakValue;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBreakValueUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class PageBreakValueUtils {
	private PageBreakValueUtils() {
		// unused
	}

	public static PageBreakValue parsePageBreak(LexicalUnit lu) {
		if (lu.getLexicalUnitType() != LexicalUnit.SAC_IDENT) {
			return null;
		}
		String ident = lu.getStringValue().toLowerCase();
		if (ident.equals("auto")) {
			return (PageBreakValue.AUTO_VALUE);
		} else if (ident.equals("always")) {
			return (PageBreakValue.ALWAYS_VALUE);
		} else if (ident.equals("avoid")) {
			return (PageBreakValue.AVOID_VALUE);
		} else if (ident.equals("left")) {
			return (PageBreakValue.LEFT_VALUE);
		} else if (ident.equals("right")) {
			return (PageBreakValue.RIGHT_VALUE);
		} else if (ident.equals("page")) {
			return (PageBreakValue.PAGE_VALUE);
		} else if (ident.equals("column")) {
			return (PageBreakValue.COLUMN_VALUE);
			// } else if (ident.equals("avoid-page")) {
			// return (PageBreakValue.AVOID_PAGE_VALUE);
			// } else if (ident.equals("avoid-column")) {
			// return (PageBreakValue.AVOID_COLUMN_VALUE);
		} else if (ident.equals("verso") || ident.equals("even")) {
			return (PageBreakValue.VERSO_VALUE);
		} else if (ident.equals("recto") || ident.equals("odd")) {
			return (PageBreakValue.RECTO_VALUE);
		} else if (ident.equals("if-left")) {
			return (PageBreakValue.IF_LEFT_VALUE);
		} else if (ident.equals("if-right")) {
			return (PageBreakValue.IF_RIGHT_VALUE);
		} else if (ident.equals("if-verso")) {
			return (PageBreakValue.IF_VERSO_VALUE);
		} else if (ident.equals("if-recto")) {
			return (PageBreakValue.IF_RECTO_VALUE);
		}
		return null;
	}
}