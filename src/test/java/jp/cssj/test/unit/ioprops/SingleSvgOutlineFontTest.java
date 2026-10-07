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
 * Single-SVG ({@code image/svg+xml}) outline mode draws using embedding-policy glyphs
 * (pdfg2d's own outlines) (2026-09-02).
 *
 * <p>
 * Previously, only keep mode defaulted to {@code core,embedded}, while outline mode used the
 * shared default (cid-keyed preferred in print). SVG has no CID-keyed font data, so it used outlines
 * from an AWT fallback font: "日" was 6% wider than the real glyph, with thicker vertical strokes.
 * Verify this by checking that the default and explicit {@code embedded} produce identical SVGs.
 * </p>
 */
public class SingleSvgOutlineFontTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String HTML = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:60mm 30mm;margin:5mm}body{margin:0}p{font:16pt serif;margin:0}"
			+ "</style></head><body><p>日日日 abc</p></body></html>";

	public void testOutlineDefaultsToEmbeddedGlyphs() throws Exception {
		final String byDefault = convert(null);
		final String embedded = convert("embedded");
		assertTrue("outline mode must write glyph paths: " + byDefault, byDefault.contains("<path"));
		assertEquals("the default outline SVG must be the embedded-policy SVG", embedded, byDefault);
	}

	public void testExplicitPolicyStillWins() throws Exception {
		// Follow an explicit policy (core alone uses different Japanese glyphs or MISSING, changing the SVG).
		final String byDefault = convert(null);
		final String core = convert("core");
		assertFalse("an explicit policy must change the output", byDefault.equals(core));
	}

	private String convert(final String policy) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "image/svg+xml");
			session.property("output.svg.text", "outline");
			if (policy != null) {
				session.property("output.pdf.fonts.policy", policy);
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///single-svg-outline.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		assertFalse("an SVG must be emitted: " + results.order, results.order.isEmpty());
		return results.data.get(results.order.get(0)).toString(StandardCharsets.UTF_8);
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
	}
}
