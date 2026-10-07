package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Verify that <b>column balancing (column-fill: balance) never makes columns smaller
 * than children with an orthogonal writing direction</b> (introduced 2026-10-03, sweep seed 11587843).
 *
 * <p>
 * Orthogonal children (horizontal writing in vertical columns or vertical writing in horizontal columns)
 * are atomic under the pagination contract and cannot split at column boundaries.
 * However, capacity search ({@code Container.getCutPointBelow}) returned the child's <b>line boundaries</b>
 * on a different axis as cut points. {@code ColumnBalancer} then chose a column capacity
 * smaller than the child and used it directly as the box size:
 * </p>
 * <ul>
 * <li>Vertical columns (vertical-rl) became <b>narrower</b> than the child. The child aligned
 * to the column's right edge (block start) and overflowed leftward. With vertical-lr body text,
 * the columns sat at the paper's left edge, drawing text outside (x&lt;0): x=−26.90 with two columns
 * and x=−62.09 with four (Chrome: 0 for both).</li>
 * <li>Horizontal columns became <b>shorter</b> than the child, so the following paragraph overlapped it
 * (after an 88 pt-tall child, the paragraph was at y=73.42; without columns, 100.32).</li>
 * </ul>
 *
 * <p>
 * Fix in {@code FlowContainer.balancePageSizeFloor}: extend the floor, previously limited to
 * same-axis opposite-progression children (RL⇄LR, 2026-08-22), to all children with a writing direction
 * different from the columns, traversing inside same-direction children too.
 * </p>
 */
public class MulticolOrthogonalFloorTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(T\\d+)\"");

	public MulticolOrthogonalFloorTest(String name) {
		super(name);
	}

	private static String document(final String bodyWritingMode, final String body) {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="595pt"?>
				<?jp.cssj.property name="output.page-height" value="842pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:0pt}
				body{margin:0;font:normal 11pt/1.2 serif;writing-mode:%s}
				div,p{margin:0;padding:0}
				</style></head><body>
				%s
				</body></html>
				""".formatted(bodyWritingMode, body);
	}

	private static final String HORIZONTAL_CHILD = "<div style=\"writing-mode:horizontal-tb;width:88pt\">T0<br>T1</div>";

	/**
	 * Horizontal-writing child inside vertical columns. Text starts at the paper's left edge (x=0) regardless of
	 * column count.
	 */
	public void testVerticalColumnsAreNotNarrowerThanHorizontalChild() throws Exception {
		for (final int count : new int[] { 2, 3, 4 }) {
			final String html = document("vertical-lr", "<div style=\"writing-mode:vertical-rl\"><div style=\"column-count:"
					+ count + "\">" + HORIZONTAL_CHILD + "</div></div>");
			final String dump = convert("vertical-c" + count, html);
			assertEquals(count + " 段: T0 の x(Chrome は 0)", 0.0, x(dump, "T0"), 0.01);
			assertEquals(count + " 段: T1 の x", 0.0, x(dump, "T1"), 0.01);
		}
	}

	/** The same holds with an extra div wrapper sharing the writing direction. */
	public void testNestedHorizontalChildIsFound() throws Exception {
		final String html = document("vertical-lr", "<div style=\"writing-mode:vertical-rl\"><div style=\"column-count:2\"><div>"
				+ HORIZONTAL_CHILD + "</div></div></div>");
		assertEquals("T0 の x(Chrome は 0)", 0.0, x(convert("vertical-nested", html), "T0"), 0.01);
	}

	/** Original document that stopped the sweep (all 930 unreduced bytes). Chrome gives x=30.0. */
	public void testSweepSeed11587843() throws Exception {
		final String html = document("vertical-lr", """
				<div style="display:list-item;position:static;float:left;writing-mode:vertical-rl;">
				<div style="column-count:4;column-gap:4pt">
				<div style="writing-mode:horizontal-tb;width:min-content;min-width:8em;max-width:90%;">
				<ul style="list-style-position:outside;list-style-type:none">
				<li>T0</li>
				<li>T1</li>
				</ul>
				</div>
				</div>
				</div>""");
		final String dump = convert("seed-11587843", html);
		assertEquals("T0 の x(Chrome は 30.0)", 30.0, x(dump, "T0"), 0.01);
	}

	/** Vertical-writing child inside horizontal columns (88 pt tall). The following paragraph does not overlap it. */
	public void testHorizontalColumnsAreNotShorterThanVerticalChild() throws Exception {
		final String html = document("horizontal-tb", "<div style=\"column-count:2\">"
				+ "<div style=\"writing-mode:vertical-rl;height:88pt\">T2<br>T3</div></div><p>T5</p>");
		final double y = y(convert("horizontal-c2", html), "T5");
		assertTrue("T5 が縦書きの子(高さ 88pt)に重なっている: y=" + y, y >= 88);
	}

	private static double x(final String dump, final String token) {
		return position(dump, token)[0];
	}

	private static double y(final String dump, final String token) {
		return position(dump, token)[1];
	}

	private static double[] position(final String dump, final String token) {
		final Matcher m = TEXT_IN_DUMP.matcher(dump);
		while (m.find()) {
			if (m.group(3).equals(token)) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		throw new AssertionError(token + " が描かれていない:\n" + dump);
	}

	/** Convert a one-page document and return its page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/multicol-orthogonal-floor/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-orthogonal-floor-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertEquals(name + ": 頁数", 1, pages.length);
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
