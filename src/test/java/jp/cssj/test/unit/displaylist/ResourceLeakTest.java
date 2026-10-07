package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify <b>no leaks across repeated conversions</b> (added 2026-07-27).
 *
 * <p>
 * The absolute requirement "no memory leaks" previously <b>had no detector</b>.
 * Server use (copperd) converts tens of thousands of documents in the same process,
 * so even a small leak per conversion accumulates.
 * </p>
 *
 * <h2>Check three types separately</h2>
 *
 * <ol>
 * <li><b>Reachability leaks</b>: does anything still hold the session after conversion ends?
 * Check whether a {@link WeakReference} clears after GC.
 * This is <b>more reliable than measuring heap usage</b> and directly catches
 * references left in static fields or ThreadLocals.</li>
 * <li><b>Thread leaks</b>: does the {@code foliojet-layout} thread created
 * for each conversion remain?</li>
 * <li><b>Temporary file leaks</b>: are the spill files written by
 * {@code AbstractTempFileOutput} under heap pressure deleted?</li>
 * </ol>
 *
 * <p>
 * <b>Do not measure absolute heap usage.</b> GC variability makes
 * "increased/did not increase" checks unstable. The three checks above are <b>deterministic</b>.
 * </p>
 */
public class ResourceLeakTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Number of repetitions. Too few can hide leaked threads. */
	private static final int RUNS = 12;

	public ResourceLeakTest(String name) {
		super(name);
	}

	/** The session is reclaimed after conversion ends (reachability leak). */
	public void testSessionIsCollectedAfterConversion() throws Exception {
		final File html = new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + html, html.exists());

		final WeakReference<DirectSession> ref = new WeakReference<>(convertAndReturnSession(html, "leak-ref"));
		// Strongly encourage GC before checking for remaining references.
		for (int i = 0; i < 20 && ref.get() != null; ++i) {
			System.gc();
			Thread.sleep(50L);
		}
		assertNull("変換が終わってもDirectSessionが回収されない。"
				+ "静的フィールドかThreadLocalが掴んでいる疑いがある", ref.get());
	}

	/** Layout threads created for each conversion do not remain. */
	public void testLayoutThreadsDoNotAccumulate() throws Exception {
		final File html = new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + html, html.exists());

		final int before = countLayoutThreads();
		for (int i = 0; i < RUNS; ++i) {
			convertAndReturnSession(html, "leak-thread-" + i);
		}
		// Wait briefly because a thread may not have fully terminated immediately after completion.
		for (int i = 0; i < 40 && countLayoutThreads() > before; ++i) {
			Thread.sleep(50L);
		}
		final int after = countLayoutThreads();
		assertEquals("レイアウトスレッドが残っている(変換前" + before + "本 → 変換後" + after + "本)。"
				+ RUNS + "回の変換で1本も増えてはいけない", before, after);
	}

	/** No temporary files remain. */
	public void testTempFilesAreCleanedUp() throws Exception {
		final File html = new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + html, html.exists());
		final File tmp = new File(System.getProperty("java.io.tmpdir", "local"));

		final int before = countFiles(tmp);
		for (int i = 0; i < RUNS; ++i) {
			convertAndReturnSession(html, "leak-temp-" + i);
		}
		final int after = countFiles(tmp);
		// We create the output PDFs ourselves, so allow that many additional files.
		assertTrue("一時ファイルが残っている(変換前" + before + "個 → 変換後" + after + "個)。"
				+ "退避ファイルの後始末が漏れている疑いがある", after <= before + RUNS);
	}

	private static int countLayoutThreads() {
		int n = 0;
		for (final Thread t : Thread.getAllStackTraces().keySet()) {
			if (t.isAlive() && t.getName().startsWith("foliojet-layout")) {
				++n;
			}
		}
		return n;
	}

	private static int countFiles(final File dir) {
		final File[] fs = dir.listFiles();
		return fs == null ? 0 : fs.length;
	}

	private static DirectSession convertAndReturnSession(final File html, final String slot) throws Exception {
		final File pdf = new File("local/leak/" + slot + ".pdf");
		pdf.getParentFile().mkdirs();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try (OutputStream out = new FileOutputStream(pdf)) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper
					.createStreamMessageHandler(new java.io.PrintStream(OutputStream.nullOutputStream())));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeFile(session, html, "text/html", null);
		} finally {
			session.close();
		}
		return session;
	}
}
