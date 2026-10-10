package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * The spaces at the end of a line do not count in the max-content size (2026-10-10). A flex item sized to its content
 * took the collapsible space before {@code </button>} in, so a button written over several lines came out one space
 * wider than in Chrome, where the rows are as wide as without the spaces (tmp/ab/repro/buttonws/flexws.html: 96.90pt
 * with or without them). Spaces kept by white-space: pre still count.
 */
public class FlexTrailingSpaceTest extends TestCase {
	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+)");
	private static final Pattern BASELINE = Pattern.compile("y=(-?[\\d.]+) Text\\[");

	public void testTrailingSpaces() throws Exception {
		final double plain = width("Open in CodePen");
		assertEquals("spaces around the text", plain, width("\n Open in CodePen\n"), 0.01);
		assertEquals("a space at the end of an inline", plain, width("<span>Open in CodePen </span>"), 0.01);
		assertEquals("spaces after an inline", plain,
				width("<span>Open in <b style=\"font-weight: normal\">CodePen</b>  </span>"), 0.01);
		assertTrue("white-space: pre keeps them", width("<span style=\"white-space: pre\">Open in CodePen  </span>")
				> plain + 1);
	}

	/**
	 * codex's review (2026-10-10): white-space: nowrap leaves them out too (Chrome 24px for "AA "), and the spaces before
	 * an inline end or start with padding stay on the line, as the line layout keeps them, so the text must not wrap; a
	 * negative word-spacing does not take them out twice.
	 */
	public void testNowrapAndInlineEnd() throws Exception {
		final double plain = width("Open in CodePen");
		assertEquals("nowrap", plain, width("<span style=\"white-space: nowrap\">Open in CodePen </span>"), 0.01);
		final String padded = page("<span style=\"padding-right: 9pt\">Open in CodePen </span>");
		final Matcher m = BASELINE.matcher(padded);
		final java.util.Set<String> lines = new java.util.HashSet<>();
		while (m.find()) {
			lines.add(m.group(1));
		}
		assertEquals("one line before an inline end with padding\n" + padded, 1, lines.size());
		final String started = page("Open in CodePen <span style=\"padding-left: 9pt\"></span>");
		final Matcher n = BASELINE.matcher(started);
		lines.clear();
		while (n.find()) {
			lines.add(n.group(1));
		}
		assertEquals("one line before an inline start with padding\n" + started, 1, lines.size());
		// A space far below zero: taken out again at the end of the block, the min-content came out 150pt
		assertTrue("a negative word-spacing",
				width("<span style=\"word-spacing: -150pt\">Open in CodePen </span>") <= plain + 0.01);
	}

	private static double width(final String content) throws Exception {
		final String page = page(content);
		final Matcher m = FRAME.matcher(page);
		assertTrue(page, m.find());
		return Double.parseDouble(m.group(1));
	}

	private static String page(final String content) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 400pt; margin: 20pt } body { margin: 0; font: 12pt/1.5 sans-serif }"
				+ " .o { display: flex; align-items: flex-start } .b { display: flex; border: 1pt solid black; "
				+ "background: #ddd }</style></head><body><div class=\"o\"><div class=\"b\">" + content
				+ "</div></div></body></html>";
		final File dir = Files.createTempDirectory("flex-trailing-space").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}
}
