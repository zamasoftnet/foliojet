package jp.cssj.test.unit._3090_bidi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

/** Logical text is exposed once per reordered Paged SVG line. */
public class ParagraphBidiPagedSvgTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testLogicalLineAriaAndTextRunAreNotDuplicated() throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("layout.bidi.paragraph", "true");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeFile(session, new File("files/unittest/3090-bidi/ua-logical-output.html"),
					"text/html", null);
		} finally {
			session.close();
		}
		final String svg = results.text("pages/0001.svg");
		final String json = results.text("pages/0001.json");
		assertEquals(1, count(svg, "aria-label=\"אבג ABC\""));
		assertEquals(1, count(svg, "data-copper-text=\"אבג ABC\""));
		assertTrue("later visual leaves must be hidden from accessibility", svg.contains("aria-hidden=\"true\""));
		assertEquals("one union-bounds TextRun per logical line", 1, count(json, "\"value\":\"אבג ABC\""));
	}

	private static int count(final String value, final String needle) {
		int count = 0;
		for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) {
			++count;
		}
		return count;
	}

	private static final class CapturingResults implements Results {
		private final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(metadata.getURI().toString(), out);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// no-op
		}

		String text(final String uri) {
			return this.data.get(uri).toString(StandardCharsets.UTF_8);
		}
	}
}
