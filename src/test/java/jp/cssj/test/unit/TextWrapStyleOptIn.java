package jp.cssj.test.unit;

import java.io.File;

/**
 * Test helper that opts the entire document into Knuth-Plass line breaking
 * (CSS {@code text-wrap-style: pretty}). Added on 2026-07-25 when the proprietary
 * {@code text.line-breaker} property was removed.
 *
 * <p>
 * Parity tests compare the same fixture laid out with both greedy and K-P line breaking.
 * They cannot put CSS in the fixture itself (which would allow only one version), so they load
 * {@code files/unittest/3200-line-breaker/text-wrap-pretty.css} as an author stylesheet
 * through {@code input.default-stylesheet}.
 * </p>
 */
public final class TextWrapStyleOptIn {
	/**
	 * Value passed to {@code input.default-stylesheet}
	 * (CSS containing only {@code html { text-wrap-style: pretty }}).
	 */
	public static final String PRETTY_STYLESHEET = new File(
			"files/unittest/3200-line-breaker/text-wrap-pretty.css").toURI().toString();

	private TextWrapStyleOptIn() {
		// Do not instantiate.
	}
}
