package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.TransparentValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TextShadowValue;
import net.zamasoft.foliojet.css.value.css3.TextShadowValue.Shadow;
import net.zamasoft.foliojet.impl.css.property.CSSColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextShadow.java 1624 2022-05-02 08:59:55Z miyabe $
 */
public class TextShadow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextShadow();

	public static net.zamasoft.foliojet.style.box.params.TextShadow[] get(CSSStyle style) {
		TextShadowValue value = (TextShadowValue) style.get(TextShadow.INFO);
		if (value.getShadows().length == 0) {
			return null;
		}
		Shadow[] src = value.getShadows();
		net.zamasoft.foliojet.style.box.params.TextShadow[] shadows = new net.zamasoft.foliojet.style.box.params.TextShadow[src.length];
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
			shadows[i] = new net.zamasoft.foliojet.style.box.params.TextShadow(x, y, color);
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