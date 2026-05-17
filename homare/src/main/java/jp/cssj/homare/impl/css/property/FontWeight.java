package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.FontValueUtils;
import jp.cssj.homare.css.value.FontWeightValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: FontWeight.java 1552 2018-04-26 01:43:24Z miyabe $
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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final FontWeightValue fontWeight = FontValueUtils.toFontWeight(lu);
		if (fontWeight == null) {
			throw new PropertyException();
		}
		return fontWeight;
	}

}
