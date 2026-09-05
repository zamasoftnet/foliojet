package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.PageRule;
import net.zamasoft.foliojet.css.style.running.LegacyPageContents;

/** 3.2の意味論を、本文の配置と反復内容の両方から検査します。観測値はstderrへ出します。 */
public final class LegacyPageContentTest extends TestCase {
	public void test020NestedAliasImageAndClear() throws Exception {
		final var result = fixture("legacy-020-nested-clear.html");
		assertEquals(3, result.pages().size());
		for (int i = 0; i < 3; ++i) {
			final String page = result.pages().get(i);
			final String text = RunningRenderTest.artifactText(page);
			assertTrue(text, text.contains("OUTERSPAN"));
			assertEquals("内側は独立したrightマスク", i % 2 == 0, text.contains("INNER"));
			assertEquals("画像はclearの配置頁から消える", i < 2, page.contains("AbsoluteRectFrame[w=12.00 h=6.00]"));
			assertTrue(page, bodyCommands(page).contains("BODY" + (i + 1)));
		}
		System.err.println("[legacy R3] 020: :left/:right recoloring excluded; regeneratable is an alias");
	}

	public void test021FixedCoordinatesAndDisplayPageCounter() throws Exception {
		final var result = fixture("legacy-021-fixed-counter.html");
		assertEquals(3, result.pages().size());
		for (int i = 0; i < 3; ++i) {
			final String page = result.pages().get(i);
			assertEquals("PAGE=" + (i + 1), RunningRenderTest.artifactText(page));
			assertFrame(page, 0, 148, 220, 12);
			final double x = coordinate(textLine(page, "PAGE="), "x");
			assertTrue("center in width:100%: " + x, x > 70 && x < 110);
		}
		// 同じ宣言を通常fixedとしても、フレームの原点・寸法は一致する。
		final String html = read("legacy-021-fixed-counter.html").replace("-cssj-page-content: footer", "position: fixed");
		final var fixed = convert(html, Map.of());
		assertFrame(fixed.pages().get(0), 0, 148, 220, 12);
	}

	public void test022MasksAndLastNamedString() throws Exception {
		final var result = fixture("legacy-022-mask-string.html");
		assertEquals(3, result.pages().size());
		assertEquals(List.of("RIGHT/1/ALPHA", "LEFT/2/GAMMA", "RIGHT/3/GAMMA"),
				result.pages().stream().map(RunningRenderTest::artifactText).toList());
	}

	public void testLastMarkerClearAndReassignmentAreOrderedAndCaseSensitive() throws Exception {
		final var result = convert(RunningRenderTest.document(
				".a{-cssj-page-content:Name;top:0;left:0}.b{-cssj-page-content:name;top:20pt;left:0}"
				+ ".clear{-cssj-page-content-clear:Name 'name'}section+section{break-before:page}",
				"<section><i class='a'>OLD</i><i class='a'>LAST</i><i class='b'>LOWER</i><p>BODY1</p></section>"
				+ "<section><p class='clear'>BODY2</p><i class='a'>REVIVED</i></section>"
				+ "<section><p>BODY3</p></section><section><p class='clear'>BODY4</p></section>"), Map.of());
		assertEquals(List.of("LASTLOWER", "REVIVED", "REVIVED", ""),
				result.pages().stream().map(RunningRenderTest::artifactText).toList());
	}

	public void testClearBelongsToItsElementAfterForcedBreak() throws Exception {
		final var result = convert(RunningRenderTest.document(
				".pc{-cssj-page-content:h;top:0;left:0}.clear{break-before:page;-cssj-page-content-clear:h}",
				"<div class='pc'>HEADER</div><p>FIRST</p><p class='clear'>SECOND</p>"), Map.of());
		assertEquals(List.of("HEADER", ""), result.pages().stream().map(RunningRenderTest::artifactText).toList());
	}

