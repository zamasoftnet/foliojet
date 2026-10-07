package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextSpacingTrimValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-spacing-trim} (Japanese text spacing T1b, 2026-07-31:
 * consult-codex-2026-07-31-text-spacing.txt). Inherited property.
 * Implements {@code normal}/{@code space-all}/{@code space-first}/{@code trim-start}/
 * {@code trim-both}/{@code auto} with CSS Text 4 semantics.
 * {@code auto} equals {@code trim-both} as the UA's high-quality setting.
 * {@code trim-all} invalidates the declaration until per-character trimming is implemented.
 *
 * @author MIYABE Tatsuhiko
 */
public class TextSpacingTrim extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextSpacingTrim();

	/** True for {@code space-all} (no trimming). */
	public static boolean isSpaceAll(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).isSpaceAll();
	}

	/** True if the value places full-width opening brackets flush at the line start. */
	public static boolean trimsLineStart(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).trimsLineStart();
	}

	/** True if the value always reduces full-width closing punctuation at line ends to half width. */
	public static boolean trimsLineEnd(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).trimsLineEnd();
	}

	/** True if the value preserves full-width line-start punctuation only on the first line and after forced breaks. */
	public static boolean spacesFirstLine(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).spacesFirstLine();
	}

	protected TextSpacingTrim() {
		super("text-spacing-trim");
	}

	public Value getDefault(CSSStyle style) {
		return TextSpacingTrimValue.NORMAL;
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
			return TextSpacingTrimValue.NORMAL;
		}
		if (tokens.eat("space-all")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.SPACE_ALL;
		}
		if (tokens.eat("space-first")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.SPACE_FIRST;
		}
		if (tokens.eat("trim-start")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.TRIM_START;
		}
		if (tokens.eat("trim-both")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.TRIM_BOTH;
		}
		if (tokens.eat("auto")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.AUTO;
		}
		throw new PropertyException();
	}
}
