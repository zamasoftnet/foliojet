package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * cti.li の申し送り(2026-09-02 夜)から、EPUB の 2 件を固定します。
 *
 * <ol>
 * <li>目次の {@code href="ch2.xhtml#ix ACCS 不正アクセス事件"}(空白・日本語の断片)で
 * {@code URISyntaxException} になり、本全体が I/O error に落ちていた。href 1 件の失敗で
 * 本を落とさない</li>
 * <li>URL 入力で {@code application/octet-stream} と名乗る EPUB を HTML として読み続けて
 * いた。中身が ZIP で先頭項目 {@code mimetype} が {@code application/epub+zip} なら EPUB と見る</li>
 * </ol>
 */
public class EpubReaderNotesTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static byte[] epub() throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			// OCF: mimetype は先頭・無圧縮
			final byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
			final ZipEntry first = new ZipEntry("mimetype");
			first.setMethod(ZipEntry.STORED);
			first.setSize(mime.length);
			first.setCompressedSize(mime.length);
			final CRC32 crc = new CRC32();
			crc.update(mime);
			first.setCrc(crc.getValue());
			zip.putNextEntry(first);
			zip.write(mime);
			zip.closeEntry();
			entry(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?>"
					+ "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
					+ "<rootfiles><rootfile full-path=\"OEBPS/content.opf\""
					+ " media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
			entry(zip, "OEBPS/content.opf", "<?xml version=\"1.0\"?>"
					+ "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\">"
					+ "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
					+ "<dc:identifier id=\"uid\">urn:uuid:reader-notes</dc:identifier>"
					+ "<dc:title>索引</dc:title><dc:language>ja</dc:language></metadata><manifest>"
					+ "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
					+ "<item id=\"ch1\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/>"
					+ "<item id=\"ch2\" href=\"ch2.xhtml\" media-type=\"application/xhtml+xml\"/>"
					+ "</manifest><spine><itemref idref=\"nav\"/><itemref idref=\"ch1\"/><itemref idref=\"ch2\"/></spine></package>");
			// 空白と日本語を含む断片(URI としては不正)
			entry(zip, "OEBPS/nav.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
					+ "<head><title>nav</title></head><body><nav epub:type=\"toc\"><ol>"
					+ "<li><a href=\"ch1.xhtml#top\">第一章</a></li>"
					+ "<li><a href=\"ch2.xhtml#ix_ACCS 不正アクセス事件\">索引 ACCS</a></li>"
					+ "</ol></nav></body></html>");
			entry(zip, "OEBPS/ch1.xhtml", xhtml("第一章 甲乙丙"));
			entry(zip, "OEBPS/ch2.xhtml", xhtml("第二章 <a id=\"ix_ACCS 不正アクセス事件\">ACCS</a> 丁戊己"));
		}
		return bytes.toByteArray();
	}

	private static String xhtml(final String body) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>"
				+ "<style type=\"text/css\">@page{size:120pt 90pt;margin:8pt}body{margin:0;font-size:11pt}"
				+ "</style></head><body><p id=\"top\">" + body + "</p></body></html>";
	}

	private static void entry(final ZipOutputStream zip, final String name, final String text) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(text.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	/** 不正な href の目次があっても、本は PDF になる。 */
	public void testBrokenTocHrefDoesNotFailTheBook() throws Exception {
		final File file = File.createTempFile("reader-notes", ".epub");
		try {
			java.nio.file.Files.write(file.toPath(), epub());
			final CapturingResults r = convert(file.toURI(), "application/pdf");
			assertEquals("one PDF: " + r.order, 1, r.order.size());
			final byte[] pdf = r.data.get(r.order.get(0)).toByteArray();
			assertTrue("must be a PDF", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
		} finally {
			file.delete();
		}
	}

	/** 拡張子も型も EPUB を名乗らないファイルでも、中身が EPUB なら EPUB として組む。 */
	public void testOctetStreamEpubIsRecognisedByItsMimetypeEntry() throws Exception {
		final File file = File.createTempFile("reader-notes", ".bin");
		try {
			java.nio.file.Files.write(file.toPath(), epub());
			final CapturingResults r = convert(file.toURI(), "application/vnd.copper.paged-svg");
			assertTrue("an EPUB yields the item bundles and index.json: " + r.order,
					r.data.containsKey("index.json") && r.data.containsKey("items/0002/pages/0001.svg"));
		} finally {
			file.delete();
		}
	}

	private CapturingResults convert(final URI uri, final String outputType) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", outputType);
			session.property("output.paged-svg.compression", "none");
			session.transcode(uri);
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
	}
}