	public void testSingleAndUnionMasksWithSingleSideImposition() throws Exception {
		final String html = RunningRenderTest.document(
				".single{-cssj-page-content:s single;top:0;left:0}"
				+ ".union{-cssj-page-content:u first left;top:20pt;left:0}section+section{break-before:page}",
				"<section><i class='single'>SINGLE</i><i class='union'>UNION</i><p>A</p></section>"
				+ "<section><p>B</p></section>");
		final var single = convert(html, Map.of("output.print-mode", "single-side"));
		assertEquals(List.of("SINGLEUNION", "SINGLE"), single.pages().stream().map(RunningRenderTest::artifactText).toList());
		final var duplex = convert(html, Map.of("output.print-mode", "double-side"));
		assertEquals(List.of("UNION", "UNION"), duplex.pages().stream().map(RunningRenderTest::artifactText).toList());
		assertTrue(LegacyPageContents.matches(PageRule.PSEUDO_SINGLE, CSSElement.PAGE_SINGLE_FIRST));
		assertTrue(LegacyPageContents.matches(PageRule.PSEUDO_SINGLE, CSSElement.PAGE_SINGLE));
	}

	public void testVerticalSideUsesNegativeCoordinatesAndFullHeight() throws Exception {
		final var result = fixture("legacy-vertical-side.html");
		assertEquals(4, result.pages().size());
		// vertical-rlは右綴じなので、最初と3枚目がleft。
		assertEquals(List.of("SIDE/1", "", "SIDE/3", ""),
				result.pages().stream().map(RunningRenderTest::artifactText).toList());
		for (final int index : new int[] { 0, 2 }) {
			assertFrame(result.pages().get(index), -11 * 72.0 / 25.4, -5 * 72.0 / 25.4,
					8 * 72.0 / 25.4, 70 * 72.0 / 25.4);
		}
	}

	public void testPaintOrderFixedThenPageContentThenMarginIncludingZIndex() throws Exception {
		final var result = convert(RunningRenderTest.document(
				"@page{@top-center{content:'MARGIN'}}.fixed{position:fixed;z-index:2147483647;left:0;top:0}"
				+ ".pc{-cssj-page-content:p;z-index:-1;left:0;top:0}",
				"<div class='fixed'>FIXED</div><div class='pc'>LEGACY</div><p>BODY</p>"), Map.of());
		final String page = result.pages().get(0);
		assertTrue(page, page.indexOf("Text[\"FIXED\"") < page.indexOf("Text[\"LEGACY\""));
		assertTrue(page, page.indexOf("Text[\"LEGACY\"") < page.indexOf("Text[\"MARGIN\""));
	}

	public void testDifferentLegacyNamesOverlapInLastMarkerOrder() throws Exception {
		// 3.2のHashMap列挙順とは異なり、異名同士も最終markerの順で後ほど手前になる。
		final var result = convert(RunningRenderTest.document(
				".a{-cssj-page-content:a}.b{-cssj-page-content:b}"
				+ ".a,.b{left:0;top:0;width:40pt;height:20pt;background:red}.a{background:blue}",
				"<div class='a'>OLD</div><div class='b'>BACK</div><div class='a'>FRONT</div><p>BODY</p>"), Map.of());
		assertEquals("BACKFRONT", RunningRenderTest.artifactText(result.pages().get(0)));
		try (final var pdf = Loader.loadPDF(result.pdf())) {
			final var image = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
			final int color = image.getRGB(65, 45) & 0xffffff;
			System.err.printf("[legacy R3 fix] overlapping last marker=%06x%n", color);
			assertEquals(0x0000ff, color);
		}
	}

