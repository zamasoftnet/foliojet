package net.zamasoft.foliojet.css.impl.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * Internal property corresponding to HTML horizontal alignment.
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSJAutoWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJAutoWidth();

	public static Value get(CSSStyle style) {
		return style.get(INFO);
	}

	/**
	 * The natural size of a form control along the width (or the height), which an auto size there computes to
	 * (2026-10-11): its natural inline size (this property: an input's size, a textarea's cols) along the inline axis,
	 * its natural block size ({@link CSSJInternalAutoBlockSize}: a textarea's rows) along the block axis, by its writing
	 * mode; auto under field-sizing: content, which sizes the control to its content.
	 */
	public static Value natural(final CSSStyle style, final boolean height) {
		if (net.zamasoft.foliojet.css.impl.property.box.FieldSizing.isContent(style)) {
			return net.zamasoft.foliojet.css.value.KeywordValue.AUTO;
		}
		final boolean vertical = net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style).isVertical();
		return height != vertical ? CSSJInternalAutoBlockSize.get(style) : get(style);
	}

	protected CSSJAutoWidth() {
		super("-cssj-auto-width");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			return KeywordValue.AUTO;
		}

		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}