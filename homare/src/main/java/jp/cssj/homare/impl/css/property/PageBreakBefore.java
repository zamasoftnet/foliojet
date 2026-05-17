package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.PageBreakValueUtils;
import jp.cssj.homare.css.value.PageBreakValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBreakBefore.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PageBreakBefore extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageBreakBefore();

	public static byte get(CSSStyle style) {
		PageBreakValue value = (PageBreakValue) style.get(INFO);
		return value.getPageBreak();
	}

	private PageBreakBefore() {
		super("page-break-before");
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