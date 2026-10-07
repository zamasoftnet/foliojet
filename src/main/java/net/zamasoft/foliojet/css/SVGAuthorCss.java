package net.zamasoft.foliojet.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.VarSubstitution;

/**
 * The SVG subset of author CSS imported into inline SVG (2026-08-07).
 *
 * <p>
 * Inline SVG is passed to Batik as an independent document, so the HTML document's
 * stylesheet does not reach it. Icons whose fill/stroke is set through CSS classes
 * are filled black by SVG's default fill=black (discovered on qiita's like button).
 * During stylesheet parsing, collect only rules containing SVG presentation
 * declarations here, then inject them into the SVG document as &lt;style&gt;
 * Batik applies the cascade ({@code CSSStyleSheetBuilder.collectSVGStyleRule}
 * collects them; {@code SVGInlineObject.getImage} injects them).
 * </p>
 *
 * <p>
 * Retain declaration values as raw token sequences. Modern sites almost always
 * set icon colors through {@code var(--color-*)} (observed on qiita),
 * so discarding declarations containing var() loses the main intended rules. Defer resolution
 * until injection, looking up custom properties in the SVG element's
 * style context ({@code CSSStyle}) via {@link VarSubstitution}. Deduplicate additions by key
 * because multiple passes reparse the same stylesheet.
 * </p>
 */
public final class SVGAuthorCss {

	/** One declaration. tokens is a raw token sequence that may contain var(). */
	public record Decl(String property, List<CssToken> tokens, boolean important) {
	}

	/** One rule (selectors filtered to a Batik-safe form plus declarations). */
	public record Rule(String selectors, List<Decl> declarations) {
	}

	private final Map<String, Rule> rules = new LinkedHashMap<String, Rule>();

	public synchronized void addRule(Rule rule) {
		final StringBuilder key = new StringBuilder(rule.selectors());
		for (final Decl d : rule.declarations()) {
			key.append('{').append(d.property()).append(':').append(d.tokens()).append(d.important());
		}
		this.rules.putIfAbsent(key.toString(), rule);
	}

	public synchronized boolean isEmpty() {
		return this.rules.isEmpty();
	}

	/**
	 * Builds CSS text to pass to Batik. Resolves declarations containing var()
	 * in {@code varContext} (the style of the SVG element receiving injection),
	 * discarding those that cannot resolve (undefined with no fallback, etc.).
	 */
	public synchronized String toCssText(CSSStyle varContext) {
		if (this.rules.isEmpty()) {
			return "";
		}
		final StringBuilder buff = new StringBuilder();
		for (final Rule rule : this.rules.values()) {
			StringBuilder decls = null;
			for (final Decl d : rule.declarations()) {
				List<CssToken> tokens = d.tokens();
				if (VarSubstitution.containsVarReference(tokens)) {
					if (varContext == null) {
						continue;
					}
					tokens = VarSubstitution.substitute(tokens, varContext);
					if (tokens == null) {
						continue;
					}
				}
				final String value = serialize(tokens);
				if (value == null || value.isEmpty() || value.indexOf('<') >= 0) {
					// Discard entire declarations for values Batik cannot read, empty values, or values
					// that could break CDATA/element boundaries. **Even one invalid value makes Batik
					// invalidate the entire stylesheet** (observed on 2026-08-07:
					// the space-separated rgb(0 0 255) invalidated everything), so this defense
					// is essential for injection to work. The injection side also gives each rule
					// its own <style>, containing any unexpected invalid value to one rule.
					continue;
				}
				if ("display".equals(d.property())
						&& !BATIK_DISPLAY_VALUES.contains(value.toLowerCase(java.util.Locale.ROOT))) {
					// Batik's CSS2-era display validation rejects flex/grid, etc.,
					// and invalidates the entire sheet (observed with display:flex on qiita).
					continue;
				}
				if (decls == null) {
					decls = new StringBuilder();
				}
				decls.append(d.property()).append(':').append(value);
				if (d.important()) {
					decls.append(" !important");
				}
				decls.append(';');
				// SVG 1.1 colors cannot represent alpha from rgba()/rgb(r g b / a).
				// Dropping it makes colors too dark (qiita's outlines use rgb(0 0 0 / 12%)),
				// so move it to the corresponding *-opacity for fill/stroke/stop-color only.
				final Double alpha = extractAlpha(tokens);
				final String opacityProp = opacityPropertyFor(d.property());
				if (alpha != null && opacityProp != null) {
					decls.append(opacityProp).append(':').append(alpha);
					if (d.important()) {
						decls.append(" !important");
					}
					decls.append(';');
				}
			}
			if (decls != null) {
				buff.append(rule.selectors()).append('{').append(decls).append("}\n");
			}
		}
		return buff.toString();
	}

