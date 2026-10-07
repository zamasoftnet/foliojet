package jp.cssj.test.unit.parser;

import junit.framework.TestCase;
import net.zamasoft.foliojet.xml.parser.MarkdownParser;

/**
 * Test that Markdown emphasis (** / *) is recognized even next to CJK punctuation.
 *
 * <p>
 * CommonMark flanking rules require whitespace or punctuation after a closing delimiter
 * if punctuation immediately precedes it. This breaks the common Japanese pattern of closing
 * emphasis after full-width punctuation and continuing with body text.
 * Check that {@code CjkFriendlyInlineParser} resolves this, including patterns that failed in
 * real documents. Also check that Latin-text behavior (ASCII-punctuation flanking rules) remains unchanged.
 * </p>
 */
public class MarkdownCjkEmphasisTest extends TestCase {

	/** A pattern that failed in a real document (考察メモ_2026-08-06.md). */
	public void testStrongClosedAfterFullwidthColon() {
		String html = MarkdownParser.toHtml("**精密に：**希釈は法的でなく政治的である。");
		assertTrue(html, html.contains("<strong>精密に：</strong>希釈は"));
	}

	public void testStrongClosedAfterIdeographicFullStop() {
		String html = MarkdownParser.toHtml("これは**本当である。**だからこそ論拠に使ってはならない。");
		assertTrue(html, html.contains("<strong>本当である。</strong>だからこそ"));
	}

	public void testStrongClosedAfterFullwidthParenthesis() {
		String html = MarkdownParser.toHtml("値は**67 / 78（85.9%）**に達した。");
		assertTrue(html, html.contains("<strong>67 / 78（85.9%）</strong>に達した"));
	}

	public void testStrongOpenedBeforeCornerBracket() {
		String html = MarkdownParser.toHtml("彼は**「在日の総意」**は虚構であると述べた。");
		assertTrue(html, html.contains("<strong>「在日の総意」</strong>は虚構である"));
	}

	public void testEmphasisWithKatakanaMiddleDot() {
		String html = MarkdownParser.toHtml("対象は*ヒト・モノ・カネ*である。");
		assertTrue(html, html.contains("<em>ヒト・モノ・カネ</em>である"));
	}

	/** Latin text: keep ASCII-punctuation flanking rules unchanged, as specified. */
	public void testAsciiPunctuationRulesUnchanged() {
		// Normal Latin-text emphasis is recognized.
		String html = MarkdownParser.toHtml("This is **bold.** And *italic* text.");
		assertTrue(html, html.contains("<strong>bold.</strong>"));
		assertTrue(html, html.contains("<em>italic</em>"));
		// Not recognized when ASCII punctuation precedes the closing delimiter and an alphanumeric follows (as specified).
		String broken = MarkdownParser.toHtml("a**b.**c");
		assertFalse(broken, broken.contains("<strong>"));
	}

	/** Check that the rule prohibiting intraword underscore emphasis is preserved. */
	public void testUnderscoreIntrawordUnchanged() {
		String html = MarkdownParser.toHtml("foo_bar_baz");
		assertFalse(html, html.contains("<em>"));
	}
}
