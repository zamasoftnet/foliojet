package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderImageValueUtils;
import net.zamasoft.foliojet.css.value.BorderImageRepeatValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code border-image-repeat} です。 */
public final class BorderImageRepeat extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderImageRepeat();

	private BorderImageRepeat() {
		super("border-image-repeat");
	}

	public static BorderImageRepeatValue get(CSSStyle style) {
		return (BorderImageRepeatValue) style.get(INFO);
	}

	public Value getDefault(CSSStyle style) {
		return BorderImageRepeatValue.STRETCH;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		return BorderImageValueUtils.parseRepeat(tokens);
	}
}
