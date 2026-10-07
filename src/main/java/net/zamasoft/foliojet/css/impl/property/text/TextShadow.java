package net.zamasoft.foliojet.css.impl.property.text;

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
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TextShadowValue;
import net.zamasoft.foliojet.css.value.css3.TextShadowValue.Shadow;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextShadow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextShadow();

	public static net.zamasoft.foliojet.layout.box.params.TextShadow[] get(CSSStyle style) {
		TextShadowValue value = (TextShadowValue) style.get(TextShadow.INFO);
		if (value.getShadows().length == 0) {
			return null;
		}
		Shadow[] src = value.getShadows();
		net.zamasoft.foliojet.layout.box.params.TextShadow[] shadows = new net.zamasoft.foliojet.layout.box.params.TextShadow[src.length];
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
				// **Use src[i].y** (fixed 2026-08-18). A copy error previously referenced x,
				// placing the `text-shadow: 0 1px` shadow at the same coordinates as the text
				// and drawing it twice (an actual defect where the audit reported 319 overlapping
				// pairs in code blocks in the reveal.js documentation).
				y = ((AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(src[i].y, style)).getLength();
			}
			if (src[i].color == null) {
				color = CSSColor.get(style);
			} else {
				color = src[i].color.getColor();
			}
			// Blur radius (2026-08-29). Previously parsed and discarded.
			final double blur = src[i].blur == null ? 0
					: Math.max(0, ((AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(src[i].blur, style)).getLength());
			shadows[i] = new net.zamasoft.foliojet.layout.box.params.TextShadow(x, y, blur, color);
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

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isNone(tokens.peek())) {
			return TextShadowValue.EMPTY_TEXT_SHADOW;
		}
		List<Shadow> shadows = null;
		LengthValue x = null;
		LengthValue y = null;
		LengthValue blur = null;
		Value color = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu == CssToken.Op.COMMA) {
				if (x == null || y == null) {
					throw new PropertyException();
				}
				if (color == null || color != KeywordValue.TRANSPARENT) {
					if (shadows == null) {
						shadows = new ArrayList<Shadow>();
					}
					shadows.add(new Shadow(x, y, blur, color instanceof ColorValue cv ? cv : null));
				}
				x = y = blur = null;
				color = null;
				continue;
			}
			// Color may appear before or after lengths (css-text-decoration-3).
			// 2026-08-29: real-world `0 -1px 0 rgba(0,0,0,.3)` failed to parse at the third length
			// (blur radius). Since 2026-08-29, blur also affects rendering
			// (the same multi-step translucent approximation as box-shadow). currentcolor
			// equals an omitted color (=the element's color at rendering time).
			if (color == null && ColorValueUtils.isCurrentColor(lu)) {
				color = KeywordValue.DEFAULT;
				continue;
			}
			if (color == null && !(lu instanceof CssToken.Dim) && !(lu instanceof CssToken.Num)) {
				if (ColorValueUtils.isTransparent(lu)) {
					color = KeywordValue.TRANSPARENT;
				} else {
					color = ColorValueUtils.toColor(ua, lu);
				}
				if (color != null) {
					continue;
				}
			}
			if (x == null) {
				x = ValueUtils.toLength(ua, lu);
				if (x != null) {
					continue;
				}
			} else if (y == null) {
				y = ValueUtils.toLength(ua, lu);
				if (y != null) {
					continue;
				}
			} else if (blur == null) {
				blur = ValueUtils.toLength(ua, lu);
				if (blur != null && !blur.isNegative()) {
					continue;
				}
			}
			throw new PropertyException();
		}
		if (x == null || y == null) {
			// A shadow needs two lengths, x/y (color only or one length is invalid).
			throw new PropertyException();
		}
		if (color == null || color != KeywordValue.TRANSPARENT) {
			if (shadows == null) {
				shadows = new ArrayList<Shadow>();
			}
			shadows.add(new Shadow(x, y, blur, color instanceof ColorValue cv ? cv : null));
		}
		if (shadows == null) {
			return TextShadowValue.EMPTY_TEXT_SHADOW;
		}
		return TextShadowValue.create((Shadow[]) shadows.toArray(new Shadow[shadows.size()]));
	}
}