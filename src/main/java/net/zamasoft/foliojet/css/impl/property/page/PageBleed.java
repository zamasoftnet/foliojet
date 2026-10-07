package net.zamasoft.foliojet.css.impl.property.page;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code @page { bleed }} (CSS Paged Media 3, 2026-08-02).
 * Values are {@code auto | <length>}; adds the same bleed
 * (the amount extending beyond crop marks) on all four sides.
 *
 * <p>
 * {@code auto} (the default) <b>follows the I/O properties</b>
 * ({@code output.trims} / {@code output.htrim} / {@code output.vtrim}).
 * Relative lengths (em, etc.) are outside the subset and invalidate the declaration.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class PageBleed extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageBleed();

	/** Negative if not specified in CSS (=follows the I/O properties). */
	public static double get(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (value instanceof AbsoluteLengthValue length) {
			return length.getLength();
		}
		return -1;
	}

	protected PageBleed() {
		super("bleed");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (lu instanceof CssToken.Ident ident && ident.is("auto")) {
			return KeywordValue.AUTO;
		}
		final AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, lu);
		if (length == null || length.getLength() < 0) {
			throw new PropertyException();
		}
		return length;
	}
}
