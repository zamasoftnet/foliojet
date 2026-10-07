package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Verify that drawing inside MathML and inline SVG appears at its position on the page
 * in page-split SVG (2026-10-04, TECH-20261003-004, item ⑳).
 *
 * <p>
 * Both draw through a Graphics2D bridge, which resets the GC to the state at the latest
 * {@code begin()} ({@code resetState()}) and reapplies the transform delta.
 * The page-split SVG GC reset to the initial state (identity matrix), so the second and later
 * characters in formulas, nested shapes, and clips were drawn near the page origin.
 * </p>
 */
public class PagedSvgNestedTransformTest extends TestCase {
	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();

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
			// Do nothing.
		}
	}

	public void testMathAndInlineSvgAreDrawnAtTheirPlace() throws Exception {
		// 72 pt margin. All contents are drawn inside it.
		final String html = """
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/><style>
				@page { size: 400pt 600pt; margin: 72pt } body { margin: 0; font-size: 9pt }
				svg { width: 60mm; height: auto }
				</style></head><body>
				<p>x <math xmlns="http://www.w3.org/1998/Math/MathML"><msup><mi>x</mi><mn>2</mn></msup><mo>+</mo>
				<mfrac><mn>1</mn><mn>3</mn></mfrac></math></p>
				<math xmlns="http://www.w3.org/1998/Math/MathML" display="block"><mi>e</mi><mo>(</mo><mi>P</mi>
				<mo>,</mo><mi>Q</mi><msup><mo>)</mo><mrow><mi>a</mi><mi>b</mi></mrow></msup></math>
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 30" width="60mm" height="30mm">
				<rect x="1" y="1" width="58" height="28" fill="#eef" stroke="#24a" stroke-width="0.6"/>
				<circle cx="15" cy="15" r="8" fill="#c33"/></svg>
				</body></html>""";
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///nested-transform.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		final ByteArrayOutputStream page = results.data.get("pages/0001.svg");
		assertNotNull(results.data.keySet().toString(), page);
		final String svg = page.toString(StandardCharsets.UTF_8);

		// Start points of shape and clip paths. None may be drawn near the page origin (inside the margin).
		final Matcher m = Pattern.compile("<path d=\"M(-?[0-9.]+)[ ,](-?[0-9.]+)").matcher(svg);
		final List<String> stray = new ArrayList<>();
		int paths = 0;
		while (m.find()) {
			++paths;
			final double x = Double.parseDouble(m.group(1));
			final double y = Double.parseDouble(m.group(2));
			// Exclude whole-page clips (rectangles starting at the origin).
			if ((x < 60 || y < 60) && !(x == 0 && y == 0)) {
				stray.add(m.group(1) + "," + m.group(2));
			}
		}
		assertTrue("math glyphs and shapes are drawn as paths: " + paths, paths >= 6);
		assertEquals("paths drawn near the page origin", List.of(), stray);
	}
}
