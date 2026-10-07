package net.zamasoft.foliojet.css.impl.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * Internal property corresponding to HTML table cellpadding.
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSJHtmlCellPadding extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlCellPadding();

	public static LengthValue get(CSSStyle style) {
		LengthValue value = (LengthValue) style.get(INFO);
		return value;
	}

	public CSSJHtmlCellPadding() {
		super("-cssj-html-cellpadding");
	}

	/**
	 * <b>Resolve on the declaring element (table) before inheritance</b> (2026-08-03).
	 * {@code attr(cellpadding px)} and {@code em} depend on the element,
	 * so inheriting them unresolved would produce different values on cells.
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		return net.zamasoft.foliojet.css.util.ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.create(style.getUserAgent(), 1, Unit.PX);
	}

	public boolean isInherited() {
		return true;
	}

	/** Made writable from CSS (2026-08-03). {@code <length>} (attr() also allowed). */
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = net.zamasoft.foliojet.css.util.BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}