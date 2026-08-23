package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RubyOverhangValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code ruby-overhang: auto | none}。 */
public final class RubyOverhang extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new RubyOverhang();

	public static RubyOverhangValue get(final CSSStyle style) {
		return (RubyOverhangValue) style.get(INFO);
	}

	private RubyOverhang() {
		super("ruby-overhang");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return RubyOverhangValue.AUTO;
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
		final RubyOverhangValue value;
		if (tokens.eat("auto")) {
			value = RubyOverhangValue.AUTO;
		} else if (tokens.eat("none")) {
			value = RubyOverhangValue.NONE;
		} else {
			throw new PropertyException();
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
