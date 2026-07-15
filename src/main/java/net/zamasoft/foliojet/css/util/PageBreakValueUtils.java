package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.PageBreakValue;

/**
 * @author MIYABE Tatsuhiko
 */
public final class PageBreakValueUtils {
	private PageBreakValueUtils() {
		// unused
	}

	public static PageBreakValue parsePageBreak(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		switch (ident.lower()) {
		case "auto":
			return PageBreakValue.AUTO_VALUE;
		case "always":
			return PageBreakValue.ALWAYS_VALUE;
		case "avoid":
			return PageBreakValue.AVOID_VALUE;
		case "left":
			return PageBreakValue.LEFT_VALUE;
		case "right":
			return PageBreakValue.RIGHT_VALUE;
		case "page":
			return PageBreakValue.PAGE_VALUE;
		case "column":
			return PageBreakValue.COLUMN_VALUE;
		case "verso":
		case "even":
			return PageBreakValue.VERSO_VALUE;
		case "recto":
		case "odd":
			return PageBreakValue.RECTO_VALUE;
		case "if-left":
			return PageBreakValue.IF_LEFT_VALUE;
		case "if-right":
			return PageBreakValue.IF_RIGHT_VALUE;
		case "if-verso":
			return PageBreakValue.IF_VERSO_VALUE;
		case "if-recto":
			return PageBreakValue.IF_RECTO_VALUE;
		default:
			return null;
		}
	}
}
