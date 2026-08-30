package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderImageValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.BorderImageWidthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code border-image-width} です。 */
public final class BorderImageWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderImageWidth();

	private BorderImageWidth() {
		super("border-image-width");
	}

	public static BorderImageWidthValue get(CSSStyle style) {
		return (BorderImageWidthValue) style.get(INFO);
	}

	public Value getDefault(CSSStyle style) {
		return BorderImageWidthValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final BorderImageWidthValue widths = (BorderImageWidthValue) value;
		return new BorderImageWidthValue(ValueUtils.emExToAbsoluteLength(widths.top(), style),
				ValueUtils.emExToAbsoluteLength(widths.right(), style),
				ValueUtils.emExToAbsoluteLength(widths.bottom(), style),
				ValueUtils.emExToAbsoluteLength(widths.left(), style));
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		return BorderImageValueUtils.parseWidth(tokens, ua);
	}
}
