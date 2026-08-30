package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderImageValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.BorderImageOutsetValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code border-image-outset} です。 */
public final class BorderImageOutset extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderImageOutset();

	private BorderImageOutset() {
		super("border-image-outset");
	}

	public static BorderImageOutsetValue get(CSSStyle style) {
		return (BorderImageOutsetValue) style.get(INFO);
	}

	public Value getDefault(CSSStyle style) {
		return BorderImageOutsetValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final BorderImageOutsetValue outset = (BorderImageOutsetValue) value;
		return new BorderImageOutsetValue(ValueUtils.emExToAbsoluteLength(outset.top(), style),
				ValueUtils.emExToAbsoluteLength(outset.right(), style),
				ValueUtils.emExToAbsoluteLength(outset.bottom(), style),
				ValueUtils.emExToAbsoluteLength(outset.left(), style));
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		return BorderImageValueUtils.parseOutset(tokens, ua);
	}
}
