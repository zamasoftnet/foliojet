package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code flex-basis} (Flex F1a, 2026-08-02).
 * {@code auto | content | <nonnegative length-percentage>} (§7.2.3).
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexBasisProperty extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FlexBasisProperty();

	public static FlexBasisValue get(CSSStyle style) {
		return (FlexBasisValue) style.get(INFO);
	}

	private FlexBasisProperty() {
		super("flex-basis");
	}

	public Value getDefault(CSSStyle style) {
		return FlexBasisValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final FlexBasisValue basis = (FlexBasisValue) value;
		if (basis.getSize() == null) {
			return basis;
		}
		return FlexBasisValue.size((QuantityValue) ValueUtils.emExToAbsoluteLength(basis.getSize(), style));
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final FlexBasisValue value = parseBasis(tokens, ua);
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}

	/** Reads one &lt;flex-basis&gt; (shared with the flex shorthand). Null if invalid. */
	public static FlexBasisValue parseBasis(final TokenStream tokens, final UserAgent ua) {
		if (tokens.eat("auto")) {
			return FlexBasisValue.AUTO_VALUE;
		}
		if (tokens.eat("content")) {
			return FlexBasisValue.CONTENT_VALUE;
		}
		final int mark = tokens.position();
		if (!tokens.hasNext()) {
			return null;
		}
		final QuantityValue size = BoxValueUtils.toPositiveLength(ua, tokens.next());
		if (size == null) {
			tokens.rewind(mark);
			return null;
		}
		return FlexBasisValue.size(size);
	}
}
