package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.VarSubstitution;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * A declaration value containing var(). An individual property's type cannot be
 * determined when parsing declarations (once per document, shared, during stylesheet
 * parsing), because the actual var() value can differ when applying the cascade to each
 * element. Retains the raw token sequence and defers interpretation ({@link PropertyInfo#parse})
 * until {@link #applyProperty} (cascade application, called for each element).
 *
 * @author MIYABE Tatsuhiko
 */
public final class DeferredProperty implements Property {
	private final String name;
	private final PropertyInfo propertyInfo;
	private final List<CssToken> tokens;
	private final UserAgent ua;
	private final URI uri;
	private final boolean important;

	public DeferredProperty(String name, PropertyInfo propertyInfo, List<CssToken> tokens, UserAgent ua, URI uri,
			boolean important) {
		this.name = name;
		this.propertyInfo = propertyInfo;
		this.tokens = tokens;
		this.ua = ua;
		this.uri = uri;
		this.important = important;
	}

	public String getName() {
		return this.name;
	}

	public URI getURI() {
		return this.uri;
	}

	public boolean isImportant() {
		return this.important;
	}

	/**
	 * Resolves var() for each element, then reruns ordinary property parsing.
	 * If the referenced custom property is absent (with no fallback), references form a
	 * cycle, or the resolved tokens cannot be interpreted as this property, follows the CSS
	 * specification's invalid-at-computed-value-time rule and <b>treats the declaration as
	 * if {@code unset} were specified</b> (see {@link #applyInvalidAtComputedValueTime};
	 * fixed on 2026-08-03: previously nothing was set, leaving lower-priority declarations
	 * that should have lost the cascade in effect).
	 * Unlike ordinary declaration parsing failures, these failures produce no warnings
	 * because they can occur for each element (to avoid flooding warnings when the same
	 * rule matches thousands of elements).
	 */
	public void applyProperty(CSSStyle style) {
		List<CssToken> substituted = VarSubstitution.substitute(this.tokens, style);
		if (substituted == null) {
			this.applyInvalidAtComputedValueTime(style);
			return;
		}
		Property resolved;
		try {
			resolved = this.propertyInfo.parse(new TokenStream(substituted), this.ua, this.uri, this.important);
		} catch (PropertyException e) {
			this.applyInvalidAtComputedValueTime(style);
			return;
		}
		if (resolved != null) {
			resolved.applyProperty(style);
		}
	}

	/**
	 * Applies the invalid-at-computed-value-time rule (2026-08-03).
	 *
	 * <p>
	 * <b>Doing nothing is insufficient.</b> Previously, {@code return} here left
	 * <b>lower-priority declarations on the same element in effect</b>:
	 * {@code p { color: blue; color: var(--未定義) }} stayed blue.
	 * The {@code var()} declaration won the cascade, so blue has already lost and
	 * must not be revived.
	 * </p>
	 *
	 * <p>
	 * The specification (CSS Variables 1, "invalid at computed-value time") treats this
	 * declaration as if {@code unset} were specified: the inherited value for inherited
	 * properties and the initial value for non-inherited properties. Set it explicitly,
	 * since {@link CSSStyle} resolves {@code unset} exactly this way. Chrome and Firefox
	 * behave the same way (confirmed on 2026-08-03).
	 * </p>
	 */
	private void applyInvalidAtComputedValueTime(final CSSStyle style) {
		// Shorthands are also possible, so **pass `unset` through the same parser**
		// instead of setting a value directly. Each shorthand then expands it to all
		// of its longhands (every property accepts CSS-wide keywords).
		final Property unset;
		try {
			unset = this.propertyInfo.parse(new TokenStream(java.util.List.of(CssToken.Keyword.UNSET)), this.ua,
					this.uri, this.important);
		} catch (PropertyException e) {
			// No property is expected to reject a CSS-wide keyword. Even if one does,
			// this is no worse than the previous behavior of doing nothing.
			return;
		}
		if (unset != null) {
			unset.applyProperty(style);
		}
	}

	public String toString() {
		return this.name + ": " + this.tokens + (this.important ? " !important" : "") + " [var() deferred]";
	}
}
