package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
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
 * A forced split of a grid whose items paint nothing past the cut line splits in the row's blank space (2026-10-28,
 * wild seed 45210105).
 *
 * <p>
 * A float holding a grid of an explicit height taller than the page, in a two-column multicol inside a four-column
 * multicol, was split with {@code FLAGS_SPLIT} on every column. {@code GridBox.split} skipped its slack path for a
 * forced split and forced the empty items instead: they kept only what they paint (nothing), so the remainder row was
 * the whole 122pt row again, and the conversion emitted empty pages without end (300 pages before the fix).
 * </p>
 */
public class GridForcedSlackSplitTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"(T\\d+)\"");

	private static final String NEST = "<div style=\"border: 2pt solid\"><div style=\"column-count: 4\">"
			+ "<div style=\"%1$s: 84pt\"></div><div style=\"column-count: 2\"><div style=\"float: left\">"
			+ "<div style=\"display: grid; %1$s: 122pt; grid-template-columns: 24pt 1fr\"><div>%2$s</div></div></div>"
			+ "<div style=\"writing-mode: %3$s; %1$s: 25pt\">%4$s</div></div></div></div>";

	public GridForcedSlackSplitTest(final String name) {
		super(name);
	}

	public void testHorizontalTerminates() throws Exception {
		final Map<String, Integer> draws = new TreeMap<>();
		final int pages = layout("400pt 120pt", "horizontal-tb", String.format(NEST, "height", "", "vertical-lr", ""),
				draws);
		assertTrue("pages=" + pages, pages <= 3);
	}

	public void testVerticalTerminates() throws Exception {
		final Map<String, Integer> draws = new TreeMap<>();
		final int pages = layout("120pt 400pt", "vertical-lr", String.format(NEST, "width", "", "horizontal-tb", ""),
				draws);
		assertTrue("pages=" + pages, pages <= 3);
	}

	/** Content of the grid and after it is drawn once each. */
	public void testWordsDrawnOnce() throws Exception {
		final Map<String, Integer> draws = new TreeMap<>();
		final int pages = layout("400pt 120pt", "horizontal-tb",
				String.format(NEST, "height", "T1", "vertical-lr", "T2") + "<p>T3</p>", draws);
		assertTrue("pages=" + pages, pages <= 4);
		assertEquals(draws.toString(), "{T1=1, T2=1, T3=1}", draws.toString());
	}

	private static int layout(final String size, final String writingMode, final String body,
			final Map<String, Integer> draws) throws Exception {
		final File dir = Files.createTempDirectory("grid-forced-slack-split").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(),
				"<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: " + size
						+ "; margin: 5pt } body { margin: 0; font: 12pt/1.2 serif; writing-mode: " + writingMode
						+ " } div, p { margin: 0; padding: 0 }</style></head><body>" + body + "</body></html>",
				StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.property("output.page-limit", "20");
			session.property("output.page-limit.abort", "normal");
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		int n = 1;
		for (;; ++n) {
			final File page = new File(dir, String.format("page-%04d.txt", n));
			if (!page.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				draws.merge(m.group(1), 1, Integer::sum);
			}
		}
		return n - 1;
	}
}
