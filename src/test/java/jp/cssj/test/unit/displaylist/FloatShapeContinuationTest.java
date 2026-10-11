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
 * A float split across pages resolves its {@code shape-outside} against each fragment's own box, as Chrome does
 * (2026-10-10, NEXT-SESSION §5 item 8c); the continuation fell back to the rectangle.
 */
public class FloatShapeContinuationTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	/**
	 * The triangle of polygon(0 0, 100% 0, 0 100%) on the continuation (70pt of a float of twelve 10pt blocks, 50pt of
	 * which stay on the first page): the second line starts where the triangle of the 70pt box is at its top,
	 * 80 × (1 − 10/70) = 68.57 (Chrome 68.55 for a float of height: 120pt split the same way); it started at the
	 * float's edge (80pt).
	 */
	public void testShapeOnContinuation() throws Exception {
		final StringBuilder words = new StringBuilder();
		for (int i = 1; i <= 40; ++i) {
			words.append(" w").append(i);
		}
		final List<String> pages = convertPages("shape", """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 200pt 100pt; margin: 10pt }
				body { margin: 0; font: 10pt/10pt serif }
				.f { float: left; width: 80pt; shape-outside: polygon(0 0, 100%% 0, 0 100%%) }
				.f div { height: 10pt }
				</style></head><body><div style="height: 30pt"></div><div class="f">%s</div><p>%s</p></body></html>
				""".formatted("<div></div>".repeat(12), words));
		assertEquals("頁数", 2, pages.size());
		final List<double[]> lines = lineStarts(pages.get(1));
		assertEquals("2 頁目の 1 行目", 80, lines.get(0)[0], 0.5);
		assertEquals("2 頁目の 2 行目", 68.57, lines.get(1)[0], 0.5);
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
		final File dir = new File("local/float-shape-continuation/" + name);
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
		}, "float-shape-continuation-" + name, 64L * 1024 * 1024);
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
