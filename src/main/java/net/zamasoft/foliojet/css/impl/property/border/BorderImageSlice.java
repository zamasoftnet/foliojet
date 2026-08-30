package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderImageValueUtils;
import net.zamasoft.foliojet.css.value.BorderImageSliceValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code border-image-slice} です。 */
public final class BorderImageSlice extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderImageSlice();

	private BorderImageSlice() {
		super("border-image-slice");
	}

	public static BorderImageSliceValue get(CSSStyle style) {
		return (BorderImageSliceValue) style.get(INFO);
	}

	public Value getDefault(CSSStyle style) {
		return BorderImageSliceValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		return BorderImageValueUtils.parseSlice(tokens, ua);
	}
}
