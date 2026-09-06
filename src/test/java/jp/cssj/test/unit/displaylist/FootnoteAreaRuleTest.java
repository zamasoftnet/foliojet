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
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.FootnoteArea;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 脚注領域F-1の規則・持ち越し・物理配置と、既定経路の保存を検査します。 */
public class FootnoteAreaRuleTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String AREA = "@footnote { float: bottom; writing-mode: horizontal-tb }";
	private static final double EPSILON = 0.01;

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
		assertEquals(0.0, capture.pages().get(0).inset(), 0.0);
		final PageMetrics second = capture.pages().get(1);
		assertTrue("持ち越し分を本文の行長から予約する", second.inset() > 0);
		assertTrue(second.inset() <= 252 * 0.6);
		assertEquals(252.0, second.innerHeight() + second.inset(), EPSILON);
		for (int i = 0; i < capture.notes().size(); ++i) {
			final Placement note = capture.notes().get(i);
			assertEquals("一頁目の呼び出しの注は二頁目へ", 2, note.page());
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
			if (line.page() == 2 && line.flow() == WritingMode.RL) {
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

	private static Placement footer(final Capture capture, final int page) {
		final List<Placement> found = capture.lines().stream()
				.filter(line -> line.page() == page && line.y() >= 252
						&& line.text().trim().equals(Integer.toString(page))).toList();
		assertEquals("下余白のノンブルを観測する", 1, found.size());
		return found.get(0);
	}

	private static Capture transcode(final String html) throws Exception {
		final CaptureUserAgent ua = new CaptureUserAgent();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.property-pi", "true");
			session.property("processing.pass-count", "1");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///footnote-area.html"), "text/html", "UTF-8");
			return ua.capture.capture();
		} finally {
			session.close();
		}
	}

	private record Placement(int page, double x, double y, double width, double height, WritingMode flow, String text) {
	}

	private record PageMetrics(double height, double visualHeight, double innerHeight, double inset, double marginBottom) {
	}

	private record Capture(List<PageMetrics> pages, List<Placement> notes, List<Placement> lines) {
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
						pageBox.getInnerHeight(), pageBox.getFootInset(), pageBox.getFrame().margin.bottom));
			}
			if (box instanceof FloatBlockBox note && box.getPos() instanceof FootnotePos) {
				this.notes.add(this.placement(box, note.getBlockParams().flow, x, y));
			} else if (box instanceof AbstractLineBox line) {
				this.lines.add(this.placement(box, line.getLineParams().flow, x, y));
			}
		}

		private Placement placement(final IBox box, final WritingMode flow, final double x, final double y) {
			final StringBuilder text = new StringBuilder();
			box.getText(text);
			return new Placement(this.page, x, y, box.getWidth(), box.getHeight(), flow, text.toString());
		}

		Capture capture() {
			return new Capture(List.copyOf(this.pages), List.copyOf(this.notes), List.copyOf(this.lines));
		}
	}
}
