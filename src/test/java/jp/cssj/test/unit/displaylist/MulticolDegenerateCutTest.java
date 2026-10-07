package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
 * Verify that <b>multi-column layout does not livelock on cuts that take no content</b>
 * (introduced 2026-07-27).
 *
 * <p>
 * <b>What happened.</b>Page breaks crossing multi-column layout ({@code columnSpanning}) do not
 * cut the frame between fragments, so the continuation <b>inherits the entire start frame</b>.
 * Applying this with the cut line at or before the inner start edge ({@code pageLimit <= 0})
 * leaves the previous fragment empty and reconstructs the continuation with <b>identical geometry</b>.
 * The next page makes the same decision, endlessly generating one blank page at a time.
 * Pages remain in {@code PDFWriterImpl.pageOutputs}, so heap usage grows monotonically
 * until {@code OutOfMemoryError}.
 * </p>
 *
 * <p>
 * <b>Why the existing guard missed it.</b>{@code ContinuationStats.guardBreakProgress}
 * compares <b>only with the immediately preceding iteration</b>, detecting only period-1 repetition.
 * Measurements showed this document <b>alternated between two states</b>
 * (ingest=12/depth=7 and ingest=12/depth=4), resetting the consecutive counter to 0 each time.
 * Five of six fuzz cases with this cause had period 1 and hit the guard;
 * this explains why <b>only the worst case escaped</b>.
 * </p>
 *
 * <p>
 * <b>About this document.</b>60x60 pt paper with 10 pt margins (=40x40 pt content),
 * containing a div with 22 pt margins; the 44 pt frame alone consumes the content area.
 * Reduced from a 1.2 KB fuzz document; vertical writing, images, inline-blocks, and
 * the second multi-column container were all <b>irrelevant</b>.
 * Before the fix: 9,273 pages then {@code OutOfMemoryError}. After: 16 pages in 2 seconds
 * (completes even with a 128 MB heap).
 * </p>
 *
 * <p>
 * <b>Keep the document inline</b> because relative image paths previously caused an hour
 * of misdiagnosis (lessons learned §6.9h). Build it here.
 * </p>
 */
public class MulticolDegenerateCutTest extends TestCase {
	public MulticolDegenerateCutTest(String name) {
		super(name);
	}

	/**
	 * Multi-column layout whose frame (22 pt margin×2=44 pt) consumes the content area (40 pt).
	 */
	private static final String HTML = """
			<?jp.cssj.property name="output.page-width" value="60pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><style>
			@page{margin:10pt}
			body{margin:0;font:normal 13pt/1.2 serif}
			p,div{margin:0;padding:0}
			</style></head><body>
			<div style="margin:22pt"><div style="column-count:2"><p>T0</p><p>T1</p></div></div>
			</body></html>
			""";

	/**
	 * Page-count limit. Before the fix, 9,273 pages were produced; even with ample headroom
	 * over the measured 16 pages, the difference is <b>three orders of magnitude</b>.
	 * A regression reliably fails here.
	 */
	private static final int MAX_PAGES = 64;

	/**
	 * Timeout. A regression runs for minutes before OOM (3 min 32 sec with a 1 GB heap),
	 * so monitor it in a worker thread <b>to avoid affecting the entire build</b>.
	 * Allow headroom over the measured 2 seconds.
	 */
	private static final long WATCHDOG_MS = 60_000L;

	public void testDegenerateColumnCutTerminates() throws Exception {
		final File dir = new File("local/multicol-degenerate");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File html = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
			w.write(HTML);
		}

		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
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
					CTISessionHelper.transcodeFile(session, html, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-degenerate");
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse("変換が" + WATCHDOG_MS / 1000 + "秒で終わらない(ライブロックの退行)", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない", pages);
		assertTrue("ページが1枚も出ていない", pages.length > 0);
		assertTrue("ページ数が過大 " + pages.length + " (修正前は9273ページでOOM)", pages.length <= MAX_PAGES);

		// Content remains. Checking only page count would let a regression that emits nothing pass.
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			all.append(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		assertTrue("T0が消えた", all.indexOf("T0") >= 0);
		assertTrue("T1が消えた", all.indexOf("T1") >= 0);
	}
}
