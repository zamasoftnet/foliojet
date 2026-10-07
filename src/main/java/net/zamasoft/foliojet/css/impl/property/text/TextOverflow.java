package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-overflow: clip | ellipsis} (css-overflow-3 §4,
 * added 2026-08-29).
 *
 * <p>
 * Not inherited; defaults to {@code clip}. When the block container's {@code overflow}
 * is not {@code visible}, {@code ellipsis} truncates the end of lines that overflow
 * in the inline direction (typically {@code white-space: nowrap} or long unbreakable words)
 * and inserts "…" (U+2026, or "..." if absent from the font).
 * See {@code TextBuilder.applyTextOverflow} for the implementation.
 * Two-value syntax ({@code text-overflow: clip ellipsis}) and string values are unsupported
 * (the entire declaration is ignored).
 * </p>
 */
public class TextOverflow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextOverflow();

	/** Keyword value for {@code ellipsis} (dedicated because KeywordValue has none). */
	public static final Value ELLIPSIS = new Value() {
		@Override
		public String toString() {
			return "ellipsis";
		}
	};

	public static byte get(final CSSStyle style) {
		return style.get(INFO) == ELLIPSIS ? BlockParams.TEXT_OVERFLOW_ELLIPSIS : BlockParams.TEXT_OVERFLOW_CLIP;
	}

	protected TextOverflow() {
		super("text-overflow");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.CLIP;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && !tokens.hasNext()) {
			if (ident.is("clip")) {
				return KeywordValue.CLIP;
			}
			if (ident.is("ellipsis")) {
				return ELLIPSIS;
			}
		}
		throw new PropertyException();
	}
}
