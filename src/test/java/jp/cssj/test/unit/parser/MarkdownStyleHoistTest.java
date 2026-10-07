package jp.cssj.test.unit.parser;

import junit.framework.TestCase;
import net.zamasoft.foliojet.xml.parser.MarkdownParser;

/**
 * Test that raw {@code <style>} in Markdown is hoisted into head
 * (2026-08-10).
 *
 * <p>
 * If left in body, streaming construction has already opened the body box, so properties affecting
 * body/html itself, such as {@code writing-mode: vertical-rl}, are not applied retroactively.
 * Measured in a Markdown manuscript for a vertically written book: fonts and {@code @page} worked,
 * but vertical writing alone did not, making the cause hard to notice.
 * </p>
 */
public class MarkdownStyleHoistTest extends TestCase {

	public void testStyleIsHoistedIntoHead() {
		String html = MarkdownParser.toHtml("<style>\nbody { writing-mode: vertical-rl; }\n</style>\n\n本文。");
		int head = html.indexOf("</head>");
		assertTrue(html, html.indexOf("writing-mode: vertical-rl") < head);
		assertFalse("bodyに<style>を残さない", html.substring(head).contains("<style"));
		assertTrue("本文は残る", html.substring(head).contains("本文。"));
	}

	public void testMultipleStylesKeepDocumentOrder() {
		String html = MarkdownParser.toHtml("<style>p { color: red; }</style>\n\n段落。\n\n<style>p { color: blue; }</style>");
		int head = html.indexOf("</head>");
		int red = html.indexOf("color: red");
		int blue = html.indexOf("color: blue");
		assertTrue(html, red >= 0 && blue >= 0 && red < blue && blue < head);
	}

	/** Keep document styles after the default style (markdown-ua.css), preserving last-wins precedence. */
	public void testHoistedStyleFollowsDefaultStyle() {
		String html = MarkdownParser.toHtml("<style>body { font-size: 3.3mm; }</style>\n\n本文。");
		assertTrue(html, html.indexOf("font-size: 3.3mm") > html.indexOf("@page"));
	}

	/** Documents without style behave as before (only a harmless empty style element is added). */
	public void testNoStyleDocumentUnchanged() {
		String html = MarkdownParser.toHtml("ただの本文。");
		assertTrue(html, html.contains("ただの本文。"));
	}

	/**
	 * Do not inject the default style (markdown-ua.css) when input.default-stylesheet is specified
	 * (2026-08-10, owner's decision). The default is an A4 report for users who specify nothing.
	 * Leaving it underneath a user-designed layout would let p{line-height} and page numbers show through.
	 */
	public void testDefaultStyleOmittedWhenUserStylesheetGiven() {
		String with = MarkdownParser.toHtml("本文。", true);
		String without = MarkdownParser.toHtml("本文。", false);
		assertTrue(with, with.contains("@page"));
		assertFalse(without, without.contains("@page"));
		assertTrue(without, without.contains("本文。"));
	}
}
