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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.ua.MultiDocumentOutput.DocumentUnit;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSVGUserAgent;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * EPUBをPaged SVGへ出すときの試験です(2026-09-02)。
 *
 * <p>
 * spine項目は<b>独立した文書</b>として組まれ、{@code items/NNNN/}の下に
 * 単一の文書と同じ形のバンドル(自分の{@code manifest.json}・フォント・画像)が
 * できる。上位には{@code index.json}だけ。項目は並列に組まれるが、結果は
 * spine順に解放されるので、並列度を変えても出力は1バイトも変わらない。
 * </p>
 */
public class PagedSvgEpubTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 章ごとに字種を分けてあるので、項目ごとのサブセットは互いに違う中身になる。 */
	private static final String[] CHAPTERS = { "第一章 甲乙丙", "第二章 丁戊己", "第三章 庚辛壬", "第四章 癸子丑" };

	private static byte[] epub(final int chapters) throws Exception {
		return epub(chapters, false);
	}

	private static byte[] epub(final int chapters, final boolean retained) throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			entry(zip, "mimetype", "application/epub+zip");
			entry(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?>"
					+ "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
					+ "<rootfiles><rootfile full-path=\"OEBPS/content.opf\""
					+ " media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
			final StringBuilder opf = new StringBuilder("<?xml version=\"1.0\"?>"
					+ "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\">"
					+ "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
					+ "<dc:identifier id=\"uid\">urn:uuid:paged-svg-epub</dc:identifier>"
					+ "<dc:title>EPUB試験</dc:title><dc:language>ja</dc:language></metadata><manifest>"
					+ "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>");
			for (int i = 1; i <= chapters; ++i) {
				opf.append("<item id=\"ch").append(i).append("\" href=\"ch").append(i)
						.append(".xhtml\" media-type=\"application/xhtml+xml\"/>");
			}
			opf.append("</manifest><spine>");
			for (int i = 1; i <= chapters; ++i) {
				opf.append("<itemref idref=\"ch").append(i).append("\"/>");
			}
			opf.append("</spine></package>");
			entry(zip, "OEBPS/content.opf", opf.toString());
			final StringBuilder nav = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
					+ "<head><title>nav</title></head><body><nav epub:type=\"toc\"><ol>");
			for (int i = 1; i <= chapters; ++i) {
				nav.append("<li><a href=\"ch").append(i).append(".xhtml#top\">").append(CHAPTERS[i - 1])
						.append("</a></li>");
			}
			nav.append("</ol></nav></body></html>");
			entry(zip, "OEBPS/nav.xhtml", nav.toString());
			for (int i = 1; i <= chapters; ++i) {
				// 保持量は先頭の章が最大、末尾が最小。通常の本文だけではhigh-waterが0になる。
				final String text = retained ? "<span style=\"display:inline-block\">"
						+ CHAPTERS[i - 1].repeat(chapters - i + 1) + "</span>" : CHAPTERS[i - 1];
				entry(zip, "OEBPS/ch" + i + ".xhtml", xhtml(text));
			}
		}
		return bytes.toByteArray();
	}

	private static String xhtml(final String text) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>"
				+ "<style type=\"text/css\">@page{size:120pt 90pt;margin:8pt}body{margin:0;font-size:11pt}"
				+ "</style></head><body><p id=\"top\">" + text + "</p></body></html>";
	}

	private static void entry(final ZipOutputStream zip, final String name, final String text) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(text.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	/** 項目ごとのバンドルと、上位のindex.json。 */
	public void testItemsAreIndependentBundles() throws Exception {
		final CapturingResults r = run(2, Map.of());
		assertTrue(r.order.toString(), r.data.containsKey("items/0001/pages/0001.svg"));
		assertTrue(r.order.toString(), r.data.containsKey("items/0002/pages/0001.svg"));
		assertTrue("each item must carry its own manifest", r.data.containsKey("items/0001/manifest.json"));
		assertTrue(r.data.containsKey("items/0002/manifest.json"));
		assertFalse("the flat page numbering must not be used for EPUB", r.data.containsKey("pages/0001.svg"));
		assertEquals("index.json must be the last result", "index.json", r.order.get(r.order.size() - 1));

		// 項目のmanifestは単一の文書と同じ形で、項目の中の相対参照
		final String manifest1 = r.text("items/0001/manifest.json");
		assertTrue(manifest1, manifest1.contains("\"pages/0001.svg\""));
		assertTrue(manifest1, manifest1.contains("\"assets/fonts/"));

		final String index = r.text("index.json");
		assertTrue(index, index.contains("\"composition\":\"epub\""));
		assertTrue(index, index.contains("\"uri\":\"OEBPS/ch1.xhtml\""));
		assertTrue(index, index.contains("\"manifest\":\"items/0002/manifest.json\""));
		assertTrue(index, index.contains("\"firstPage\":2"));
		assertTrue(index, index.contains("\"pageCount\":2"));
		assertTrue("the toc must point at the item", index.contains("\"item\":2"));
		assertTrue(index, index.contains("\"title\":\"EPUB試験\""));
	}

	/**
	 * 既定({@code document})では、<b>項目ごとに</b>サブセットができ、項目を
	 * 閉じた時点で出ること。
	 */
	public void testDocumentScopeIsPerSpineItem() throws Exception {
		final CapturingResults r = run(2, Map.of());
		final List<String> fonts1 = fonts(r, "items/0001/");
		final List<String> fonts2 = fonts(r, "items/0002/");
		assertFalse("item 1 must carry its own subset: " + r.order, fonts1.isEmpty());
		assertFalse("item 2 must carry its own subset: " + r.order, fonts2.isEmpty());

		// 項目1のサブセットは、項目2のページより先に出る
		assertTrue("the first item's subset must precede the second item's page: " + r.order,
				r.order.indexOf(fonts1.get(0)) < r.order.indexOf("items/0002/pages/0001.svg"));

		// ページは自分の項目のサブセットを指す
		assertTrue(fontHref(r.text("items/0001/pages/0001.svg")).startsWith("assets/fonts/"));
		assertTrue(r.text("items/0002/pages/0001.svg").contains("../assets/fonts/"));
	}

	/** {@code page}は従来どおりページごと。 */
	public void testPageScopeIsUnchangedForEpub() throws Exception {
		final CapturingResults r = run(2, Map.of("output.paged-svg.font-scope", "page"));
		for (final String item : new String[] { "items/0001/", "items/0002/" }) {
			final String page = item + "pages/0001.svg";
			final String href = fontHref(r.text(page));
			assertNotNull(href);
			assertTrue(href + " must precede " + page + ": " + r.order,
					r.order.indexOf(item + href) < r.order.indexOf(page));
		}
	}

	/** {@code input.epub.spine}で1項目だけ組む。番号はspine内の位置で固定。 */
	public void testSpineSelectionKeepsItemNumbers() throws Exception {
		final CapturingResults r = run(3, Map.of("input.epub.spine", "ch2"));
		assertFalse(r.order.toString(), r.order.stream().anyMatch(u -> u.startsWith("items/0001/")));
		assertTrue(r.order.toString(), r.data.containsKey("items/0002/pages/0001.svg"));
		assertFalse(r.order.toString(), r.order.stream().anyMatch(u -> u.startsWith("items/0003/")));
		final String index = r.text("index.json");
		assertTrue(index, index.contains("\"index\":1,\"idref\":\"ch1\",\"uri\":\"OEBPS/ch1.xhtml\",\"included\":false}"));
		assertTrue(index, index.contains("\"index\":2,\"idref\":\"ch2\",\"uri\":\"OEBPS/ch2.xhtml\",\"included\":true"));
		assertTrue(index, index.contains("\"firstPage\":1,\"pageCount\":1"));

		// 番号と範囲でも選べる
		final CapturingResults byNumber = run(3, Map.of("input.epub.spine", "1, 3"));
		assertTrue(byNumber.data.containsKey("items/0001/pages/0001.svg"));
		assertFalse(byNumber.data.containsKey("items/0002/pages/0001.svg"));
		assertTrue(byNumber.data.containsKey("items/0003/pages/0001.svg"));
	}

	/** 並列度を変えても出力は同一。逐次で組んでも並列で組んでも1バイトも変わらない。 */
	public void testConcurrencyDoesNotChangeTheOutput() throws Exception {
		final CapturingResults sequential = run(4, Map.of("processing.concurrency", "1"));
		final CapturingResults parallel = run(4, Map.of("processing.concurrency", "4"));
		assertEquals("the result order must be the same", sequential.order, parallel.order);
		for (final String uri : sequential.order) {
			assertTrue(uri + " must be byte-identical",
					Arrays.equals(sequential.data.get(uri).toByteArray(), parallel.data.get(uri).toByteArray()));
		}
		assertEquals(4, (int) sequential.order.stream().filter(u -> u.endsWith("/manifest.json")).count());
	}

	/** 子の保持量は合算せず、逐次・並列とも冊全体の最大値を親へ残す。 */
	public void testRetainedTextHighWaterAggregatesChildren() throws Exception {
		for (final String concurrency : new String[] { "1", "4" }) {
			final List<UserAgent> children = new ArrayList<>();
			final PagedSVGUserAgent ua = new PagedSVGUserAgent() {
				@Override
				public UserAgent openDocument(final DocumentUnit unit) {
					final UserAgent child = super.openDocument(unit);
					children.add(child);
					return child;
				}
			};
			final CapturingResults results = this.run(epub(4, true),
					Map.of("processing.concurrency", concurrency, "processing.retained-text-limit", "64"), ua);
			assertEquals(4, children.size());
			assertTrue(results.data.containsKey("index.json"));
			long maximum = 0;
			long total = 0;
			for (final UserAgent child : children) {
				final var accounting = child.getRetainedTextLimit();
				assertEquals("子にも同じ上限を渡すこと", 64L, accounting.getLimit());
				assertTrue("各項目で実際に保持すること", accounting.getHighWater() > 0);
				maximum = Math.max(maximum, accounting.getHighWater());
				total += accounting.getHighWater();
			}
			assertTrue("末尾の子の値で上書きしてはならないfixtureであること",
					children.get(0).getRetainedTextLimit().getHighWater()
							> children.get(3).getRetainedTextLimit().getHighWater());
			assertTrue("子の合計は上限を超えるfixtureであること", total > 64);
			assertEquals("親に子の最大high-waterを集約すること", maximum, ua.getRetainedTextLimit().getHighWater());
			assertEquals("子の会計を親の累計へ持ち越さないこと", 0L, ua.getRetainedTextLimit().getCurrentBytes());
			assertEquals(64L, ua.getRetainedTextLimit().getLimit());
		}
	}

	/** ページSVGが最初に参照するサブセットのURI({@code assets/fonts/...})。 */
	private static String fontHref(final String svg) {
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(assets/fonts/[^)\"' ]+)").matcher(svg);
		return m.find() ? m.group(1) : null;
	}

	private static List<String> fonts(final CapturingResults r, final String prefix) {
		return r.order.stream().filter(u -> u.startsWith(prefix + "assets/fonts/")).toList();
	}

	private CapturingResults run(final int chapters, final Map<String, String> extraProps) throws Exception {
		return this.run(epub(chapters), extraProps, null);
	}

	private CapturingResults run(final byte[] document, final Map<String, String> extraProps,
			final PagedSVGUserAgent ua) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			if (ua != null) session.setUserAgent(ua);
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.default-font-family", "'Noto Serif JP'");
			session.property("output.paged-svg.compression", "none");
			for (final Map.Entry<String, String> e : extraProps.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(document),
					URI.create("file:///paged-svg-epub.epub"), "application/epub+zip", null);
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
		public synchronized FragmentedOutput nextBuilder(final SourceMetadata metadata) {
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
