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
 * 単一SVG({@code image/svg+xml})の outline モードが、埋め込み方針の字形(pdfg2d 自身の
 * 輪郭)で描くこと(2026-09-02)。
 *
 * <p>
 * 以前は keep モードだけが {@code core,embedded} を既定にしていて、outline は共通の既定
 * (print では cid-keyed 優先)のまま組んでいた。SVG に CID-keyed の実体は無いので AWT の
 * 代替フォントの輪郭になり、「日」が本物より 6% 広く縦画が太かった。既定と明示の
 * {@code embedded} が同じ SVG になることで固定する。
 * </p>
 */
public class SingleSvgOutlineFontTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

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
		// 明示した方針には従う(core だけなら和文は別の字形か MISSING になり、SVG が変わる)
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
			// 何もしない
		}
	}
}
