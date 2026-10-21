package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
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
 * The remainder of a float split at the page start may come out larger than the float was, and still be split again
 * (2026-10-21, FIT seed 30125121, triage §92). A vertical-rl column flex of width 119pt wraps its items into lines
 * about 290pt wide; in vertical-lr it does not fit beside the paragraph before it and moves whole into the float's
 * remainder, where it is laid out again at the width of its lines. The remainder grew from 396.2pt to 557.7pt, the
 * guard against remainders rebuilt at the same size every page took that for no progress, and the whole remainder
 * (the flex and the 20 paragraphs after it) was placed on page 2, off the paper.
 */
public class FloatGrownRemainderSplitTest extends TestCase {
	private static final String FLEX = "<div style=\"display: flex; writing-mode: vertical-rl; width: 119pt;"
			+ " flex-direction: column; flex-wrap: %s; gap: .5em\"><p>T1</p>"
			+ "<div style=\"flex: 1 0 auto; min-width: 8em; max-width: 90%%\"><p>T2</p></div>"
			+ "<div style=\"flex: 0 0 8em\"><p>T3</p></div>"
			+ "<div style=\"flex: 0 2 auto; width: 8em; min-width: 8em; max-width: 90%%\"><p>T4</p></div></div>";

	public void testWrappingFlex() throws Exception {
		assertAllOnThePaper(String.format(FLEX, "wrap"));
	}

	public void testNowrapFlex() throws Exception {
		assertAllOnThePaper(String.format(FLEX, "nowrap"));
	}

	private static void assertAllOnThePaper(final String flex) throws Exception {
		final StringBuilder tail = new StringBuilder();
		for (int i = 5; i < 25; ++i) {
			tail.append("<p>T").append(i).append("</p>");
		}
		final List<String> pages = convert("<div style=\"float: right\"><p>T0</p>" + flex + tail + "</div>");
		final TreeSet<Integer> seen = new TreeSet<>();
		for (int i = 0; i < pages.size(); ++i) {
			final Matcher m = Pattern.compile(" x=(-?[\\d.]+) y=[\\d.]+ Text\\[\"T(\\d+)\"").matcher(pages.get(i));
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1));
				assertTrue("T" + m.group(2) + " on the paper of page " + (i + 1) + ": x=" + x, x >= 0 && x < 120);
				seen.add(Integer.parseInt(m.group(2)));
			}
		}
		assertEquals("every paragraph drawn", 25, seen.size());
	}

	private static List<String> convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("float-grown-remainder").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 120pt 400pt; margin: 0 } body { margin: 0; font: 11pt/1.2 serif;"
				+ " writing-mode: vertical-lr } p, div { margin: 0; padding: 0 }</style></head><body>" + body
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
