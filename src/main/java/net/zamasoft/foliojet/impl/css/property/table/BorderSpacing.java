package net.zamasoft.foliojet.impl.css.property.table;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractCompositePrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderSpacing extends AbstractCompositePrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_H = new BorderSpacing();
	public static final PrimitivePropertyInfo INFO_V = new BorderSpacing();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_H, INFO_V };

	public static double getHorizontal(CSSStyle style) {
		AbsoluteLengthValue h = (AbsoluteLengthValue) style.get(INFO_H);
		return h.getLength();
	}

	public static double getVertical(CSSStyle style) {
		AbsoluteLengthValue v = (AbsoluteLengthValue) style.get(INFO_V);
		return v.getLength();
	}

	protected BorderSpacing() {
		super("border-spacing");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return true;
	}

	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}

	protected Entry[] parseValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.isInherit()) {
			return new Entry[] { new Entry(BorderSpacing.INFO_H, KeywordValue.INHERIT),
					new Entry(BorderSpacing.INFO_V, KeywordValue.INHERIT) };
		}
		LengthValue h = ValueUtils.toLength(ua, tokens.next());
		LengthValue v;
		if (!tokens.hasNext()) {
			v = h;
		} else {
			v = ValueUtils.toLength(ua, tokens.next());
		}
		if (h != null && v != null) {
			return new Entry[] { new Entry(BorderSpacing.INFO_H, h), new Entry(BorderSpacing.INFO_V, v) };
		}
		throw new PropertyException();
	}

}