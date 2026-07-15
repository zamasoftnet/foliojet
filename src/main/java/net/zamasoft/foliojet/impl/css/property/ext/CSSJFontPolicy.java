package net.zamasoft.foliojet.impl.css.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.foliojet.css.token.TokenStream;

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

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		// @parseProperty

		final CSSJFontPolicyValue fontPolicy = FontValueUtils.toFontPolicy(tokens);
		if (fontPolicy == null) {
			throw new PropertyException();
		}
		return fontPolicy;
	}
}
