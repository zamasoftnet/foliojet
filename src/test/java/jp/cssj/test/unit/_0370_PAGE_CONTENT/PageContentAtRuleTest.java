package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.util.List;
import java.util.Map;

import com.helger.css.decl.CSSPageRule;
import com.helger.css.decl.CSSUnknownRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;
import com.helger.css.writer.CSSWriterSettings;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.style.running.PageContentAtRules;

/** ph-cssへ渡す前後のASTと、合成ノードの実配置を検査します。 */
public final class PageContentAtRuleTest extends TestCase {
	public void testTopLevelAndPageNestedFormsHaveIdenticalOutputAndPlacement() throws Exception {
		final String nested = LegacyPageContentTest.read("legacy-at-rule.html");
		final String top = nested.replace("@page :left { @-cssj-page-content footer {", "@-cssj-page-content footer left {")
				.replace("} }\nhtml", "}\nhtml");
		assertFalse(top.contains("@page :left"));
		final var n = LegacyPageContentTest.convert(nested, Map.of());
		final var t = LegacyPageContentTest.convert(top, Map.of());
		assertEquals(n.pages(), t.pages());
		assertEquals(3, n.pages().size());
		for (int i = 0; i < 3; ++i) {
			final String page = n.pages().get(i);
			assertEquals(i == 1, page.contains("Text[\"FOOT/"));
			assertTrue(page, page.contains("Text[\"MARGIN\""));
		}
		final String second = n.pages().get(1);
		assertTrue(second, second.contains("Text[\"FOOT/2\""));
		LegacyPageContentTest.assertFrame(second, 0, 148, 220, 12);
		final double x = LegacyPageContentTest.coordinate(LegacyPageContentTest.textLine(second, "FOOT/2"), "x");
		assertTrue("text-align:center: " + x, x > 70 && x < 110);
	}

	public void testPreprocessingPreservesPageRulesDeclarationsAndMarginBlocks() {
		final String css = "@page { size:300pt 200pt; margin:20pt; @top-center{content:'KEEP } ;'} }"
				+ "@page :first {margin-top:30pt; color:red; @-cssj-page-content head{content:'H';top:-5pt}}"
				+ "@page :left{ @bottom-left{content:'LEFT'} @-cssj-page-content foot{content:counter(page);bottom:0;} }"
				+ "@page :right{margin-right:40pt} @page :single{margin-left:10pt}";
		final String processed = PageContentAtRules.preprocess(css);
		final var before = parse(css);
		final var after = parse(processed);
		assertEquals(pageRules(before), pageRules(after));
		assertEquals(5, pageRules(after).size());
		final var unknown = after.getAllRules().stream().filter(CSSUnknownRule.class::isInstance)
				.map(CSSUnknownRule.class::cast).toList();
		assertEquals(2, unknown.size());
		assertEquals("head first", unknown.get(0).getParameterList().trim());
		assertEquals("foot left", unknown.get(1).getParameterList().trim());
		assertEquals(processed, PageContentAtRules.preprocess(processed));
		System.err.println("[legacy R3] page AST before/after=" + pageRules(after) + "\n" + processed);
	}

	public void testScannerSkipsQuotedCommentedEscapedAndNestedLookalikes() {
		final String opaque = "/* @page{@-cssj-page-content fake{content:'X'}} */"
				+ "p{content:'@page { @-cssj-page-content fake { } }';background:url(data:x,{a;b});}"
				+ "@unknown{@page{@-cssj-page-content fake{content:'X'}}}"
				+ "@page{@top-center{content:'\\\"}';@-cssj-page-content fake{content:'X'}}}";
		assertEquals(opaque, PageContentAtRules.preprocess(opaque));
		final String real = "@page /* selector comment */ :left { margin:20pt;"
				+ "@-cssj-page-content 'a/*literal*/' {content:'escaped \\\' } ;';"
				+ "background:url(data:x,{a;b});--nested:{a:{b:c}};} margin-bottom:30pt;}";
		final String processed = PageContentAtRules.preprocess(real);
		assertTrue(processed, processed.contains("@-cssj-page-content 'a/*literal*/' left {"));
		assertTrue(processed, processed.contains("--nested:{a:{b:c}}"));
		assertTrue(processed, processed.contains("margin-bottom:30pt;"));
		assertEquals(1, processed.split("@-cssj-page-content", -1).length - 1);
		for (final String incomplete : List.of("@page{@-cssj-page-content x{content:'a'", "@page{content:'unterminated")) {
			assertEquals(incomplete, PageContentAtRules.preprocess(incomplete));
		}
	}

