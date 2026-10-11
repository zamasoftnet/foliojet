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
	 * discarding those that cannot resolve (undefined with no fallback, etc.). Each selector also goes in from its
	 * compounds that can start inside the svg ({@link #selectorsFor}, 2026-10-11).
	 */
	public synchronized String toCssText(CSSStyle varContext) {
		if (this.rules.isEmpty()) {
			return "";
		}
		final StringBuilder buff = new StringBuilder();
		for (final Rule rule : this.rules.values()) {
			StringBuilder decls = null;
			// Without what the svg's HTML box applies already (svgDecls)
			final StringBuilder svgDecls = new StringBuilder();
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
				final int start = decls.length();
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
				if (!BOX_PROPERTIES.contains(d.property())) {
					svgDecls.append(decls, start, decls.length());
				}
			}
			if (decls == null) {
				continue;
			}
			final StringBuilder selectors = new StringBuilder();
			final StringBuilder onSvg = new StringBuilder();
			for (final String selector : rule.selectors().split(",")) {
				if (!selector.isBlank()) {
					selectors.append(selectors.length() == 0 ? "" : ",")
							.append(selectorsFor(selector.trim(), varContext, onSvg));
				}
			}
			buff.append(selectors).append('{').append(decls).append("}\n");
			if (onSvg.length() > 0 && svgDecls.length() > 0) {
				buff.append(onSvg).append('{').append(svgDecls).append("}\n");
			}
		}
		return buff.toString();
	}

	/**
	 * A selector as it applies in one inline svg (2026-10-11): as written, which may match inside the SVG document, and
	 * from each compound that can start inside the svg, the part before matched against the svg element's ancestors in
	 * the HTML document and dropped. Batik sees no HTML ancestor: "span.c svg" matched nothing (Chrome fills the svg in
	 * span.c), and mkdocs-material's ".md-typeset .twemoji svg { fill: currentcolor }" left its icons black.
	 * <ul>
	 * <li>The compound after a child combinator, when it matches the svg element and the part before its ancestors
	 * ("div.u > SVG" as "svg").</li>
	 * <li>A compound after a descendant combinator, when the part before matches proper ancestors (".f .g path" as
	 * "path"; ".q svg" does not reach svg.q itself).</li>
	 * </ul>
	 * The specificity of the part dropped does not count inside the svg, and "div > svg" reaches a nested svg too
	 * (CSS-SUPPORT §8). A form whose subject can be the svg element goes into {@code onSvg}, to take the rule without
	 * what the svg's HTML box applies already (codex: picocss's ".code > a.copy-to-clipboard > svg { opacity: .5 }" on
	 * the root made Batik's drawing an image).
	 *
	 * @param selector a selector in the Batik-safe form (tags, classes, ids, descendant and child combinators;
	 *                 CSSStyleSheetBuilder.collectSVGStyleRule)
	 * @param host     the svg element's style, null for none
	 * @param onSvg    receives the forms whose subject can be the svg element, comma-separated
	 */
	static String selectorsFor(final String selector, final CSSStyle host, final StringBuilder onSvg) {
		final CSSElement svg = host == null ? null : host.getCSSElement();
		if (svg == null) {
			return selector;
		}
		final List<CSSElement> ancestors = new ArrayList<CSSElement>();
		for (CSSStyle s = host.getParentStyle(); s != null; s = s.getParentStyle()) {
			// Elements of the document only, not anonymous boxes (codex: "tr svg" in a display: table-cell div)
			if (s.getCSSElement() != null && !s.isAnonStyle()) {
				ancestors.add(s.getCSSElement());
			}
		}
		final List<String> compounds = new ArrayList<String>();
		final List<Boolean> child = new ArrayList<Boolean>(); // whether '>' comes before the compound
		boolean nextChild = false;
		for (final String token : selector.replace(">", " > ").trim().split("\\s+")) {
			if (token.equals(">")) {
				nextChild = true;
				continue;
			}
			compounds.add(token);
			child.add(nextChild);
			nextChild = false;
		}
		final StringBuilder out = new StringBuilder(selector);
		final Map<Long, Boolean> memo = new java.util.HashMap<Long, Boolean>();
		for (int k = 1; k < compounds.size(); ++k) {
			final String compound = compounds.get(k);
			final boolean onRoot = matches(compound, svg);
			if (child.get(k) ? onRoot && prefix(compounds, child, k - 1, true, ancestors, 0, memo)
					: prefix(compounds, child, k - 1, false, ancestors, 0, memo)) {
				final StringBuilder form = onRoot && k == compounds.size() - 1 ? onSvg : out;
				// The type of the svg element in lower case, as Batik matches it ("div.u > SVG")
				final int end = typeEnd(compound);
				form.append(form.length() == 0 ? "" : ",")
						.append(onRoot ? compound.substring(0, end).toLowerCase(java.util.Locale.ROOT)
								: compound.substring(0, end))
						.append(compound.substring(end));
				for (int i = k + 1; i < compounds.size(); ++i) {
					form.append(child.get(i) ? " > " : " ").append(compounds.get(i));
				}
			}
		}
		return out.toString();
	}

	/**
	 * Whether compounds[0..last] match the ancestors from index {@code from} outwards: compounds[last] on that one when
	 * a child combinator follows it, else on it or one further out. Memoised (a long run of descendant combinators
	 * over a deep tree tried the same pairs again and again).
	 */
	private static boolean prefix(final List<String> compounds, final List<Boolean> child, final int last,
			final boolean childNext, final List<CSSElement> ancestors, final int from, final Map<Long, Boolean> memo) {
		if (last < 0) {
			return true;
		}
		final long key = ((long) last << 33) | ((long) from << 1) | (childNext ? 1 : 0);
		final Boolean known = memo.get(key);
		if (known != null) {
			return known.booleanValue();
		}
		boolean result = false;
		for (int i = from; i < ancestors.size(); ++i) {
			if (matches(compounds.get(last), ancestors.get(i))
					&& prefix(compounds, child, last - 1, child.get(last), ancestors, i + 1, memo)) {
				result = true;
				break;
			}
			if (childNext) {
				break;
			}
		}
		memo.put(key, Boolean.valueOf(result));
		return result;
	}

	/** What the HTML box of an svg element applies to it already (toCssText). */
	private static final java.util.Set<String> BOX_PROPERTIES = java.util.Set.of("opacity", "filter", "clip-path",
			"mask");

	/** The end of the type selector at the start of a compound (0 when it has none). */
	private static int typeEnd(final String compound) {
		int i = 0;
		while (i < compound.length() && compound.charAt(i) != '.' && compound.charAt(i) != '#') {
			++i;
		}
		return i;
	}

	/** Whether a compound of the Batik-safe form (a type or *, .classes, #ids) matches the element. */
	private static boolean matches(final String compound, final CSSElement ce) {
		int i = typeEnd(compound);
		final String type = compound.substring(0, i);
		if (!type.isEmpty() && !type.equals("*") && (ce.lName == null || !ce.lName.equalsIgnoreCase(type))) {
			return false;
		}
		while (i < compound.length()) {
			final char kind = compound.charAt(i);
			int j = i + 1;
			while (j < compound.length() && compound.charAt(j) != '.' && compound.charAt(j) != '#') {
				++j;
			}
			final String name = compound.substring(i + 1, j);
			if (kind == '#' ? !name.equals(ce.id) : !hasClass(ce, name)) {
				return false;
			}
			i = j;
		}
		return true;
	}

	private static boolean hasClass(final CSSElement ce, final String name) {
		if (ce.styleClasses != null) {
			for (final String c : ce.styleClasses) {
				if (c.equals(name)) {
					return true;
				}
			}
		}
		return false;
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
		if (token instanceof CssToken.Num || token instanceof CssToken.Percent || token instanceof CssToken.Str) {
			return token.toString();
		}
		if (token instanceof CssToken.Dim dim) {
			// Batik's scanner knows only the CSS2 units. Any other unit (rem, vw, ch, ...) makes it reject the whole
			// <style> with warning 280D (Docusaurus' .close{font-size:1.5rem} in every inline SVG, 2026-10-09),
			// so drop just this declaration and keep the rest of the rule.
			return dim.unitText() != null && BATIK_UNITS.contains(dim.unitText().toLowerCase(java.util.Locale.ROOT))
					? token.toString()
					: null;
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

	/** Dimension units Batik's CSS2-era scanner reads. */
	private static final java.util.Set<String> BATIK_UNITS = java.util.Set.of( //
			"px", "pt", "pc", "in", "cm", "mm", "em", "ex", "deg", "rad", "grad", "ms", "s", "hz", "khz");

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
