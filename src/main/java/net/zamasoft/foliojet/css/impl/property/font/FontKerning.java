package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontKerningValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * {@code font-kerning: auto | normal | none}(css-fonts-4 §6.3、2026-08-29)。
 *
 * <p>
 * カーニング本体は実装済みで、{@code none}で切る制御だけが無かった
 * (実サイトの警告で確認)。{@code none}は{@code kern}機能の明示offとして
 * フォントの機能列へ渡す——pdfg2dの{@code FontMetricsImpl}は明示offの
 * ときだけペア調整を無効にする。{@code font-feature-settings}の明示指定は
 * これより後に重ねるので、そちらが優先する。
 * </p>
 */
public class FontKerning extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontKerning();

	/** {@code kern}を明示offにする機能列。 */
	private static final FontFeatureSet KERN_OFF = FontFeatureSet.of(new int[] { FontFeatureSet.packTag("kern") },
			new int[] { 0 });

	public static FontKerningValue get(final CSSStyle style) {
		return (FontKerningValue) style.get(INFO);
	}

	/** {@code none}なら{@code kern} 0、それ以外は空(何も上書きしない)。 */
	public static FontFeatureSet featureSet(final CSSStyle style) {
		return get(style) == FontKerningValue.NONE_VALUE ? KERN_OFF : FontFeatureSet.EMPTY;
	}

	protected FontKerning() {
		super("font-kerning");
	}

	public Value getDefault(final CSSStyle style) {
		return FontKerningValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && !tokens.hasNext()) {
			switch (ident.lower()) {
			case "auto":
				return FontKerningValue.AUTO_VALUE;
			case "normal":
				return FontKerningValue.NORMAL_VALUE;
			case "none":
				return FontKerningValue.NONE_VALUE;
			default:
				break;
			}
		}
		throw new PropertyException();
	}
}
