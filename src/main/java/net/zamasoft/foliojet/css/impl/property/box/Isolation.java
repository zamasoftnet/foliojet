package net.zamasoft.foliojet.css.impl.property.box;

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
 * {@code isolation: auto | isolate} (compositing-1 §3, added 2026-08-29).
 *
 * <p>
 * Accepted, but has no effect (and emits no warning). The approximation that applies
 * {@code mix-blend-mode} to individual drawing elements (see {@link MixBlendMode})
 * does not create isolation groups, so this currently cannot have meaningful behavior.
 * </p>
 */
public class Isolation extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Isolation();

	/** Keyword value for {@code isolate}. */
	public static final Value ISOLATE = new Value() {
		@Override
		public String toString() {
			return "isolate";
		}
	};

	protected Isolation() {
		super("isolation");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && !tokens.hasNext()) {
			if (ident.is("auto")) {
				return KeywordValue.AUTO;
			}
			if (ident.is("isolate")) {
				return ISOLATE;
			}
		}
		throw new PropertyException();
	}
}