	public void testDeclarationsAfterLegacyBlockArePreserved() {
		final String css = "@page :left {margin-top:10pt; @-cssj-page-content p{content:'P'} margin-bottom:20pt;}";
		final String control = "@page :left {margin-top:10pt; margin-bottom:20pt;}";
		assertEquals(pageRules(parse(control)), pageRules(parse(PageContentAtRules.preprocess(css))));
		// ph-cssは未知のブロック以降の宣言も失う場合がある。ここでは著者の宣言を保護する。
		System.err.println("[legacy R3] original trailing declarations=" + pageRules(parse(css)));
	}

	public void testMediaConditionsAndRuleOrderSurviveHoisting() throws Exception {
		final String css = "@media screen{@page{@-cssj-page-content hidden{content:'HIDDEN'}}}"
				+ "@supports(display:block){@media print{@page{@-cssj-page-content p{content:'EARLY';top:0;left:0}}}}"
				+ "@-cssj-page-content p {content:'LAST';top:0;left:0;font:10pt serif}"
				+ "@page :first{margin-top:40pt;@top-center{content:'FIRST'}}";
		final var result = LegacyPageContentTest.convert(RunningRenderTest.document(css, "<p>BODY</p>"), Map.of());
		final String page = result.pages().get(0);
		assertEquals("LAST", RunningRenderTest.artifactText(page));
		assertTrue(page, page.contains("Text[\"FIRST\""));
		assertFalse(page, page.contains("HIDDEN") || page.contains("EARLY"));
	}

	public void testAtRuleCanBeClearedAndReplacedByMarker() throws Exception {
		final var result = LegacyPageContentTest.convert(RunningRenderTest.document(
				"@-cssj-page-content p{content:'GLOBAL';top:0;left:0;font:10pt serif}"
				+ ".clear{-cssj-page-content-clear:'p'}.pc{-cssj-page-content:p;top:0;left:0}"
				+ "section+section{break-before:page}",
				"<section><p>BODY1</p></section><section><p class='clear'>BODY2</p></section>"
				+ "<section><div class='pc'>MARKER</div><p>BODY3</p></section>"), Map.of());
		assertEquals(List.of("GLOBAL", "", "MARKER"), result.pages().stream().map(RunningRenderTest::artifactText).toList());
	}

	public void testEscapedPageKeywordIsRecognizedAsWholeIdentifier() {
		assertHoisted("@p\\61 ge :left", "left");
		assertHoisted("@\\000070age :left", "left");
	}

	public void testEscapedPagePseudoClassIsDecoded() {
		assertHoisted("@page :l\\65 ft", "left");
		assertHoisted("@page :\\00006ceft", "left");
		assertHoisted("@page :l\\65\r\nft", "left");
	}

	public void testNonAsciiAndEscapedPageNamesKeepPseudoMask() {
		assertHoisted("@page 章:left", "left");
		assertHoisted("@page \\7ae0:left", "left");
		assertHoisted("@page chapter2:left", "left");
		assertHoisted("@page --chapter:left", "left");
		assertHoisted("@page a\\,b:left, 章:right", "left right");
		assertHoisted("@page a\\:b:left", "left");
	}

	public void testEscapedNewlineKeepsStringOpaqueIncludingCrLf() {
		for (final String newline : List.of("\r\n", "\n", "\r", "\f")) {
			final String opaque = "p{content:'before\\" + newline
					+ "} @page { @-cssj-page-content fake {content:X} } after';}";
			assertEquals(opaque, PageContentAtRules.preprocess(opaque));
			final String processed = PageContentAtRules.preprocess(opaque
					+ "@page :left{@-cssj-page-content h{content:'H'}}");
			assertTrue(processed, processed.startsWith(opaque));
			assertTrue(processed, processed.endsWith("\n@-cssj-page-content h left {content:'H'}"));
			assertEquals(processed, PageContentAtRules.preprocess(processed));
		}
	}

