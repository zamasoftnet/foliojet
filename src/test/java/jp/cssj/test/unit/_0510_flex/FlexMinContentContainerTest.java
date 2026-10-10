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
 * A flex container sized by its own {@code width: min-content} takes the flex intrinsic sizes (CSS Flexbox §9.9.1), as
 * a nested one does (2026-10-10; the widths are Chrome's). Laid out on a scratch page at width 0 instead, its items
 * shrank to what the flex algorithm let them: an item with {@code flex: 0 0 200px} kept its 200px.
 */
public class FlexMinContentContainerTest extends TestCase {
	private static final Pattern OUTLINE = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=[\\d.]+\\] outline\\[");

	public void testFixedBasisInWrappingRow() throws Exception {
		// A wrapping row asks the largest min-content contribution, without the basis: Chrome 60px (it was 200px)
		assertEquals(45, width("flex-wrap: wrap", "flex: 0 0 200px"), 0.1);
	}

	public void testMaxContentItem() throws Exception {
		// Chrome 130px, the item's max-content and the tail (it was 120px, the tail overlapping)
		assertEquals(97.5, width("", "width: max-content; flex: none"), 0.1);
	}

	public void testMinContentItem() throws Exception {
		// Chrome 70px (it was 120px)
		assertEquals(52.5, width("", "width: min-content; flex: none"), 0.1);
	}

	public void testIntrinsicMinWidthOverMaxWidth() throws Exception {
		// Chrome 130px (it was 40px)
		assertEquals(97.5, width("", "min-width: max-content; max-width: 40px"), 0.1);
	}

	public void testOwnMaxWidthUnderIntrinsicMinWidth() throws Exception {
		// Its own max-width does not cap the content its min-width: max-content takes: Chrome 130px
		assertEquals(97.5, width("min-width: max-content; max-width: 40px", ""), 0.1);
	}

	public void testOwnMaxWidthStillCapsParent() throws Exception {
		// The root's content before its own sizes is for its own sizing only: an inline-block holding a width:
		// max-content flex container with max-width: 40px is 40px wide, as in Chrome
		final Matcher m = OUTLINE.matcher(convert("<div style=\"display: inline-block; outline: 1px solid blue\">"
				+ "<div style=\"display: flex; width: max-content; max-width: 40px; overflow: hidden\"><i></i><i></i></div>"
				+ "</div>"));
		assertTrue(m.find());
		assertEquals(30, Double.parseDouble(m.group(1)), 0.1);
	}

	/** The outlined width of a width: min-content flex container holding an item with two 60px boxes, and a 10px tail. */
	private static double width(final String container, final String item) throws Exception {
		final Matcher m = OUTLINE.matcher(convert("<div style=\"display: flex; width: min-content; align-items: flex-start; "
				+ "outline: 1px solid blue; " + container + "\"><div style=\"" + item + "\"><i></i> <i></i></div>"
				+ "<div style=\"flex: none; width: 10px; height: 10px; background: #000\"></div></div>"));
		assertTrue(m.find());
		return Double.parseDouble(m.group(1));
	}

	private static String convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("flex-min-content-container").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300px 300px; margin: 10px } body { margin: 0; font-size: 0 }"
				+ " i { display: inline-block; width: 60px; height: 10px; background: #ccc }"
				+ "</style></head><body>" + body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		return Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
	}
}
