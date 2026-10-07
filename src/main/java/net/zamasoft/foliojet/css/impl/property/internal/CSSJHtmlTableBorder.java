package net.zamasoft.foliojet.css.impl.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * Internal property corresponding to HTML table border.
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSJHtmlTableBorder extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlTableBorder();

	public static CSSJHtmlTableBorderValue get(CSSStyle style) {
		CSSJHtmlTableBorderValue value = (CSSJHtmlTableBorderValue) style.get(INFO);
		return value;
	}

	public CSSJHtmlTableBorder() {
		super("-cssj-html-table-border");
	}

	/**
	 * <b>Resolve on the declaring table before inheritance</b> (2026-08-03).
	 * Attribute-derived values ({@code attr(border px)}) depend on the element,
	 * so they cannot resolve on cells if inherited unresolved.
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		if (value instanceof Unresolved unresolved) {
			return unresolved.resolve(style);
		}
		return value;
	}

	/**
	 * Unresolved value immediately after parsing. May contain {@code attr()},
	 * so make it concrete at the computed-value stage ({@link #getComputedValue}).
	 */
	private record Unresolved(Value width, Value color) implements Value {
		Value resolve(CSSStyle style) {
			final Value w = net.zamasoft.foliojet.css.util.ValueUtils.emExToAbsoluteLength(this.width, style);
			final Value c = this.color == null ? null
					: net.zamasoft.foliojet.css.util.ValueUtils.emExToAbsoluteLength(this.color, style);
			if (!(w instanceof net.zamasoft.foliojet.css.value.LengthValue length)) {
				return net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue.NULL_BORDER;
			}
			return new net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue(length,
					c instanceof net.zamasoft.foliojet.css.value.ColorValue color ? color : null);
		}
	}

	public Value getDefault(CSSStyle style) {
		return CSSJHtmlTableBorderValue.NULL_BORDER;
	}

	public boolean isInherited() {
		return true;
	}

	/**
	 * <b>Made writable from CSS</b> (2026-08-03). Syntax:
	 * {@code -cssj-html-table-border: <length> <color>?}.
	 * Needed to move table {@code border}/{@code bordercolor} attributes to CSS;
	 * this value is the internal path for distributing border width and color from tables to cells.
	 */
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		Value width = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, lu,
				net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.LENGTH);
		if (width == null) {
			width = net.zamasoft.foliojet.css.util.BorderValueUtils.toBorderWidth(ua, lu);
		}
		if (width == null) {
			throw new PropertyException();
		}
		Value color = null;
		if (tokens.hasNext()) {
			final net.zamasoft.foliojet.css.token.CssToken next = tokens.next();
			color = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, next,
					net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR);
			if (color == null) {
				color = net.zamasoft.foliojet.css.util.ColorValueUtils.toColor(ua, next);
			}
			if (color == null) {
				throw new PropertyException();
			}
		}
		return new Unresolved(width, color);
	}
}