package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.draw.LogicalTextDrawable;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassCensusEvent;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** T5c: block absolute は先行行の後。本文の改行機会・字送りは変えない。 */
public final class AbsoluteStaticPositionTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final double LINE = 16;
	private static final double BORDER = 2;
	private static final String ABS = "<div class='a'></div>";
	// display:table の位置マーカーは TableBorder、通常ブロックと置換要素は AbsoluteRectFrame。
	private static final Pattern FRAME = Pattern.compile(
			"x=([-0-9.]+) y=([-0-9.]+) (?:artifact )?(?:AbsoluteRectFrame|TableBorder)\\[w=([-0-9.]+) h=([-0-9.]+)\\]");

	private static String document(final boolean twoPass, final boolean vertical, final String style,
			final String body) {
		return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><style>"
				+ "@page{size:300pt 400pt;margin:10pt}body{margin:0;font:12pt/16pt serif}"
				+ ".host{position:relative;width:200pt;min-width:200pt;max-width:200pt;border:2pt solid #888;"
				+ (vertical ? "writing-mode:vertical-rl;height:200pt;min-height:200pt;max-height:200pt;" : "")
				// 幅の制約を保ちつつ、最後の width:auto (縦組みは height:auto も)で必ず TwoPass にする。
				+ (twoPass ? "float:left;width:auto;" + (vertical ? "height:auto;" : "") : "") + style + "}"
				+ ".a,.ref{display:block;margin:0;padding:0;border:1pt solid red;"
				+ (vertical ? "width:18pt;height:60pt" : "width:60pt;height:18pt") + "}"
				+ ".a{position:absolute;" + (vertical ? "top:0;left:auto;right:auto" : "left:0") + "}"
				+ "table.a{display:table;border-spacing:0}td{padding:0}.ib{display:inline-block;width:30pt;height:24pt}"
				+ ".leader::before{content:leader('.')}.f{float:left;width:200pt;height:60pt}"
				+ "</style></head><body><div id='t5c-host' class='host'>" + body + "</div></body></html>";
	}

	private record Frame(double x, double y, double width, double height) { }
	private record Rendering(List<String> pages, List<String> text, List<String> clusters, int absoluteTables) { }

	private static Object field(final Object object, final String name) throws ReflectiveOperationException {
		for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
			try {
				final Field f = type.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(object);
			} catch (final NoSuchFieldException e) {
				// 継承元の描画属性を読む。
			}
		}
		throw new NoSuchFieldException(name);
	}

	/** source offset / 段落 ID に依存せず、文字・座標・run・グリフごとの送りを比較する。 */
	private static void textGeometry(final Drawer drawer, final int page, final List<String> result,
			final List<String> clusters)
			throws ReflectiveOperationException {
		final List<?> commands = (List<?>) field(drawer, "paintCommands");
		if (commands != null) {
			for (final Object command : commands) {
				final Object drawable = field(command, "drawable");
				if (!(drawable instanceof LogicalTextDrawable)) continue;
				final StringBuilder geometry = new StringBuilder().append(page).append(':')
						.append(field(command, "x")).append(',').append(field(command, "y"))
						.append('/').append(field(drawable, "ascent")).append('/').append(field(drawable, "descent"));
				final List<?> contents = (List<?>) field(drawable, "contents");
				final int off = (int) field(drawable, "off"), len = (int) field(drawable, "len");
				for (int i = off; i < off + len; ++i) {
					if (!(contents.get(i) instanceof Text run)) continue;
					geometry.append('|').append(run.getChars(), 0, run.getCharCount())
							.append('/').append(run.getFontStyle().getSize()).append('/').append(run.getAdvance())
							.append('/').append(run.getLetterSpacing())
							.append('/').append(Arrays.toString(Arrays.copyOf(run.getGlyphIds(), run.getGlyphCount())));
					int charOffset = 0;
					for (int g = 0; g < run.getGlyphCount(); ++g) {
						geometry.append(',').append(run.xAdvances() == null ? 0 : run.xAdvances().get(g));
						final int length = run.getClusterLengths()[g];
						clusters.add(new String(run.getChars(), charOffset, length));
						charOffset += length;
					}
				}
				result.add(geometry.toString());
			}
		}
		final List<?> children = (List<?>) field(drawer, "stackingContexts");
		if (children != null) {
			for (final Object child : children) textGeometry((Drawer) field(child, "drawer"), page, result, clusters);
		}
	}

	/** DirectSession は別スレッド。static volatile の既存観測点を期間限定で使う。 */
	private static Rendering render(final String html, final File source, final boolean twoPass) throws Exception {
		final List<String> pages = new ArrayList<>(), text = new ArrayList<>();
		final List<String> clusters = new ArrayList<>();
		final AtomicInteger hostBinds = new AtomicInteger();
		final AtomicInteger absoluteTables = new AtomicInteger();
		final AtomicReference<Throwable> captureFailure = new AtomicReference<>();
		final Field observer = RangeHandle.class.getDeclaredField("replayStartObserver");
		observer.setAccessible(true);
		final Object saved = observer.get(null);
		final Field tableObserver = DocumentBuilder.class.getDeclaredField("absoluteTableObserver");
		tableObserver.setAccessible(true);
		final Object savedTableObserver = tableObserver.get(null);
		tableObserver.set(null, (Consumer<TableBox>) table -> absoluteTables.incrementAndGet());
		observer.set(null, (BiConsumer<RangeHandle, ReplayIntent>) (handle, intent) -> {
			if (intent != ReplayIntent.MAIN || handle.fromId() == 0) return;
			if (handle.source().get(handle.fromId() - 1) instanceof LayoutSource.Start start
					&& start.recipe() instanceof BoxRecipe.FloatBlock root
					&& "t5c-host".equals(root.params().materialize().element.id())) {
				hostBinds.incrementAndGet();
			}
		});
		try (final var census = ContinuationStats.beginTwoPassCensus();
				final var output = DisplayListDumper.observePages((drawer, page) -> {
					final StringBuilder dump = new StringBuilder();
					drawer.dump(dump, "");
					pages.add(dump.toString());
					try {
						textGeometry(drawer, page, text, clusters);
					} catch (final Exception e) {
						captureFailure.compareAndSet(null, e);
					}
				})) {
			ContinuationStats.reset();
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (source == null) {
					CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
							Path.of("files/unittest/t5c.html").toAbsolutePath().toUri(), "text/html", "UTF-8");
				} else {
					CTISessionHelper.transcodeFile(session, source, "text/html", null);
				}
			} finally {
				session.close();
			}
			assertFalse("描画なし", pages.isEmpty());
			assertNull("描画の観測失敗", captureFailure.get());
			if (twoPass) {
				assertTrue("宿主自身の MAIN range bind が未発火", hostBinds.get() > 0);
				assertTrue("range census が未発火", census.snapshot(TwoPassCensusEvent.BIND).entrySet().stream()
						.anyMatch(e -> !e.getKey().measurement() && e.getKey().sealOutcome().equals("accepted") && e.getValue() > 0));
			} else if (source == null) {
				assertEquals("live 宿主", 0, hostBinds.get());
			}
			return new Rendering(pages, text, clusters, absoluteTables.get());
		} finally {
			observer.set(null, saved);
			tableObserver.set(null, savedTableObserver);
		}
	}

	private static double offset(final Rendering rendering, final boolean vertical) {
		assertEquals("ページ数", 1, rendering.pages().size());
		final String page = rendering.pages().get(0);
		final List<Frame> frames = new ArrayList<>();
		final var matcher = FRAME.matcher(page);
		while (matcher.find()) {
			frames.add(new Frame(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)),
					Double.parseDouble(matcher.group(3)), Double.parseDouble(matcher.group(4))));
		}
		assertEquals("宿主と位置マーカーの枠: " + frames + "\n" + page, 2, frames.size());
		final Frame host = frames.get(0), marker = frames.get(1);
		assertEquals("宿主の行方向の外寸", 204, vertical ? host.height() : host.width(), 0.01);
		return vertical ? host.x() + host.width() - BORDER - marker.x() - marker.width()
				: marker.y() - host.y() - BORDER;
	}

	private static double assertReference(final boolean twoPass, final boolean vertical, final String style,
			final String before, final String absolute) throws Exception {
		final double expected = offset(render(document(twoPass, vertical, style,
				before + "<div class='ref'></div>"), null, twoPass), vertical);
		final Rendering rendering = render(document(twoPass, vertical, style, before + absolute), null, twoPass);
		if (absolute.startsWith("<table")) {
			assertTrue("absolute 表の MAIN DocumentBuilder.TABLE 入口が未発火", rendering.absoluteTables() > 0);
		}
		final double actual = offset(rendering, vertical);
		assertEquals(before + " の通常フロー閉鎖位置", expected, actual, 0.02);
		return actual;
	}

	private static void assertCases(final boolean twoPass, final boolean vertical) throws Exception {
		assertEquals("内容なし", 0, assertReference(twoPass, vertical, "", "", ABS), 0.02);
		assertEquals("潰れる空白", 0, assertReference(twoPass, vertical, "", "   ", ABS), 0.02);
		assertEquals("先行1字", LINE, assertReference(twoPass, vertical, "", "A", ABS), 0.02);
		// br の metrics と和文フォントの metrics の合成で 16pt より僅かに高くなりうる。
		assertReference(twoPass, vertical, "", "いいいい<br/>", ABS);
		assertEquals("明示2行", 2 * LINE, assertReference(twoPass, vertical, "", "A<br/>B", ABS), 0.02);
		for (final String content : List.of(
				"two lines of text that wrap around here yes indeed",
				"あいうえおかきくけこさしすせそた（ち）",
				"<span class='ib'></span>",
				"A<span style='font-size:28pt;line-height:36pt;vertical-align:8pt'>B</span>",
				"A<span class='ib' style='vertical-align:10pt'></span><span class='ib' style='vertical-align:-9pt'></span>")) {
			assertReference(twoPass, vertical, "", content, ABS);
		}
		assertEquals("leader だけの行", LINE,
				assertReference(twoPass, vertical, "", "<span class='leader'></span>", ABS), 0.02);
		for (final String whiteSpace : List.of("pre", "pre-wrap")) {
			assertEquals("保存空白/tab " + whiteSpace, LINE,
					assertReference(twoPass, vertical, "white-space:" + whiteSpace, "  \t", ABS), 0.02);
		}
		assertReference(twoPass, vertical, "", "A", "<table class='a'><tr><td>T</td></tr></table>");
		assertReference(twoPass, vertical, "", "A", "<img class='a' src='red.png'>");
		final Rendering joined = render(document(twoPass, vertical, "", "A" + ABS + "B"), null, twoPass);
		assertEquals("A→absolute→B", LINE, offset(joined, vertical), 0.02);
		assertEquals("A/B の run と字送り", render(document(twoPass, vertical, "", "AB"), null, twoPass).text(), joined.text());
	}

	public void testLiveFlow() throws Exception { assertCases(false, false); }
	public void testTwoPassRange() throws Exception { assertCases(true, false); }
	public void testVerticalLiveFlow() throws Exception { assertCases(false, true); }
	public void testVerticalTwoPassRange() throws Exception { assertCases(true, true); }

	public void testFloatAndNowrap() throws Exception {
		for (final boolean twoPass : new boolean[] { false, true }) {
			assertEquals("全幅 float の下の行", 60 + LINE,
					assertReference(twoPass, false, "white-space:nowrap", "<div class='f'></div>A", ABS), 0.02);
		}
	}

	public void testPendingWordUsesCssSpacingBesideFloat() throws Exception {
		for (final boolean twoPass : new boolean[] { false, true }) {
			final String before = "<div class='f' style='width:150pt'></div>abc";
			assertEquals("字間なしの abc は float 脇", LINE,
					assertReference(twoPass, false, "white-space:nowrap;hyphens:auto", before, ABS), 0.02);
			assertEquals("20pt の字間を含む abc は float の下", 60 + LINE,
					assertReference(twoPass, false, "white-space:nowrap;hyphens:auto;letter-spacing:20pt", before, ABS), 0.02);
		}
	}

	public void testLigatureAcrossAbsolute() throws Exception {
		for (final boolean twoPass : new boolean[] { false, true }) {
			final String font = "<?jp.cssj.property name=\"output.pdf.fonts.policy\" value=\"embedded\"?>"
					+ "<style>@font-face{font-family:t5c-ligature;"
					+ "src:url('1080-FONT/MinionPro-Regular.otf')}</style>";
			for (final String hyphens : List.of("manual", "auto")) {
				final String style = "font-family:t5c-ligature;hyphens:" + hyphens;
				final String referenceHtml = document(twoPass, false, style, "fi").replace("</head>", font + "</head>");
				final String actualHtml = document(twoPass, false, style, "f" + ABS + "i").replace("</head>", font + "</head>");
				final Rendering reference = render(referenceHtml, null, twoPass);
				final Rendering actual = render(actualHtml, null, twoPass);
				assertEquals("基準自体が fi 合字になっている", List.of("fi"), reference.clusters());
				assertEquals("f→absolute→i が1グリフ", List.of("fi"), actual.clusters());
				assertEquals("fi の run・字形・幅・位置", reference.text(), actual.text());
				final String repeatedHtml = document(twoPass, false, style, "f" + ABS + ABS + "i")
						.replace("</head>", font + "</head>");
				assertEquals("同じ先行文字を二度計量しても本文不変", reference.text(), render(repeatedHtml, null, twoPass).text());
			}
		}
	}

	public void testNowrapAcrossAbsolute() throws Exception {
		for (final boolean twoPass : new boolean[] { false, true }) {
			final Rendering reference = render(document(twoPass, false, "white-space:nowrap", "AB"), null, twoPass);
			final Rendering actual = render(document(twoPass, false, "white-space:nowrap", "A" + ABS + "B"), null, twoPass);
			assertEquals("nowrap の A/B の run・字送り", reference.text(), actual.text());
			assertEquals("先行 A の行高", LINE, offset(actual, false), 0.02);
		}
	}

	public void testMixedBidiAndIsolateKeepText() throws Exception {
		final String bidi = "<?jp.cssj.property name=\"layout.bidi.paragraph\" value=\"true\"?>";
		for (final boolean twoPass : new boolean[] { false, true }) {
			for (final String[] sample : List.of(
					new String[] { "ABC אב", "ג DEF" },
					new String[] { "ABC <span style='direction:rtl;unicode-bidi:isolate'>אב", "ג</span> DEF" })) {
				final Rendering reference = render(document(twoPass, false, "", sample[0] + sample[1])
						.replace("</head>", bidi + "</head>"), null, twoPass);
				final Rendering actual = render(document(twoPass, false, "", sample[0] + ABS + sample[1])
						.replace("</head>", bidi + "</head>"), null, twoPass);
				assertEquals("bidi のページ数", reference.pages().size(), actual.pages().size());
				// barrier を挟むと段落の解決順は変わりうる。文字の欠落・重複だけを検査する。
				assertEquals("bidi の文字集合", codePoints(reference), codePoints(actual));
				assertTrue("ヘブライ文字が描かれた", codePoints(actual).contains((int) 'א'));
			}
		}
	}

	private static List<Integer> codePoints(final Rendering rendering) {
		return String.join("", rendering.clusters()).codePoints().sorted().boxed().toList();
	}

	public void testTextCombineAcrossAbsoluteKeepsText() throws Exception {
		for (final boolean twoPass : new boolean[] { false, true }) {
			for (final String[] sample : List.of(
					new String[] { "あ<span style='text-combine-upright:all'>12", "34</span>い" },
					new String[] { "あ<span style='text-combine-upright:all'>1234</span>", "い" })) {
				final Rendering reference = render(document(twoPass, true, "", sample[0] + sample[1]), null, twoPass);
				final Rendering actual = render(document(twoPass, true, "", sample[0] + ABS + sample[1]), null, twoPass);
				assertFalse("縦中横の本文なし", reference.text().isEmpty());
				assertEquals("縦中横の文字の欠落・重複なし", codePoints(reference), codePoints(actual));
				assertEquals("縦中横の圧縮後の本文の字形・位置", reference.text(), actual.text());
				// 未確定の縦中横の静的位置と、最終的な圧縮寸法の一致は要求しない。
			}
		}
	}

	public void testBreakOpportunitiesAndJustificationStayUnchanged() throws Exception {
		for (final boolean twoPass : new boolean[] { false, true }) {
			for (final String[] sample : List.of(
					new String[] { "ab", "cdef", "font-family:monospace;min-width:30pt;max-width:30pt;" },
					new String[] { "あいうえおか（", "きくけこ）さしすせそ", "min-width:60pt;max-width:60pt;" },
					new String[] { "hy", "phenation representation internationalization", "hyphens:auto;min-width:60pt;max-width:60pt;" },
					new String[] { "hy&shy;", "phenation word word word", "min-width:60pt;max-width:60pt;" },
					new String[] { "one two ab", "cdef three four five six seven", "text-align:justify;min-width:90pt;max-width:90pt;" })) {
				final Rendering reference = render(document(twoPass, false, sample[2], sample[0] + sample[1]), null, twoPass);
				final Rendering actual = render(document(twoPass, false, sample[2], sample[0] + ABS + sample[1]), null, twoPass);
				assertFalse("本文なし", reference.text().isEmpty());
				assertEquals(sample[0] + "|" + sample[1], reference.text(), actual.text());
			}
		}
	}

	public void testInlineLevelStaysOnLine() throws Exception {
		for (final boolean twoPass : new boolean[] { false, true }) {
			final String html = document(twoPass, false, "", "A<span class='a' style='display:inline'></span>B");
			assertEquals("inline-level は現在行", 0, offset(render(html, null, twoPass), false), 0.02);
		}
	}

	/**
	 * 明示 inset の fixed.html は T5c の対象外。D7 全体の照合は既存ゲートで行う。
	 * 修正前後の D7 全属性の実測差分は、p1/p2 の page.paint[0].contents の run 分割だけ。
	 * 本文の原点は (0,0)/(0,10)、幅は 30+30=60pt。p3 は byte 一致。
	 */
	public void testExplicitInsetsKeepFixedDocumentText() throws Exception {
		final Rendering actual = render(null, new File("files/unittest/0170-position/fixed.html"), false);
		assertEquals("fixed.html ページ数", 3, actual.pages().size());
		for (int page = 1; page <= 2; ++page) {
			final String position = page + ":0.0," + (page == 1 ? "0.0" : "10.0") + "/";
			assertTrue("fixed.html p" + page + " 本文の座標・run・幅", actual.text().stream()
					.anyMatch(value -> value.startsWith(position) && value.contains("|フローフロー/10.0/60.0/")));
		}
	}
}
