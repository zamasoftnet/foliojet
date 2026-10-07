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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Tests for output that <b>returns page-split SVG in a single ZIP</b> (B-2, 2026-08-29).
 *
 * <p>
 * Based on a user report (Japan Liberal Party Kawasaki). A multi-result bundle cannot be received
 * through a one-shot, sessionless REST call ({@code POST /transcode}) and fails with 4001.
 * ZIP provides one result that can be returned directly. Extracting it yields the same structure
 * as directory output, and {@code manifest.json} references resolve unchanged.
 * </p>
 */
public class PagedSvgZipBundleTest extends TestCase {
	private static final String HTML = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:200pt 160pt;margin:10pt}body{margin:0;font-size:12pt}</style></head><body>"
			+ "<p>ABC</p><p style=\"page-break-before:always\">DEF</p></body></html>";

	/** There is exactly one result, containing an extractable ZIP. */
	public void testSingleZipResult() throws Exception {
		final Map<String, byte[]> entries = convert();
		assertTrue("ページSVGが入っていません: " + entries.keySet(), entries.containsKey("pages/0001.svg"));
		assertTrue("2ページ目が入っていません: " + entries.keySet(), entries.containsKey("pages/0002.svg"));
		assertTrue("manifestが入っていません: " + entries.keySet(), entries.containsKey("manifest.json"));
		final String page = new String(entries.get("pages/0001.svg"), StandardCharsets.UTF_8);
		assertTrue("ページSVGの中身がSVGではありません: " + page.substring(0, Math.min(80, page.length())),
				page.contains("<svg"));
	}

	/**
	 * Do not compress contents separately: ZIP compresses them. Avoid double compression and use .svg/.json
	 * entry names.
	 */
	public void testEntriesAreNotGzipped() throws Exception {
		final Map<String, byte[]> entries = convert();
		for (final String name : entries.keySet()) {
			assertFalse("ZIPの中でさらにgzipしています: " + name, name.endsWith(".svgz") || name.endsWith(".json.gz"));
		}
		final byte[] page = entries.get("pages/0001.svg");
		assertFalse("ページSVGがgzipのままです", page.length > 2 && (page[0] & 0xFF) == 0x1F && (page[1] & 0xFF) == 0x8B);
	}

	/** Manifest references match ZIP entry names (extract and open directly). */
	public void testManifestUrisMatchEntryNames() throws Exception {
		final Map<String, byte[]> entries = convert();
		final String manifest = new String(entries.get("manifest.json"), StandardCharsets.UTF_8);
		// Pages are referenced by "svg":"pages/0001.svg" and "data":"pages/0001.json",
		// and resources by "uri":"assets/…".
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("\"(?:uri|svg|data)\"\s*:\s*\"((?:pages|assets)/[^\"]+)\"").matcher(manifest);
		int checked = 0;
		while (m.find()) {
			final String uri = m.group(1);
			assertTrue("manifestが指すURIがZIPにありません: " + uri + " / " + entries.keySet(),
					entries.containsKey(uri));
			++checked;
		}
		assertTrue("manifestにURIが1つもありません: " + manifest, checked > 0);
	}

	private static Map<String, byte[]> convert() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<String> resultUris = new ArrayList<>();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			final SingleResult single = new SingleResult(new StreamFragmentedOutput(out));
			session.setResults(new jp.cssj.cti2.results.Results() {
				public boolean hasNext() {
					return single.hasNext();
				}

				public net.zamasoft.zstream.io.FragmentedOutput nextBuilder(
						final net.zamasoft.zstream.resolver.SourceMetadata metadata) throws java.io.IOException {
					resultUris.add(metadata.getURI().toString());
					return single.nextBuilder(metadata);
				}

				public void end() throws java.io.IOException {
					single.end();
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg+zip");
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					new File("files/unittest/3080-MODERN-CSS/clip-path-circle.html").toURI(), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		assertEquals("結果は1件だけであること: " + resultUris, 1, resultUris.size());
		assertEquals("paged-svg.zip", resultUris.get(0));
		final Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
				entries.put(e.getName(), zip.readAllBytes());
			}
		}
		assertFalse("ZIPが空です", entries.isEmpty());
		return entries;
	}
}
