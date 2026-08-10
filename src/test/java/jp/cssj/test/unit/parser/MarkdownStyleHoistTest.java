package jp.cssj.test.unit.parser;

import junit.framework.TestCase;
import net.zamasoft.foliojet.xml.parser.MarkdownParser;

/**
 * Markdown中の生{@code <style>}がheadへ巻き上げられることのテストです
 * (2026-08-10)。
 *
 * <p>
 * body内に残すと、ストリーミング構築ではbodyのボックスが既に開いている
 * ため、body/html自身に効くプロパティ——{@code writing-mode: vertical-rl}
 * 等——が遡って適用されない(縦書き書籍のMarkdown原稿で実測: フォントや
 * {@code @page}は効くのに縦書きだけ効かず、原因に気づきにくい)。
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

	/** 既定スタイル(markdown-ua.css)より文書側が後=後勝ちを保つ。 */
	public void testHoistedStyleFollowsDefaultStyle() {
		String html = MarkdownParser.toHtml("<style>body { font-size: 3.3mm; }</style>\n\n本文。");
		assertTrue(html, html.indexOf("font-size: 3.3mm") > html.indexOf("@page"));
	}

	/** styleの無い文書は従来どおり(空のstyle要素が増えるだけで無害)。 */
	public void testNoStyleDocumentUnchanged() {
		String html = MarkdownParser.toHtml("ただの本文。");
		assertTrue(html, html.contains("ただの本文。"));
	}
}
