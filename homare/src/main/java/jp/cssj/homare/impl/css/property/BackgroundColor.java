package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.value.PaintValue;
import jp.cssj.homare.css.value.TransparentValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundColor.java 1626 2022-05-03 00:35:38Z miyabe $
 */
public class BackgroundColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundColor();

	public static PaintValue get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_TRANSPARENT) {
			return null;
		}
		return (PaintValue)value;
	}

	protected BackgroundColor() {
		super("background-color");
	}

	public Value getDefault(CSSStyle style) {
		return TransparentValue.TRANSPARENT_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ColorValueUtils.isTransparent(lu)) {
			return TransparentValue.TRANSPARENT_VALUE;
		}
		Value value = ColorValueUtils.toPaint(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}