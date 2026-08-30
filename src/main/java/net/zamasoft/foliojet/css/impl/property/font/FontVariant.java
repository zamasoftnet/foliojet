package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontVariantAlternatesValue;
import net.zamasoft.foliojet.css.value.FontVariantEastAsianValue;
import net.zamasoft.foliojet.css.value.FontVariantLigaturesValue;
import net.zamasoft.foliojet.css.value.FontVariantNumericValue;
import net.zamasoft.foliojet.css.value.FontVariantValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-variant}ショートハンドです。
 *
 * <p>
 * 旧実装ではCSS2の{@code normal | small-caps}だけを単一プロパティとして
 * 保持していました。現在はcaps/ligatures/alternates/numeric/east-asianの
 * 各ロングハンドへ展開し、旧{@code small-caps}値は
 * {@link FontVariantCaps}へそのまま接続します。
 * </p>
 */
public final class FontVariant extends AbstractShorthandPropertyInfo {
	private static final Set<String> CAPS = Set.of("small-caps", "all-small-caps", "petite-caps",
			"all-petite-caps", "unicase", "titling-caps");
	private static final Set<String> LIGATURES = Set.of("common-ligatures", "no-common-ligatures",
			"discretionary-ligatures", "no-discretionary-ligatures", "historical-ligatures",
			"no-historical-ligatures", "contextual", "no-contextual");
	private static final Set<String> NUMERIC = Set.of("lining-nums", "oldstyle-nums", "proportional-nums",
			"tabular-nums", "diagonal-fractions", "stacked-fractions", "ordinal", "slashed-zero");
	private static final Set<String> EAST_ASIAN = Set.of("jis78", "jis83", "jis90", "jis04", "simplified",
			"traditional", "full-width", "proportional-width", "ruby");
	private static final Set<String> ALTERNATE_FUNCTIONS = Set.of("stylistic", "styleset", "character-variant",
			"swash", "ornaments", "annotation");

	public static final ShorthandPropertyInfo INFO = new FontVariant();

	/** 旧APIとの互換用。capsロングハンドのコードを返します。 */
	public static double get(final CSSStyle style) {
		return FontVariantCaps.get(style).getFontVariant();
	}

	private FontVariant() {
		super("font-variant");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { FontVariantCaps.INFO, FontVariantLigatures.INFO,
				FontVariantAlternates.INFO, FontVariantNumeric.INFO, FontVariantEastAsian.INFO };
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("normal")) {
			setNormal(primitives);
			return;
		}
		if (tokens.size() == 1 && tokens.eat("none")) {
			setNormal(primitives);
			primitives.set(FontVariantLigatures.INFO, FontVariantLigaturesValue.NONE_VALUE);
			return;
		}

		final List<CssToken> caps = new ArrayList<CssToken>();
		final List<CssToken> ligatures = new ArrayList<CssToken>();
		final List<CssToken> alternates = new ArrayList<CssToken>();
		final List<CssToken> numeric = new ArrayList<CssToken>();
		final List<CssToken> eastAsian = new ArrayList<CssToken>();
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token instanceof CssToken.Ident ident) {
				final String keyword = ident.lower();
				if (CAPS.contains(keyword)) {
					caps.add(token);
				} else if (LIGATURES.contains(keyword)) {
					ligatures.add(token);
				} else if (keyword.equals("historical-forms")) {
					alternates.add(token);
				} else if (NUMERIC.contains(keyword)) {
					numeric.add(token);
				} else if (EAST_ASIAN.contains(keyword)) {
					eastAsian.add(token);
				} else {
					throw new PropertyException();
				}
			} else if (token instanceof CssToken.Func function
					&& ALTERNATE_FUNCTIONS.contains(function.name().toLowerCase(Locale.ROOT))) {
				alternates.add(token);
			} else {
				throw new PropertyException();
			}
		}
		if (caps.isEmpty() && ligatures.isEmpty() && alternates.isEmpty() && numeric.isEmpty()
				&& eastAsian.isEmpty()) {
			throw new PropertyException();
		}

		primitives.set(FontVariantCaps.INFO, caps.isEmpty() ? FontVariantValue.NORMAL_VALUE
				: ((FontVariantCaps) FontVariantCaps.INFO).parseValue(new TokenStream(caps), ua, uri));
		primitives.set(FontVariantLigatures.INFO, ligatures.isEmpty() ? FontVariantLigaturesValue.NORMAL_VALUE
				: ((FontVariantLigatures) FontVariantLigatures.INFO).parseValue(new TokenStream(ligatures), ua, uri));
		primitives.set(FontVariantAlternates.INFO, alternates.isEmpty() ? FontVariantAlternatesValue.NORMAL_VALUE
				: ((FontVariantAlternates) FontVariantAlternates.INFO).parseValue(new TokenStream(alternates), ua, uri));
		primitives.set(FontVariantNumeric.INFO, numeric.isEmpty() ? FontVariantNumericValue.NORMAL_VALUE
				: ((FontVariantNumeric) FontVariantNumeric.INFO).parseValue(new TokenStream(numeric), ua, uri));
		primitives.set(FontVariantEastAsian.INFO, eastAsian.isEmpty() ? FontVariantEastAsianValue.NORMAL_VALUE
				: ((FontVariantEastAsian) FontVariantEastAsian.INFO).parseValue(new TokenStream(eastAsian), ua, uri));
	}

	private static void setNormal(final Primitives primitives) {
		primitives.set(FontVariantCaps.INFO, FontVariantValue.NORMAL_VALUE);
		primitives.set(FontVariantLigatures.INFO, FontVariantLigaturesValue.NORMAL_VALUE);
		primitives.set(FontVariantAlternates.INFO, FontVariantAlternatesValue.NORMAL_VALUE);
		primitives.set(FontVariantNumeric.INFO, FontVariantNumericValue.NORMAL_VALUE);
		primitives.set(FontVariantEastAsian.INFO, FontVariantEastAsianValue.NORMAL_VALUE);
	}
}
