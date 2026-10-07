package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.VarSubstitution;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * The set of properties that can be interpreted in a given context (element, @page, @font-face).
 *
 * @author MIYABE Tatsuhiko
 */
public abstract class PropertySet {
	private static final Logger LOG = Logger.getLogger(PropertySet.class.getName());

	private final Map<String, PropertyInfo> nameToInfo = new HashMap<String, PropertyInfo>();

	/**
	 * Registers a property.
	 */
	protected final void put(PropertyInfo... infos) {
		for (PropertyInfo info : infos) {
			this.nameToInfo.put(info.getName(), info);
		}
	}

	/**
	 * Registers a property under an alias (vendor prefix, etc.).
	 */
	protected final void alias(String name, PropertyInfo info) {
		this.nameToInfo.put(name, info);
	}

	protected PropertyInfo getPropertyParser(String name) {
		return this.nameToInfo.get(name);
	}

	/**
	 * Enumerates registered properties (for registration consistency tests:
	 * {@code PropertyCodeRegistryTest} statically checks that every interpretable property
	 * has a cascade code). Prevents recurrence of the pitfall encountered with @page size:
	 * registering only the name without assigning a code made set() silently drop the value
	 * (2026-08-01).
	 */
	public final java.util.Collection<PropertyInfo> registeredInfos() {
		return java.util.Collections.unmodifiableCollection(this.nameToInfo.values());
	}

