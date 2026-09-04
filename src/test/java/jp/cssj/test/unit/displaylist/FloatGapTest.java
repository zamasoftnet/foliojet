package jp.cssj.test.unit.displaylist;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.PageFloatPos;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 分割不能floatの先送りで旧頁に排除跡を残さないことの回帰テスト。 */
public class FloatGapTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final long WATCHDOG_MS = 60_000L;
	private static final double EPSILON = 0.05;

	public FloatGapTest(final String name) {
		super(name);
	}

	/** pre=30では直交floatが現頁に収まり、重なる縦行だけが100pt下から始まる。 */
	public void testVerticalRlFitKeepsFloatAndWrapsBesideIt() throws Exception {
		final Capture capture = transcode("vertical-fit-30", verticalGapDocument(30, true), 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "pre=30のfigure");
		assertEquals("収まるfigureは現頁", 1, floating.page());
		final List<BoxBounds> post = lines(capture, "POST");
		assertTrue("figureと同じ頁に後続縦行が必要", post.stream().anyMatch(line -> line.page() == floating.page()));
		assertNoIntersections("pre=30", floating, post);
		assertTrue("figure脇の縦行は100pt下から始まる",
				post.stream().anyMatch(line -> line.page() == floating.page()
						&& overlapsX(line.bounds(), floating.bounds())
						&& Math.abs(line.bounds().getMinY() - floating.bounds().getMaxY()) <= 0.75));
		assertWordsPreserved(capture, "PRE", 30);
		assertWordsPreserved(capture, "POST", 8);
		assertWordsPreserved(capture, "TAIL", 40);
		assertWordsPreserved(capture, "FN-BODY", 1);
		assertTrue("ページ数は有限", capture.pageCount() <= 8);
	}

	/**
	 * pre=36/42/48ではfigureだけを次頁へ送り、旧頁の全後続縦行を全長に保つ。
	 * 脚注は付けない——脚注予約で旧頁の末尾が縮み、pre=42では後続の縦行が
	 * 旧頁に残らない(脚注の型は別試験)。
	 */
	public void testVerticalRlMoveLeavesNoGap() throws Exception {
		for (final int preCount : new int[] { 36, 42, 48 }) {
			final Capture capture = transcode("vertical-move-" + preCount,
					verticalGapDocument(preCount, false), 1, null);
			final BoxBounds floating = only(capture.normalFloats(), "pre=" + preCount + "のfigure");
			assertEquals("収まらないfigureは次頁: pre=" + preCount, 2, floating.page());
			// 表示リストの座標は版面内辺基準(0..180)
			assertEquals("次頁のblock-startへ置く", 180, floating.bounds().getMaxX(), 0.75);
			final List<BoxBounds> pageOnePost = capture.lines().stream()
					.filter(line -> line.page() == 1
							&& (line.text().contains("POST") || line.text().contains("TAIL")))
					.toList();
			assertTrue("旧頁にfigure後の縦行が必要: pre=" + preCount, !pageOnePost.isEmpty());
			for (final BoxBounds line : pageOnePost) {
				assertEquals("旧頁の縦行は跡地を空けず版面上端から始まる: pre=" + preCount,
						0, line.bounds().getMinY(), 0.75);
			}
			assertNoIntersections("pre=" + preCount, floating, capture.lines());
			assertWordsPreserved(capture, "PRE", preCount);
			assertWordsPreserved(capture, "POST", 8);
			assertWordsPreserved(capture, "TAIL", 40);
			assertTrue("ページ数は有限: pre=" + preCount, capture.pageCount() <= 8);
		}
	}

	/** 後着脚注は配置済みatomic floatを押し出さず、callだけを現頁に残して本文を送る。 */
	public void testFootnoteBodyDefersBehindPlacedAtomicFloat() throws Exception {
		final String html = verticalGapDocument(30, true).replace("float:footnote;",
				"float:footnote; width:60pt;");
		final Capture capture = transcode("footnote-behind-atomic", html, 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "脚注前のfigure");
		final BoxBounds footnote = only(capture.footnotes(), "送られた脚注本文");
		assertEquals("配置済みfigureは現頁に残る", 1, floating.page());
		assertEquals("脚注本文は次頁へ送る", 2, footnote.page());
		assertTrue("脚注callを捕捉できる", !capture.footnoteCalls().isEmpty());
		for (final BoxBounds call : capture.footnoteCalls()) {
			assertEquals("脚注callはfigureの頁に残る", floating.page(), call.page());
		}
		assertNoIntersections("脚注による再移動なし", floating, capture.lines());
	}

	/** atomic floorより下へ入らない先頭bottomは強制予約せず次頁へ送る。 */
	public void testFirstBottomFloatDefersBehindAtomicFloatFloor() throws Exception {
		final String html = verticalGapDocument(30, false).replace("<p class='post'>",
				"<div class='bottom'></div><p class='post'>").replace(".note {",
				".bottom { float:bottom; width:30pt; height:50pt; background:#666 }\n.note {");
		final Capture capture = transcode("bottom-behind-atomic", html, 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "bottom前のfigure");
		final BoxBounds bottom = only(capture.bottomFloats(), "延期したbottom");
		assertEquals("atomic figureは現頁", 1, floating.page());
		assertEquals("先頭bottomは次頁へ延期", 2, bottom.page());
		assertTrue("bottomはatomic floorの後の頁へ明示的に延期する", floating.page() < bottom.page());
	}

	/**
	 * marginだけが紙の外へ出る分割不能figure(実文書cti.liの`margin: 0 1.5em 1.2em`)は、
	 * 描画実測では収まって見えても占有寸法で判定してMOVE_TO_NEXTにし、跡地を残さない。
	 */
	public void testMarginOnlyOverflowMovesWithoutGap() throws Exception {
		// pre=30: アンカーは96pt、figureの枠は80pt(=176≦180で収まる)だが、
		// 横margin 5pt×2 を足した占有寸法90ptでは186>180で収まらない
		final String html = verticalGapDocument(30, false).replace("width:80pt; height:100pt;",
				"width:80pt; height:100pt; margin:0 5pt;");
		final Capture capture = transcode("vertical-margin-overflow", html, 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "margin超過のfigure");
		assertEquals("margin込みで収まらないfigureは次頁", 2, floating.page());
		final List<BoxBounds> pageOnePost = capture.lines().stream()
				.filter(line -> line.page() == 1
						&& (line.text().contains("POST") || line.text().contains("TAIL")))
				.toList();
		assertTrue("旧頁にfigure後の縦行が必要", !pageOnePost.isEmpty());
		for (final BoxBounds line : pageOnePost) {
			assertEquals("旧頁の縦行は跡地を空けず版面上端から始まる", 0, line.bounds().getMinY(), 0.75);
		}
		assertNoIntersections("margin超過", floating, capture.lines());
		assertWordsPreserved(capture, "PRE", 30);
		assertWordsPreserved(capture, "POST", 8);
		assertWordsPreserved(capture, "TAIL", 40);
	}

	/** 入れ子の局所先頭floatでも、親が頁先頭でなければ収まる時だけ現頁へ残す。 */
	public void testNestedFirstFloatUsesEffectiveFragmentStart() throws Exception {
		final Capture fitting = transcode("nested-first-fit", nestedFirstFloatDocument(99.0), 1, null);
		final BoxBounds fittingFloat = only(fitting.normalFloats(), "収まる入れ子先頭figure");
		assertEquals("収まる入れ子先頭figureは現頁", 1, fittingFloat.page());
		final List<BoxBounds> fittingLines = linesOnPage(fitting, "NEST", 1);
		assertTrue("収まるfigureと同じ頁に入れ子本文が必要", !fittingLines.isEmpty());
		assertNoIntersections("収まる入れ子先頭figure", fittingFloat, fittingLines);
		assertTrue("収まる入れ子本文はfigureの下へ回り込む",
				fittingLines.stream().anyMatch(line -> overlapsX(line.bounds(), fittingFloat.bounds())
						&& Math.abs(line.bounds().getMinY() - fittingFloat.bounds().getMaxY()) <= 0.75));

		final Capture overflowing = transcode("nested-first-move", nestedFirstFloatDocument(101.5), 1, null);
		final BoxBounds movedFloat = only(overflowing.normalFloats(), "収まらない入れ子先頭figure");
		assertEquals("親が頁先頭でないfigureは次頁", 2, movedFloat.page());
		final List<BoxBounds> oldPageLines = linesOnPage(overflowing, "NEST", 1);
		assertTrue("送り元頁に入れ子本文が必要", !oldPageLines.isEmpty());
		for (final BoxBounds line : oldPageLines) {
			assertEquals("送り元の入れ子本文はfigure跡地を空けない", 0, line.bounds().getMinY(), 0.75);
		}
		assertNoIntersections("収まらない入れ子先頭figure", movedFloat, overflowing.lines());
	}

	/** bottom→本文→atomicの逆順でもbottom予約を保ち、atomicだけを次頁へ送る。 */
	public void testReservedBottomStaysWhenLaterAtomicFloatWouldEnterItsBand() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 } body { font:10pt/12pt monospace }
				.bottom { float:bottom; box-sizing:border-box; width:60pt; height:30pt; background:#666 }
				.spacer { box-sizing:border-box; height:72pt; background:#eee }
				.atomic { float:inline-start; writing-mode:vertical-rl;
				          width:60pt; height:40pt; background:#999 }
				.after { text-align:justify }
				</style></head><body><div class='bottom'></div>
				<div class='spacer'>REVERSE-BODY</div><div class='atomic'></div>
				<p class='after'>%s</p></body></html>
				""".formatted(repeatedWords("REVERSE-AFTER", 80));
		final Capture capture = transcode("bottom-body-atomic", html, 1, null);
		final BoxBounds bottom = only(capture.bottomFloats(), "先に予約したbottom");
		final BoxBounds atomic = only(capture.normalFloats(), "後着atomic figure");
		assertEquals("予約済みbottomは現頁に残る", 1, bottom.page());
		assertEquals("bottom帯へ入るatomicだけを次頁へ送る", 2, atomic.page());
		assertTrue("atomicの延期を明示", bottom.page() < atomic.page());
		assertNoIntersections("予約済みbottom", bottom, capture.lines());
		assertNoIntersections("延期atomic", atomic, capture.lines());
		final List<BoxBounds> after = linesOnPage(capture, "REVERSE-AFTER", 1);
		assertTrue("atomic送り元頁に後続本文が必要", !after.isEmpty());
		assertEquals("後続本文はatomic跡地を作らず固定spacer直後から始まる",
				72, after.get(0).bounds().getMinY(), 0.75);
		assertEquals("bottom帯より上の最初の行は全幅", 180, after.get(0).bounds().getWidth(), 0.75);
	}

	/** vertical-rlの分割不能floatは0.7ptのpainted sliverなら配置・分割ともKeepする。 */
	public void testVerticalRlUnsplittableOverflowPointSevenStaysWithoutGap() throws Exception {
		final Capture capture = transcode("vertical-tolerance-0_7", verticalToleranceDocument(100.7), 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "0.7pt超過figure");
		assertEquals("0.7pt超過は現頁に残す", 1, floating.page());
		final List<BoxBounds> lines = linesOnPage(capture, "SLIVER", 1);
		assertTrue("0.7pt超過figureと同頁に本文が必要", !lines.isEmpty());
		assertNoIntersections("0.7pt超過", floating, lines);
		assertTrue("0.7pt超過figureの列は下へ回り込む",
				lines.stream().anyMatch(line -> overlapsX(line.bounds(), floating.bounds())
						&& Math.abs(line.bounds().getMinY() - floating.bounds().getMaxY()) <= 0.75));
	}

	/** vertical-rlの分割不能floatは1.5pt超過なら送り、旧頁に排除跡を残さない。 */
	public void testVerticalRlUnsplittableOverflowOnePointFiveMovesWithoutGap() throws Exception {
		final Capture capture = transcode("vertical-tolerance-1_5", verticalToleranceDocument(101.5), 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "1.5pt超過figure");
		assertEquals("1.5pt超過は次頁へ送る", 2, floating.page());
		final List<BoxBounds> oldPageLines = linesOnPage(capture, "SLIVER", 1);
		assertTrue("送り元頁に後続本文が必要", !oldPageLines.isEmpty());
		for (final BoxBounds line : oldPageLines) {
			assertEquals("1.5pt超過の送り元は排除跡を残さない", 0, line.bounds().getMinY(), 0.75);
		}
		assertNoIntersections("1.5pt超過", floating, capture.lines());
	}

	/** 行末floatがMOVE_TO_NEXTなら、その判定前の現在行を狭めない。 */
	public void testInlineEndMoveKeepsCurrentLineFullWidth() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 150pt; margin:10pt }
				html,body,p { margin:0 }
				body { font:10pt/12pt monospace }
				.pre { box-sizing:border-box; height:96pt }
				.fig { float:inline-end; display:block; writing-mode:vertical-rl;
				       width:80pt; height:80pt; background:#999 }
				.end { text-align:justify }
				</style></head><body><p class='pre'>PRE-END</p>
				<p class='end'>ENDHEAD <span class='fig'></span>%s</p></body></html>
				""".formatted(repeatedWords("ENDTAIL", 40));
		final Capture capture = transcode("inline-end-move", html, 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "inline-end figure");
		assertEquals("inline-end figureは次頁", 2, floating.page());
		final List<BoxBounds> currentLines = linesOnPage(capture, "ENDHEAD", 1);
		assertEquals("anchorを含む現在行", 1, currentLines.size());
		assertEquals("MOVE_TO_NEXTの現在行は180pt全幅", 180,
				currentLines.get(0).bounds().getWidth(), 0.75);
	}

	/** 横組の鏡像でも直交floatを先送りし、旧頁の横行を全幅に保つ。 */
	public void testHorizontalTbMirrorLeavesNoGap() throws Exception {
		final String html = """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:300pt 200pt; margin:10pt }
				html,body,p { margin:0 }
				body { font:10pt/12pt monospace }
				.pre { box-sizing:border-box; height:108pt }
				.fig { float:inline-start; writing-mode:vertical-rl;
				       width:100pt; height:80pt; background:#999 }
				.post { text-align:justify }
				</style></head><body><p class='pre'>PRE-H</p><div class='fig'></div>
				<p class='post'>%s</p></body></html>
				""".formatted(repeatedWords("MIRROR", 80));
		final Capture capture = transcode("horizontal-mirror", html, 1, null);
		final BoxBounds floating = only(capture.normalFloats(), "横組鏡像figure");
		assertEquals("収まらないfigureは次頁", 2, floating.page());
		assertEquals("次頁の上端へ置く", 0, floating.bounds().getMinY(), 0.75);
		final List<BoxBounds> pageOnePost = linesOnPage(capture, "MIRROR", 1);
		assertTrue("旧頁に後続横行が必要", !pageOnePost.isEmpty());
		for (final BoxBounds line : pageOnePost) {
			assertEquals("旧頁の横行は跡地を空けず版面左端から始まる", 0,
					line.bounds().getMinX(), 0.75);
		}
		assertNoIntersections("横組鏡像", floating, capture.lines());
		assertWordsPreserved(capture, "MIRROR", 80);
	}

	/** ownerと同じ書字軸のBLOCK floatは従来どおりページ境界で分割する。 */
	public void testSameWritingAxisFloatStillSplits() throws Exception {
		final String html = verticalGapDocument(36, false)
				.replace("writing-mode:horizontal-tb;", "writing-mode:vertical-rl;");
		final Capture capture = transcode("same-axis-split", html, 1, null);
		assertTrue("同軸figureの先頭断片は旧頁に残る",
				capture.normalFloats().stream().anyMatch(floating -> floating.page() == 1));
		assertTrue("同軸figureの残余断片は次頁にある",
				capture.normalFloats().stream().anyMatch(floating -> floating.page() == 2));
		assertWordsPreserved(capture, "POST", 8);
	}

	/** atomic floorのmax通知はTwoPass replayでも表示リストを変えない。 */
	public void testTwoPassMainCaseHasIdenticalDisplayLists() throws Exception {
		final String html = verticalGapDocument(36, true);
		final File one = new File("local/unittest/float-gap-pass-parity/pass-1");
		final File two = new File("local/unittest/float-gap-pass-parity/pass-2");
		final Capture passOne = transcode("float-gap-pass-1", html, 1, one);
		final Capture passTwo = transcode("float-gap-pass-2", html, 2, two);
		assertEquals("pass-count=1のfigure数", 1, passOne.normalFloats().size());
		assertEquals("pass-count=2のfigure数", 1, passTwo.normalFloats().size());
		assertDisplayListsEqual(one, two);
	}

	private static String verticalGapDocument(final int preCount, final boolean footnote) {
		final String note = footnote ? "<span class='note'>FN-BODY</span>" : "";
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 300pt; margin:10pt }
				html,body,p { margin:0 }
				body { writing-mode:vertical-rl; font:10pt/12pt monospace }
				p { text-align:justify }
				.fig { float:inline-start; writing-mode:horizontal-tb;
				       width:80pt; height:100pt; background:#999 }
				.note { float:footnote; font:10pt/12pt monospace }
				</style></head><body><p class='pre'>%s</p><div class='fig'></div>
				<p class='post'>%s%s%s</p></body></html>
				""".formatted(indexedWords("PRE", preCount), indexedWords("POST", 8), note,
					indexedWords("TAIL", 40));
	}

	private static String nestedFirstFloatDocument(final double spacerWidth) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 180pt; margin:10pt }
				html,body,p,section { margin:0 }
				body { writing-mode:vertical-rl; font:10pt/12pt monospace }
				.spacer { box-sizing:border-box; width:%.1fpt; height:1pt; background:#eee }
				.nested { margin:0; padding:0 }
				.fig { float:inline-start; writing-mode:horizontal-tb;
				       width:80pt; height:60pt; background:#999 }
				.post { text-align:justify }
				</style></head><body><div class='spacer'></div><section class='nested'>
				<div class='fig'></div><p class='post'>%s</p></section></body></html>
				""".formatted(spacerWidth, repeatedWords("NEST", 100));
	}

	private static String verticalToleranceDocument(final double spacerWidth) {
		return """
				<!DOCTYPE html><html><head><meta charset='UTF-8'><style>
				@page { size:200pt 180pt; margin:10pt }
				html,body,p { margin:0 }
				body { writing-mode:vertical-rl; font:10pt/12pt monospace }
				.spacer { box-sizing:border-box; width:%.1fpt; height:1pt; background:#eee }
				.fig { float:inline-start; writing-mode:horizontal-tb;
				       width:80pt; height:60pt; background:#999 }
				.post { text-align:justify }
				</style></head><body><div class='spacer'></div><div class='fig'></div>
				<p class='post'>%s</p></body></html>
				""".formatted(spacerWidth, repeatedWords("SLIVER", 100));
	}

	private static String indexedWords(final String prefix, final int count) {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			text.append(prefix).append(String.format("%03d", i)).append(' ');
		}
		return text.toString();
	}

	private static String repeatedWords(final String word, final int count) {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			text.append(word).append(' ');
		}
		return text.toString();
	}

	private static Capture transcode(final String name, final String html, final int passCount, final File dumpDir)
			throws Exception {
		final File dir = new File("build/float-gap/" + name);
		dir.mkdirs();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		if (dumpDir != null) {
			deleteChildren(dumpDir);
			dumpDir.mkdirs();
		}

		synchronized (DisplayListDumper.class) {
			final String previous = System.getProperty(DisplayListDumper.DIR_PROPERTY);
			if (dumpDir == null) {
				System.clearProperty(DisplayListDumper.DIR_PROPERTY);
			} else {
				System.setProperty(DisplayListDumper.DIR_PROPERTY, dumpDir.getPath());
			}
			try {
				return transcodeWithWatchdog(name, input, new File(dir, "out.pdf"), passCount);
			} finally {
				if (previous == null) {
					System.clearProperty(DisplayListDumper.DIR_PROPERTY);
				} else {
					System.setProperty(DisplayListDumper.DIR_PROPERTY, previous);
				}
			}
		}
	}

	private static Capture transcodeWithWatchdog(final String name, final File input, final File pdf,
			final int passCount) throws Exception {
		final Capture[] capture = new Capture[1];
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final CapturePDFUserAgent ua = new CapturePDFUserAgent();
				try {
					session.setUserAgent(ua);
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("processing.pass-count", Integer.toString(passCount));
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
				capture[0] = ua.capture();
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "float-gap-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		assertNotNull(name + ": 配置結果を取得できない", capture[0]);
		return capture[0];
	}

	private static List<BoxBounds> lines(final Capture capture, final String token) {
		return capture.lines().stream().filter(line -> line.text().contains(token)).toList();
	}

	private static List<BoxBounds> linesOnPage(final Capture capture, final String token, final int page) {
		return capture.lines().stream().filter(line -> line.page() == page && line.text().contains(token)).toList();
	}

	private static BoxBounds only(final List<BoxBounds> boxes, final String label) {
		assertEquals(label + "の件数", 1, boxes.size());
		return boxes.get(0);
	}

	private static void assertNoIntersections(final String label, final BoxBounds floating,
			final List<BoxBounds> lines) {
		for (final BoxBounds line : lines) {
			if (line.page() == floating.page()) {
				assertFalse(label + ": 行とfigureが交差: float=" + floating.bounds()
						+ " line=" + line.bounds() + " text=" + line.text(),
						intersects(floating.bounds(), line.bounds()));
			}
		}
	}

	private static void assertWordsPreserved(final Capture capture, final String word, final int expected) {
		final StringBuilder text = new StringBuilder();
		for (final BoxBounds line : capture.lines()) {
			text.append(line.text()).append(' ');
		}
		int count = 0;
		int from = 0;
		while ((from = text.indexOf(word, from)) >= 0) {
			++count;
			from += word.length();
		}
		assertEquals(word + "の語数", expected, count);
	}

	private static boolean overlapsX(final Rectangle2D a, final Rectangle2D b) {
		return Math.min(a.getMaxX(), b.getMaxX()) - Math.max(a.getMinX(), b.getMinX()) > EPSILON;
	}

	private static boolean overlapsY(final Rectangle2D a, final Rectangle2D b) {
		return Math.min(a.getMaxY(), b.getMaxY()) - Math.max(a.getMinY(), b.getMinY()) > EPSILON;
	}

	private static boolean intersects(final Rectangle2D a, final Rectangle2D b) {
		return overlapsX(a, b) && overlapsY(a, b);
	}

	private static void assertDisplayListsEqual(final File expectedDir, final File actualDir) throws Exception {
		final File[] expectedPages = expectedDir.listFiles((dir, name) -> name.endsWith(".txt"));
		final File[] actualPages = actualDir.listFiles((dir, name) -> name.endsWith(".txt"));
		assertNotNull(expectedPages);
		assertNotNull(actualPages);
		assertTrue("比較元の表示リストが生成されている", expectedPages.length > 0);
		assertTrue("比較先の表示リストが生成されている", actualPages.length > 0);
		Arrays.sort(expectedPages, Comparator.comparing(File::getName));
		Arrays.sort(actualPages, Comparator.comparing(File::getName));
		assertEquals("表示リストのページ数", expectedPages.length, actualPages.length);
		for (int i = 0; i < expectedPages.length; ++i) {
			assertEquals("表示リスト名", expectedPages[i].getName(), actualPages[i].getName());
			assertEquals(expectedPages[i].getName(),
					Files.readString(expectedPages[i].toPath(), StandardCharsets.UTF_8),
					Files.readString(actualPages[i].toPath(), StandardCharsets.UTF_8));
		}
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}

	private record BoxBounds(int page, Rectangle2D bounds, String text) {
	}

	private record Capture(int pageCount, List<BoxBounds> normalFloats, List<BoxBounds> topFloats,
			List<BoxBounds> bottomFloats, List<BoxBounds> footnotes, List<BoxBounds> footnoteCalls,
			List<BoxBounds> lines) {
	}

	private static final class CapturePDFUserAgent extends PDFUserAgent {
		private CaptureVisitor captureVisitor;

		@Override
		public void prepare(final PrepareMode mode) {
			super.prepare(mode);
			this.captureVisitor = new CaptureVisitor(this);
			this.visitor = this.captureVisitor;
		}

		Capture capture() {
			return this.captureVisitor.capture();
		}
	}

	private static final class CaptureVisitor extends PDFVisitor {
		private int page = 0;
		private final List<BoxBounds> normalFloats = new ArrayList<>();
		private final List<BoxBounds> topFloats = new ArrayList<>();
		private final List<BoxBounds> bottomFloats = new ArrayList<>();
		private final List<BoxBounds> footnotes = new ArrayList<>();
		private final List<BoxBounds> footnoteCalls = new ArrayList<>();
		private final List<BoxBounds> lines = new ArrayList<>();

		CaptureVisitor(final UserAgent ua) {
			super(ua);
		}

		@Override
		public void nextPage(final PDFGC gc) {
			super.nextPage(gc);
			++this.page;
		}

		@Override
		public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x,
				final double y) {
			super.visitBox(transform, box, drawer, x, y);
			final Shape shape = transform == null ? new Rectangle2D.Double(x, y, box.getWidth(), box.getHeight())
					: transform.createTransformedShape(new Rectangle2D.Double(x, y, box.getWidth(), box.getHeight()));
			final Rectangle2D bounds = shape.getBounds2D();
			if (box.getParams() != null && box.getParams().element == CSSElement.FOOTNOTE_CALL) {
				this.footnoteCalls.add(new BoxBounds(this.page, bounds, ""));
			}
			if (box instanceof IFloatBox) {
				final BoxBounds found = new BoxBounds(this.page, bounds, "");
				if (box.getPos() instanceof PageFloatPos pageFloat) {
					(pageFloat.top ? this.topFloats : this.bottomFloats).add(found);
				} else if (box.getPos() instanceof FootnotePos) {
					this.footnotes.add(found);
				} else {
					this.normalFloats.add(found);
				}
			} else if (box instanceof AbstractLineBox) {
				final StringBuilder text = new StringBuilder();
				box.getText(text);
				if (text.length() > 0) {
					this.lines.add(new BoxBounds(this.page, bounds, text.toString()));
				}
			}
		}

		Capture capture() {
			return new Capture(this.page, List.copyOf(this.normalFloats), List.copyOf(this.topFloats),
					List.copyOf(this.bottomFloats), List.copyOf(this.footnotes), List.copyOf(this.footnoteCalls),
					List.copyOf(this.lines));
		}
	}
}
