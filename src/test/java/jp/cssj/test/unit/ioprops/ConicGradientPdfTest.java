package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** PDFのconic-gradientがType 4メッシュとして出力されることの試験。 */
public class ConicGradientPdfTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final Pattern TYPE4 = Pattern.compile("/ShadingType\\s+4(?=\\s|/|>>)");

	public void testOpaqueConicUsesType4InPdf17AndPdfA1() throws Exception {
		final Conversion pdf17 = convert("conic-gradient(red, blue)", "1.7");
		assertTrue("PDF 1.7 must contain a Type 4 mesh shading", containsType4(pdf17.pdf));
		assertFalse("the PDF conic gradient must not report approximation 2822",
				pdf17.hasMessage(MessageCodes.WARN_APPROXIMATED_RENDERING));
		try (PDDocument ignored = Loader.loadPDF(pdf17.pdf)) {
			// 診断用: PDF と描画を build/tmp に残す
			final java.io.File dir = new java.io.File("build/tmp");
			dir.mkdirs();
			java.nio.file.Files.write(new java.io.File(dir, "conic-gradient-17.pdf").toPath(), pdf17.pdf);
			javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(ignored).renderImageWithDPI(0, 144),
					"png", new java.io.File(dir, "conic-gradient-17.png"));
			// Loading with PDFBox also verifies that the emitted PDF is structurally readable.
		}

		final Conversion pdfa = convert("conic-gradient(red, blue)", "1.4A-1");
		assertTrue("opaque Type 4 mesh shading is allowed in PDF/A-1", containsType4(pdfa.pdf));
		assertFalse("opaque PDF/A-1 conic gradient must not report approximation 2822",
				pdfa.hasMessage(MessageCodes.WARN_APPROXIMATED_RENDERING));
		try (PDDocument ignored = Loader.loadPDF(pdfa.pdf)) {
			// Keep the PDFBox parse check for the conformance-profile path too.
		}
	}

	public void testTransparentConicHasSoftMaskExtGState() throws Exception {
		final Conversion transparent = convert(
				"conic-gradient(rgba(255,0,0,.25), rgba(0,0,255,.75))", "1.7");
		assertTrue("transparent conic gradient must still use a Type 4 mesh", containsType4(transparent.pdf));
		assertFalse("transparent PDF 1.7 conic gradient must not report approximation 2822",
				transparent.hasMessage(MessageCodes.WARN_APPROXIMATED_RENDERING));
		try (PDDocument pdf = Loader.loadPDF(transparent.pdf)) {
			assertTrue("transparent conic gradient must install an SMask ExtGState", hasSoftMaskExtGState(pdf));
		}
	}

	private static Conversion convert(final String background, final String version) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page{size:80mm 60mm;margin:10mm}body{margin:0}.conic{width:50mm;height:35mm;background:"
				+ background + "}</style></head><body><div class=\"conic\"></div></body></html>";
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
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///conic-gradient-pdf.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return new Conversion(out.toByteArray(), List.copyOf(messages.codes));
	}

	private static boolean containsType4(final byte[] pdf) {
		return TYPE4.matcher(new String(pdf, StandardCharsets.ISO_8859_1)).find();
	}

	private static boolean hasSoftMaskExtGState(final PDDocument pdf) {
		for (final PDPage page : pdf.getPages()) {
			final PDResources resources = page.getResources();
			if (resources == null) {
				continue;
			}
			for (final COSName name : resources.getExtGStateNames()) {
				if (resources.getExtGState(name).getCOSObject().containsKey(COSName.SMASK)) {
					return true;
				}
			}
		}
		return false;
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
