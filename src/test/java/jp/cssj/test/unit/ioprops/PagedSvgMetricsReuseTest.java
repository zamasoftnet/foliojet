package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
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
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Regression test that reconverting Paged SVG does not reopen images (2026-08-28).
 *
 * <p>
 * Page SVG image references use content-hash names, {@code assets/images/<sha256>.<ext>},
 * so even {@code resources=omit}, which does not re-emit the data, reread images just to determine
 * their names. Record resource identity in the previous {@code metrics.json} and pass it through
 * {@code input.image-metrics} to write the same pages <b>without opening any image</b>.
 * </p>
 */
public class PagedSvgMetricsReuseTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** A 1x1 PNG. */
	private static final String PNG =
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

	public void testReconvertReferencesAssetsWithoutOpeningImages() throws Exception {
		final File dir = Files.createTempDirectory("copper-psvg-reuse").toFile();
		try {
			final File png = new File(dir, "picture.png");
			Files.write(png.toPath(), Base64.getDecoder().decode(PNG));
			final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style type=\"text/css\">"
					+ "@page{size:100pt 80pt;margin:8pt}body{margin:0;font-size:12pt}img{width:10pt;height:10pt}"
					+ "</style></head><body><p>画像 <img src=\"" + png.toURI() + "\"></p></body></html>";
			final File htmlFile = new File(dir, "doc.html");
			Files.write(htmlFile.toPath(), html.getBytes(StandardCharsets.UTF_8));

			// First conversion: emit data and receive the dimension table.
			final CountingResolver first = new CountingResolver();
			final CapturingResults full = convert(htmlFile, first, Map.of());
			final String metricsUri = "metrics.json";
			assertTrue("単一パスの変換でも寸法表が出るべき", full.data.containsKey(metricsUri));
			final String metrics = full.text(metricsUri);
			assertTrue("寸法表に資源の同一性が要る: " + metrics, metrics.contains("\"sha256\""));
			final String pageUri = "pages/0001.svg";
			final String page = full.text(pageUri);
			assertTrue("ページが資源を参照しているべき: " + page, page.contains("assets/images/"));
			assertTrue("1回目は画像を開く: " + first.opened, opened(first, "picture.png"));

			// Second conversion: omit data and supply the dimension table. Images should not be opened.
			final CountingResolver second = new CountingResolver();
			final CapturingResults reused = convert(htmlFile, second, Map.of(
					"output.paged-svg.resources", "omit",
					"input.image-metrics", "data:application/json;base64,"
							+ Base64.getEncoder().encodeToString(metrics.getBytes(StandardCharsets.UTF_8))));
			assertEquals("ページは前回と同一であるべき", page, reused.text(pageUri));
			assertFalse("再変換で画像を開いてはならない: " + second.opened, opened(second, "picture.png"));
			assertFalse("実体は出し直さない", reused.data.keySet().stream().anyMatch(u -> u.startsWith("assets/images/")));
		} finally {
			deleteTree(dir);
		}
	}

	private static boolean opened(final CountingResolver resolver, final String name) {
		return resolver.opened.stream().anyMatch(u -> u.endsWith(name));
	}

	private static CapturingResults convert(final File html, final SourceResolver resolver,
			final Map<String, String> props) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(resolver);
			// Do not set input.include. If set, the ACL takes effect before the injected resolver,
			// and file: resolution bypasses this CountingResolver.
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			// The default is gzip (2026-08-28). Leave pages uncompressed to compare their contents.
			session.property("output.paged-svg.compression", "none");
			for (final Map.Entry<String, String> e : props.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeFile(session, html, "text/html", null);
		} finally {
			session.close();
		}
		return results;
	}

	private static void deleteTree(final File dir) {
		final File[] files = dir.listFiles();
		if (files != null) {
			for (final File f : files) {
				deleteTree(f);
			}
		}
		if (!dir.delete()) {
			dir.deleteOnExit();
		}
	}

	/** A resolver that counts which URIs are actually opened. */
	private static final class CountingResolver implements SourceResolver {
		private final SourceResolver delegate = CompositeSourceResolver.createGenericCompositeSourceResolver();
		final List<String> opened = new ArrayList<>();

		@Override
		public Source resolve(final URI uri) throws IOException {
			this.opened.add(uri.toString());
			return this.delegate.resolve(uri);
		}

		@Override
		public void release(final Source source) {
			this.delegate.release(source);
		}
	}

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
			// NOP
		}

		String text(final String uri) {
			return this.data.get(uri).toString(StandardCharsets.UTF_8);
		}
	}
}
