package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSFloat extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSFloat();

	public static byte get(CSSStyle style) {
		CSSFloatValue value = (CSSFloatValue) style.get(INFO);
		return value.getFloat();
	}

	private CSSFloat() {
		super("float");
	}

	public Value getDefault(CSSStyle style) {
		return CSSFloatValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				return CSSFloatValue.NONE_VALUE;
			} else if (ident.equals("left")) {
				return CSSFloatValue.LEFT_VALUE;
			} else if (ident.equals("right")) {
				return CSSFloatValue.RIGHT_VALUE;
			} else if (ident.equals("start") || ident.equals("inline-start")) {
				// inline-start is the standard css-logical-1 name (2026-08-29). Same as the existing
				// start, which uses direction to choose left/right.
				return CSSFloatValue.START_VALUE;
			} else if (ident.equals("end") || ident.equals("inline-end")) {
				return CSSFloatValue.END_VALUE;
			} else if (ident.equals("top")) {
				// Page floats (GCPM/Prince family, 2026-08-02).
				return CSSFloatValue.PAGE_TOP_VALUE;
			} else if (ident.equals("bottom")) {
				return CSSFloatValue.PAGE_BOTTOM_VALUE;
			} else if (ident.equals("block-start")) {
				// Logical directions in css-page-floats (2026-10-05). top/bottom now mean physical top/bottom in vertical writing,
				// so use these for the previous vertical placement (block start/end).
				return CSSFloatValue.PAGE_BLOCK_START_VALUE;
			} else if (ident.equals("block-end")) {
				return CSSFloatValue.PAGE_BLOCK_END_VALUE;
			} else if (ident.equals("footnote")) {
				// GCPM/Prince-style footnote float (F0, 2026-07-31).
				return CSSFloatValue.FOOTNOTE_VALUE;
			} else if (ident.equals("-cssj-note-start")) {
				// JLREQ parallel notes (sidenotes in horizontal writing; headnotes in vertical writing).
				return CSSFloatValue.PAGE_NOTE_START_VALUE;
			} else if (ident.equals("-cssj-note-end")) {
				// JLREQ parallel notes (sidenotes in horizontal writing; footnotes in vertical writing).
				return CSSFloatValue.PAGE_NOTE_END_VALUE;
			}
		}
		throw new PropertyException();
	}

}
