package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
 * ラスタ画像出力({@code image/png})の既定フォント方針が埋め込み(pdfg2d 自身の輪郭)で
 * あること(2026-09-03、ユーザー判断)。
 *
 * <p>
 * 単一SVGの outline と同じ描画経路で、共通の既定(cid-keyed)のままだと字形データを
 * 持たないフォントが AWT のシステムフォントで描かれていた。既定と明示の
 * {@code embedded} が同じ PNG になることで固定する。
 * </p>
 */
public class RasterFontPolicyTest extends TestCase {
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

	public void testRasterDefaultsToEmbeddedGlyphs() throws Exception {
		final byte[] byDefault = convert(null);
		final byte[] embedded = convert("embedded");
		assertTrue("a PNG must be emitted", byDefault.length > 8 && byDefault[1] == 'P' && byDefault[2] == 'N');
		assertTrue("the default PNG must be the embedded-policy PNG", Arrays.equals(embedded, byDefault));
	}

	public void testExplicitPolicyStillWins() throws Exception {
		// 明示した方針には従う(core だけなら和文は別の字形か MISSING になり、画素が変わる)
		final byte[] byDefault = convert(null);
		final byte[] core = convert("core");
		assertFalse("an explicit policy must change the output", Arrays.equals(byDefault, core));
	}

	private byte[] convert(final String policy) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "image/png");
			if (policy != null) {
				session.property("output.pdf.fonts.policy", policy);
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///raster-font-policy.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		assertFalse("a PNG must be emitted: " + results.order, results.order.isEmpty());
		return results.data.get(results.order.get(0)).toByteArray();
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
