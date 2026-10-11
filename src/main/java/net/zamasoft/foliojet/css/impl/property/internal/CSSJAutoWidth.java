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
		final Value natural = height != vertical ? CSSJInternalAutoBlockSize.get(style) : get(style);
		// A button's fit-content is no computed value (a child's width: inherit gets auto): see fitContent
		return natural == KeywordValue.FIT_CONTENT ? KeywordValue.AUTO : natural;
	}

	/**
	 * Whether the inline size of a box whose computed inline size is auto is fit-content (2026-10-11): a button's in the
	 * flow, whatever its display, as in Chrome (bbc's display: block "BBC in other languages" filled the line: 487pt,
	 * Chrome 154.5pt). In a flex or grid container, or absolutely positioned, it stretches as other boxes.
	 */
	public static boolean fitContent(final CSSStyle style) {
		if (get(style) != KeywordValue.FIT_CONTENT) {
			return false;
		}
		final byte parent = net.zamasoft.foliojet.css.impl.property.box.Display.getFlattenedParentDisplay(style);
		if (parent == net.zamasoft.foliojet.css.value.DisplayValue.FLEX
				|| parent == net.zamasoft.foliojet.css.value.DisplayValue.GRID) {
			return false;
		}
		final byte position = net.zamasoft.foliojet.css.impl.property.box.CSSPosition.get(style);
		return position != net.zamasoft.foliojet.css.value.PositionValue.ABSOLUTE
				&& position != net.zamasoft.foliojet.css.value.PositionValue.FIXED;
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
		// fit-content: a button's (html-ua.css, 2026-10-11)
		if (ValueUtils.isKeyword(lu, "fit-content")) {
			return KeywordValue.FIT_CONTENT;
		}

		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}