	/**
	 * Serializes values into a form Batik's CSS2-era parser can read. Return null
	 * (discard the entire declaration) for tokens whose readability is uncertain.
	 * Keep acceptance narrow because one invalid value invalidates the entire stylesheet in Batik.
	 */
	private static String serialize(List<CssToken> tokens) {
		final List<String> parts = new ArrayList<String>(tokens.size());
		for (final CssToken token : tokens) {
			final String part = serializeToken(token);
			if (part == null) {
				return null;
			}
			parts.add(part);
		}
		return String.join(" ", parts).trim();
	}

	private static String serializeToken(CssToken token) {
		if (token instanceof CssToken.Num || token instanceof CssToken.Percent || token instanceof CssToken.Dim
				|| token instanceof CssToken.Str) {
			return token.toString();
		}
		if (token instanceof CssToken.Ident ident) {
			return ident.name();
		}
		if (token instanceof CssToken.Uri uri) {
			// url(#id), etc. Required for gradient references inside the SVG document.
			return "url(" + uri.uri() + ")";
		}
		if (token == CssToken.Op.COMMA) {
			return ",";
		}
		if (token == CssToken.Op.SLASH) {
			return "/";
		}
		if (token instanceof CssToken.Func func) {
			// Allow only color functions. #hex becomes an rgb() function during tokenization,
			// but naive space-separated serialization is unreadable by Batik, so rebuild it
			// as comma-separated rgb(r,g,b). Drop rgba() alpha because Batik
			// (SVG 1.1 colors) cannot represent it (approximation). Batik cannot interpret
			// other functions (calc, etc.), so discard their entire declarations.
			if (func.is("rgb") || func.is("rgba")) {
				final List<Integer> components = new ArrayList<Integer>(4);
				for (final CssToken arg : func.args()) {
					if (arg instanceof CssToken.Num num) {
						components.add((int) Math.round(num.value()));
					} else if (arg instanceof CssToken.Percent percent) {
						components.add((int) Math.round(percent.value() * 2.55));
					}
				}
				if (components.size() < 3) {
					return null;
				}
				return "rgb(" + clamp255(components.get(0)) + "," + clamp255(components.get(1)) + ","
						+ clamp255(components.get(2)) + ")";
			}
			return null;
		}
		// Keyword (inherit, etc.), unicode-range, and similar tokens are out of scope.
		return null;
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/** Returns the alpha (0..1) if the value is a single rgba() or similar function with alpha. */
	private static Double extractAlpha(List<CssToken> tokens) {
		if (tokens.size() != 1 || !(tokens.get(0) instanceof CssToken.Func func)
				|| !(func.is("rgb") || func.is("rgba"))) {
			return null;
		}
		final List<Double> nums = new ArrayList<Double>(4);
		final List<Boolean> pcts = new ArrayList<Boolean>(4);
		for (final CssToken arg : func.args()) {
			if (arg instanceof CssToken.Num num) {
				nums.add(num.value());
				pcts.add(Boolean.FALSE);
			} else if (arg instanceof CssToken.Percent percent) {
				nums.add(percent.value());
				pcts.add(Boolean.TRUE);
			}
		}
		if (nums.size() < 4) {
			return null;
		}
		double a = nums.get(3).doubleValue();
		if (pcts.get(3).booleanValue()) {
			a /= 100d;
		}
		return Double.valueOf(Math.max(0d, Math.min(1d, a)));
	}

	/** Values accepted by Batik's CSS2-era display validation. */
	private static final java.util.Set<String> BATIK_DISPLAY_VALUES = java.util.Set.of( //
			"none", "inline", "block", "list-item", "run-in", "compact", "marker", //
			"table", "inline-table", "table-row-group", "table-header-group", "table-footer-group", //
			"table-row", "table-column-group", "table-column", "table-cell", "table-caption", "inherit");

	private static String opacityPropertyFor(String property) {
		switch (property) {
		case "fill":
			return "fill-opacity";
		case "stroke":
			return "stroke-opacity";
		case "stop-color":
			return "stop-opacity";
		default:
			return null;
		}
	}
}