	public void testRunningAndLegacyShareNamespaceWithLastAssignmentWinning() throws Exception {
		final String css = ".legacy{-cssj-page-content:h;left:0;top:0}.standard{position:running(h)}"
				+ "@page{@top-center{content:element(h,last)}}";
		for (final boolean legacyLast : List.of(false, true)) {
			final String legacy = "<div class='legacy'>LEGACY</div>";
			final String standard = "<div class='standard'>STANDARD</div>";
			final var result = convert(RunningRenderTest.document(css,
					(legacyLast ? standard + legacy : legacy + standard) + "<p>BODY</p>"), Map.of());
			final String text = RunningRenderTest.artifactText(result.pages().get(0));
			System.err.println("[legacy R3 fix] shared namespace, legacyLast=" + legacyLast + ": " + text);
			// legacyが勝つと頁固定層とelement(last)の両方で同じテンプレートを参照できる。
			assertEquals(legacyLast ? "LEGACYLEGACY" : "STANDARD", text);
		}
	}

	public void testPagePaddingUsesSamePercentageContainingBlockAsFixed() throws Exception {
		final String css = "@page{size:300pt 200pt;margin:20pt;padding:10pt}"
				+ ".pc{left:0;top:0;width:100%;height:100%;background:red;POSITION}";
		for (final String position : List.of("position:fixed", "-cssj-page-content:h")) {
			final var result = convert(RunningRenderTest.document(css.replace("POSITION", position),
					"<div class='pc'></div><p>BODY</p>"), Map.of());
			final String page = result.pages().get(0);
			System.err.println("[legacy R3 fix] padded page " + position + ":\n" + page);
			assertFrame(page, 0, 0, 260, 160);
		}
	}

	public void testNamedTransitionReplacesPageEvenWithRegisteredLegacy() throws Exception {
		for (final String content : List.of("", "HEADER")) {
			final var result = convert(RunningRenderTest.document(
					"@-cssj-page-content h{content:'" + content + "';left:0;top:0;font:10pt serif}"
					+ "p{page:chapter}", "<p>BODY</p>"), Map.of());
			System.err.println("[legacy R3 fix] named transition, content=" + content + ", pages=" + result.pages().size());
			assertEquals(1, result.pages().size());
			assertEquals(content, RunningRenderTest.artifactText(result.pages().get(0)));
			assertTrue(bodyCommands(result.pages().get(0)).contains("BODY"));
		}
	}

	public void testClearOnDiscardedPageDoesNotReviveOnFollowingPages() throws Exception {
		final var result = convert(RunningRenderTest.document(
				"@-cssj-page-content h{content:'OLD';left:0;top:0;font:10pt serif}"
				+ ".clear{-cssj-page-content-clear:h}p{page:chapter}p+p{break-before:page}",
				"<div class='clear'></div><p>FIRST</p><p>SECOND</p>"), Map.of());
		System.err.println("[legacy R3 fix] discarded clear, pages=" + result.pages().size());
		assertEquals(2, result.pages().size());
		assertEquals(List.of("", ""), result.pages().stream().map(RunningRenderTest::artifactText).toList());
		assertTrue(bodyCommands(result.pages().get(0)).contains("FIRST"));
		assertTrue(bodyCommands(result.pages().get(1)).contains("SECOND"));
	}

	public void testEmptyLegacyRegistrationHasNoPaintButBackgroundDoes() throws Exception {
		for (final boolean background : List.of(false, true)) {
			final var ua = new RunningRenderTest.TestUA();
			final var result = RunningRenderTest.convert(RunningRenderTest.document(
					"@-cssj-page-content h{content:'';left:0;top:0;width:20pt;height:10pt;z-index:1;"
					+ (background ? "background:red" : "") + "}", "<p>BODY</p>"), ua, false, true, Map.of());
			RunningRenderTest.assertNoReplayWarnings(result);
			final var blank = new net.zamasoft.foliojet.layout.MeasurePageGenerator(ua,
					RunningSideEffectTest.params(ua, net.zamasoft.foliojet.css.CSSStyle.getCSSStyle(ua, null, CSSElement.ANON)),
					260, 160).nextPage();
			assertEquals(1, LegacyPageContents.active(ua.getPassContext().getRunningState(),
					CSSElement.PAGE_RIGHT_ODD, List.of()).size());
			assertEquals(background, LegacyPageContents.paintsAnything(ua, CSSElement.PAGE_RIGHT_ODD, null, blank, List.of()));
		}
	}

