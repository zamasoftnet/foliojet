package net.zamasoft.foliojet.css.impl.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJRubyValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJRuby extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJRuby();

	public static byte get(CSSStyle style) {
		CSSJRubyValue value = (CSSJRubyValue) style.get(INFO);
		return value.getRuby();
	}

	protected CSSJRuby() {
		super("-cssj-ruby");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		// Ruby is annotated text (decoration attached to characters), not a box
		// (2026-07-25 specification decision; development record
		// spec-decision.md). Role markers (ruby/rb/rt) do not depend on display.
		// StyleBuilder always forces ruby-related elements to INLINE,
		// and the text processing layer (StyledTextUnitizer) assembles the units.
		// Removed display guards (requiring INLINE_BLOCK/BLOCK) from the old box-based approach.
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CSSJRubyValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				return CSSJRubyValue.NONE_VALUE;
			} else if (ident.equals("ruby")) {
				return CSSJRubyValue.RUBY_VALUE;
			} else if (ident.equals("rb")) {
				return CSSJRubyValue.RB_VALUE;
			} else if (ident.equals("rt")) {
				return CSSJRubyValue.RT_VALUE;
			} else if (ident.equals("rtc")) {
				return CSSJRubyValue.RTC_VALUE;
			}
		}
		throw new PropertyException();
	}

}
