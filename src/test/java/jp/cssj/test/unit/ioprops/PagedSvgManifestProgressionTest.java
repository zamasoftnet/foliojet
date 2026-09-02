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
 * ページ分割SVGの {@code manifest.json} に頁の進む向きが入ること(2026-09-02、
 * cti.li の要望)。
 *
 * <p>
 * 読み器が要るのは綴じ方向ではなく「頁がどちらへ進むか」で、{@code binding} が
 * {@code single}(印刷モード未指定)でも縦組みなら右から読む。以前は
 * {@code left} でなければ右綴じと判定して、横組みの英文が右から並んだ。
 * </p>
 */
public class PagedSvgManifestProgressionTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static String html(final String rootStyle) {
		return "<!DOCTYPE html><html style=\"" + rootStyle + "\"><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:100mm 100mm;margin:10mm}body{margin:0;font-size:10pt}"
				+ "</style></head><body><p>本文 body text</p></body></html>";
	}

	public void testVerticalRlProgressesRightToLeft() throws Exception {
		final String manifest = convert(html("writing-mode:vertical-rl"));
		assertTrue(manifest, manifest.contains("\"pageProgressionDirection\":\"rtl\""));
	}

	public void testHorizontalProgressesLeftToRight() throws Exception {
		final String manifest = convert(html(""));
		assertTrue(manifest, manifest.contains("\"pageProgressionDirection\":\"ltr\""));
	}

	public void testVerticalLrProgressesLeftToRight() throws Exception {
		final String manifest = convert(html("writing-mode:vertical-lr"));
		assertTrue(manifest, manifest.contains("\"pageProgressionDirection\":\"ltr\""));
	}

	private String convert(final String html) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///progression.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return results.text("manifest.json");
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
