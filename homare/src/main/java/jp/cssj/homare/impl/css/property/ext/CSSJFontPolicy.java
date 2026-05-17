package jp.cssj.homare.impl.css.property.ext;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.FontValueUtils;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.ext.CSSJFontPolicyValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJFontPolicy.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJFontPolicy extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJFontPolicy();

	public static FontPolicyList get(CSSStyle style) {
		return ((CSSJFontPolicyValue) style.get(INFO)).asFontPolicyList();
	}

	protected CSSJFontPolicy() {
		super("-cssj-font-policy");
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getDefaultFontPolicy();
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (style.getUserAgent().getDefaultFontPolicy() == CSSJFontPolicyValue.PDFA1_VALUE) {
			return CSSJFontPolicyValue.PDFA1_VALUE;
		}
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		// @parseProperty

		final CSSJFontPolicyValue fontPolicy = FontValueUtils.toFontPolicy(lu);
		if (fontPolicy == null) {
			throw new PropertyException();
		}
		return fontPolicy;
	}
}
