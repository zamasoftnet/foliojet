package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
 * ページ分割SVGと PDF の同時出力({@code output.paged-svg.pdf=true})の試験です
 * (2026-09-03、cti.li の要望「1回の変換で PDF と Paged SVG を両方」)。
 */
public class PagedSvgWithPdfTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config",
				System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String HTML = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>同時出力の見本</title><style>"
			+ "@page{size:100mm 60mm;margin:8mm}body{margin:0;font-size:12pt}.next{page-break-before:always}"
			+ "</style></head><body><p>一頁目の本文 ALPHA</p>"
			+ "<p class=\"next\">二頁目の本文 BRAVO <span style=\"box-shadow:2pt 2pt 3pt #888;background:#eee\">影付き</span></p>"
			+ "</body></html>";

	public void testPdfIsEmittedNextToThePages() throws Exception {
		final CapturingResults r = convert(true);
		final String manifest = r.text("manifest.json");
		assertTrue(manifest, manifest.contains("\"pageCount\":2"));
		assertTrue("the manifest names the PDF: " + manifest, manifest.contains("\"pdf\":\"document.pdf\""));
		assertTrue("the PDF must be emitted before the manifest: " + r.order,
				r.order.indexOf("document.pdf") < r.order.indexOf("manifest.json"));
		final byte[] pdf = r.data.get("document.pdf").toByteArray();
		// 診断用に残す(build/tmp)
		final java.io.File dump = new java.io.File("build/tmp/paged-svg-with-pdf.pdf");
		dump.getParentFile().mkdirs();
		java.nio.file.Files.write(dump.toPath(), pdf);
		assertTrue("PDF magic", pdf.length > 4 && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals("the PDF has the same pages", 2, doc.getNumberOfPages());
			final String text = new PDFTextStripper().getText(doc);
			assertTrue("the PDF keeps a text layer (not outlines): " + text,
					text.contains("ALPHA") && text.contains("BRAVO") && text.contains("一頁目の本文"));
			assertEquals("同時出力の見本", doc.getDocumentInformation().getTitle());
		}
		// ページSVG側は従来どおり
		assertTrue(r.text("pages/0002.json").contains("BRAVO"));
	}

	public void testDefaultEmitsNoPdf() throws Exception {
		final CapturingResults r = convert(false);
		assertFalse(r.order.toString(), r.order.contains("document.pdf"));
		assertFalse(r.text("manifest.json").contains("\"pdf\":"));
	}

	private CapturingResults convert(final boolean withPdf) throws Exception {
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(results);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "application/vnd.copper.paged-svg");
			session.property("output.paged-svg.compression", "none");
			session.property("output.default-font-family", "'Noto Serif JP'");
			if (withPdf) {
				session.property("output.paged-svg.pdf", "true");
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///paged-svg-with-pdf.html"), "text/html", "UTF-8");
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
