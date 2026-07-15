package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.FontWeightValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class FontWeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontWeight();

	public static Weight get(CSSStyle style) {
		return ((FontWeightValue) style.get(INFO)).getWeight();
	}

	protected FontWeight() {
		super("font-weight");
	}

	public Value getDefault(CSSStyle style) {
		return FontWeightValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		FontWeightValue fontWeight = (FontWeightValue) value;
		if (fontWeight == FontWeightValue.BOLDER_VALUE) {
			CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle == null) {
				parentStyle = style;
			}
			fontWeight = ((FontWeightValue) parentStyle.get(INFO)).bolder();
		} else if (fontWeight == FontWeightValue.LIGHTER_VALUE) {
			CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle == null) {
				parentStyle = style;
			}
			fontWeight = ((FontWeightValue) parentStyle.get(INFO)).lighter();
		}
		return fontWeight;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final FontWeightValue fontWeight = FontValueUtils.toFontWeight(lu);
		if (fontWeight == null) {
			throw new PropertyException();
		}
		return fontWeight;
	}

}
