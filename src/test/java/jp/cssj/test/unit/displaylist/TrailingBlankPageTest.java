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
 * Verify that <b>no page is created with nothing to paint</b> (added 2026-07-27).
 *
 * <p>
 * css-break-3 §4.4: "each fragmentainer takes a nonzero amount of content". Measurements of
 * the last remaining defect category in 20,000-seed fuzzing, "blank pages" (18 cases),
 * showed that <b>multiple independent paths</b> reach the same outcome: a page with nothing to paint.
 * Pin down one case for each of <b>two fixed paths</b> here.
 * Both produce <b>a trailing blank page</b>.
 * </p>
 *
 * <ol>
 * <li><b>Nested floats</b> ({@code seed 2434}): when a float's contents are also floats,
 * the old {@code paintsNothingBeyondPage} gave up because {@code getContentSize()} does not count
 * nested floats, and <b>always</b> scheduled a cut. A scheduled cut always creates a page in
 * {@code endFlowBlock}'s float-cutting loop. Twelve of the 18 cases had this shape.</li>
 * <li><b>Column height exceeds available capacity</b> ({@code seed 8986}): column balancing rounds
 * the height up to the float bottom, making the multi-column box longer than the remaining paper.
 * Although nothing is painted in the excess area, automatic inter-block page breaking (interflow)
 * used only <b>box geometry</b> to break the page. One of the 18 cases.</li>
 * </ol>
 *
 * <p>
 * <b>Unfixed remaining paths</b> (sweep {@code seed 15448} and {@code 17726}, two of 20,000 cases):
 * content does overflow the paper, but at an <b>unsplittable position</b>
 * (content overflowing its box, or a block moved whole by {@code page-break-inside:avoid}),
 * so a page break leaves an empty continuation fragment. <b>"Discard continuation fragments
 * that paint nothing" causes body content loss</b>: an empty fragment is not something with nothing
 * to take, but a container that will receive content through source replay.
 * We withdrew this approach after measuring five losses (entire tables inside floats)
 * in a 20,000-seed sweep.
 * </p>
 *
 * <p>
 * <b>Build documents here</b>: external files risk changing the result through relative image references
 * (lessons §6.9h). This is why the selected seeds use no images.
 * </p>
 *
 * <p>
 * <b>Check token preservation as well as page count.</b> Any number of blank pages can be eliminated
 * by discarding content, so page count alone does not detect regressions.
 * </p>
 */
public class TrailingBlankPageTest extends TestCase {
	/** Timeout. Measured runtime is under one second per case. */
	private static final long WATCHDOG_MS = 60_000L;

	public TrailingBlankPageTest(String name) {
		super(name);
	}

	/** Path 1: a float inside a float. */
	private static final String NESTED_FLOAT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="300pt"?>
			<?jp.cssj.property name="output.page-height" value="150pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 11pt/1.2 serif;writing-mode:vertical-lr}
			p,div{margin:0;padding:0}
			</style></head><body>
			<p><span style="display:inline-block;width:187pt;height:65pt">T0</span></p>
			<div style="writing-mode:vertical-lr">
			<div style="float:left;width:125pt">
			<div style="float:right;width:97pt">
			<p>T1</p>
			</div>
			</div>
			<ul style="list-style-position:inside;list-style-type:disc">
			<li>T2</li>
			<li>T3</li>
			</ul>
			</div>
			<p><ruby>T4<rt>T5</rt></ruby><ruby>T6<rt>T7</rt></ruby></p>
			</body></html>
			""";

	/** Path 2: column height exceeds available capacity. */
	private static final String OVERLONG_COLUMN = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="300pt"?>
			<?jp.cssj.property name="output.page-height" value="150pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{margin:0;font:normal 13pt/1.2 serif;writing-mode:vertical-lr}
			p,div{margin:0;padding:0}
			</style></head><body>
			<p><span style="display:inline-block;width:191pt;height:135pt">T0</span></p>
			<div style="column-count:2;column-gap:5pt">
			<div style="margin:4pt;padding:2pt;border:0pt solid black">
			<div style="page-break-inside:avoid;margin:3pt">
			<p>T1</p>
			<p>T2</p>
			<p>T3</p>
			</div>
			<p><ruby>T4<rt>T5</rt></ruby><ruby>T6<rt>T7</rt></ruby><ruby>T8<rt>T9</rt></ruby></p>
			<div style="float:right;width:109pt">
			<p>T10</p>
			</div>
			</div>
			<div style="clear:both"><span style="font-size:35pt">T11</span></div>
			</div>
			</body></html>
			""";

	public void testNestedFloatMakesNoBlankPage() throws Exception {
		assertNoBlankPage("nested-float", NESTED_FLOAT, 8);
	}

	public void testOverlongColumnMakesNoBlankPage() throws Exception {
		assertNoBlankPage("overlong-column", OVERLONG_COLUMN, 12);
	}

	/**
	 * Convert and check that (1) no page has an empty display list, and (2) every token
	 * T0..T(n-1) appears on some page.
	 *
	 * @param name      working directory name
	 * @param html      document
	 * @param tokenCount number of T tokens in the document
	 */
	private static void assertNoBlankPage(final String name, final String html, final int tokenCount)
			throws Exception {
		final File dir = new File("local/trailing-blank/" + name);
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
		}, "trailing-blank-" + name, 64L * 1024 * 1024);
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
		java.util.Arrays.sort(pages);

		final List<Integer> blanks = new ArrayList<>();
		final StringBuilder all = new StringBuilder();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
			all.append(dump);
			boolean drew = false;
			for (final String line : dump.split("\n")) {
				final String t = line.trim();
				if (!t.isEmpty() && !t.startsWith("drawer")) {
					drew = true;
				}
			}
			if (!drew) {
				blanks.add(i + 1);
			}
		}
		assertTrue(name + ": 白紙ページ " + blanks + " (全" + pages.length + "ページ)", blanks.isEmpty());

		// Discarding content can also eliminate blank pages. Make that visible as a regression.
		final List<String> lost = new ArrayList<>();
		for (int i = 0; i < tokenCount; ++i) {
			if (all.indexOf("T" + i) < 0) {
				lost.add("T" + i);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
	}
}
