package jp.cssj.test.unit.displaylist;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.css.CSSStyleSheet;
import net.zamasoft.foliojet.css.CSSStyleSheetBuilder;
import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.FootnoteArea;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 脚注領域の規則・持ち越し・固定帯(F-7)と、既定経路の保存を検査します。 */
public class FootnoteAreaRuleTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String AREA = "@footnote { float: bottom; writing-mode: horizontal-tb }";
	private static final double EPSILON = 0.01;
	private static final String FIXED_AREA = "@footnote { float: bottom; writing-mode: horizontal-tb; height: 40pt }";

	public void testHeightDescriptorsAndInvalidValues() throws Exception {
		final List<Short> warnings = new ArrayList<>();
		final PDFUserAgent ua = new PDFUserAgent() { };
		try {
			assertNull(FootnoteArea.DEFAULT.height);
			assertEquals(0.0, FootnoteArea.DEFAULT.minHeight, 0.0);
			parse(ua, "@page { @footnote { height: 40pt; min-height: 12pt } }", warnings);
			final FootnoteArea first = ua.getUAContext().getFootnoteArea();
			assertEquals(40.0, first.height, 0.0);
			assertEquals(12.0, first.minHeight, 0.0);
			assertTrue(first.isHeightFixed());
			assertTrue(warnings.isEmpty());
			parse(ua, "@footnote { height: 50%; min-height: 10%; height: -1pt; min-height: -2pt;"
					+ " height: none; min-height: auto }", warnings);
			assertSame("不正値は直前の有効値を消さない", first, ua.getUAContext().getFootnoteArea());
			assertEquals(6, warnings.size());
			for (final short warning : warnings) assertEquals(MessageCodes.WARN_BAD_CSS_SYNTAX, warning);
			parse(ua, "@footnote { height: auto; min-height: 0; float: bottom; writing-mode: horizontal-tb }", warnings);
			assertFalse(ua.getUAContext().getFootnoteArea().isHeightFixed());
			assertEquals(0.0, ua.getUAContext().getFootnoteArea().minHeight, 0.0);
			assertEquals("更新前の値は不変", 40.0, first.height, 0.0);
			parse(ua, "@footnote { height: 1in; min-height: 1pc }", warnings);
			assertEquals(72.0, ua.getUAContext().getFootnoteArea().height, EPSILON);
			assertEquals(12.0, ua.getUAContext().getFootnoteArea().minHeight, EPSILON);
			assertEquals(6, warnings.size());
		} finally {
			ua.dispose();
		}
	}

	public void testFixedBottomBandOnEveryPageWithoutProbe() throws Exception {
		final Capture capture = transcode(fixedFixture());
		final Capture baseline = transcode(fixedFixture().replace(FIXED_AREA, ""));
		assertEquals(3, capture.pages().size());
		assertEquals("FootnotePageProbe自体を生成しない", 0L, capture.probes());
		assertEquals("Bの報告も無い", 0, capture.reports());
		for (final PageMetrics page : capture.pages()) {
			assertEquals(40.0, page.inset(), EPSILON);
			assertEquals("注なしページも同じ内寸", 212.0, page.innerHeight(), EPSILON);
			assertEquals(300.0, page.height(), EPSILON);
			assertEquals(24.0, page.marginBottom(), EPSILON);
		}
		assertEquals(2, capture.notes().size());
		for (int i = 0; i < 2; ++i) {
			final Placement note = capture.notes().get(i);
			assertEquals("帯には一件ずつFIFOで入る", i + 1, note.page());
			assertEquals("間隙6ptの後から配置する", 218.0, note.y(), EPSILON);
			assertTrue(note.y() + note.height() <= 252 + EPSILON);
			assertTrue("呼び出しページの番号を保持: " + note.text(), note.text().startsWith((i + 1) + ". "));
		}
		assertTrue(capture.notes().stream().noneMatch(note -> note.page() == 3));
		assertTrue(capture.pages().get(0).separator());
		assertTrue(capture.pages().get(1).separator());
		assertFalse("注なしページには区切り罫線を引かない", capture.pages().get(2).separator());
		for (int page = 1; page <= 3; ++page) {
			assertEquals("柱の計測用ページには固定帯を予約しない", footer(baseline, page), footer(capture, page));
		}
	}

	public void testFixedBandEmitsFirstPageBeforeSecondPageText() throws Exception {
		final var secondPageText = new java.util.concurrent.atomic.AtomicBoolean();
		final var firstPageBeforeSecondText = new java.util.concurrent.atomic.AtomicBoolean();
		final StringBuilder inputText = new StringBuilder();
		final var append = net.zamasoft.foliojet.layout.fragment.LayoutSource.class.getDeclaredField("appendObserver");
		append.setAccessible(true);
		final Object saved = append.get(null);
		try {
			append.set(null, (java.util.function.Consumer<net.zamasoft.foliojet.layout.fragment.LayoutSource.Event>) event -> {
				if (event instanceof net.zamasoft.foliojet.layout.fragment.LayoutSource.Chars chars) {
					inputText.append(chars.payload().freshChars());
					if (inputText.indexOf("二頁目") >= 0) secondPageText.set(true);
				}
			});
			transcode(fixedFixture(), page -> {
				if (page == 1) firstPageBeforeSecondText.set(!secondPageText.get());
			});
			assertTrue("二頁目の本文を実際に入力した", secondPageText.get());
			assertTrue("一頁目は後続本文を待たずに出力する", firstPageBeforeSecondText.get());
		} finally {
			append.set(null, saved);
		}
	}

	public void testZeroHeightNotesStillReserveFixedBand() throws Exception {
		final String html = fixedFixture().replace("height: 24pt; font-size: 10pt; line-height: 14pt",
				"height: 0; font-size: 0; line-height: 0")
				.replace("</style>", ".note::footnote-marker { content: none } </style>");
		final Capture capture = transcode(html);
		assertEquals(2, capture.notes().size());
		for (final Placement note : capture.notes()) {
			assertEquals(0.0, note.height(), EPSILON);
			assertEquals(1, note.page());
		}
		for (final PageMetrics page : capture.pages()) assertEquals(40.0, page.inset(), EPSILON);
	}

	public void testFixedHeightZeroAndOversizedMakeProgress() throws Exception {
		final Capture capture = transcode(fixedFixture().replace("height: 40pt", "height: 0"));
		assertEquals(0L, capture.probes());
		assertEquals("帯に単独でも入らない注を欠落させない", 2, capture.notes().size());
		assertEquals(2, capture.notes().get(0).page());
		assertEquals(3, capture.notes().get(1).page());
		for (final PageMetrics page : capture.pages()) assertEquals(0.0, page.inset(), 0.0);
		assertTrue(capture.notes().get(1).text().startsWith("2. "));
	}

	public void testMinimumBottomBandStillProbesAndGrows() throws Exception {
		final Capture capture = transcode(fixedFixture().replace("height: 40pt", "min-height: 40pt"));
		assertEquals(1L, capture.probes());
		assertTrue("min-heightはBを省略しない", capture.reports() > 0);
		assertEquals(3, capture.pages().size());
		assertEquals(2, capture.notes().size());
		assertEquals("二件分の報告が下限より大きければ伸びる", 54.0, capture.pages().get(0).inset(), EPSILON);
		for (final Placement note : capture.notes()) assertEquals(1, note.page());
		assertEquals("注なしでも下限を予約", 40.0, capture.pages().get(2).inset(), EPSILON);
		for (final PageMetrics page : capture.pages()) assertTrue(page.inset() >= 40 - EPSILON);
	}

	public void testMinimumOverridesFixedHeightAndBandLimitWarnsOnce() throws Exception {
		final Capture minimum = transcode(fixedFixture().replace("height: 40pt", "height: 20pt; min-height: 40pt"));
		assertEquals(0L, minimum.probes());
		for (final PageMetrics page : minimum.pages()) assertEquals(40.0, page.inset(), EPSILON);
		final var warnings = new java.util.concurrent.ConcurrentLinkedQueue<java.util.logging.LogRecord>();
		final var logger = java.util.logging.Logger.getLogger(net.zamasoft.foliojet.layout.builder.impl.RootBuilder.class.getName());
		final var handler = new java.util.logging.Handler() {
			public void publish(final java.util.logging.LogRecord record) { warnings.add(record); }
			public void flush() { }
			public void close() { }
		};
		logger.addHandler(handler);
		try {
			final Capture limited = transcode(fixedFixture().replace("height: 40pt", "height: 200pt"));
			for (final PageMetrics page : limited.pages()) assertEquals(252 * 0.6, page.inset(), EPSILON);
			assertEquals("文書全体で上限警告一回", 1L, warnings.stream()
					.filter(record -> record.getMessage().startsWith("footnote area limited to ")).count());
			warnings.clear();
			// min-heightでは仮組み(B)も走る。Bは警告せず、文書全体で一回のまま。
			final Capture minimumLimited = transcode(fixedFixture().replace("height: 40pt", "min-height: 200pt"));
			assertTrue(minimumLimited.probes() > 0);
			for (final PageMetrics page : minimumLimited.pages()) assertEquals(252 * 0.6, page.inset(), EPSILON);
			assertEquals("min-heightでもBとCで二重に警告しない", 1L, warnings.stream()
					.filter(record -> record.getMessage().startsWith("footnote area limited to ")).count());
		} finally {
			logger.removeHandler(handler);
		}
	}

	public void testBlockEndFixedAndMinimumBands() throws Exception {
		final String html = fixedFixture().replace("float: bottom; writing-mode: horizontal-tb; height: 40pt",
				"float: block-end; height: 40pt").replace("writing-mode: vertical-rl", "writing-mode: horizontal-tb");
		final Capture fixed = transcode(html);
		assertEquals(0L, fixed.probes());
		assertEquals(3, fixed.pages().size());
		assertEquals(2, fixed.notes().size());
		for (int i = 0; i < 2; ++i) {
			assertEquals(i + 1, fixed.notes().get(i).page());
			assertEquals(218.0, fixed.notes().get(i).y(), EPSILON);
		}
		final Capture minimum = transcode(html.replace("height: 40pt", "min-height: 40pt"));
		assertEquals(2, minimum.notes().size());
		for (final Placement note : minimum.notes()) assertEquals(1, note.page());
		assertEquals("下限40ptから二件分54ptへ伸びる", 204.0, minimum.notes().get(0).y(), EPSILON);
		// 注の無い本文でも、block方向の容量を各ページで先に引く。
		final String body = html.substring(0, html.indexOf("<body>")) + "<body>"
				+ "<p>本文だけの行。</p>".repeat(24) + "</body></html>";
		final Capture empty = transcode(body);
		assertTrue(empty.notes().isEmpty());
		assertTrue(empty.pages().size() > 1);
		for (final Placement line : empty.lines()) {
			if (line.text().contains("本文だけの行")) assertTrue(line.y() + line.height() <= 212 + EPSILON);
		}
	}

	public void testDefaultHeightKeepsDisplayListBytes() throws Exception {
		final String html = fixture().replace(AREA, "");
		final Capture baseline = transcode(html);
		final Capture explicit = transcode(html.replace("@page {", "@page { @footnote { height: auto; min-height: 0 }"));
		TwoPassFlowSealTest.assertPagesEqual("既定のheight/min-heightは表示リストbyte不変",
				baseline.displayLists(), explicit.displayLists());
		assertEquals(0L, explicit.probes());
		final Capture bottom = transcode(fixture());
		final Capture bottomAuto = transcode(fixture().replace(AREA, FIXED_AREA.replace("height: 40pt", "height: auto; min-height: 0")));
		TwoPassFlowSealTest.assertPagesEqual("既存bottom経路もauto/0で表示リストbyte不変",
				bottom.displayLists(), bottomAuto.displayLists());
	}

	public void testPageAndTolerantTopLevelRules() throws Exception {
		for (final String css : new String[] { "@page { " + AREA + " }", AREA,
				"@page chapter:first, appendix:left { " + AREA + " }" }) {
			final List<Short> warnings = new ArrayList<>();
			final PDFUserAgent ua = new PDFUserAgent() { };
			try {
				parse(ua, css, warnings);
				assertEquals(FootnoteArea.Position.BOTTOM, ua.getUAContext().getFootnoteArea().position);
				assertEquals(WritingMode.TB, ua.getUAContext().getFootnoteArea().flow);
				assertTrue("@footnoteを未知のマージンボックスとしない: " + warnings, warnings.isEmpty());
			} finally {
				ua.dispose();
			}
		}
	}

	public void testUnsupportedDescriptorsWarnAndFallBack() throws Exception {
		final List<Short> warnings = new ArrayList<>();
		final PDFUserAgent ua = new PDFUserAgent() { };
		try {
			parse(ua, "@page :first, :left { @footnote { float: bottom; float: top;"
					+ " writing-mode: sideways-rl; max-height: 50pt; color: red } }", warnings);
			assertEquals(FootnoteArea.Position.BLOCK_END, ua.getUAContext().getFootnoteArea().position);
			assertNull(ua.getUAContext().getFootnoteArea().flow);
			assertEquals("セレクタごとに警告を重複しない", 4, warnings.size());
			for (final short code : warnings) {
				assertEquals(MessageCodes.WARN_BAD_CSS_SYNTAX, code);
			}
			parse(ua, "@footnote { writing-mode: vertical-lr } @footnote { writing-mode: bad }", warnings);
			assertEquals("不正な向きは直前の有効値を消さない", WritingMode.LR,
					ua.getUAContext().getFootnoteArea().flow);
			parse(ua, "@footnote { writing-mode: vertical-rl }", warnings);
			assertEquals(WritingMode.RL, ua.getUAContext().getFootnoteArea().flow);
		} finally {
			ua.dispose();
		}
	}

	public void testDefaultAndDocumentReset() throws Exception {
		final PDFUserAgent ua = new PDFUserAgent() { };
		try {
			parse(ua, "@page { margin: 24pt }", new ArrayList<>());
			assertSame(FootnoteArea.DEFAULT, ua.getUAContext().getFootnoteArea());
			ua.getUAContext().setFootnoteArea(FootnoteArea.DEFAULT.withPosition(FootnoteArea.Position.BOTTOM));
			ua.getUAContext().setFootnoteArea(null);
			assertSame(FootnoteArea.DEFAULT, ua.getUAContext().getFootnoteArea());
			for (final PrepareMode mode : new PrepareMode[] { PrepareMode.DOCUMENT, PrepareMode.STRUCTURE_SCAN }) {
				ua.getUAContext().setFootnoteArea(FootnoteArea.DEFAULT.withFlow(WritingMode.TB));
				ua.prepare(mode);
				assertSame(FootnoteArea.DEFAULT, ua.getUAContext().getFootnoteArea());
			}
		} finally {
			ua.dispose();
		}
	}

	public void testVerticalBottomBandKeepsPaperAndMarginBoxes() throws Exception {
		final String html = fixture();
		final Capture capture = transcode(html);
		final Capture baseline = transcode(html.replace(AREA, ""));
		assertEquals("縦組みの明示block-endも既定と同じ", baseline,
				transcode(html.replace(AREA, "@footnote { float: block-end }")));
		assertEquals("本文と注を二ページで出す", 2, capture.pages().size());
		assertEquals("注を欠落・重複させない", 2, capture.notes().size());
		assertEquals(0.0, capture.pages().get(1).inset(), 0.0);
		final PageMetrics second = capture.pages().get(0);
		assertTrue("同頁の二件を本文の行長から予約する", second.inset() > 0);
		assertTrue(second.inset() <= 252 * 0.6);
		assertEquals(252.0, second.innerHeight() + second.inset(), EPSILON);
		for (int i = 0; i < capture.notes().size(); ++i) {
			final Placement note = capture.notes().get(i);
			assertEquals("一頁目の呼び出しの注を同じ頁へ", 1, note.page());
			assertEquals(WritingMode.TB, note.flow());
			assertEquals("箱の構築時から横書きの版面幅を使う", 252.0, note.width(), EPSILON);
			assertEquals("横書き帯の左端", 0.0, note.x(), EPSILON);
			assertTrue("本文の内側の末尾へ置く", note.y() >= second.innerHeight() + 6 - EPSILON);
			assertTrue("下余白へ食い込まない", note.y() + note.height() <= 252 + EPSILON);
			assertTrue("call頁の番号を保つ: " + note.text(), note.text().startsWith((i + 1) + ". "));
		}
		final Placement first = capture.notes().get(0);
		assertEquals("帯の先頭から配置する", second.innerHeight() + 6, first.y(), EPSILON);
		assertTrue("注は上からFIFO順", capture.notes().get(1).y() >= first.y() + first.height() - EPSILON);
		for (int i = 0; i < 2; ++i) {
			final PageMetrics page = capture.pages().get(i);
			assertEquals("用紙の高さを縮めない", 300.0, page.height(), 0.0);
			assertEquals("視覚上の用紙の高さも縮めない", 300.0, page.visualHeight(), 0.0);
			assertEquals(24.0, page.marginBottom(), 0.0);
			assertEquals("ノンブルの座標・寸法は規則なしと同一", footer(baseline, i + 1), footer(capture, i + 1));
		}
		assertTrue("二頁目にも本文がある", capture.lines().stream()
				.anyMatch(line -> line.page() == 2 && line.flow() == WritingMode.RL));
		for (final Placement line : capture.lines()) {
			if (line.page() == 1 && line.flow() == WritingMode.RL) {
				assertTrue("本文の行を帯の前で閉じる", line.y() + line.height() <= second.innerHeight() + EPSILON);
			}
		}
	}

	/** 非正方形のページと、作者の padding・margin 付きの注(codex F-1 レビューの任意項目)。 */
	public void testNotePaddingOnNonSquarePage() throws Exception {
		final String html = fixture().replace("output.page-height\" value=\"300pt\"", "output.page-height\" value=\"320pt\"")
				.replace(".note { float: footnote;", ".note { float: footnote; padding: 0 10pt; margin: 0 30pt;");
		final Capture capture = transcode(html);
		assertEquals(2, capture.pages().size());
		final PageMetrics second = capture.pages().get(1);
		assertEquals("縦 320pt の版面は 272pt: 帯を引いた内寸と帯の和", 272.0, second.innerHeight() + second.inset(), EPSILON);
		assertEquals(2, capture.notes().size());
		for (final Placement note : capture.notes()) {
			assertEquals("padding を含めて帯の幅(border-box)", 252.0, note.width(), EPSILON);
			assertEquals("左右の margin は 0、左端揃え", 0.0, note.x(), EPSILON);
			assertTrue(note.y() + note.height() <= 272 + EPSILON);
		}
	}

	public void testHorizontalBottomUsesUnchangedBlockEndPath() throws Exception {
		final String html = fixture().replace("writing-mode: vertical-rl", "writing-mode: horizontal-tb");
		final Capture baseline = transcode(html.replace(AREA, ""));
		assertEquals("bottomは既定と同じ箱・配置・寸法", baseline, transcode(html));
		assertEquals("明示block-endも既定と同じ", baseline,
				transcode(html.replace(AREA, "@footnote { float: block-end }")));
		assertFalse(baseline.notes().isEmpty());
		assertEquals("横組みの注は従来どおり呼び出し頁へ", 1, baseline.notes().get(0).page());
	}

	public void testOversizedCarryInMakesProgressAtEndOfDocument() throws Exception {
		final String html = fixture();
		final String shortBody = html.substring(0, html.indexOf("<body>"))
				+ "<body><p>本文<span class='note' style='height:400pt'>巨大な注</span>続き"
				+ "<span class='note'>次の注</span>終わり</p></body></html>";
		final Capture capture = transcode(shortBody);
		assertEquals("巨大注と後続注をFIFOで送り、EOFで停止する", 3, capture.pages().size());
		assertEquals(2, capture.notes().size());
		assertEquals(2, capture.notes().get(0).page());
		assertEquals(3, capture.notes().get(1).page());
		final PageMetrics second = capture.pages().get(1);
		assertEquals("予約だけを帯の上限で止める", 252 * 0.6, second.inset(), EPSILON);
		assertEquals("巨大注も帯の上端から溢れさせる", second.innerHeight() + 6,
				capture.notes().get(0).y(), EPSILON);
		assertTrue(capture.notes().get(0).height() >= 400);
		assertTrue("後続注のcall頁の番号を保持する", capture.notes().get(1).text().startsWith("2. "));
	}

	private static void parse(final PDFUserAgent ua, final String css, final List<Short> warnings)
			throws Exception {
		ua.setMessageHandler((code, args, message) -> warnings.add(code));
		final CSSStyleSheetBuilder builder = new CSSStyleSheetBuilder(ua);
		builder.setCSSStyleSheet(new CSSStyleSheet());
		final InputSource source = new InputSource(new StringReader(css));
		source.setURI("file:///footnote-area.css");
		builder.parse(source);
	}

	private static String fixture() throws Exception {
		return Files.readString(Path.of("files/unittest/0125-footnote/footnote-bottom-vertical-rl.html"),
				StandardCharsets.UTF_8);
	}

	private static String fixedFixture() throws Exception {
		return Files.readString(Path.of("files/unittest/0125-footnote/footnote-bottom-fixed-height.html"), StandardCharsets.UTF_8);
	}

	private static Placement footer(final Capture capture, final int page) {
		final List<Placement> found = capture.lines().stream()
				.filter(line -> line.page() == page && line.y() >= 252
						&& line.text().trim().equals(Integer.toString(page))).toList();
		assertEquals("下余白のノンブルを観測する", 1, found.size());
		return found.get(0);
	}

	private static Capture transcode(final String html) throws Exception {
		return transcode(html, page -> { });
	}

	private static Capture transcode(final String html, final java.util.function.IntConsumer pageOutput) throws Exception {
		final CaptureUserAgent ua = new CaptureUserAgent();
		final var reports = new java.util.concurrent.atomic.AtomicInteger();
		ua.getUAContext().setFootnotePageProbeListener(report -> reports.incrementAndGet());
		final List<byte[]> displayLists = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try (final AutoCloseable observer = DisplayListDumper.observePages((drawer, number) -> {
			final StringBuilder text = new StringBuilder();
			drawer.dump(text, "");
			displayLists.add(text.toString().getBytes(StandardCharsets.UTF_8));
			pageOutput.accept(number);
		})) {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.property-pi", "true");
			session.property("processing.pass-count", "1");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///footnote-area.html"), "text/html", "UTF-8");
			return ua.capture.capture(ua.getUAContext().getFootnotePageProbeCount(), reports.get(), displayLists);
		} finally {
			session.close();
		}
	}

	private record Placement(int page, double x, double y, double width, double height, WritingMode flow, String text) {
	}

	private record PageMetrics(double height, double visualHeight, double innerHeight, double inset, double marginBottom,
			boolean separator) {
	}

	private record Capture(List<PageMetrics> pages, List<Placement> notes, List<Placement> lines,
			long probes, int reports, List<byte[]> displayLists) {
		@Override
		public boolean equals(final Object other) {
			return other instanceof Capture capture && this.pages.equals(capture.pages)
					&& this.notes.equals(capture.notes) && this.lines.equals(capture.lines);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(this.pages, this.notes, this.lines);
		}
	}

	/** DirectSessionの変換スレッドからUAインスタンスへ記録し、ThreadLocalを使いません。 */
	private static final class CaptureUserAgent extends PDFUserAgent {
		private CaptureVisitor capture;

		@Override
		public void prepare(final PrepareMode mode) {
			super.prepare(mode);
			this.capture = new CaptureVisitor(this);
			this.visitor = this.capture;
		}
	}

	private static final class CaptureVisitor extends PDFVisitor {
		private int page;
		private boolean mainPage;
		private final List<PageMetrics> pages = new ArrayList<>();
		private final List<Placement> notes = new ArrayList<>();
		private final List<Placement> lines = new ArrayList<>();

		CaptureVisitor(final UserAgent ua) {
			super(ua);
		}

		@Override
		public void nextPage(final PDFGC gc) {
			super.nextPage(gc);
			++this.page;
			this.mainPage = true;
		}

		@Override
		public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x,
				final double y) {
			super.visitBox(transform, box, drawer, x, y);
			if (box instanceof PageBox pageBox && this.mainPage) {
				this.mainPage = false;
				this.pages.add(new PageMetrics(pageBox.getHeight(), pageBox.getVisualHeight(),
						pageBox.getInnerHeight(), pageBox.getFootInset(), pageBox.getFrame().margin.bottom, hasSeparator(pageBox)));
			}
			if (box instanceof FloatBlockBox note && box.getPos() instanceof FootnotePos) {
				this.notes.add(this.placement(box, note.getBlockParams().flow, x, y));
			} else if (box instanceof AbstractLineBox line) {
				this.lines.add(this.placement(box, line.getLineParams().flow, x, y));
			}
		}

		private static boolean hasSeparator(final PageBox page) {
			try {
				final var block = PageBox.class.getDeclaredField("footnoteSeparatorAxis");
				final var line = PageBox.class.getDeclaredField("footnoteSeparatorLineAxis");
				block.setAccessible(true);
				line.setAccessible(true);
				return block.getDouble(page) >= 0 || line.getDouble(page) >= 0;
			} catch (final ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		}

		private Placement placement(final IBox box, final WritingMode flow, final double x, final double y) {
			final StringBuilder text = new StringBuilder();
			box.getText(text);
			return new Placement(this.page, x, y, box.getWidth(), box.getHeight(), flow, text.toString());
		}

		Capture capture(final long probes, final int reports, final List<byte[]> displayLists) {
			return new Capture(List.copyOf(this.pages), List.copyOf(this.notes), List.copyOf(this.lines),
					probes, reports, List.copyOf(displayLists));
		}
	}
}
