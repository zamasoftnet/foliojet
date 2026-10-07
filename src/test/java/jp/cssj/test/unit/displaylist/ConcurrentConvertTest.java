package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>Converts the same document concurrently</b> to check for failures that do not occur in isolation
 * (added on 2026-07-26).
 *
 * <p>
 * This engine is intended to handle multiple conversions concurrently on a server ({@code copperd}),
 * so <b>concurrent execution matches production conditions</b>.
 * All existing validation, however, converted individual documents sequentially and fundamentally
 * could not exercise concurrency-only defects. Indeed, parallelizing the random-generation sweep
 * to 22 threads found <b>documents that succeeded alone but failed only under concurrency</b>.
 * </p>
 *
 * <p>
 * The default runs the bundled fixture a few times. For sweeps, any document can be exercised with
 * {@code -Dfoliojet.concurrentDoc=<path> -Dfoliojet.concurrentRuns=N
 * -Dfoliojet.concurrentThreads=T}.
 * </p>
 */
public class ConcurrentConvertTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	public ConcurrentConvertTest(String name) {
		super(name);
	}

	/**
	 * Default regression: concurrent conversion of a document with tables, multi-column layout, and floats
	 * does not fail.
	 */
	public void testConcurrentConversionIsStable() throws Exception {
		final String doc = System.getProperty("foliojet.concurrentDoc");
		final int runs = Integer.parseInt(System.getProperty("foliojet.concurrentRuns", "48"));
		final int threads = Integer.parseInt(System.getProperty("foliojet.concurrentThreads",
				String.valueOf(Math.max(2, Runtime.getRuntime().availableProcessors() - 2))));
		final File file = doc != null ? new File(doc)
				: new File("files/unittest/0495-span/rowspan-crosses-rowgroup.html");
		assertTrue("文書がない: " + file, file.exists());

		final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		final AtomicInteger next = new AtomicInteger(0);
		final Thread[] workers = new Thread[threads];
		for (int w = 0; w < threads; ++w) {
			final int id = w;
			workers[w] = new Thread(() -> {
				for (;;) {
					final int i = next.getAndIncrement();
					if (i >= runs) {
						return;
					}
					try {
						convert(file, "c" + id);
					} catch (final Throwable t) {
						failures.add(t);
					}
				}
			}, "concurrent-convert-" + w);
			workers[w].setDaemon(true);
			workers[w].start();
		}
		for (final Thread t : workers) {
			t.join(180_000L);
			assertFalse("並行変換が終わらない", t.isAlive());
		}
		if (!failures.isEmpty()) {
			final StringBuilder sb = new StringBuilder();
			sb.append(file).append(" を ").append(threads).append("スレッドで").append(runs).append("回変換して ")
					.append(failures.size()).append("件失敗。最初の例外:\n");
			final Throwable first = failures.get(0);
			for (Throwable c = first; c != null; c = c.getCause()) {
				sb.append("  ").append(c).append('\n');
				for (int i = 0; i < Math.min(c.getStackTrace().length, 8); ++i) {
					sb.append("      ").append(c.getStackTrace()[i]).append('\n');
				}
				if (c.getCause() == c) {
					break;
				}
			}
			fail(sb.toString());
		}
	}

	private static void convert(final File html, final String slot) throws Exception {
		final File pdf = new File("local/concurrent/" + slot + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				// Sending concurrent-conversion warnings to System.err across 16 threads × hundreds of runs
				// clogs Gradle's message path, preventing workers from exiting
				// (encountered on 2026-07-26; a harness issue, not an engine issue).
				// Discard to NULL_DEVICE: a custom lambda handler caused
				// conversion failures (2026-07-26).
				// Sending large numbers of warnings to System.err clogs Gradle's message path,
				// preventing workers from exiting (encountered on 2026-07-26).
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(
						new java.io.PrintStream(java.io.OutputStream.nullOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, html, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
