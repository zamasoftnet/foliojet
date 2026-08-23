package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RubyMergeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code ruby-merge: separate | merge | auto}。 */
public final class RubyMerge extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new RubyMerge();

	public static RubyMergeValue get(final CSSStyle style) {
		return (RubyMergeValue) style.get(INFO);
	}

	private RubyMerge() {
		super("ruby-merge");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return RubyMergeValue.SEPARATE;
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
		final RubyMergeValue value;
		if (tokens.eat("separate")) {
			value = RubyMergeValue.SEPARATE;
		} else if (tokens.eat("merge")) {
			value = RubyMergeValue.MERGE;
		} else if (tokens.eat("auto")) {
			value = RubyMergeValue.AUTO;
		} else {
			throw new PropertyException();
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
