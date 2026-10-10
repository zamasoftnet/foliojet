package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
 * The sizes boxes keep across pages (2026-10-10, NEXT-SESSION §5 item 8); expected values measured in Chrome
 * (print, the same pages).
 *
 * <ul>
 * <li>A box with {@code height: auto} and {@code max-height} stops at the max-height in all, its fragments sharing it,
 * and the rest of its content overflows: a float went on with all its content (30 + 80 + 50pt for a 60pt max).</li>
 * <li>A split float's continuation resolves its {@code shape-outside} against its own box (it fell back to the
 * rectangle).</li>
 * </ul>
 */
public class FragmentContinuationSizeTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	/** 200x100pt pages with 10pt margins, a 50pt block, then {@code body} (16 lines of 10pt in .f). */
	private static String document(final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 200pt 100pt; margin: 10pt }
				body { margin: 0; font: 10pt/10pt serif }
				%s
				</style></head><body><div style="height: 50pt"></div>%s</body></html>
				""".formatted(style, body);
	}

	private static String lines(final int count) {
		final StringBuilder lines = new StringBuilder();
		for (int i = 1; i <= count; ++i) {
			lines.append("<div>L").append(i).append("</div>");
		}
		return lines.toString();
	}

	/** max-height: 60pt split at 30pt: 30pt on each page, the last lines overflow onto the third (as in Chrome). */
	public void testMaxHeightSharedByFragments() throws Exception {
		for (final String floating : new String[] { "float: left; ", "" }) {
			final List<String> pages = convertPages("max-" + (floating.isEmpty() ? "block" : "float"),
					document(".f { " + floating + "width: 50pt; max-height: 60pt; background: #ccc }",
							"<div class=\"f\">" + lines(16) + "</div><p>after</p>"));
			assertEquals(floating + "頁数", 3, pages.size());
			assertEquals(floating + "1 頁目の箱", 30, height(pages.get(0), 50), 0.01);
			assertEquals(floating + "2 頁目の箱", 30, height(pages.get(1), 50), 0.01);
			assertTrue(floating + "3 頁目に箱は無い:\n" + pages.get(2), height(pages.get(2), 50) <= 0.01);
			assertEquals(floating + "3 頁目の最初の行", 0, y(pages.get(2), "L12"), 0.01);
		}
	}

	/** With 4pt padding and a 2pt border, the second fragment holds the 36pt left and the end frame (Chrome 40.5). */
	public void testMaxHeightWithFrame() throws Exception {
		final List<String> pages = convertPages("max-frame", document(
				".f { float: left; width: 50pt; max-height: 60pt; padding: 4pt; border: 2pt solid #000 }",
				"<div class=\"f\">" + lines(16) + "</div>"));
		assertEquals("1 頁目の箱", 30, height(pages.get(0), 62), 0.01);
		assertEquals("2 頁目の箱(残りの 36pt と下の枠)", 42, height(pages.get(1), 62), 0.01);
	}

	/** A max-height the content does not reach changes nothing: 30 + 80 + 50pt. */
	public void testMaxHeightNotReached() throws Exception {
		final List<String> pages = convertPages("max-200", document(
				".f { float: left; width: 50pt; max-height: 200pt; background: #ccc }",
				"<div class=\"f\">" + lines(16) + "</div>"));
		assertEquals(30, height(pages.get(0), 50), 0.01);
		assertEquals(80, height(pages.get(1), 50), 0.01);
		assertEquals(50, height(pages.get(2), 50), 0.01);
	}

	/**
	 * The triangle of polygon(0 0, 100% 0, 0 100%) on the continuation (70pt of a 120pt float): the second line starts
	 * where the triangle of the 70pt box is at its top (Chrome 68.55); it started at the float's edge (80pt).
	 */
	public void testShapeOnContinuation() throws Exception {
		final StringBuilder words = new StringBuilder();
		for (int i = 1; i <= 40; ++i) {
			words.append(" w").append(i);
		}
		final List<String> pages = convertPages("shape", document(
				".f { float: left; width: 80pt; height: 120pt; shape-outside: polygon(0 0, 100% 0, 0 100%) }"
						+ " body > div:first-child { height: 30pt !important }",
				"<div class=\"f\"></div><p>" + words + "</p>"));
		assertEquals("頁数", 2, pages.size());
		final List<double[]> lines = lineStarts(pages.get(1));
		assertEquals("2 頁目の 1 行目", 80, lines.get(0)[0], 0.5);
		assertEquals("2 頁目の 2 行目", 68.57, lines.get(1)[0], 0.5);
	}

	/** The height of the first frame of the given width on a page (0 if none). */
	private static double height(final String page, final double width) {
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			if (Math.abs(Double.parseDouble(m.group(3)) - width) < 0.01) {
				return Double.parseDouble(m.group(4));
			}
		}
		return 0;
	}

	private static double y(final String page, final String text) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(2));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	/** The x of the first word of each line (by y), in order. */
	private static List<double[]> lineStarts(final String page) {
		final java.util.TreeMap<Double, Double> starts = new java.util.TreeMap<>();
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
			starts.merge(y, x, Math::min);
		}
		final List<double[]> lines = new ArrayList<>();
		starts.forEach((y, x) -> lines.add(new double[] { x, y }));
		return lines;
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/fragment-continuation-size/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "fragment-continuation-size-" + name, 64L * 1024 * 1024);
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
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