	public void testNegativeCoordinatesAndOverflowAreActuallyPainted() throws Exception {
		final var result = convert(RunningRenderTest.document(
				".pc{-cssj-page-content:p;left:-15pt;top:-15pt;width:10pt;height:10pt;background:red}"
				+ ".pc span{position:absolute;left:45pt;top:45pt;width:10pt;height:10pt;background:blue}",
				"<div class='pc'><span></span></div><p>BODY</p>"), Map.of());
		try (final var pdf = Loader.loadPDF(result.pdf())) {
			final var image = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
			final int red = image.getRGB(20, 20) & 0xffffff;
			final int blue = image.getRGB(65, 65) & 0xffffff;
			System.err.printf("[legacy R3] outside page-area=%06x, outside box=%06x%n", red, blue);
			assertEquals(0xff0000, red);
			assertEquals(0x0000ff, blue);
		}
	}

	public void testLegacyOnlyPageIsNotDiscarded() throws Exception {
		final var ua = new RunningRenderTest.TestUA();
		final var result = RunningRenderTest.convert(RunningRenderTest.document(
				".pc{-cssj-page-content:p;left:0;top:0}", "<div class='pc'>ONLY</div>"), ua, false, true, Map.of());
		assertEquals(1, result.pages().size());
		assertEquals("ONLY", RunningRenderTest.artifactText(result.pages().get(0)));
		assertEquals("", bodyCommands(result.pages().get(0)));

		// 空要素の高さだけでは改頁しない。既に出力した頁の次の空頁について、
		// 実際の白紙判定を直接検査し、0頁防止/強制改頁の例外で通る試験にしない。
		final var type = Class.forName("net.zamasoft.foliojet.css.style.PageSequence");
		final var constructor = type.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		final var context = new net.zamasoft.foliojet.css.StyleContext(new net.zamasoft.foliojet.css.CSSStyleSheet(), null, null);
		final Object sequence = constructor.newInstance(ua, context, new net.zamasoft.foliojet.ua.impl.NopImposition(ua),
				null, null, (java.util.function.Consumer<String>) name -> { });
		for (final var fieldValue : Map.of("emittedPages", 1, "pageElement", CSSElement.PAGE_RIGHT_ODD).entrySet()) {
			final var field = type.getDeclaredField(fieldValue.getKey());
			field.setAccessible(true);
			field.set(sequence, fieldValue.getValue());
		}
		final var generator = new net.zamasoft.foliojet.layout.MeasurePageGenerator(ua,
				RunningSideEffectTest.params(ua, net.zamasoft.foliojet.css.CSSStyle.getCSSStyle(ua, null, CSSElement.ANON)),
				260, 160);
		final var blank = generator.nextPage();
		blank.setSourceAnchor(7);
		final var check = type.getDeclaredMethod("paintsNothing", net.zamasoft.foliojet.layout.box.impl.PageBox.class,
				boolean.class, boolean.class);
		check.setAccessible(true);
		assertEquals(false, check.invoke(sequence, blank, false, false));
		final var registry = ua.getPassContext().getRunningRegistry();
		final var template = registry.state().resolve("p", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value();
		final long clear = registry.nextOrder();
		registry.clear(clear, List.of("p"));
		registry.bindBox(clear, 7);
		assertEquals(true, check.invoke(sequence, blank, false, false));
		assertEquals("previewはpendingを消費しない", 1, registry.pendingCount());
		assertSame(template, registry.state().resolve("p", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).value());
		final var draw = type.getDeclaredMethod("drawPage", net.zamasoft.foliojet.layout.box.impl.PageBox.class,
				boolean.class, boolean.class);
		draw.setAccessible(true);
		assertEquals(false, draw.invoke(sequence, blank, false, false));
		assertEquals("drawPageが棄却時もclearを確定する", 0, registry.pendingCount());
		assertEquals(net.zamasoft.foliojet.ua.PageAssignmentState.Presence.TOMBSTONE,
				registry.state().resolve("p", net.zamasoft.foliojet.ua.PageAssignmentState.Mode.LAST).presence());
		final var next = generator.nextPage();
		next.setSourceAnchor(8);
		assertEquals(false, draw.invoke(sequence, next, false, false));
		final long revive = registry.nextOrder();
		registry.complete(revive, template);
		registry.bindBox(revive, 8);
		assertEquals("初めてこの頁に置かれるlegacyも保持する", false, check.invoke(sequence, next, false, false));
		assertEquals(1, registry.pendingCount());
	}

	public void testLegacyTokenLeavesBodyDisplayListInvariant() throws Exception {
		for (final String body : List.of("<p>Alpha beta TOKEN gamma delta epsilon.</p>",
				"<section>TOKEN<p>Alpha beta gamma delta.</p></section>",
				"<p style='white-space:pre'>A\n   TOKENB</p>",
				"<table><tr><td style='writing-mode:vertical-rl'>Alpha TOKEN beta gamma</td></tr></table>")) {
			final String css = "p{width:85pt;line-height:1.4}.pc{-cssj-page-content:p;top:0;left:0;"
					+ "font-size:80pt;line-height:4;direction:rtl;unicode-bidi:bidi-override}";
			final var with = convert(RunningRenderTest.document(css, body.replace("TOKEN", "<i class='pc'>HEADER</i>")), Map.of());
			final var without = convert(RunningRenderTest.document(css, body.replace("TOKEN", "")), Map.of());
			assertEquals(without.pages().stream().map(LegacyPageContentTest::bodyCommands).toList(),
					with.pages().stream().map(LegacyPageContentTest::bodyCommands).toList());
		}
	}

	public void testReplayDoesNotChangeSharedPageState() throws Exception {
		final var ua = new RunningSideEffectTest.AuditUA(true, agent -> { });
		final var result = RunningRenderTest.convert(read("legacy-020-nested-clear.html"), ua, true, true, Map.of());
		RunningRenderTest.assertNoReplayWarnings(result);
		assertEquals(3, ua.checkedPages);
	}

	static String read(final String name) throws Exception {
		return Files.readString(Path.of("files/unittest/0370-page-content", name));
	}

	static RunningRenderTest.Conversion fixture(final String name) throws Exception {
		return convert(read(name), Map.of());
	}

	static RunningRenderTest.Conversion convert(final String html, final Map<String, String> properties) throws Exception {
		final var result = RunningRenderTest.convert(html, new RunningRenderTest.TestUA(), false, true, properties);
		RunningRenderTest.assertNoReplayWarnings(result);
		return result;
	}

	static String bodyCommands(final String page) {
		return page.lines().filter(line -> line.contains("x=") && !line.contains("artifact "))
				.map(String::trim).collect(java.util.stream.Collectors.joining("\n"));
	}

	static String textLine(final String page, final String text) {
		return page.lines().filter(line -> line.contains("Text[\"" + text)).findFirst().orElseThrow();
	}

	static double coordinate(final String line, final String name) {
		final var matcher = Pattern.compile("\\b" + name + "=([-0-9.]+)").matcher(line);
		assertTrue(line, matcher.find());
		return Double.parseDouble(matcher.group(1));
	}

	static void assertFrame(final String page, final double x, final double y, final double width, final double height) {
		final String frame = page.lines().filter(line -> line.contains("RectFrame[")
				&& Math.abs(coordinate(line, "w") - width) < .02 && Math.abs(coordinate(line, "h") - height) < .02)
				.findFirst().orElseThrow(() -> new AssertionError(page));
		assertEquals(frame, x, coordinate(frame, "x"), .02);
		assertEquals(frame, y, coordinate(frame, "y"), .02);
	}
}
