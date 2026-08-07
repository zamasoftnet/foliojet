package jp.cssj.test.unit.parser;

import junit.framework.TestCase;
import net.zamasoft.foliojet.xml.parser.MarkdownParser;

/**
 * Markdownの強調(** / *)がCJK句読点に隣接しても解釈されることのテストです。
 *
 * <p>
 * CommonMarkのフランキング規則では「閉じデリミタの直前が句読点なら直後は
 * 空白か句読点」が要求され、全角句読点の直後で強調を閉じて地の文が続く
 * 和文の常用パターンが壊れる。{@code CjkFriendlyInlineParser}がこれを
 * 解消していることを、実文書で壊れた実例パターンを含めて確認する。
 * 欧文の挙動(ASCII句読点のフランキング規則)を変えていないことも確認する。
 * </p>
 */
public class MarkdownCjkEmphasisTest extends TestCase {

	/** 実文書(考察メモ_2026-08-06.md)で壊れていた実例パターン。 */
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

	/** 欧文: ASCII句読点のフランキング規則は仕様どおり変えない。 */
	public void testAsciiPunctuationRulesUnchanged() {
		// 通常の欧文強調は解釈される
		String html = MarkdownParser.toHtml("This is **bold.** And *italic* text.");
		assertTrue(html, html.contains("<strong>bold.</strong>"));
		assertTrue(html, html.contains("<em>italic</em>"));
		// 閉じデリミタの直前がASCII句読点で直後が英数字の場合は解釈されない(仕様どおり)
		String broken = MarkdownParser.toHtml("a**b.**c");
		assertFalse(broken, broken.contains("<strong>"));
	}

	/** アンダースコアの語中強調禁止規則が維持されていることの確認。 */
	public void testUnderscoreIntrawordUnchanged() {
		String html = MarkdownParser.toHtml("foo_bar_baz");
		assertFalse(html, html.contains("<em>"));
	}
}
