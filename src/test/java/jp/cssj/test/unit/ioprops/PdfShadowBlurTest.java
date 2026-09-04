package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** PDF出力では本文をベクタのまま保ち、ぼかした影だけをラスタ化することの試験。 */
public class PdfShadowBlurTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String PARAGRAPH_TEXT = "影だけを滑らかにぼかします";

	public void testPdf17RasterizesOnlyShadows() throws Exception {
		final Conversion exact = convert(html(true), "1.7", false);
		assertFalse("PDF 1.7 must not report the stepped blur approximation",
				exact.hasMessage(MessageCodes.WARN_APPROXIMATED_RENDERING));
		try (PDDocument pdf = Loader.loadPDF(exact.pdf)) {
			dump(pdf, exact.pdf, "pdf-shadow-blur-17");
			assertTrue("box-shadow and text-shadow must each produce an image with an SMask",
					countImages(pdf, true) >= 2);
			assertTrue("the paragraph must remain extractable text",
					new PDFTextStripper().getText(pdf).contains(PARAGRAPH_TEXT));
		}

		final Conversion tagged = convert(html(true), "1.7", true);
		assertFalse("tagged PDF 1.7 must not report the stepped blur approximation",
				tagged.hasMessage(MessageCodes.WARN_APPROXIMATED_RENDERING));
		assertEquals("shadow images are artifacts, not Figure structure elements", 0, countFigures(tagged.pdf));

		final Conversion control = convert(html(false), "1.7", true);
		assertEquals("the shadow-free control must have the same absence of Figure elements", 0,
				countFigures(control.pdf));
	}

	public void testPdfA1FallsBackToSteppedFills() throws Exception {
		final Conversion pdfa = convert(html(true), "1.4A-1", false);
		assertTrue("PDF/A-1 must report approximation 2822",
				pdfa.hasMessage(MessageCodes.WARN_APPROXIMATED_RENDERING));
		try (PDDocument pdf = Loader.loadPDF(pdfa.pdf)) {
			dump(pdf, pdfa.pdf, "pdf-shadow-blur-a1");
			assertEquals("PDF/A-1 must not create shadow image XObjects", 0, countImages(pdf, false));
		}
	}

	private static String html(final boolean shadows) {
		return "<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:100mm 80mm;margin:10mm}html{font-family:'Noto Serif JP'}body{margin:0}"
				+ ".box{box-sizing:border-box;width:45mm;height:14mm;padding:2mm;background:#f8f8f8;"
				+ "border:1pt solid #888;"
				+ (shadows ? "box-shadow:4pt 4pt 6pt rgba(0,0,0,.5);" : "")
				+ "}p{margin:14pt 0 0;font:16pt/1.5 'Noto Serif JP';"
				+ (shadows ? "text-shadow:2pt 2pt 4pt #444;" : "")
				+ "}</style></head><body><div class=\"box\">箱の影</div><p>" + PARAGRAPH_TEXT
				+ "</p></body></html>";
	}

	private static Conversion convert(final String html, final String version, final boolean tagged) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final Messages messages = new Messages();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(messages);
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			session.property("output.pdf.version", version);
			session.property("output.pdf.fonts.policy", "embedded");
			if (tagged) {
				session.property("output.pdf.tagged", "true");
				session.property("output.pdf.tagged.lang", "ja");
			}
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///pdf-shadow-blur.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return new Conversion(out.toByteArray(), List.copyOf(messages.codes));
	}

	/** Counts page image resources; an SMask image itself is not a page resource. */
	private static int countImages(final PDDocument pdf, final boolean requireSoftMask) throws IOException {
		int count = 0;
		for (final PDPage page : pdf.getPages()) {
			final PDResources resources = page.getResources();
			if (resources == null) {
				continue;
			}
			for (final COSName name : resources.getXObjectNames()) {
				final PDXObject object = resources.getXObject(name);
				if (object instanceof PDImageXObject image
						&& (!requireSoftMask || image.getCOSObject().containsKey(COSName.SMASK))) {
					++count;
				}
			}
		}
		return count;
	}

	private static int countFigures(final byte[] bytes) throws IOException {
		try (PDDocument pdf = Loader.loadPDF(bytes)) {
			final PDStructureTreeRoot root = pdf.getDocumentCatalog().getStructureTreeRoot();
			assertNotNull("tagged output must contain a structure tree", root);
			return countFigures(root);
		}
	}

	private static int countFigures(final PDStructureNode node) {
		int count = 0;
		final List<Object> kids = node.getKids();
		if (kids == null) {
			return 0;
		}
		for (final Object kid : kids) {
			if (kid instanceof PDStructureElement element) {
				if ("Figure".equals(element.getStructureType())) {
					++count;
				}
				count += countFigures(element);
			}
		}
		return count;
	}

	/** 診断用: PDF と 1 頁目の描画(PDFBox、144dpi)を build/tmp に残す。 */
	private static void dump(final PDDocument pdf, final byte[] bytes, final String name) throws Exception {
		final java.io.File dir = new java.io.File("build/tmp");
		dir.mkdirs();
		java.nio.file.Files.write(new java.io.File(dir, name + ".pdf").toPath(), bytes);
		final java.awt.image.BufferedImage image = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,
				144);
		javax.imageio.ImageIO.write(image, "png", new java.io.File(dir, name + ".png"));
	}

	private record Conversion(byte[] pdf, List<Short> messages) {
		boolean hasMessage(final int code) {
			for (final short actual : this.messages) {
				if ((actual & 0xffff) == code) {
					return true;
				}
			}
			return false;
		}
	}

	private static final class Messages implements MessageHandler {
		final List<Short> codes = new ArrayList<>();

		@Override
		public void message(final short code, final String[] args, final String message) {
			this.codes.add(code);
		}
	}
}