	public final Property parseDeclaration(String name, List<CssToken> value, UserAgent ua, URI uri,
			boolean important) {
		if (isCustomPropertyName(name)) {
			// Retain custom properties (--name) as raw token sequences without type validation
			// (do not lowercase the name, since names are case-sensitive).
			return new CustomProperty(name, value, uri, important);
		}
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph != null) {
			if (VarSubstitution.containsEnvReference(value)) {
				// env() does not depend on the element, so substitute it at parse time (2026-08-29).
				// An unknown name with no fallback invalidates the entire declaration (specification).
				final List<CssToken> substituted = VarSubstitution.substituteEnv(value);
				if (substituted == null) {
					ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, new TokenStream(value).toString(),
							"env()");
					return null;
				}
				value = substituted;
			}
			if (VarSubstitution.containsVarReference(value)) {
				// The actual var() value can differ when applying the cascade to each element,
				// so parsing cannot be finalized here (once per document during stylesheet parsing).
				// Defer it until per-element application (see DeferredProperty).
				return new DeferredProperty(name, ph, value, ua, uri, important);
			}
			if (isRevert(value)) {
				// revert/revert-layer (css-cascade-4/5, 2026-08-29). Discarding the declaration
				// is the closest approximation: revert-layer restores the preceding layer's
				// value, and revert restores the UA/user-origin value. Both match or approximate
				// the cascade result without this declaration (differences arise only when
				// the same layer or origin contains another declaration).
				// Previously, these produced invalid-value warnings, but the result was the same.
				return null;
			}
			TokenStream tokens = new TokenStream(value);
			try {
				return ph.parse(tokens, ua, uri, important);
			} catch (PropertyException e) {
				String m = name + ":" + tokens + ":" + e.getMessage();
				LOG.log(Level.FINE, m, e);
				ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, tokens.toString(), e.getMessage());
				return null;
			}
		}
		if (SVG_PRESENTATION_PROPERTIES.contains(name.toLowerCase(java.util.Locale.ROOT))) {
			// SVG presentation attributes (fill/stroke, etc.) have no meaning for HTML boxes,
			// but their rules are passed to Batik for inline SVG, where they take effect
			// (CSSStyleSheetBuilder.collectSVGStyleRule). Warning about them here as
			// unsupported made fill the most frequent false warning on 31 of 50 real sites
			// (2026-08-29). Accept silently.
			return null;
		}
		ua.message(isIgnored(name) ? MessageCodes.WARN_IGNORED_CSS_PROPERTY
				: MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, name);
		return null;
	}

	/**
	 * SVG presentation attributes absent from HTML boxes but forwarded to inline SVG
	 * (2026-08-29). Excludes {@code opacity}/{@code clip-path}/{@code mask}/
	 * {@code filter}, which are also HTML-side properties.
	 */
	private static final Set<String> SVG_PRESENTATION_PROPERTIES = Set.of("fill", "fill-opacity", "fill-rule",
			"stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
			"stroke-dasharray", "stroke-dashoffset", "stop-color", "stop-opacity", "marker-start", "marker-mid",
			"marker-end", "marker", "text-anchor", "dominant-baseline", "baseline-shift", "alignment-baseline",
			"vector-effect", "shape-rendering", "color-interpolation", "color-interpolation-filters",
			"flood-color", "flood-opacity", "lighting-color", "clip-rule", "glyph-orientation-vertical",
			"glyph-orientation-horizontal", "enable-background", "color-rendering");

	/** Whether the value is a standalone {@code revert}/{@code revert-layer}. */
	private static boolean isRevert(final List<CssToken> value) {
		return value.size() == 1 && value.get(0) instanceof CssToken.Ident ident
				&& (ident.is("revert") || ident.is("revert-layer"));
	}

	/**
	 * Properties <b>intentionally unsupported</b> because they have no meaning for
	 * static typesetting (2026-08-28).
	 *
	 * <p>
	 * Lists properties that concern only on-screen interaction, changes over time, or
	 * input devices. Using the same warning as for not-yet-implemented properties mixes
	 * them into counts used to select implementation candidates from real-site warnings:
	 * in one observed article, 45 of 126 unsupported warnings fell into this category.
	 * Strip prefixes ({@code -webkit-}, {@code -moz-}, {@code -ms-}, {@code -o-}) before checking.
	 * </p>
	 */
	private static final Set<String> IGNORED_PROPERTIES = Set.of(
			// Input devices and interaction
			"cursor", "pointer-events", "user-select", "touch-action", "caret-color",
			"resize", "appearance", "tap-highlight-color", "user-drag", "user-modify",
			"overscroll-behavior", "overscroll-behavior-x", "overscroll-behavior-y",
			"scroll-behavior", "scrollbar-color", "scrollbar-width", "scroll-snap-type",
			"scroll-snap-align", "scroll-margin", "scroll-padding",
			// Changes over time
			"transition", "transition-property", "transition-duration",
			"transition-timing-function", "transition-delay",
			"animation", "animation-name", "animation-duration", "animation-timing-function",
			"animation-delay", "animation-iteration-count", "animation-direction",
			"animation-fill-mode", "animation-play-state", "will-change",
			// Added on 2026-08-29 from observations of 50 sites. Controls screen rendering,
			// scrolling, GPU compositing, and input devices; has no effect on paper.
			"text-size-adjust", "font-smoothing", "osx-font-smoothing", "overflow-scrolling",
			"backface-visibility", "overflow-style", "touch-callout", "text-rendering",
			"color-scheme", "transform-style", "perspective", "perspective-origin",
			"backdrop-filter", "interpolation-mode", "text-decoration-skip",
			"text-decoration-skip-ink", "scrollbar-gutter", "khtml-user-select", "speak",
			"contain-intrinsic-size", "contain", "ms-filter", "print-color-adjust",
			"scroll-snap-stop", "scroll-margin-top", "scroll-margin-bottom", "scroll-margin-left",
			"scroll-margin-right", "scroll-padding-top", "scroll-padding-bottom",
			"scroll-padding-left", "scroll-padding-right", "scroll-margin-block",
			"scroll-margin-inline", "scroll-padding-block", "scroll-padding-inline",
			"scroll-timeline", "view-transition-name", "accent-color", "field-sizing",
			"box-orient", "box-direction", "box-pack", "box-align", "box-flex",
			"box-ordinal-group", "box-lines", "font-optical-sizing",
			// zoom (render-time scaling) and text-underline-position were implemented and removed from the ignore list on 2026-08-29
			"image-rendering", "ime-mode", "font-smooth", "line-clamp-fallback");

	/** Whether the name without its prefix is in {@link #IGNORED_PROPERTIES}. */
	static boolean isIgnored(final String name) {
		if (name == null) {
			return false;
		}
		String bare = name.toLowerCase(java.util.Locale.ROOT);
		for (final String prefix : new String[] { "-webkit-", "-moz-", "-ms-", "-o-", "-khtml-" }) {
			if (bare.startsWith(prefix)) {
				bare = bare.substring(prefix.length());
				break;
			}
		}
		return IGNORED_PROPERTIES.contains(bare);
	}

	private static boolean isCustomPropertyName(String name) {
		return name.length() > 2 && name.charAt(0) == '-' && name.charAt(1) == '-';
	}

	/**
	 * Evaluates @supports (name: value). Only checks whether the property name is
	 * registered and the given value can be parsed as that property, then discards the
	 * actual value. Unlike ordinary {@link #parseDeclaration}, failures produce no warnings:
	 * @supports tests for lack of support, so an unsupported value is a normal result.
	 */
	public final boolean supports(String name, List<CssToken> value, UserAgent ua, URI uri) {
		if (isCustomPropertyName(name)) {
			// Custom property declaration syntax is always valid (CSS specification: properties
			// starting with -- accept arbitrary token sequences).
			return true;
		}
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph == null) {
			return false;
		}
		if (VarSubstitution.containsEnvReference(value)) {
			value = VarSubstitution.substituteEnv(value);
			if (value == null) {
				return false;
			}
		}
		if (VarSubstitution.containsVarReference(value)) {
			// The actual var() value can differ per element, so do not evaluate it here;
			// always return true (matching browser behavior: declarations containing var()
			// are unconditionally considered supported in @supports checks).
			return true;
		}
		try {
			return ph.parse(new TokenStream(value), ua, uri, false) != null;
		} catch (PropertyException e) {
			return false;
		}
	}
}
