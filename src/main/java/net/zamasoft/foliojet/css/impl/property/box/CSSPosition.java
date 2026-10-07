package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.RunningPositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSPosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSPosition();

	public static byte get(CSSStyle style) {
		final Value value = style.get(INFO);
		// R1a retains the running value and treats it as static during layout.
		return value instanceof PositionValue position ? position.getPosition() : PositionValue.STATIC;
	}

	private CSSPosition() {
		super("position");
	}

	public Value getDefault(CSSStyle style) {
		return PositionValue.STATIC_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Func func && func.is("running")) {
			final TokenStream params = func.argStream();
			final String name = params.ident();
			if (name == null || params.hasNext() || tokens.hasNext()
					|| java.util.Set.of("none", "default", "initial", "inherit", "unset", "revert", "revert-layer")
							.contains(name.toLowerCase(java.util.Locale.ROOT))) {
				throw new PropertyException();
			}
			return new RunningPositionValue(name);
		}
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("static")) {
				return PositionValue.STATIC_VALUE;
			} else if (ident.equals("relative")) {
				return PositionValue.RELATIVE_VALUE;
			} else if (ident.equals("absolute")) {
				return PositionValue.ABSOLUTE_VALUE;
			} else if (ident.equals("fixed")) {
				return PositionValue.FIXED_VALUE;
			} else if (ident.equals("sticky") || ident.equals("-webkit-sticky")) {
				// -webkit-sticky is the Safari alias (2026-08-29).
				// Paper has no scrollport, so create the same containing block as relative
				// but use zero inset displacement. Collapsing to RELATIVE_VALUE would make
				// bottom, etc. act as normal relative offsets, sending fragments after page breaks
				// outside the type area. Thus, retain sticky as a distinct computed value.
				return PositionValue.STICKY_VALUE;
			}
		}
		throw new PropertyException();
	}

}
