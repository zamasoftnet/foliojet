package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextFillColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextFillColor();

	public static net.zamasoft.pdfg2d.gc.paint.Color get(CSSStyle style) {
		Value value = style.get(TextFillColor.INFO);
		if (value == KeywordValue.TRANSPARENT) {
			// 完全透明の色実体を返す(2026-08-18)。以前はnullを返していたが、
			// 描画側(AbstractTextBox等)の「nullなら色を設定しない」は
			// **既定の黒のまま描く**という意味で、transparentのつもりの文字が
			// 黒く見えていた——prism-editor(透明textarea+ハイライトpreの
			// 重ね)でコードが二重に見える実欠陥(chartjs-docs)。
			// alpha 0で描けばcolor:transparentと同じ扱いになり、
			// text-stroke併用のアウトライン文字も正しく残る
			return net.zamasoft.pdfg2d.gc.paint.RGBAColor.create(0, 0, 0, 0);
		}
		if (value == KeywordValue.DEFAULT) {
			return CSSColor.get(style);
		}
		return ((ColorValue) value).getColor();
	}

	protected TextFillColor() {
		super("-cssj-text-fill-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && ident.is("currentcolor")) {
			return KeywordValue.DEFAULT;
		}
		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		Value value = ColorValueUtils.toColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}