package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.OverflowValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * overflow-x / overflow-y の軸別プリミティブです。一括指定の
 * {@code overflow}は{@link net.zamasoft.foliojet.css.impl.property.shorthand.OverflowShorthand}が
 * 両軸へ展開します。
 *
 * @author MIYABE Tatsuhiko
 */
public class Overflow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_X = new Overflow("overflow-x");
	public static final PrimitivePropertyInfo INFO_Y = new Overflow("overflow-y");

	/**
	 * 両軸を単一の描画モードへ畳みます。
	 *
	 * <p>
	 * CSS Overflow 3の計算規則では、片軸が非visibleなら他軸のvisibleは
	 * autoへ計算される(軸別に「クリップする/しない」を混在させることは
	 * できない)。印刷ではhidden/scroll/autoはいずれもクリップで
	 * 同じ扱いなので、両軸visibleのときだけvisible、それ以外は
	 * 非visible側のモードを返す。
	 * </p>
	 */
	public static OverflowMode get(CSSStyle style) {
		final OverflowMode x = ((OverflowValue) style.get(INFO_X)).getOverflow();
		final OverflowMode y = ((OverflowValue) style.get(INFO_Y)).getOverflow();
		if (x == y) {
			return x;
		}
		if (x == OverflowMode.VISIBLE) {
			return y;
		}
		if (y == OverflowMode.VISIBLE) {
			return x;
		}
		// 両軸とも非visibleで種類が異なる場合、描画はどれもクリップで
		// 等価。強い方(hidden)を優先する
		return (x == OverflowMode.HIDDEN || y == OverflowMode.HIDDEN) ? OverflowMode.HIDDEN : x;
	}

	private Overflow(String name) {
		super(name);
	}

	public Value getDefault(CSSStyle style) {
		return OverflowValue.VISIBLE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = toValue(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	/**
	 * overflowのキーワード1つを値へ変換します。該当しなければnull。
	 */
	public static Value toValue(CssToken lu) {
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			switch (ident) {
			case "visible":
				return OverflowValue.VISIBLE_VALUE;
			case "hidden":
			// clip(CSS Overflow 3)はスクロール不能なクリップ。印刷では
			// hiddenと等価
			case "clip":
				return OverflowValue.HIDDEN_VALUE;
			case "scroll":
				return OverflowValue.SCROLL_VALUE;
			case "auto":
				return OverflowValue.AUTO_VALUE;
			}
		}
		return null;
	}

}
