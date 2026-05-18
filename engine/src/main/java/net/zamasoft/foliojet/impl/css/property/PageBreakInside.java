package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.PageBreakInsideValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBreakInside.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PageBreakInside extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageBreakInside();

	public static byte get(CSSStyle style) {
		PageBreakInsideValue value = (PageBreakInsideValue) style.get(INFO);
		return value.getPageBreakInside();
	}

	private PageBreakInside() {
		super("page-break-inside");
	}

	public Value getDefault(CSSStyle style) {
		return PageBreakInsideValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT: {
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("auto")) {
				return PageBreakInsideValue.AUTO_VALUE;
			} else if (ident.equals("avoid")) {
				return PageBreakInsideValue.AVOID_VALUE;
				// } else if (ident.equals("avoid-page")) {
				// return PageBreakInsideValue.AVOID_PAGE_VALUE;
				// } else if (ident.equals("avoid-column")) {
				// return PageBreakInsideValue.AVOID_COLUMN_VALUE;
			}
		}

		default:
			throw new PropertyException();
		}
	}

}