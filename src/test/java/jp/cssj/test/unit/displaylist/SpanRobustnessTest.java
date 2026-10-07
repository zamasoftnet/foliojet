package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
 * Verify that conversion terminates without exceptions even when table {@code colspan}/{@code rowspan}
 * values are abnormal (added 2026-07-25).
 *
 * <p>
 * Regression tests for two P0-level defects found by static analysis in an independent review
 * (codex, robustness fixture design). Negative, zero, and non-numeric values were already normalized,
 * but <b>huge positive values alone passed through unchecked</b>.
 * </p>
 *
 * <ul>
 * <li>{@code colspan="2147483647"}: {@code IncrementalTableBuilder} adds {@code CellContent}
 * once per spanned column, resulting in about 2.1 billion additions and exhausting memory
 * before termination.</li>
 * <li>{@code rowspan="2147483647"}: with {@code border-collapse: collapse},
 * {@code borderRow + rowspan - 1} in {@code CollapsedBorderRules.streamSpacing}
 * overflows int to a negative value, causing {@code IndexOutOfBoundsException}
 * at {@code List.get(negative value)}.</li>
 * </ul>
 *
 * <p>
 * The fix clamps values in {@code StyleBuilder} to the HTML Standard limits
 * (colspan 1000 / rowspan 65534), matching real browser behavior.
 * </p>
 *
 * <p>
 * <b>Includes a watchdog</b>: before the fix, conversion "never ended" rather than "failed",
 * so the test itself hangs unless it is stopped by a time limit.
 * </p>
 */
public class SpanRobustnessTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Time limit per document. Normally finishes in under one second. */
	private static final long WATCHDOG_MS = 60_000L;

	public SpanRobustnessTest(String name) {
		super(name);
	}

	public void testMaxColspanTerminates() throws Exception {
		convertWithin("max-colspan.html");
	}

	public void testMaxRowspanWithCollapsedBorders() throws Exception {
		convertWithin("max-rowspan.html");
	}

	public void testInvalidSpansAreNormalized() throws Exception {
		convertWithin("invalid-span.html");
	}

	/**
	 * When {@code rowspan} exceeds the actual table row count (2026-07-25, found by random document generation),
	 * {@code TableCollapsedBorders.getHBorder}/{@code getVBorder} returned null with
	 * {@code border-collapse: collapse}, causing {@code NullPointerException}
	 * in {@code CollapsedBorderRules.gridSpacing}.
	 */
	public void testRowspanBeyondRowCount() throws Exception {
		convertWithin("over-rowspan.html");
		convertWithin("over-rowspan-sep.html");
		convertWithin("ragged-collapse.html");
	}

	/**
	 * Conversion does not throw even for malformed image-map {@code area} elements
	 * (2026-07-25, found by independent review). With {@code shape="default"}, omitted shape/coords,
	 * unknown shapes, or insufficient coordinates, an {@code Area} without a shape was registered as is,
	 * causing {@code NullPointerException} in {@code createTransformedShape(null)}.
	 */
	public void testImageMapWithDegenerateAreas() throws Exception {
		convertWithin("image-map.html");
	}

	/**
	 * Convert the document on a separate thread and verify that it finishes without exceptions
	 * within {@link #WATCHDOG_MS}.
	 */
	private static void convertWithin(final String name) throws Exception {
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(name);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "span-robustness-" + name);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		if (worker.isAlive()) {
			// The pre-fix colspan case reaches here (never finishes).
			fail(name + ": " + WATCHDOG_MS + "ms以内に変換が終わりませんでした");
		}
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わりました", failure[0]);
		}
	}

	private static void convert(final String name) throws Exception {
		final File pdf = new File("local/unittest/span-robustness/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, new File("files/unittest/0490-robustness/" + name), "text/html",
						null);
			} finally {
				session.close();
			}
		}
		assertTrue(name + ": PDFが出力されていません", pdf.length() > 0);
	}
}
