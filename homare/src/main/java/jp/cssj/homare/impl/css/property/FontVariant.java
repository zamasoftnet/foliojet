package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.FontValueUtils;
import jp.cssj.homare.css.value.FontVariantValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: FontVariant.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FontVariant extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariant();

	public static double get(CSSStyle style) {
		return ((FontVariantValue) style.get(INFO)).getFontVariant();
	}

	protected FontVariant() {
		super("font-variant");
	}

	public Value getDefault(CSSStyle style) {
		return FontVariantValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final FontVariantValue fontVariant = FontValueUtils.toFontVariant(lu);
		if (fontVariant == null) {
			throw new PropertyException();
		}
		return fontVariant;
	}

}