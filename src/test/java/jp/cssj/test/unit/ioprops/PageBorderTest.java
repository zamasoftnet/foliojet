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
 * {@code @page} の {@code border} と {@code padding}(css-page-3 §3.1)の試験です(2026-09-03)。
 * 余白の内側に枠線と内側余白を取り、版面はその内側になる。
 */
public class PageBorderTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static String html(final String pageExtra) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:100mm 60mm;margin:10mm;" + pageExtra + "}body{margin:0;font-size:12pt}"
				+ "</style></head><body><p style=\"margin:0\">本文 ALPHA</p></body></html>";
	}

	public void testBorderAndPaddingShrinkThePageArea() throws Exception {
		final String plain = convert(html("")).text("pages/0001.json");
		final String framed = convert(html("border:3pt solid #ff0000;padding:5pt")).text("pages/0001.json");
		final double x0 = firstTextX(plain);
		final double x1 = firstTextX(framed);
		// 余白 10mm=28.35pt に枠線 3pt と内側余白 5pt が足される
		assertEquals("the page area starts inside the border and padding", x0 + 8, x1, 0.01);
	}

	public void testBorderIsDrawn() throws Exception {
		final String svg = convert(html("border:3pt solid #ff0000")).text("pages/0001.svg");
		assertTrue("the page border must be painted in red: " + svg, svg.contains("#ff0000"));
		final String plain = convert(html("")).text("pages/0001.svg");
		assertFalse(plain.contains("#ff0000"));
	}

	/** 最初の文字列の左端(run の transform の平行移動 + bounds の x)。 */
	private static double firstTextX(final String pageJson) {
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("\"transform\":\\[[^\\]]*?,([-0-9.]+),[-0-9.]+\\],\"bounds\":\\[([-0-9.]+),").matcher(pageJson);
		assertTrue("a text run is expected: " + pageJson, m.find());
		return Double.parseDouble(m.group(1)) + Double.parseDouble(m.group(2));
	}

	private CapturingResults convert(final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			session.property("output.default-font-family", "'Noto Serif JP'");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///page-border.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results;
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
			// 何もしない
		}

		String text(final String uri) {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return out.toString(StandardCharsets.UTF_8);
		}
	}
}
