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
				// **src[i].yを使う**(2026-08-18修正)。従来はコピーミスでxを
				// 参照しており、`text-shadow: 0 1px`の影が本体と同座標に
				// 落ちて二重描画になっていた(reveal.jsドキュメントの
				// コードブロックで監査が重なり319対を報告した実欠陥)
				y = ((AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(src[i].y, style)).getLength();
			}
			if (src[i].color == null) {
				color = CSSColor.get(style);
			} else {
				color = src[i].color.getColor();
			}
			shadows[i] = new net.zamasoft.foliojet.layout.box.params.TextShadow(x, y, color);
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
					shadows.add(new Shadow(x, y, color instanceof ColorValue cv ? cv : null));
				}
				x = y = blur = null;
				color = null;
				continue;
			}
			// 色は長さの前後どちらにも書ける(css-text-decoration-3)。
			// 2026-08-29: 実サイトの `0 -1px 0 rgba(0,0,0,.3)` は3つ目の長さ
			// (ぼかし半径)で解析失敗していた。ぼかしは受理して無視する
			// (影はぼかさず描く——記録済みの近似)。currentcolorは
			// 色なし(=描画時にその要素のcolor)と同じ
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
			// 影にはx/yの2つの長さが要る(色だけ・長さ1つは無効)
			throw new PropertyException();
		}
		if (color == null || color != KeywordValue.TRANSPARENT) {
			if (shadows == null) {
				shadows = new ArrayList<Shadow>();
			}
			shadows.add(new Shadow(x, y, color instanceof ColorValue cv ? cv : null));
		}
		if (shadows == null) {
			return TextShadowValue.EMPTY_TEXT_SHADOW;
		}
		return TextShadowValue.create((Shadow[]) shadows.toArray(new Shadow[shadows.size()]));
	}
}