package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 縦組みのページフロートの向きを固定します(2026-10-05、ユーザー決定「仕様に合わせる」)。
 *
 * <p>
 * css-page-floats の {@code top}・{@code bottom} は書字方向に応じて block-start/inline-start・block-end/inline-end
 * で、物理の上下を指す。縦組みの {@code float: bottom} は用紙の下(行の末尾側)に置き、図と並ぶ行を短くする。
 * それまでの縦組みの置き方(ブロックの末尾=vertical-rl の左端、行の始まり側)は {@code float: block-end} で書く。
 * {@code float: top} は縦組みでも右上(行の始まり側)で、もともと物理の上だった。
 * </p>
 */
public class VerticalPageFloatTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** 版面: 120mm×80mm から余白 6mm ずつ。 */
	private static final double CONTENT_WIDTH = (120 - 12) * 72 / 25.4;

	private static final double CONTENT_HEIGHT = (80 - 12) * 72 / 25.4;

	private static final double FONT = 10;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?Text\\[\"([^\"]*)\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	public VerticalPageFloatTest(String name) {
		super(name);
	}

	/** 図(40mm×20mm、背景つき)を本文の前に書いた 1 頁の縦組み。 */
	private static String document(final String writingMode, final String floating) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page{size:120mm 80mm;margin:6mm}
				html{writing-mode:%s;font:10pt/1.5 serif}
				body{margin:0}
				p{margin:0}
				.fig{float:%s;width:40mm;height:20mm;margin:0;background:#c66}
				</style></head><body>
				<div class="fig"></div><p>%s</p>
				</body></html>
				""".formatted(writingMode, floating, "本文。".repeat(100));
	}

	/** vertical-rl の bottom: 左下(ブロックの末尾・行の末尾)に置き、並ぶ行は図の上で終わる。 */
	public void testBottomIsPhysicalBottom() throws Exception {
		final String page = single(convert("rl-bottom", document("vertical-rl", "bottom")));
		final double[] fig = frame(page);
		assertEquals("左端", 0, fig[0], 0.5);
		assertEquals("下端", CONTENT_HEIGHT, fig[1] + fig[3], 0.5);
		assertNoOverlap(page, fig);
		assertTrue("図と並ぶ行がある(行が短くなって図の上に残る)", linesBeside(page, fig) > 0);
	}

	/** vertical-lr の bottom: 右下。 */
	public void testBottomVerticalLr() throws Exception {
		final String page = single(convert("lr-bottom", document("vertical-lr", "bottom")));
		final double[] fig = frame(page);
		assertEquals("右端", CONTENT_WIDTH, fig[0] + fig[2], 0.5);
		assertEquals("下端", CONTENT_HEIGHT, fig[1] + fig[3], 0.5);
		assertNoOverlap(page, fig);
	}

	/** block-end: 従来の縦組みの bottom の置き方(左上)。 */
	public void testBlockEndKeepsBlockEndCorner() throws Exception {
		final String page = single(convert("rl-block-end", document("vertical-rl", "block-end")));
		final double[] fig = frame(page);
		assertEquals("左端", 0, fig[0], 0.5);
		assertEquals("上端", 0, fig[1], 0.5);
		assertNoOverlap(page, fig);
	}

	/** top は縦組みでも右上(物理の上)。block-start も同じ。 */
	public void testTopIsPhysicalTop() throws Exception {
		for (final String floating : new String[] { "top", "block-start" }) {
			final String page = single(convert("rl-" + floating, document("vertical-rl", floating)));
			final double[] fig = frame(page);
			assertEquals(floating + ": 右端", CONTENT_WIDTH, fig[0] + fig[2], 0.5);
			assertEquals(floating + ": 上端", 0, fig[1], 0.5);
			assertNoOverlap(page, fig);
		}
	}

	/** 横組みでは bottom と block-end は同じ置き方。 */
	public void testHorizontalBottomEqualsBlockEnd() throws Exception {
		final double[] bottom = frame(single(convert("h-bottom", document("horizontal-tb", "bottom"))));
		final double[] blockEnd = frame(single(convert("h-block-end", document("horizontal-tb", "block-end"))));
		for (int i = 0; i < 4; ++i) {
			assertEquals(bottom[i], blockEnd[i], 0.01);
		}
	}

	/**
	 * 幅いっぱいの図(ブロック方向に版面全部。縦組みの width の % は不定のブロック方向の寸法に対するもので
	 * 効かないので長さで書く)を本文の途中に書くと、既に組んだ行と重ならないよう次の頁の下へ回り、
	 * その頁の行は全部、図の上で終わる(jigensha の縦組みの本で図・表を頁の地へ寄せる形)。
	 */
	public void testFullWidthBottomGoesToNextPageAndShortensAllLines() throws Exception {
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page{size:120mm 80mm;margin:6mm}
				html{writing-mode:vertical-rl;font:10pt/1.5 serif}
				body{margin:0}
				p{margin:0}
				.fig{float:bottom;width:108mm;height:20mm;margin:0;background:#c66}
				</style></head><body>
				<p>%s</p><div class="fig"></div><p>%s</p>
				</body></html>
				""".formatted("前文。".repeat(40), "本文。".repeat(200));
		final String[] pages = convert("rl-full-width", html);
		assertTrue("2 頁以上", pages.length >= 2);
		assertFalse("1 頁目に図は無い", FRAME.matcher(pages[0]).find());
		final double[] fig = frame(pages[1]);
		assertEquals("幅いっぱい", CONTENT_WIDTH, fig[2], 0.5);
		assertEquals("下端", CONTENT_HEIGHT, fig[1] + fig[3], 0.5);
		assertNoOverlap(pages[1], fig);
		assertTrue("2 頁目の行は図の脇で短い", linesBeside(pages[1], fig) > 5);
	}

	/** 縦組みの 2 段組の中の bottom も本文と重ならない(段組の中は一次元の予約)。 */
	public void testVerticalMulticolBottomDoesNotOverlap() throws Exception {
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page{size:120mm 80mm;margin:6mm}
				html{writing-mode:vertical-rl;font:10pt/1.5 serif}
				body{margin:0}
				p{margin:0}
				.text{column-count:2;column-gap:2em}
				.fig{float:bottom;width:30mm;height:20mm;margin:0;background:#c66}
				</style></head><body><div class="text">
				<p>%s</p><div class="fig"></div><p>%s</p>
				</div></body></html>
				""".formatted("前文。".repeat(20), "本文。".repeat(200));
		final String[] pages = convert("rl-multicol", html);
		boolean found = false;
		for (final String page : pages) {
			if (FRAME.matcher(page).find()) {
				assertNoOverlap(page, frame(page));
				found = true;
			}
		}
		assertTrue("図が無い", found);
	}

	private static String single(final String[] pages) {
		assertEquals("1 頁", 1, pages.length);
		return pages[0];
	}

	private static double[] frame(final String page) {
		final Matcher m = FRAME.matcher(page);
		assertTrue("図が無い: " + page, m.find());
		final double[] r = { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
				Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
		assertFalse("図が 2 つ", m.find());
		return r;
	}

	/**
	 * 縦組みの行の矩形(x は行の左、y は行頭。長さは全角の字数×字の大きさ)。行末の句読点はぶら下げで
	 * 行の外へ出てよく、縦組みの字面は字の枠の上寄りなので、長さに数えない。
	 */
	private static List<double[]> lines(final String page) {
		final List<double[]> lines = new ArrayList<>();
		final Matcher t = TEXT.matcher(page);
		while (t.find()) {
			final double x = Double.parseDouble(t.group(1));
			final double y = Double.parseDouble(t.group(2));
			final double width = Double.parseDouble(t.group(4)) + Double.parseDouble(t.group(5));
			final String text = t.group(3);
			final int chars = text.endsWith("。") || text.endsWith("、") ? text.length() - 1 : text.length();
			lines.add(new double[] { x, y, width, chars * FONT });
		}
		assertFalse("行が無い", lines.isEmpty());
		return lines;
	}

	private static boolean intersects(final double[] a, final double[] b) {
		return a[0] < b[0] + b[2] - 0.5 && b[0] < a[0] + a[2] - 0.5 && a[1] < b[1] + b[3] - 0.5
				&& b[1] < a[1] + a[3] - 0.5;
	}

	private static void assertNoOverlap(final String page, final double[] fig) {
		for (final double[] line : lines(page)) {
			assertFalse("図と重なる行: " + java.util.Arrays.toString(line) + " 図 " + java.util.Arrays.toString(fig),
					intersects(line, fig));
		}
	}

	/** 図とブロック方向(x)で並ぶ行の数。 */
	private static int linesBeside(final String page, final double[] fig) {
		int n = 0;
		for (final double[] line : lines(page)) {
			if (line[0] < fig[0] + fig[2] - 0.5 && fig[0] < line[0] + line[2] - 0.5) {
				++n;
			}
		}
		return n;
	}

	/** 変換して、各頁の表示リストを頁順に返します。 */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/vertical-page-float/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "vertical-page-float-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		final String[] dumps = new String[pages.length];
		for (int i = 0; i < pages.length; ++i) {
			dumps[i] = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
		}
		return dumps;
	}
}
