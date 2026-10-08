package jp.cssj.test.unit.displaylist;

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
 * A float broken page by page after the end of the input is progress for the livelock guard (2026-10-09, rubydoc-api).
 * The input position, the cursor and the break target stay the same at every break while the float gives up a page of
 * its content, and the guard took 32 such breaks for a livelock: it stopped breaking pages and laid the rest of the
 * float out in place, down to 22644pt on the 33rd page. A float carrying less to the next page than at the last break
 * has moved on; 1200 lines of 12pt take 150 pages of 200pt, all on the paper.
 */
public class FloatProgressGuardTest extends TestCase {
	private static final Pattern TEXT_Y = Pattern.compile("y=([-\\d.]+) Text\\[\"Paragraph\"");

	public void testLongFloatAtTheEnd() throws Exception {
		final StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 200pt; margin: 0 } body { margin: 0; font: 10pt/12pt serif } p { margin: 0 }"
				+ "</style></head><body><div style=\"float: left; width: 100%\">");
		for (int i = 1; i <= 1200; ++i) {
			html.append("<p>Paragraph ").append(i)
					.append(" of the long floated column, which goes on page after page after the end of the input.</p>");
		}
		html.append("</div></body></html>");
		final File dir = Files.createTempDirectory("float-progress-guard").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		int count = 0;
		for (final File page : pages) {
			final Matcher m = TEXT_Y.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				++count;
				assertTrue(page.getName() + " paragraph below the page at " + m.group(1),
						Double.parseDouble(m.group(1)) + 12 <= 200 + 0.01);
			}
		}
		assertEquals("paragraphs", 1200, count);
		assertEquals("pages", 150, pages.length);
	}
}
