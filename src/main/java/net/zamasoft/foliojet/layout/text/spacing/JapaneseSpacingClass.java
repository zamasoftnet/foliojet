package net.zamasoft.foliojet.layout.text.spacing;

/**
 * Character classes for Japanese spacing (Japanese spacing adjustment S0, 2026-07-31;
 * consult-codex-2026-07-31-text-spacing.txt Q2/S0; JLREQ classes used for punctuation spacing).
 * Always classify by Unicode code point (int): unlike the existing char-based kinsoku
 * (line-breaking rules) API, this preserves supplementary planes (recommendation Q2).
 *
 * <p>
 * The character sets in the JLREQ appendix, cl-01/cl-02/cl-05–cl-07, are authoritative.
 * The sets originally came from OpenTypeFont.getKerning and TextBuilder; corrections to
 * mismatches with the appendix must be treated as intentional display-list golden differences.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum JapaneseSpacingClass {

	/** CL01: opening brackets. */
	OPENING,

	/** CL02: closing brackets. */
	CLOSING,

	/** CL06/CL07: full stops and commas (the original implementation does not distinguish them). */
	PUNCTUATION,

	/** CL05: middle dots (JLREQ 3.1.5; glyphs include quarter-em space before and after). */
	MIDDLE_DOT,

	/** Not applicable. */
	OTHER;

	private static final String CL01 = "‘“（〔［｛〈《「『【⦅〘〖«〝";

	private static final String CL02 = "’”）〕］｝〉》」』】⦆〙〗»〟";

	private static final String CL0607 = "。．、，";

	/** Middle dot, fullwidth colon, and fullwidth semicolon (fullwidth UCS forms in JLREQ appendix A.5). */
	private static final String CL05 = "・：；";

	/** Classifies a code point. */
	public static JapaneseSpacingClass of(final int codePoint) {
		if ((codePoint < 0x2000 && codePoint != 0x00AB && codePoint != 0x00BB) || codePoint > 0xFFFF) {
			// Covers BMP symbol ranges + guillemets «» (JLREQ appendix A cl-01/02.
			// The old guard rejected everything below U+2000, leaving «» in the CL01/CL02 tables unreachable)
			return OTHER;
		}
		final char c = (char) codePoint;
		if (CL01.indexOf(c) >= 0) {
			return OPENING;
		}
		if (CL02.indexOf(c) >= 0) {
			return CLOSING;
		}
		if (CL0607.indexOf(c) >= 0) {
			return PUNCTUATION;
		}
		if (CL05.indexOf(c) >= 0) {
			return MIDDLE_DOT;
		}
		return OTHER;
	}
}
