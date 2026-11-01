package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The wrapper of a {@code display: table} past floats (2026-11-02, strict seed 58059903, 768 pages where Chrome has 4).
 * The table sits past the floats inside its wrapper, so the wrapper holds the space it was pushed by; moved whole, it
 * carried that extent on as a definite size, and each page left an empty box and the rest of the size.
 */
public class TableWrapperExtentTest extends TestCase {
	private static final String DOCUMENT = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page { size: 60pt 60pt; margin: 5pt } body { margin: 0; font: 12pt/1.2 serif; writing-mode: %s }"
			+ "</style></head><body>%s</body></html>";

	/** Floats wider than the page in three columns, then the table, after a float that takes the page first. */
	private static String body(final String pageSize, final String pageMin, final String crossMode) {
		return "<div style=\"float: left; " + pageMin + ": 8em\">T7</div><div style=\"border: 1pt solid black\">"
				+ "<div style=\"column-count: 3\"><div style=\"float: right; writing-mode: " + crossMode
				+ "\"><input /></div><div style=\"float: right\"><div style=\"margin: 1pt\"><div style=\"writing-mode: "
				+ crossMode + "; " + pageSize + ": 123pt\"></div></div></div><div style=\"display: table; " + pageSize
				+ ": 21pt\">T11</div></div></div>";
	}

	private static void assertFewPagesWithTheTable(final File dir) throws Exception {
		final File[] pages = dir.listFiles((d, name) -> name.startsWith("page-"));
		assertTrue(pages.length + " pages", pages.length <= 6);
		int tables = 0;
		for (final File page : pages) {
			if (Files.readString(page.toPath(), StandardCharsets.UTF_8).contains("Text[\"T11\"")) {
				++tables;
			}
		}
		assertEquals(1, tables);
	}

	public void testVerticalWriting() throws Exception {
		assertFewPagesWithTheTable(dump(String.format(DOCUMENT, "vertical-rl", body("width", "min-width", "horizontal-tb"))));
	}

	public void testHorizontalWriting() throws Exception {
		assertFewPagesWithTheTable(dump(String.format(DOCUMENT, "horizontal-tb", body("height", "min-height", "vertical-rl"))));
	}

	/** The size of an aspect-ratio is the wrapper's own: a table split across pages keeps what is left of it. */
	public void testAspectRatioOfASplitTable() throws Exception {
		final File dir = dump("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: 240pt 200pt; margin: 0 }"
				+ "body { margin: 0; font: 10pt/10pt serif } table { width: 100pt; border-spacing: 0; aspect-ratio: 1 / 2 }"
				+ "tr { page-break-inside: avoid } td { height: 100pt; padding: 0; vertical-align: top }</style></head><body>"
				+ "<table><tr><td>T1</td></tr><tr><td>T2</td></tr><tr><td>T3</td></tr></table><div>T9</div></body></html>");
		assertTrue(Files.readString(new File(dir, "page-0002.txt").toPath(), StandardCharsets.UTF_8).contains("Text[\"T9\""));
	}

	private static File dump(final String html) throws Exception {
		final File dir = Files.createTempDirectory("table-wrapper-extent").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return dir;
	}
}
