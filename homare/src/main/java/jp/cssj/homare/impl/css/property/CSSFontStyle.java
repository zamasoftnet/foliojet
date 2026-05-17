package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.FontValueUtils;
import jp.cssj.homare.css.value.FontStyleValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSFontStyle.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSFontStyle extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSFontStyle();

	public static Style get(CSSStyle style) {
		return ((FontStyleValue) style.get(INFO)).getFontStyle();
	}

	protected CSSFontStyle() {
		super("font-style");
	}

	public Value getDefault(CSSStyle style) {
		return FontStyleValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final FontStyleValue fontStyle = FontValueUtils.toFontStyle(lu);
		if (fontStyle == null) {
			throw new PropertyException();
		}
		return fontStyle;
	}

}
