package jp.cssj.homare.impl.css.property.internal;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * HTMLのテーブルcellpaddingに相当する内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJHtmlCellPadding.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJHtmlCellPadding extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlCellPadding();

	public static LengthValue get(CSSStyle style) {
		LengthValue value = (LengthValue) style.get(INFO);
		return value;
	}

	public static void set(CSSStyle style, LengthValue value) {
		style.set(INFO, value);
	}

	public CSSJHtmlCellPadding() {
		super("-cssj-html-cellpadding");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.create(style.getUserAgent(), 1, LengthValue.UNIT_PX);
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		throw new UnsupportedOperationException();
	}
}