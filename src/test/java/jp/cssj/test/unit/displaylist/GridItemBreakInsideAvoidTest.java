package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * {@code break-inside: avoid} on grid and flex items (2026-10-15, TECH-20261015-006 ②). The container called its items'
 * split directly, past the avoid check a block child gets from its parent, so a row crossing the page end was cut
 * through its avoid items. Now the row goes to the next page whole, as a table row with an avoid cell does. Rows without
 * avoid still split at the page end, and an avoid item taller than a page still splits.
 *
 * <p>
 * The page area is 280x380 pt. A 150 pt spacer and rows of 100 pt items with 8 pt gaps put the third row at 366 pt,
 * across the page end.
 * </p>
 */
public class GridItemBreakInsideAvoidTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final double PAGE = 380;

	private static final Pattern FRAME = Pattern
			.compile("y=(-?[\\d.]+) (?:artifact )?AbsoluteRectFrame\\[w=[\\d.]+ h=([\\d.]+)\\]");

	private static String document(final String container, final String item) {
		final StringBuilder items = new StringBuilder();
		for (int i = 0; i < 6; ++i) {
			items.append("<div class='item'>T").append(i).append("</div>");
		}
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="400pt"?>
				<style>
				@page { margin: 10pt }
				body { margin: 0; font: 10pt serif }
				.spacer { height: 150pt }
				.box { %s gap: 8pt }
				.item { background: #ccc; %s }
				</style></head><body><div class="spacer"></div><div class="box">%s</div></body></html>
				""".formatted(container, item, items);
	}

	private static final String GRID = "display: grid; grid-template-columns: 1fr 1fr;";
	private static final String FLEX = "display: flex; flex-wrap: wrap;";
	private static final String FLEX_ITEM = "flex: 0 0 136pt;";

	/** A grid row with avoid items goes to the next page whole. */
	public void testGridRowMovesWhole() throws Exception {
		assertRowMoves("grid", document(GRID, "height: 100pt; break-inside: avoid;"));
	}

	/** The same for a wrapping flex line. */
	public void testFlexLineMovesWhole() throws Exception {
		assertRowMoves("flex", document(FLEX, FLEX_ITEM + " height: 100pt; break-inside: avoid;"));
	}

	/** One avoid item is enough to move its row. */
	public void testOneAvoidItemMovesTheRow() throws Exception {
		final String html = document(GRID, "height: 100pt;").replace("<div class='item'>T4</div>",
				"<div class='item' style='page-break-inside: avoid'>T4</div>");
		assertRowMoves("one", html);
	}

	/** Without avoid the row still splits at the page end (CSS-SUPPORT §8: Chrome moves it, Copper fills the page). */
	public void testRowWithoutAvoidSplits() throws Exception {
		final List<List<double[]>> pages = frames("plain", document(GRID, "height: 100pt;"));
		assertEquals("2頁", 2, pages.size());
		boolean cut = false;
		for (final double[] f : pages.get(0)) {
			cut |= f[1] < 100 - 0.01;
		}
		assertTrue("avoid の無い行は頁の境で割れる", cut);
	}

	/** An avoid item taller than a page still splits instead of moving page after page. */
	public void testAvoidItemTallerThanPageSplits() throws Exception {
		final List<List<double[]>> pages = frames("tall", document(GRID, "height: 500pt; break-inside: avoid;"));
		assertTrue("先へ進む(頁数 " + pages.size() + ")", pages.size() <= 6);
		for (final List<double[]> page : pages) {
			for (final double[] f : page) {
				assertTrue("版面の外に出ない: y=" + f[0] + " h=" + f[1], f[0] + f[1] <= PAGE + 0.01);
			}
		}
	}

	private static void assertRowMoves(final String name, final String html) throws Exception {
		final List<List<double[]>> pages = frames(name, html);
		assertEquals(name + ": 2頁", 2, pages.size());
		assertEquals(name + ": 1頁目は2行", 4, pages.get(0).size());
		for (final double[] f : pages.get(0)) {
			assertEquals(name + ": 1頁目の項目は割れない", 100, f[1], 0.01);
		}
		assertEquals(name + ": 2頁目は3行目の2項目", 2, pages.get(1).size());
		for (final double[] f : pages.get(1)) {
			assertEquals(name + ": 2頁目の頭から", 0, f[0], 0.01);
			assertEquals(name + ": 2頁目の項目も割れない", 100, f[1], 0.01);
		}
	}

	/** Per page, {y, h} of each item frame. */
	private static List<List<double[]>> frames(final String name, final String html) throws Exception {
		final List<List<double[]>> pages = new ArrayList<>();
		for (final String page : convert(name, html)) {
			final List<double[]> frames = new ArrayList<>();
			final Matcher m = FRAME.matcher(page);
			while (m.find()) {
				frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) });
			}
			pages.add(frames);
		}
		return pages;
	}

	private static List<String> convert(final String name, final String html) throws Exception {
		final File dir = new File("local/grid-item-break-inside-avoid/" + name);
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
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "grid-item-avoid-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", files);
		java.util.Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File f : files) {
			pages.add(java.nio.file.Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
