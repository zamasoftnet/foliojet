package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.FontValueUtils;
import jp.cssj.homare.css.value.FontFamilyValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSFontFamily.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSFontFamily extends AbstractPrimitivePropertyInfo {
	public static final AbstractPrimitivePropertyInfo INFO = new CSSFontFamily();

	public static FontFamilyList get(CSSStyle style) {
		return ((FontFamilyValue) style.get(INFO)).asFontFamilyList();
	}

	protected CSSFontFamily() {
		super("font-family");
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getDefaultFontFamily();
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final FontFamilyValue fontFamily = FontValueUtils.toFontFamily(ua, lu);
		if (fontFamily == null) {
			throw new PropertyException();
		}
		return fontFamily;
	}

}
