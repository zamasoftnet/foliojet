package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
 * Verify that <b>a split cell's content appears in the head fragment rather than the continuation fragment</b>
 * (added 2026-07-27).
 *
 * <p>
 * <b>What happened.</b> {@code TableCellBox.split} passed the cut position to cell content as
 * "the row's physical split line - {@code verticalAlign}". Since {@code verticalAlign} comes from
 * <b>the difference between the finalized cell height and the content height</b>, a cell much taller
 * than its content due to {@code rowspan} or a tall adjacent cell could have
 * <b>alignment space alone extend beyond the cut line</b>. Then no content unit remained in the head
 * fragment: <b>only the frame appeared on the preceding page, with the text on the next</b>.
 * To the reader, this looked like a row with an empty cell.
 * </p>
 *
 * <p>
 * <b>This is not specific to rowspan.</b> Fuzzing discovered it (invariant 7, "reading order is preserved",
 * seed 130 and others). All 12 observed cases involved {@code rowspan} cells, but the essential conditions
 * are <b>"cell height &gt; content height" and "the row splits"</b>.
 * The first document below reproduces the same failure with {@code rowspan} <b>absent</b>:
 * a tall neighboring cell suffices. The second document is the {@code rowspan} version.
 * </p>
 *
 * <p>
 * <b>Output before the fix</b> (first document): page 1 had {@code P1 B1 B2}
 * (only the frame for cell A); page 2 had {@code A B3 B4 B5}. {@code A}, which precedes
 * {@code B2} in document order, appeared on a <b>later page</b>.
 * </p>
 *
 * <p>
 * <b>Do not use external document files</b>: a relative image reference previously caused
 * an hour of misdiagnosis (lessons §6.9h). Build the documents here.
 * </p>
 */
public class SplitCellValignReadingOrderTest extends TestCase {
	public SplitCellValignReadingOrderTest(String name) {
		super(name);
	}

	/** Display-list text. Extract it the same way as {@code RandomDocumentFuzzTest}. */
	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("(?:Text|RubyUnit)\\[\"([^\"]*)\"(?: ruby=\"([^\"]*)\")?");

	/** Timeout. Measured runtime is under one second. */
	private static final long WATCHDOG_MS = 60_000L;

	private static final String HEAD = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="60pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 12pt/1.2 serif}
			p,div,td{margin:0;padding:0}
			table{border-collapse:separate;table-layout:fixed}
			td{border:1pt solid black;%s}
			</style></head><body>
			""";

	/**
	 * No rowspan. The single-line {@code A} cell stretches to about 104 pt due to its seven-line neighbor.
	 * The split line is about 36 pt from the row start; the roughly 45 pt alignment space from
	 * the default {@code vertical-align} (middle) alone extends beyond it.
	 * The cell's {@code page-break-inside:auto} allows splitting even when the row is not at the page top
	 * (the default avoid moves the entire row to the next page, so this defect is not reached).
	 */
	private static final String HTML_NO_ROWSPAN = String.format(HEAD, "page-break-inside:auto") + """
			<p>P1</p>
			<table><tbody>
			<tr><td>A</td><td>B1<br>B2<br>B3<br>B4<br>B5<br>B6<br>B7</td></tr>
			</tbody></table>
			</body></html>
			""";

	/** Document order. {@code A} precedes {@code B1}. */
	private static final String[] ORDER_NO_ROWSPAN = { "P1", "A", "B1", "B2", "B3", "B4", "B5", "B6", "B7" };

	/**
	 * Rowspan version. {@code A} spans two rows, making its height several times the content height.
	 * The split occurs inside the second row.
	 */
	private static final String HTML_ROWSPAN = String.format(HEAD, "") + """
			<p>P1</p>
			<table><tbody>
			<tr><td rowspan="2">A</td><td>B1<br>B2</td></tr>
			<tr><td>C1<br>C2<br>C3<br>C4<br>C5</td></tr>
			</tbody></table>
			</body></html>
			""";

	private static final String[] ORDER_ROWSPAN = { "P1", "A", "B1", "B2", "C1", "C2", "C3", "C4", "C5" };

	public void testSplitCellContentStaysInTheFirstFragment() throws Exception {
		check("no-rowspan", HTML_NO_ROWSPAN, ORDER_NO_ROWSPAN);
		check("rowspan", HTML_ROWSPAN, ORDER_ROWSPAN);
	}

	private static void check(final String name, final String html, final String[] orderedTokens) throws Exception {
		final File dir = new File("local/split-cell-valign/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "split-cell-valign-" + name);
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

		// The **first page on which each token appears**. Ignore within-page painting order, which is an implementation detail
		// (spanning cells are painted after the rows they span are finalized).
		final Map<String, Integer> firstPage = new HashMap<String, Integer>();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
			final Matcher m = TEXT_IN_DUMP.matcher(dump);
			while (m.find()) {
				final Integer page = Integer.valueOf(i);
				firstPage.putIfAbsent(m.group(1), page);
				if (m.group(2) != null) {
					firstPage.putIfAbsent(m.group(2), page);
				}
			}
		}

		// No content is lost. Checking order alone lets a "disappeared" regression pass.
		for (final String token : orderedTokens) {
			assertNotNull(name + ": " + token + "が消えた", firstPage.get(token));
		}
		// Reading order: a token earlier in document order must not appear on a later page than a subsequent token.
		int prev = -1;
		String prevToken = null;
		for (final String token : orderedTokens) {
			final int at = firstPage.get(token).intValue();
			assertTrue(name + ": 読み順が入れ替わった: 文書順では" + prevToken + "→" + token + " だが、" + token + "はページ"
					+ (at + 1) + "、" + prevToken + "はページ" + (prev + 1), at >= prev);
			prev = at;
			prevToken = token;
		}
	}
}
