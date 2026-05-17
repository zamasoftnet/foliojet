package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.value.ColorValue;
import jp.cssj.homare.css.value.DefaultValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.CSSColor;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextEmphasisColor.java 1624 2022-05-02 08:59:55Z miyabe $
 */
public class TextEmphasisColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextEmphasisColor();

	public static Color get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == DefaultValue.DEFAULT_VALUE) {
			value = style.get(CSSColor.INFO);
		}
		return ((ColorValue) value).getColor();
	}

	protected TextEmphasisColor() {
		super("-cssj-text-emphasis-color");
	}

	public Value getDefault(CSSStyle style) {
		return DefaultValue.DEFAULT_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = ColorValueUtils.toColor(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}