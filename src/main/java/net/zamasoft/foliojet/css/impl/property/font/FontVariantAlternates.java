package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontVariantAlternatesValue;
import net.zamasoft.foliojet.css.value.FontVariantAlternatesValue.Alternate;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-variant-alternates}(CSS Fonts)です。
 *
 * <p>
 * {@code historical-forms}はOpenType {@code hist}として描画段へ搬送します。
 * {@code stylistic()/styleset()/character-variant()/swash()/ornaments()/annotation()}
 * は第一フォントファミリの{@code @font-feature-values}を参照してOpenType機能へ
 * 解決します。未定義名を含む関数だけを無視します。
 * </p>
 */
public final class FontVariantAlternates extends AbstractPrimitivePropertyInfo {
	private static final Set<String> FUNCTIONS = Set.of("stylistic", "styleset", "character-variant", "swash",
			"ornaments", "annotation");

	public static final PrimitivePropertyInfo INFO = new FontVariantAlternates();

	public static FontVariantAlternatesValue get(final CSSStyle style) {
		return (FontVariantAlternatesValue) style.get(INFO);
	}

	private FontVariantAlternates() {
		super("font-variant-alternates");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return FontVariantAlternatesValue.NORMAL_VALUE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("normal")) {
			return FontVariantAlternatesValue.NORMAL_VALUE;
		}
		boolean historicalForms = false;
		final Set<String> seen = new HashSet<String>();
		final List<Alternate> alternates = new ArrayList<Alternate>();
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token instanceof CssToken.Ident ident && ident.is("historical-forms")) {
				if (historicalForms) {
					throw new PropertyException();
				}
				historicalForms = true;
				continue;
			}
			if (!(token instanceof CssToken.Func function)) {
				throw new PropertyException();
			}
			final String name = function.name().toLowerCase(Locale.ROOT);
			if (!FUNCTIONS.contains(name) || !seen.add(name)) {
				throw new PropertyException();
			}
			final List<String> names = parseNames(function.argStream());
			if (names.size() != 1 && !name.equals("styleset") && !name.equals("character-variant")) {
				throw new PropertyException();
			}
			alternates.add(new Alternate(name, names));
		}
		if (!historicalForms && alternates.isEmpty()) {
			throw new PropertyException();
		}
		return FontVariantAlternatesValue.create(historicalForms, alternates);
	}

	private static List<String> parseNames(final TokenStream args) throws PropertyException {
		final List<String> names = new ArrayList<String>();
		boolean needName = true;
		while (args.hasNext()) {
			if (!needName) {
				if (!args.eatComma()) {
					throw new PropertyException();
				}
				needName = true;
				continue;
			}
			final String name = args.ident();
			if (name == null || isReserved(name)) {
				throw new PropertyException();
			}
			names.add(name);
			needName = false;
		}
		if (names.isEmpty() || needName) {
			throw new PropertyException();
		}
		return names;
	}

	private static boolean isReserved(final String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
		case "initial", "inherit", "unset", "default", "revert", "revert-layer" -> true;
		default -> false;
		};
	}
}
