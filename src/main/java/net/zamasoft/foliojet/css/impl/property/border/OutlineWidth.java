package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * outline-width 特性です(CSS UI 3 §4、2026-08-29)。値は
 * {@code <line-width>}(border-widthと同じ。thin/medium/thick可)。
 *
 * @author MIYABE Tatsuhiko
 */
public class OutlineWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new OutlineWidth();

	public static double get(CSSStyle style) {
		return ((AbsoluteLengthValue) style.get(INFO)).getLength();
	}

	protected OutlineWidth() {
		super("outline-width");
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getBorderWidth(BorderWidthKeyword.MEDIUM);
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final LengthValue value = BorderValueUtils.toBorderWidth(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
