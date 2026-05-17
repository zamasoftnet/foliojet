package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.ColorValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.TransparentValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.css3.TextShadowValue;
import jp.cssj.homare.css.value.css3.TextShadowValue.Shadow;
import jp.cssj.homare.impl.css.property.CSSColor;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextShadow.java 1624 2022-05-02 08:59:55Z miyabe $
 */
public class TextShadow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextShadow();

	public static jp.cssj.homare.style.box.params.TextShadow[] get(CSSStyle style) {
		TextShadowValue value = (TextShadowValue) style.get(TextShadow.INFO);
		if (value.getShadows().length == 0) {
			return null;
		}
		Shadow[] src = value.getShadows();
		jp.cssj.homare.style.box.params.TextShadow[] shadows = new jp.cssj.homare.style.box.params.TextShadow[src.length];
		for (int i = 0; i < src.length; ++i) {
			double x;
			double y;
			Color color;
			if (src[i].x == null) {
				x = 0;
			} else {
				x = ((AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(src[i].x, style)).getLength();
			}
			if (src[i].y == null) {
				y = 0;
			} else {
				y = ((AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(src[i].x, style)).getLength();
			}
			if (src[i].color == null) {
				color = CSSColor.get(style);
			} else {
				color = src[i].color.getColor();
			}
			shadows[i] = new jp.cssj.homare.style.box.params.TextShadow(x, y, color);
		}
		return shadows;
	}

	protected TextShadow() {
		super("text-shadow");
	}

	public Value getDefault(CSSStyle style) {
		return TextShadowValue.EMPTY_TEXT_SHADOW;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isNone(lu)) {
			return TextShadowValue.EMPTY_TEXT_SHADOW;
		}
		List<Shadow> shadows = null;
		LengthValue x = null;
		LengthValue y = null;
		Value color = null;
		for (; lu != null; lu = lu.getNextLexicalUnit()) {
			if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
				if (color == null || color != TransparentValue.TRANSPARENT_VALUE) {
					if (shadows == null) {
						shadows = new ArrayList<Shadow>();
					}
					shadows.add(new Shadow(x, y, (ColorValue) color));
				}
				x = y = null;
				color = null;
				continue;
			}
			if (x == null) {
				x = ValueUtils.toLength(ua, lu);
				if (x != null) {
					continue;
				}
			}
			if (y == null) {
				y = ValueUtils.toLength(ua, lu);
				if (y != null) {
					continue;
				}
			}
			if (color == null) {
				if (ColorValueUtils.isTransparent(lu)) {
					color = TransparentValue.TRANSPARENT_VALUE;
				} else {
					color = ColorValueUtils.toColor(ua, lu);
				}
				if (color != null) {
					continue;
				}
			}
			throw new PropertyException();
		}
		if (color == null || color != TransparentValue.TRANSPARENT_VALUE) {
			if (shadows == null) {
				shadows = new ArrayList<Shadow>();
			}
			shadows.add(new Shadow(x, y, (ColorValue) color));
		}
		if (shadows == null) {
			return TextShadowValue.EMPTY_TEXT_SHADOW;
		}
		return TextShadowValue.create((Shadow[]) shadows.toArray(new Shadow[shadows.size()]));
	}
}