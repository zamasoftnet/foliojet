package jp.cssj.test.unit.container_queries;

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
 * The inline size of a query container is estimated from the styles before layout (2026-10-11): one pass gives the
 * same answer as several. A block of auto width fills its containing block less its margins, borders and padding,
 * percentages resolve, the root sits in the configured page area; a float of auto width is not known, and a query
 * against it does not hold. The page is 300pt wide; the query asks for 200pt exactly (199pt to 201pt).
 */
public class ContainerSizeEstimateTest extends TestCase {
	private static final String QUERY = ".c { container-type: inline-size } .t::before { content: \"no\" } "
			+ "@container (min-width: 199pt) and (max-width: 201pt) { .t::before { content: \"yes\" } }";

	/** 300pt less 20pt + 20pt of padding and a 10pt border is 250pt; 20% of it as a margin leaves 200pt. */
	private static final String PADDED = "<div style=\"padding: 0 20pt; border-left: 10pt solid\"><div class=\"c\" "
			+ "style=\"margin-right: 20%\"><div class=\"t\"></div></div></div>";

	public void testAutoWidthInPadding() throws Exception {
		assertEquals("yes", text(PADDED, "1", "0"));
	}

	public void testPercentageWidth() throws Exception {
		assertEquals("yes", text("<div style=\"width: 250pt\"><div class=\"c\" style=\"width: 80%\"><div class=\"t\">"
				+ "</div></div></div>", "1", "0"));
	}

	public void testBorderBoxWidth() throws Exception {
		assertEquals("yes", text("<div class=\"c\" style=\"width: 220pt; padding: 0 10pt; box-sizing: border-box\">"
				+ "<div class=\"t\"></div></div>", "1", "0"));
	}

	public void testStretchedColumnItem() throws Exception {
		// A flex item stretched across a column fills it: 300pt less 50pt of padding each side
		assertEquals("yes", text("<div style=\"display: flex; flex-direction: column; padding: 0 50pt\"><div class=\"c\">"
				+ "<div class=\"t\"></div></div></div>", "1", "0"));
	}

	public void testFloatOfColumnItemIgnored() throws Exception {
		// float does not apply to a flex item: stretched across the column, it fills it
		assertEquals("yes", text("<div style=\"display: flex; flex-direction: column; padding: 0 50pt\"><div class=\"c\" "
				+ "style=\"float: left\"><div class=\"t\"></div></div></div>", "1", "0"));
	}

	public void testFloatNotKnown() throws Exception {
		// Sized by its content in layout: not estimated, the query does not hold (Chrome: it would if 200pt wide)
		assertEquals("no", text("<div class=\"c\" style=\"float: left\"><div class=\"t\"></div><div style=\"width: "
				+ "200pt\"></div></div>", "1", "0"));
	}

	public void testAlgorithmSizedNotKnown() throws Exception {
		// A 200pt flex item shrinks in a row (Chrome 150pt), a column that wraps splits its width, a block in a table
		// sits in an anonymous cell: not estimated, the query does not hold
		assertEquals("no", text("<div style=\"display: flex\"><div class=\"c\" style=\"width: 200pt; min-width: 0\">"
				+ "<div class=\"t\"></div></div><div style=\"width: 200pt; min-width: 0\"></div></div>", "1", "0"));
		assertEquals("no", text("<div style=\"display: flex; flex-flow: column wrap; height: 20pt; padding: 0 50pt\">"
				+ "<div class=\"c\" style=\"flex: none; height: 20pt\"><div class=\"t\"></div></div>"
				+ "<div style=\"flex: none; height: 20pt\"></div></div>", "1", "0"));
		assertEquals("no", text("<div style=\"display: table; width: 200pt\"><div class=\"c\"><div class=\"t\"></div>"
				+ "</div></div>", "1", "0"));
	}

	public void testDocumentPageNotConsulted() throws Exception {
		// The configured margins are 36pt, the document's @page has none: the root is estimated in the configured page
		// area (228pt) as media queries take it, the container's 14pt margins leave 200pt (laid out 272pt wide)
		for (final String passes : new String[] { "1", "3" }) {
			assertEquals(passes, "yes", text("<style>@page { margin: 0 }</style><div class=\"c\" "
					+ "style=\"margin: 0 14pt\"><div class=\"t\"></div></div>", passes, "36pt"));
		}
	}

	public void testSameInSeveralPasses() throws Exception {
		assertEquals("yes", text(PADDED, "3", "0"));
	}

	/** The text of the query's generated content. */
	private static String text(final String body, final String passes, final String margins) throws Exception {
		final File dir = Files.createTempDirectory("container-size-estimate").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "body { margin: 0 } " + QUERY + "</style></head><body>" + body + "</body></html>",
				StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.property("output.page-width", "300pt");
			session.property("output.page-height", "300pt");
			session.property("output.page-margins", margins);
			session.property("processing.pass-count", passes);
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String list = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		return list.contains("Text[\"yes\"") ? "yes" : list.contains("Text[\"no\"") ? "no" : list;
	}
}
