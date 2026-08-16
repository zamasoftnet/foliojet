package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.image.ImageUserAgent;
import net.zamasoft.foliojet.ua.impl.image.RasterImageLoader;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.foliojet.ua.impl.svg.SVGUserAgent;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.protocol.stream.StreamSource;

/** 中間パスがserializerや最終描画を起動しないことの回帰テスト。 */
public class MeasurePassOutputTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
	private static final byte[] HTML = ("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
			+ "@page{size:100pt 80pt;margin:0}body{margin:0}.wide{width:300pt}"
			+ "</style></head><body><div class=\"wide\">measure</div><img width=\"1\" height=\"1\" "
			+ "src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(PNG)
			+ "\"></body></html>").getBytes(StandardCharsets.UTF_8);

	public void testPdfMiddlePassDoesNotCreateWriter() throws Exception {
		final CountingPDFUserAgent ua = new CountingPDFUserAgent();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		transcode(ua, single(out), HTML, "text/html", "application/pdf", true);
		assertEquals("実出力ページは最終パスの1回だけ", 1, ua.pageCalls);
		assertFalse("中間パスでPDFWriterを作らない", ua.writerSeenInNonOutputPass);
		assertTrue("最終PDFが出る", out.size() > 0);
	}

	public void testRawImageFormatterUsesNopImposition() throws Exception {
		final CountingPDFUserAgent ua = new CountingPDFUserAgent();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		transcode(ua, single(out), PNG, "image/png", "application/pdf", false);
		assertEquals("画像formatterも実出力は最終パスだけ", 1, ua.pageCalls);
		assertFalse("画像formatterの事前パスでもPDFWriterを作らない", ua.writerSeenInNonOutputPass);
		assertTrue(out.size() > 0);
	}

	public void testImageUserAgentDoesNotRasterizeMiddlePass() throws Exception {
		final CountingImageUserAgent ua = new CountingImageUserAgent();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		transcode(ua, single(out), HTML, "text/html", "image/png", false);
		assertEquals("ラスター化は最終パスの1回だけ", 1, ua.pageCalls);
		assertTrue(out.size() > 0);
	}

	public void testSvgUserAgentDoesNotSerializeMiddlePass() throws Exception {
		final CountingSVGUserAgent ua = new CountingSVGUserAgent();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		transcode(ua, single(out), HTML, "text/html", "image/svg+xml", false);
		assertEquals("SVG DOM生成は最終パスの1回だけ", 1, ua.pageCalls);
		assertTrue(out.toString(StandardCharsets.UTF_8).contains("<svg"));
	}

	public void testCallerProvidedNopResultsIsNotPassState() throws Exception {
		final CountingPDFUserAgent ua = new CountingPDFUserAgent();
		transcode(ua, NopResults.SHARED_INSTANCE, HTML, "text/html", "application/pdf", false);
		assertEquals("NopResultsでも最終パスは状態遷移する", 1, ua.pageCalls);
		assertFalse(ua.writerSeenInNonOutputPass);
	}

	public void testRasterLayoutMetricsMatchDecodedExifOrientation() throws Exception {
		final BufferedImage sourceImage = new BufferedImage(7, 3, BufferedImage.TYPE_INT_RGB);
		final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(sourceImage, "jpeg", jpeg));
		final byte[] orientedJpeg = addExifOrientation(jpeg.toByteArray(), 6);
		final RasterImageLoader loader = new RasterImageLoader();
		final Image metrics = loader.loadImageForLayout(streamSource(orientedJpeg, "image/jpeg"));
		final Image decoded = loader.loadImage(null, streamSource(orientedJpeg, "image/jpeg"));
		assertEquals(decoded.getWidth(), metrics.getWidth(), 0.0);
		assertEquals(decoded.getHeight(), metrics.getHeight(), 0.0);
		assertEquals(3.0, metrics.getWidth(), 0.0);
		assertEquals(7.0, metrics.getHeight(), 0.0);
	}

	public void testContinuousMiddleThenLastRestoresOutput() throws Exception {
		final CountingPDFUserAgent ua = new CountingPDFUserAgent();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = newSession(ua, single(out), "application/pdf");
		try {
			session.setContinuous(true);
			session.property("processing.pass-count", "1");
			session.property("processing.middle-pass", "true");
			transcodeStream(session, HTML, "text/html");
			assertEquals(0, ua.pageCalls);
			assertFalse(ua.writerSeenInNonOutputPass);
			session.property("processing.middle-pass", "false");
			transcodeStream(session, HTML, "text/html");
			session.join();
		} finally {
			session.close();
		}
		assertEquals(1, ua.pageCalls);
		assertTrue("退避した出力先へ最終PDFが出る", out.size() > 0);
	}

	public void testEpubPageSpreadBlankRunsInMeasurePass() throws Exception {
		final CountingPDFUserAgent ua = new CountingPDFUserAgent();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		transcode(ua, single(out), pageSpreadEpub(), "application/epub+zip", "application/pdf", false);
		assertEquals("page-spreadの空白頁も中間パスの進行点を通る", 1, ua.middlePageCalls);
		try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
			assertEquals("本文2頁とpage-spread空白頁", 3, pdf.getNumberOfPages());
			assertTrue(pageText(pdf, 1).contains("TARGET-2"));
			assertTrue(pageText(pdf, 1).contains("TOTAL-1/2"));
			assertEquals("", pageText(pdf, 2).trim());
			assertTrue(pageText(pdf, 3).contains("SECOND"));
		}
	}

	private static SingleResult single(final ByteArrayOutputStream out) {
		return new SingleResult(new StreamFragmentedOutput(out));
	}

	private static void transcode(final UserAgent ua, final Results results, final byte[] input,
			final String inputType, final String outputType, final boolean expand) throws Exception {
		final DirectSession session = newSession(ua, results, outputType);
		try {
			session.property("processing.pass-count", "2");
			session.property("processing.page-references", "true");
			if (expand) {
				session.property("output.expand-with-content", "true");
			}
			transcodeStream(session, input, inputType);
		} finally {
			session.close();
		}
	}

	private static DirectSession newSession(final UserAgent ua, final Results results, final String outputType)
			throws Exception {
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		session.setUserAgent(ua);
		session.setResults(results);
		session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
		session.property("input.include", "**");
		session.property("output.type", outputType);
		return session;
	}

	private static void transcodeStream(final DirectSession session, final byte[] input, final String inputType)
			throws Exception {
		CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(input), URI.create("urn:test:measure-pass"),
				inputType, "UTF-8");
	}

	private static Source streamSource(final byte[] bytes, final String mimeType) throws Exception {
		return new StreamSource(URI.create("urn:test:raster-metrics"), new ByteArrayInputStream(bytes), mimeType, null);
	}

	private static byte[] addExifOrientation(final byte[] jpeg, final int orientation) throws Exception {
		final byte[] app1 = new byte[] {
				(byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
				'E', 'x', 'i', 'f', 0x00, 0x00,
				'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
				0x00, 0x01,
				0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
				0x00, (byte) orientation, 0x00, 0x00,
				0x00, 0x00, 0x00, 0x00 };
		final ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + app1.length);
		out.write(jpeg, 0, 2);
		out.write(app1);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	private static byte[] pageSpreadEpub() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
			put(zip, "mimetype", "application/epub+zip");
			put(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?>"
					+ "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">"
					+ "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" "
					+ "media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
			put(zip, "OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\">"
					+ "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>spread</dc:title>"
					+ "<dc:identifier>spread</dc:identifier></metadata><manifest>"
					+ "<item id=\"a\" href=\"a.xhtml\" media-type=\"application/xhtml+xml\"/>"
					+ "<item id=\"b\" href=\"b.xhtml\" media-type=\"application/xhtml+xml\"/>"
					+ "</manifest><spine page-progression-direction=\"ltr\"><itemref idref=\"a\"/>"
					+ "<itemref idref=\"b\" properties=\"page-spread-left\"/></spine></package>");
			final String style = "@page{size:100pt 100pt;margin:5pt;counter-increment:page}"
					+ "body{margin:0;font:10pt sans-serif}a:after{content:' TARGET-' target-counter(attr(href),page)"
					+ " ' TOTAL-' counter(page) '/' counter(pages)}";
			put(zip, "OEBPS/a.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><style>" + style
					+ "</style></head><body><a href=\"b.xhtml#target\">FIRST</a></body></html>");
			put(zip, "OEBPS/b.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><style>" + style
					+ "</style></head><body><div id=\"target\">SECOND</div></body></html>");
		}
		return out.toByteArray();
	}

	private static void put(final ZipOutputStream zip, final String name, final String value) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(value.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static String pageText(final PDDocument pdf, final int page) throws Exception {
		final PDFTextStripper stripper = new PDFTextStripper();
		stripper.setStartPage(page);
		stripper.setEndPage(page);
		return stripper.getText(pdf);
	}

	private static final class CountingPDFUserAgent extends PDFUserAgent {
		int pageCalls;
		int middlePageCalls;
		boolean writerSeenInNonOutputPass;

		@Override
		public FontManager getFontManager() {
			final FontManager fontManager = super.getFontManager();
			checkWriter();
			return fontManager;
		}

		@Override
		public Image getImage(final Source source) throws java.io.IOException {
			final Image image = super.getImage(source);
			checkWriter();
			return image;
		}

		@Override
		public GC nextPage() {
			if (this.isMeasurePass()) {
				++this.middlePageCalls;
			} else {
				++this.pageCalls;
			}
			return super.nextPage();
		}

		private void checkWriter() {
			if (!this.isMeasurePass() && !this.isStructureScanPass()) {
				return;
			}
			try {
				final Field field = PDFUserAgent.class.getDeclaredField("pdfWriter");
				field.setAccessible(true);
				this.writerSeenInNonOutputPass |= field.get(this) != null;
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		}
	}

	private static final class CountingImageUserAgent extends ImageUserAgent {
		int pageCalls;

		@Override
		public GC nextPage() {
			++this.pageCalls;
			return super.nextPage();
		}
	}

	private static final class CountingSVGUserAgent extends SVGUserAgent {
		int pageCalls;

		@Override
		public GC nextPage() {
			++this.pageCalls;
			return super.nextPage();
		}
	}
}