	public void testPageKeywordPrefixesAndUnknownPseudoClassesAreNotHoisted() {
		for (final String header : List.of("@page1", "@page_name", "@page-extra", "@page章", "@p\\61 ge1",
				"@page :left1", "@page :l\\65 ftx", "@page 1chapter:left", "@page :left,")) {
			final String css = header + " { @-cssj-page-content h {content:'X'} }";
			assertEquals(header, css, PageContentAtRules.preprocess(css));
		}
	}

	public void testOverwrittenAtRulesRetainOnlyCurrentSnapshotsAndInstallIncrementally() throws Exception {
		final var ua = new RunningRenderTest.TestUA();
		RunningRenderTest.assertNoReplayWarnings(RunningRenderTest.convert(
				RunningRenderTest.document("", "<p>BODY</p>"), ua, false, false, Map.of()));
		final var sheet = new net.zamasoft.foliojet.css.CSSStyleSheet();
		final var registry = ua.getPassContext().getRunningRegistry();
		long installed = 0;
		for (int i = 0; i < 1000; ++i) {
			final var template = PageContentAtRules.synthesize(ua, "h", (byte) 0, null);
			sheet.addPageContent(template);
			assertEquals(1, sheet.getPageContents().size());
			assertSame(template, sheet.getPageContents().get(0));
			if (i % 10 == 9) {
				final var added = new java.util.ArrayList<net.zamasoft.foliojet.css.style.running.RunningTemplate>();
				installed = sheet.installPageContents(installed, value -> {
					added.add(value);
					registry.state().assign(value.name(), value, registry.nextOrder(), false);
				});
				assertEquals(List.of(template), added);
				assertSame(template, registry.state().resolve("h", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value());
				assertTrue(registry.retainedCandidateCount("h") <= 3);
				registry.endPage();
			}
		}
		sheet.installPageContents(installed, value -> fail("登録済みのテンプレートを再登録しない"));
		final var secondReader = new java.util.ArrayList<net.zamasoft.foliojet.css.style.running.RunningTemplate>();
		sheet.installPageContents(0, secondReader::add);
		assertEquals(sheet.getPageContents(), secondReader);
		System.err.println("[legacy R3 fix] 1000 overrides: stylesheet=" + sheet.getPageContents().size()
				+ ", registry=" + registry.retainedCandidateCount("h"));
		// 同名を上書きした位置へ並べ直す。大文字小文字の違う名前は独立。
		sheet.addPageContent(PageContentAtRules.synthesize(ua, "H", (byte) 0, null));
		sheet.addPageContent(PageContentAtRules.synthesize(ua, "h", (byte) 0, null));
		assertEquals(List.of("H", "h"), sheet.getPageContents().stream().map(value -> value.name()).toList());
		final var names = new java.util.ArrayList<String>();
		sheet.installPageContents(installed, value -> names.add(value.name()));
		assertEquals(List.of("H", "h"), names);
	}

	private static void assertHoisted(final String header, final String mask) {
		final String css = header + " {margin:20pt;@-cssj-page-content h{content:'H'}margin-bottom:30pt;}";
		final String processed = PageContentAtRules.preprocess(css);
		final String lifted = "\n@-cssj-page-content h " + mask + " {content:'H'}";
		assertTrue(processed, processed.startsWith(header + " {margin:20pt;"));
		assertTrue(processed, processed.endsWith("margin-bottom:30pt;}" + lifted));
		assertEquals(1, processed.split("@-cssj-page-content", -1).length - 1);
		assertEquals(processed, PageContentAtRules.preprocess(processed));
		final var rule = (CSSUnknownRule) parse(lifted).getRuleAtIndex(0);
		assertEquals("h " + mask, rule.getParameterList().trim());
		System.err.println("[legacy R3 fix] hoisted " + header + ": " + processed);
	}

	private static CascadingStyleSheet parse(final String css) {
		final var sheet = CSSReader.readFromStringReader(css, new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler()));
		assertNotNull(sheet);
		return sheet;
	}

	private static List<String> pageRules(final CascadingStyleSheet sheet) {
		return sheet.getAllRules().stream().filter(CSSPageRule.class::isInstance)
				.map(rule -> rule.getAsCSSString(new CSSWriterSettings(), 0)).toList();
	}
}
