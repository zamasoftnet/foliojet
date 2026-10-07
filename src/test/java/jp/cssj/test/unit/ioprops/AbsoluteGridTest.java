package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Tests for absolutely positioned grid/Flex containers (E-3, 2026-09-02).
 *
 * <p>
 * User report (2026-08-30): a grid with {@code position:absolute} fell back to a normal block
 * instead of forming a grid and emitted warning 2823. Absolutely positioning the type area
 * within the sheet is a standard print pattern, so it cannot be excluded. Support was added
 * by wrapping an anonymous static grid box inside the absolutely positioned box.
 * </p>
 */
public class AbsoluteGridTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static String html(final String display) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
				+ "@page{size:300pt 200pt;margin:0}body{margin:0;font-size:10pt}"
				+ ".box{position:absolute;left:20pt;top:30pt;width:200pt;height:100pt;display:" + display
				+ ";grid-template-columns:1fr 1fr;column-gap:10pt;border:1pt solid #000;padding:5pt}"
				+ ".box>div{border:1pt solid #999}"
				+ "</style></head><body>"
				+ "<div class=\"box\"><div>AAA</div><div>BBB</div></div>"
				+ "</body></html>";
	}

	private record Run(double x, double y, String text) {
	}

	/** In a two-column grid, AAA and BBB align at the same height, with BBB in the right column. */
	public void testAbsolutelyPositionedGridLaysOutColumns() throws Exception {
		final List<Run> runs = runs(convert(html("grid")));
		final Run a = find(runs, "AAA"), b = find(runs, "BBB");
		assertEquals("both items must sit on the same row", a.y, b.y, 1.0);
		// Content width 200 pt, columns (200-10)/2=95 pt. BBB starts around 20+1+5+95+10=131 pt.
		assertTrue("BBB must be in the second column: A x=" + a.x + " B x=" + b.x, b.x - a.x > 90);
		assertTrue("the grid must sit at the absolute position: A x=" + a.x, a.x > 20 && a.x < 40);
		assertTrue("the grid must sit at the absolute position: A y=" + a.y, a.y > 30 && a.y < 55);
	}

	/** An absolutely positioned Flex container uses the same wrapper for layout. */
	public void testAbsolutelyPositionedFlexLaysOutInline() throws Exception {
		final List<Run> runs = runs(convert(html("flex")));
		final Run a = find(runs, "AAA"), b = find(runs, "BBB");
		assertEquals("both items must sit on the same row", a.y, b.y, 1.0);
		assertTrue("BBB must follow AAA on the row: A x=" + a.x + " B x=" + b.x, b.x > a.x + 10);
	}

	private static Run find(final List<Run> runs, final String text) {
		for (final Run r : runs) {
			if (r.text.equals(text)) {
				return r;
			}
		}
		throw new AssertionError(text + " must be drawn: " + runs);
	}

	private static List<Run> runs(final String svg) {
		final List<Run> runs = new ArrayList<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("<text x=\"([^\"]*)\" y=\"([^\"]*)\"[^>]*data-copper-text=\"([^\"]*)\"").matcher(svg);
		while (m.find()) {
			runs.add(new Run(Double.parseDouble(m.group(1).trim().split("\\s+")[0]),
					Double.parseDouble(m.group(2).trim().split("\\s+")[0]), m.group(3)));
		}
		return runs;
	}

	private String convert(final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///absolute-grid.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results.text("pages/0001.svg");
	}

	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final String uri = metadata.getURI().toString();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(uri, out);
			this.order.add(uri);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// Do nothing.
		}

		String text(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toString(StandardCharsets.UTF_8);
		}
	}
}
