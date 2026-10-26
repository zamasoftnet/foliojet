package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * A longhand property whose value is a single keyword (2026-10-26, design-pattern review): each keyword maps to a
 * shared value constant and the computed value is the specified one. The subclasses keep only their table and typed
 * accessors; they used to spell each keyword in a chain of comparisons.
 */
public abstract class KeywordPropertyInfo extends AbstractPrimitivePropertyInfo {
	private final boolean inherited;

	private final Value initial;

	private final Map<String, Value> keywords;

	/**
	 * @param keywords lower-case keywords and their values
	 */
	protected KeywordPropertyInfo(final String name, final boolean inherited, final Value initial,
			final Map<String, Value> keywords) {
		super(name);
		this.inherited = inherited;
		this.initial = initial;
		this.keywords = keywords;
	}

	public Value getDefault(CSSStyle style) {
		return this.initial;
	}

	public boolean isInherited() {
		return this.inherited;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.next() instanceof CssToken.Ident ident) {
			final Value value = this.keywords.get(ident.lower());
			if (value != null) {
				return value;
			}
		}
		throw new PropertyException();
	}
}
