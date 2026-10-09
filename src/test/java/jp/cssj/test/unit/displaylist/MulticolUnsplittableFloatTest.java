package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A float that column balancing cannot cut (in the other writing direction, orthogonal or replaced) sets the least
 * column height of its multicol, as such a box in the flow does (2026-10-09, sweep defect R2, seed 12679054). Column
 * balancing counted only the flow: a multicol holding only floats got the least column height and stayed where the
 * page's rest was too small for its floats, which ran off the page. Now the multicol is cut at the page or moves to
 * the next page: an orthogonal float moves whole, as in Chrome; a float in the other direction on the same axis is
 * cut at the page like any float (Chrome moves it).
 */
public class MulticolUnsplittableFloatTest extends TestCase {
	public void testReversedFloatInVerticalColumns() throws Exception {
		final List<String> pages = convert("size: 120pt 400pt", "writing-mode: vertical-rl",
				"<div style=\"width: 70pt\">PRE</div><div style=\"column-count: 4\"><div style=\"float: right;"
						+ " writing-mode: vertical-lr; min-width: 64pt\">FL</div></div><p>END</p>");
		assertEquals("pages", 2, pages.size());
		assertTrue("drawn", (pages.get(0) + pages.get(1)).contains("Text[\"FL\""));
		for (final String page : pages) {
			assertFalse("nothing left of the page: " + page, page.contains("x=-"));
		}
	}

	public void testOrthogonalFloatInHorizontalColumns() throws Exception {
		final List<String> pages = convert("size: 400pt 120pt", "writing-mode: horizontal-tb",
				"<div style=\"height: 70pt\">PRE</div><div style=\"column-count: 2\"><div style=\"float: right;"
						+ " writing-mode: vertical-rl; min-height: 64pt\">FL</div></div><p>END</p>");
		assertMoved(pages);
	}

	private static void assertMoved(final List<String> pages) {
		assertEquals("pages", 2, pages.size());
		assertFalse("not on the first page", pages.get(0).contains("Text[\"FL\""));
		assertTrue("on the second page", pages.get(1).contains("Text[\"FL\""));
		assertFalse("nothing left of or above the page: " + pages.get(1), pages.get(1).contains("=-"));
	}

	private static List<String> convert(final String size, final String mode, final String body) throws Exception {
		final File dir = Files.createTempDirectory("multicol-unsplittable-float").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { " + size
				+ "; margin: 10pt } body { margin: 0; font: 8pt/1.2 serif; " + mode + " }</style></head><body>" + body
				+ "</body></html>", StandardCharsets.UTF_8);
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
