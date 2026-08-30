package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

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
 * {@code font-synthesis-small-caps}です(css-fonts-4、2026-08-30新設)。
 *
 * <p>
 * 値は{@code auto | none}、既定は{@code auto}で継承する。
 * 本エンジンにはスモールキャップの合成機構がないため、プロパティの受理・
 * カスケード・継承だけを行い、合成の実処理にはまだ使用しない。
 * </p>
 */
public class FontSynthesisSmallCaps extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontSynthesisSmallCaps();

	/** スモールキャップ合成を許す指定か。 */
	public static boolean get(final CSSStyle style) {
		return style.get(INFO) != KeywordValue.NONE;
	}

	protected FontSynthesisSmallCaps() {
		super("font-synthesis-small-caps");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("auto")) {
				return KeywordValue.AUTO;
			}
			if (ident.is("none")) {
				return KeywordValue.NONE;
			}
		}
		throw new PropertyException();
	}
}
