package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextAutospaceValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-autospace} (Japanese text spacing A1, 2026-07-31:
 * consult-codex-2026-07-31-text-spacing.txt). Inherited property.
 * The computed initial value is {@code normal}, as specified, but the UA stylesheet sets
 * {@code html { text-autospace: no-autospace }}, so existing documents look unchanged
 * (enabling it by default later requires one UA line change; recommendation Q4).
 *
 * @author MIYABE Tatsuhiko
 */
public class TextAutospace extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextAutospace();

	/** Effective flags ({@code TextAutospaceValue.ALPHA}|{@code NUMERIC}). */
	public static byte getFlags(CSSStyle style) {
		return ((TextAutospaceValue) style.get(INFO)).getFlags();
	}

	protected TextAutospace() {
		super("text-autospace");
	}

	public Value getDefault(CSSStyle style) {
		return TextAutospaceValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("normal")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextAutospaceValue.NORMAL;
		}
		if (tokens.eat("no-autospace")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextAutospaceValue.NO_AUTOSPACE;
		}
		// ideograph-alpha || ideograph-numeric (any order; no duplicates).
		byte flags = 0;
		while (tokens.hasNext()) {
			if (tokens.eat("ideograph-alpha")) {
				if ((flags & TextAutospaceValue.ALPHA) != 0) {
					throw new PropertyException();
				}
				flags |= TextAutospaceValue.ALPHA;
			} else if (tokens.eat("ideograph-numeric")) {
				if ((flags & TextAutospaceValue.NUMERIC) != 0) {
					throw new PropertyException();
				}
				flags |= TextAutospaceValue.NUMERIC;
			} else {
				// auto/punctuation/insert/replace, etc. are outside the subset (invalid declaration).
				throw new PropertyException();
			}
		}
		switch (flags) {
		case TextAutospaceValue.ALPHA:
			return TextAutospaceValue.IDEOGRAPH_ALPHA;
		case TextAutospaceValue.NUMERIC:
			return TextAutospaceValue.IDEOGRAPH_NUMERIC;
		case TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC:
			return TextAutospaceValue.IDEOGRAPH_ALPHA_NUMERIC;
		default:
			throw new PropertyException();
		}
	}
}
