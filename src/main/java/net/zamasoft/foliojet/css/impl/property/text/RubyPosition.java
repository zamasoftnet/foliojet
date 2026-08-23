package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RubyPositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** CSS Ruby Level 1の{@code ruby-position}。 */
public final class RubyPosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new RubyPosition();

	public static RubyPositionValue get(final CSSStyle style) {
		return (RubyPositionValue) style.get(INFO);
	}

	private RubyPosition() {
		super("ruby-position");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return RubyPositionValue.ALTERNATE;
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
		if (tokens.eat("inter-character")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return RubyPositionValue.INTER_CHARACTER;
		}
		boolean alternate = false;
		Boolean over = null;
		while (tokens.hasNext()) {
			if (tokens.eat("alternate")) {
				if (alternate) {
					throw new PropertyException();
				}
				alternate = true;
			} else if (tokens.eat("over")) {
				if (over != null) {
					throw new PropertyException();
				}
				over = Boolean.TRUE;
			} else if (tokens.eat("under")) {
				if (over != null) {
					throw new PropertyException();
				}
				over = Boolean.FALSE;
			} else {
				throw new PropertyException();
			}
		}
		if (!alternate && over == null) {
			throw new PropertyException();
		}
		if (!alternate) {
			return over.booleanValue() ? RubyPositionValue.OVER : RubyPositionValue.UNDER;
		}
		if (over == null) {
			return RubyPositionValue.ALTERNATE;
		}
		return over.booleanValue() ? RubyPositionValue.ALTERNATE_OVER : RubyPositionValue.ALTERNATE_UNDER;
	}
}
