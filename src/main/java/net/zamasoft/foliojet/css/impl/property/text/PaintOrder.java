package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.PaintOrderValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code paint-order: normal | [ fill || stroke || markers ]}です。
 */
public final class PaintOrder extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PaintOrder();

	public static PaintOrderValue get(final CSSStyle style) {
		return (PaintOrderValue) style.get(INFO);
	}

	private PaintOrder() {
		super("paint-order");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return PaintOrderValue.NORMAL;
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
		if (tokens.eat("normal")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return PaintOrderValue.NORMAL;
		}
		final byte[] order = new byte[3];
		int count = 0;
		while (tokens.hasNext()) {
			final byte value;
			if (tokens.eat("fill")) {
				value = PaintOrderValue.FILL;
			} else if (tokens.eat("stroke")) {
				value = PaintOrderValue.STROKE;
			} else if (tokens.eat("markers")) {
				value = PaintOrderValue.MARKERS;
			} else {
				throw new PropertyException();
			}
			for (int i = 0; i < count; ++i) {
				if (order[i] == value) {
					throw new PropertyException();
				}
			}
			order[count++] = value;
		}
		if (count == 0) {
			throw new PropertyException();
		}
		return PaintOrderValue.create(order, count);
	}
}
