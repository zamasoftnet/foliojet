package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
 * A token with no break opportunity that is exactly as long as the line builder lets one text run grow (10001 glyphs,
 * or a multiple of it) converts like any other length. The builder closed the text itself with the run's last glyph,
 * and the shaper's end of run then found no open text: a NullPointerException that aborted the whole conversion. A
 * long URL or a base64 string in a document is enough to get there.
 *
 * <p>
 * Each document is KEEP, the token of x and AFTER in three paragraphs: the conversion finishes, every x is drawn and
 * the paragraph after the token is laid out.
 * </p>
 */
public class LongTextRunTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
			+ "@page { size: 300pt 400pt; margin: 0 } body { margin: 0; font: 10pt/12pt serif } p { margin: 0 }"
			+ "</style></head><body>";

	public void testRunOf10000() throws Exception {
		assertRunLaidOut(10000, "");
	}

	/** The length at which the builder closes the run itself, with nothing after it. */
	public void testRunOf10001() throws Exception {
		assertRunLaidOut(10001, "");
	}

	public void testRunOf10002() throws Exception {
		assertRunLaidOut(10002, "");
	}

	public void testRunOf20000() throws Exception {
		assertRunLaidOut(20000, "");
	}

	public void testRunOf20001() throws Exception {
		assertRunLaidOut(20001, "");
	}

	/** Two full runs: the second one is closed with the token's last glyph. */
	public void testRunOf20002() throws Exception {
		assertRunLaidOut(20002, "");
	}

	/** The token in a 100pt wide box, which it overflows on one line. */
	public void testRunOf10001InNarrowBox() throws Exception {
		assertRunLaidOut(10001, "width: 100pt");
	}

	public void testRunOf20002InNarrowBox() throws Exception {
		assertRunLaidOut(20002, "width: 100pt");
	}

	/** The token broken into lines of the 100pt wide box: every line break starts a new run, and nothing is lost. */
	public void testRunOf10001BrokenIntoLines() throws Exception {
		assertRunLaidOut(10001, "width: 100pt; overflow-wrap: break-word");
	}

	private static void assertRunLaidOut(final int length, final String style) throws Exception {
		final List<String> pages = layout(
				"<p>KEEP</p><p style=\"" + style + "\">" + "x".repeat(length) + "</p><p>AFTER</p>");
		assertFalse("no page for a token of " + length, pages.isEmpty());
		boolean keep = false, after = false;
		int drawn = 0;
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				final String text = m.group(1);
				keep |= text.equals("KEEP");
				after |= text.equals("AFTER");
				for (int i = 0; i < text.length(); ++i) {
					if (text.charAt(i) == 'x') {
						++drawn;
					}
				}
			}
		}
		assertTrue("KEEP before a token of " + length, keep);
		assertEquals("glyphs of the token drawn", length, drawn);
		assertTrue("AFTER, the paragraph after a token of " + length, after);
	}

	/** Converts the document and returns the display list dump of each page. */
	private static List<String> layout(final String body) throws Exception {
		final File dir = Files.createTempDirectory("long-text-run").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), HEAD + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> pages = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			pages.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
