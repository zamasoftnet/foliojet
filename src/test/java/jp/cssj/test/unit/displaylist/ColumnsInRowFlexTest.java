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
 * A vertical row flex on a horizontal page with columns in an item (2026-11-02, triage §159, fit seed 58159393). The
 * columns marked the intrinsic size as inflated, which cut the flex to the height of the paper, as narrower columns
 * would fit; but a row does not narrow its items, so they stood past the box and past the paper, where neither a break
 * nor a rescue saw them. What stood below the paper was lost.
 */
public class ColumnsInRowFlexTest extends TestCase {
	private static final Pattern TOKEN = Pattern.compile("y=([-\\d.]+) (?:artifact )?Text\\[\"(T\\d+)\"");

	public void testItemsPastThePaperReachTheNextPage() throws Exception {
		final File dir = dump("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: 300pt 150pt; margin: 0 }"
				+ "body { margin: 0; font: 8pt/1.2 serif } div, p { margin: 0; padding: 0 }</style></head><body>"
				+ "<div style=\"display: flex; writing-mode: vertical-rl\"><div style=\"flex: none; height: 140pt\">T0</div>"
				+ "<div style=\"flex: none\"><div style=\"column-count: 2; column-gap: 19pt\">"
				+ "<p><span style=\"display: inline-block; width: 20pt; height: 60pt\">T1</span></p>"
				+ "<p><span style=\"display: inline-block; width: 20pt; height: 60pt\">T2</span></p>"
				+ "</div></div></div></body></html>");
		assertTrue(textOnTheSheet(dir, "T1", 150));
		assertTrue(textOnTheSheet(dir, "T2", 150));
	}

	private static boolean textOnTheSheet(final File dir, final String token, final double pageHeight) throws Exception {
		for (final File page : dir.listFiles((d, name) -> name.startsWith("page-"))) {
			final Matcher m = TOKEN.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				final double y = Double.parseDouble(m.group(1));
				if (m.group(2).equals(token) && y >= 0 && y < pageHeight) {
					return true;
				}
			}
		}
		return false;
	}

	private static File dump(final String html) throws Exception {
		final File dir = Files.createTempDirectory("columns-in-row-flex").toFile();
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
