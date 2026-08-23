package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RubyAlignValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code ruby-align: start | center | space-between | space-around}。 */
public final class RubyAlign extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new RubyAlign();

	public static RubyAlignValue get(final CSSStyle style) {
		return (RubyAlignValue) style.get(INFO);
	}

	private RubyAlign() {
		super("ruby-align");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return RubyAlignValue.SPACE_AROUND;
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
		final RubyAlignValue value;
		if (tokens.eat("start")) {
			value = RubyAlignValue.START;
		} else if (tokens.eat("center")) {
			value = RubyAlignValue.CENTER;
		} else if (tokens.eat("space-between")) {
			value = RubyAlignValue.SPACE_BETWEEN;
		} else if (tokens.eat("space-around")) {
			value = RubyAlignValue.SPACE_AROUND;
		} else {
			throw new PropertyException();
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
