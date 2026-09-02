package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
 * {@code output.paged-svg.resources=source}(2026-09-02): ウェブ上の画像は複写せず、
 * 取得元の URL をそのまま {@code <image href>} に書く。
 */
public class PagedSvgSourceResourcesTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static String html(final String imageUri) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:120pt 90pt;margin:8pt}body{margin:0}img{width:40pt;height:40pt}"
				+ "</style></head><body><img src=\"" + imageUri + "\">"
				+ "<img src=\"data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7\">"
				+ "</body></html>";
	}

	public void testWebImagesAreReferencedBySourceUrl() throws Exception {
		final URI png = new File("files/unittest/trans.png").getAbsoluteFile().toURI();
		final CapturingResults r = convert(html(png.toString()), "source");
		final String page = r.text("pages/0001.svg");
		// URI の表記(file:/F:/… と file:///mnt/f/…)は環境で違うので、パス部分で見る
		final String tail = "/files/unittest/trans.png\"";
		assertTrue("the page must reference the source URL: " + page, page.contains("href=\"file:")
				&& page.contains(tail));
		final String manifest = r.text("manifest.json");
		assertTrue("the manifest must record the source: " + manifest,
				manifest.contains("\"source\":\"file:") && manifest.contains(tail));
		// 取得元の無い data: 画像は従来どおり共有資源へ
		int assets = 0;
		for (final String uri : r.order) {
			if (uri.startsWith("assets/images/")) {
				++assets;
			}
		}
		assertEquals("only the data: image becomes a shared asset: " + r.order, 1, assets);
	}

	public void testReferenceModeStillCopiesWebImages() throws Exception {
		final URI png = new File("files/unittest/trans.png").getAbsoluteFile().toURI();
		final CapturingResults r = convert(html(png.toString()), "reference");
		final String page = r.text("pages/0001.svg");
		assertFalse("reference mode must not point at the source: " + page, page.contains("href=\"file:"));
		int assets = 0;
		for (final String uri : r.order) {
			if (uri.startsWith("assets/images/")) {
				++assets;
			}
		}
		assertEquals("both images are shared assets: " + r.order, 2, assets);
	}

	private CapturingResults convert(final String html, final String mode) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			session.property("output.paged-svg.resources", mode);
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/paged-svg-source.html").getAbsoluteFile().toURI(), "text/html", "UTF-8");
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
