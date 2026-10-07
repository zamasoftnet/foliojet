package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that <b>blocks taller than 14400 pt can paginate</b> (added 2026-08-17).
 *
 * <p>
 * The initial {@code max-height} value was the UA's {@code getMaxSize()} (=14400 pt),
 * so {@code AbstractBlockBox} <b>clamped</b> taller blocks to this value.
 * 14400 pt is the PDF <b>paper</b> dimension limit, not a box height limit.
 * </p>
 *
 * <h2>Mechanism</h2>
 *
 * <p>
 * A clamped box no longer recognizes that it does not fit on the paper, even though it still overflows.
 * If it starts partway down a page, it moves to the next page, where the same decision recurs,
 * so <b>page breaks repeat without consuming a single line of content</b>.
 * The progress guard ({@code ContinuationStats.guardBreakProgress}) abandons automatic page breaks
 * on the 32nd attempt, and subsequent content is lost: measured with this document,
 * 201 pages became 34. In real documents, this broke the glossary table in
 * {@code files/realworld/w3c-jlreq}; the builder state after abandonment caused
 * {@code NullPointerException}, <b>failing the entire conversion</b>.
 * </p>
 *
 * <h2>About the checks</h2>
 *
 * <p>
 * Require <b>that the guard does not fire</b>, as well as checking page count.
 * Once the mechanism that "gives up after 32 attempts and moves on" handles a livelock,
 * content is already lost. This detects the problem closer to its mechanism than a page-count check.
 * </p>
 */
public class TallBlockPaginationTest extends TestCase {
	/** Timeout. Measured runtime is under 5 seconds. */
	private static final long WATCHDOG_MS = 120_000L;

	/** Row count. Each row is 90 pt plus borders, so the table exceeds 18,000 pt, definitely beyond the 14400 pt limit. */
	private static final int ROWS = 200;

	public TallBlockPaginationTest(final String name) {
		super(name);
	}

	/**
	 * A table over 18,000 pt tall starting partway down the page (150 pt into a 180 pt content area).
	 * Before the fix, content ended at page 34 and the guard fired once.
	 */
	public void testTableTallerThanPdfPageLimitPaginates() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 10pt/1.2 serif;margin:0}
				table{border-collapse:collapse}
				td{border:1pt solid black;padding:0}
				</style></head><body>
				<div style="height:150pt">TOP</div>
				<table>
				""");
		for (int i = 0; i < ROWS; ++i) {
			html.append("<tr><td>R").append(i).append("</td><td><div style=\"height:90pt\">C").append(i)
					.append("</div></td></tr>\n");
		}
		html.append("</table>\n<p>TAIL</p>\n</body></html>\n");

		final long alarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		final int pages = convert("tall-block-pagination", html.toString());

		assertEquals("14400ptより高いブロックで前進保証ガードが発火した(内容が失われる)", alarms,
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
		// Each row is 90 pt and the page content area is 180 pt. Rows are laid out across page boundaries, so
		// page count is comparable to row count. If clamping returns, it drops to about 34 pages.
		assertTrue("表の内容が最後まで組まれていない(ページ数=" + pages + ")", pages >= ROWS);
	}

	/**
	 * <b>Conversion completes even with a livelock</b> (2026-08-17).
	 *
	 * <p>
	 * Explicitly setting `max-height` above the page content height still causes livelock
	 * after fixing the initial value. Since a `max-height` larger than the paper is meaningless
	 * for printing, content loss itself is considered the document's responsibility.
	 * <b>Crashes or hangs are the implementation's responsibility</b>, however,
	 * so verify completion without exceptions even when the guard fires.
	 * </p>
	 *
	 * <p>
	 * In a real document (w3c-jlreq), the abandoned `TextBuilder` received `INLINE_END`
	 * without a start and characters without a font, causing `NullPointerException`
	 * and failing the entire conversion. A synthetic document does not yet reproduce
	 * the same crash; reproduction steps are recorded in `the development records`.
	 * </p>
	 */
	public void testLivelockDegradesWithoutFailing() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 8pt/1.2 serif;margin:0}
				table{border-collapse:collapse;max-height:500pt}
				td,th{border:1pt solid black;padding:0}
				</style></head><body>
				<div style="height:150pt">TOP</div>
				<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody>
				""");
		for (int i = 0; i < ROWS; ++i) {
			html.append("<tr><td>R").append(i)
					.append("</td><td><p lang=\"en\">Definition ").append(i)
					.append(" with an <a href=\"#x\">inline link</a> and <span>a span</span> inside.</p>")
					.append("<div style=\"height:90pt\">C").append(i).append("</div></td></tr>\n");
		}
		html.append("</tbody></table>\n");
		html.append("<section><h2><span>References</span><a href=\"#r\"></a></h2>\n");
		for (int i = 0; i < 20; ++i) {
			html.append("<p>Trailing ").append(i)
					.append(" with <a href=\"#z\">a link</a> and <span>a <b>nested</b> span</span>.</p>\n");
		}
		html.append("</section>\n</body></html>\n");

		final long alarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		// 2026-08-23: rejecting source replay reentry and disabling replay on entire-table MOVE
		// (SourceReplayer/TableBox) eliminated this livelock itself.
		// Pagination now works correctly without firing the guard (204 pages,
		// each row exactly once). Previously, the expected result was the guard's degraded output
		// (34 pages with overflowing placement). The same fix also restored the Trailing paragraph after the table,
		// so pin down both the rows and subsequent content below.
		final int pages = convert("livelock-degrade", html.toString());
		assertTrue("ページが出ていない", pages > 0);
		assertEquals("ライブロックガードが発火した(解消済みのはず)", alarms,
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
		// No rows are lost or duplicated.
		final java.util.Map<String, Integer> count = new java.util.HashMap<>();
		int trailing = 0;
		int references = 0;
		final File dir = new File("local/livelock-degrade");
		for (final File f : dir.listFiles((d, name) -> name.endsWith(".txt"))) {
			final String text = java.nio.file.Files.readString(f.toPath());
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(R\\d+)\"").matcher(text);
			while (m.find()) {
				count.merge(m.group(1), 1, Integer::sum);
			}
			trailing += occurrences(text, "Text[\"Trailing\"");
			references += occurrences(text, "Text[\"References\"");
		}
		for (int i = 0; i < ROWS; ++i) {
			final Integer c = count.get("R" + i);
			assertNotNull("R" + i + " が消失した", c);
			assertEquals("R" + i + " が複製された", 1, c.intValue());
		}
		assertEquals("表の後のTrailing段落が消失または複製された", 20, trailing);
		assertEquals("表の後の見出しが消失または複製された", 1, references);
	}

	private static int occurrences(final String text, final String needle) {
		int count = 0;
		for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
			++count;
		}
		return count;
	}

	/**
	 * Convert and return the display-list page count.
	 */
	private static int convert(final String name, final String html) throws Exception {
		final File dir = new File("local/" + name);
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
		}, name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		return pages.length;
	}
}
