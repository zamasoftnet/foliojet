package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A box whose block flow runs the other way on the same axis (vertical-lr in a vertical-rl page) is not cut where
 * the page's rest is too narrow for it: it moves whole to the next page, as in Chrome (2026-10-09, sweep defect R,
 * seed 12679054). In a multicol of 3 or more columns, the column balancing cut it into pieces narrower than the rest
 * of the page, and its text at its start (left) was drawn at x=-34, off the page.
 */
public class ReversedBlockFlowBreakTest extends TestCase {
	public void testTwoColumns() throws Exception {
		assertMovesWhole("column-count: 2");
	}

	public void testFourColumns() throws Exception {
		assertMovesWhole("column-count: 4");
	}

	public void testNoColumns() throws Exception {
		assertMovesWhole("background: #fee");
	}

	/**
	 * At the start of the page, where moving makes no progress, a box larger than the page is cut, as in Chrome: kept
	 * whole, its start ran 80pt off the page (2026-10-09, after 85272cf8; seed 12555259).
	 */
	public void testLargerThanThePageIsCutAtThePageStart() throws Exception {
		final List<String> pages = convert("<div style=\"writing-mode: vertical-lr\">"
				+ "<div style=\"width: 60pt\">B0</div><div style=\"width: 60pt\">B1</div><div style=\"width: 60pt\">B2</div>"
				+ "</div><p>END</p>");
		assertEquals("pages", 2, pages.size());
		for (final String page : pages) {
			final Matcher m = Pattern.compile(" x=(-?[\\d.]+) y=[\\d.]+ Text\\[\"(\\w+)").matcher(page);
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1));
				assertTrue(m.group(2) + " on the page: " + x, x >= 0 && x < 100);
			}
		}
	}

	private static void assertMovesWhole(final String container) throws Exception {
		final List<String> pages = convert("<div style=\"width: 70pt\">PRE</div><div style=\"" + container
				+ "\"><div style=\"writing-mode: vertical-lr; min-width: 64pt\">BOX</div></div><p>END</p>");
		assertEquals("pages", 2, pages.size());
		assertFalse("not on the first page", pages.get(0).contains("Text[\"BOX\""));
		assertTrue("at the start of the second page: " + pages.get(1),
				Pattern.compile("x=36\\.00 y=[\\d.]+ Text\\[\"BOX\"").matcher(pages.get(1)).find());
	}

	private static List<String> convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("reversed-block-flow-break").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 120pt 400pt; margin: 10pt } body { margin: 0; font: 8pt/1.2 serif;"
				+ " writing-mode: vertical-rl }</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] dumps = dir.listFiles((d, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		Arrays.sort(dumps);
		final List<String> pages = new ArrayList<>();
		for (final File dump : dumps) {
			pages.add(Files.readString(dump.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
