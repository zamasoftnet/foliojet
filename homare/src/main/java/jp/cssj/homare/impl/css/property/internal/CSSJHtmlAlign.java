package jp.cssj.homare.impl.css.property.internal;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.internal.CSSJHtmlAlignValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * HTMLの水平アラインメント相当する内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJHtmlAlign.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJHtmlAlign extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlAlign();

	public static byte get(CSSStyle style) {
		CSSJHtmlAlignValue value = (CSSJHtmlAlignValue) style.get(INFO);
		return value.getHtmlAlign();
	}

	public CSSJHtmlAlign() {
		super("-cssj-html-align");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CSSJHtmlAlignValue.START_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		throw new UnsupportedOperationException();
	}
}