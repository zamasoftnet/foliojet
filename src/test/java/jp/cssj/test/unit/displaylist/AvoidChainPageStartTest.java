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
 * A chain of margin-less blocks joined by {@code page-break-before: avoid} fills its pages (2026-10-09). The avoid
 * pushback of the body probes the block before the chain just before its end (FLAGS_AVOID_PROBE). At the start of the
 * page that block had no break keeping the avoids or widows, and left its first line alone on the page instead, page
 * after page (jp-wikisource: 41 18 1 1 38 4 1 39 38 lines). Now it stays whole and the body relaxes its avoid. The
 * page breaks are Chrome's.
 */
public class AvoidChainPageStartTest extends TestCase {
	private static final Pattern WORD = Pattern.compile("Text\\[\"([DT])(\\d\\d)");
	private static final Pattern LINE = Pattern.compile("Text\\[\"L(\\d\\d)");

	/** A paragraph before the chain: its last 2 lines (widows) go with the chain once, and only once. */
	public void testParagraphBeforeChainIsNotLeftLineByLine() throws Exception {
		final StringBuilder body = new StringBuilder("<p>HEAD</p><p>");
		for (int i = 0; i < 15; ++i) {
			body.append(String.format("%sL%02d line of the paragraph.", i == 0 ? "" : "<br>", i));
		}
		body.append("</p>");
		for (int i = 0; i < 30; ++i) {
			body.append(String.format("<dl><dt>T%02d</dt><dd>D%02d body</dd></dl>", i, i));
		}
		final List<String> pages = convert(body.toString());
		assertEquals("pages", 5, pages.size());
		final int[] linePage = new int[15];
		for (int p = 0; p < pages.size(); ++p) {
			final Matcher m = LINE.matcher(pages.get(p));
			while (m.find()) {
				linePage[Integer.parseInt(m.group(1))] = p;
			}
		}
		assertEquals("L00-L12 on page 1", 0, linePage[12]);
		assertEquals("L13 and L14 on page 2", 1, linePage[13]);
		assertEquals("L13 and L14 on page 2", 1, linePage[14]);
		assertTrue("the chain starts on page 2", pages.get(1).contains("Text[\"T00"));
	}

	/** Each dl at the start of a page stays whole: 4 pages. */
	public void testChainOfDefinitionListsFillsPages() throws Exception {
		final StringBuilder body = new StringBuilder("<p>HEAD</p>");
		for (int i = 0; i < 30; ++i) {
			body.append(String.format("<dl><dt>T%02d</dt><dd>D%02d body</dd></dl>", i, i));
		}
		final List<String> pages = convert(body.toString());
		assertEquals("pages", 4, pages.size());
		final int[] termPage = new int[30];
		final int[] descPage = new int[30];
		Arrays.fill(termPage, -1);
		Arrays.fill(descPage, -1);
		final int[] perPage = new int[pages.size()];
		for (int p = 0; p < pages.size(); ++p) {
			final Matcher m = WORD.matcher(pages.get(p));
			while (m.find()) {
				final int i = Integer.parseInt(m.group(2));
				if (m.group(1).equals("T")) {
					termPage[i] = p;
					++perPage[p];
				} else {
					descPage[i] = p;
				}
			}
		}
		for (int i = 0; i < 30; ++i) {
			assertTrue("article " + i + " is drawn", termPage[i] >= 0);
			assertEquals("dd keeps with its dt: article " + i, termPage[i], descPage[i]);
		}
		for (int p = 0; p < pages.size() - 1; ++p) {
			assertTrue("page " + (p + 1) + " is filled: " + Arrays.toString(perPage), perPage[p] >= 9);
		}
	}

	private static List<String> convert(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 0 } body { margin: 0; font: 10pt/15pt serif }"
				+ " p, dl, dd, dt { margin: 0 } dl, dd { page-break-before: avoid }</style></head><body>" + body
				+ "</body></html>";
		final File dir = Files.createTempDirectory("avoid-chain-page-start").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] dumps = dir.listFiles((d, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		assertNotNull(dumps);
		Arrays.sort(dumps);
		final List<String> pages = new ArrayList<>();
		for (final File dump : dumps) {
			pages.add(Files.readString(dump.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
