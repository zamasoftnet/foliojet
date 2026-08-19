package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-variation-settings}です(css-fonts-4、2026-08-20新設)。
 *
 * <p>
 * <b>@font-faceディスクリプタとして</b>対応する——指定の軸座標
 * (例: {@code "wdth" 75, "slnt" -10})で可変フォントを固定インスタンス化
 * する({@code VariableFontInstancer})。wght軸が明示されていれば
 * ウェイト掃引はせずその1本を生成する。<b>要素プロパティとしての適用は
 * 未対応</b>(静的インスタンス方式では要素ごとの軸適用が高価。
 * 要素ごとにはfont-weightの通常機構を使う)。
 * </p>
 */
public class FontVariationSettings extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariationSettings();

	/** 軸タグ→座標のリスト値。 */
	public record AxesValue(Map<String, Double> axes) implements Value {
	}

	/** 指定の軸マップ(normal/未指定はnull)。 */
	public static Map<String, Double> get(final CSSStyle style) {
		final Value value = style.get(FontVariationSettings.INFO);
		return value instanceof AxesValue v ? v.axes() : null;
	}

	protected FontVariationSettings() {
		super("font-variation-settings");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final Map<String, Double> axes = new LinkedHashMap<>();
		boolean first = true;
		while (tokens.hasNext()) {
			if (!first) {
				tokens.eatComma();
				if (!tokens.hasNext()) {
					break;
				}
			}
			final CssToken lu = tokens.next();
			if (first && lu instanceof CssToken.Ident ident && ident.is("normal")) {
				return KeywordValue.NORMAL;
			}
			first = false;
			if (!(lu instanceof CssToken.Str str) || str.value().length() != 4) {
				throw new PropertyException();
			}
			if (!tokens.hasNext()) {
				throw new PropertyException();
			}
			final CssToken num = tokens.next();
			if (!(num instanceof CssToken.Num n)) {
				throw new PropertyException();
			}
			axes.put(str.value(), n.value());
		}
		if (axes.isEmpty()) {
			throw new PropertyException();
		}
		return new AxesValue(axes);
	}
}
