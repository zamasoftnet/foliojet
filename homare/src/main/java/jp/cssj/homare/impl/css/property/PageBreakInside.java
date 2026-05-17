package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.PageBreakInsideValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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