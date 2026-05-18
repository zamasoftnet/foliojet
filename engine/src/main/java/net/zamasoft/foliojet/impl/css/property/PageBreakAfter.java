package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.PageBreakValueUtils;
import net.zamasoft.foliojet.css.value.PageBreakValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBreakAfter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PageBreakAfter extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageBreakAfter();

	public static byte get(CSSStyle style) {
		PageBreakValue value = (PageBreakValue) style.get(INFO);
		return value.getPageBreak();
	}

	private PageBreakAfter() {
		super("page-break-after");
	}

	public Value getDefault(CSSStyle style) {
		return PageBreakValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		PageBreakValue value = PageBreakValueUtils.parsePageBreak(lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}