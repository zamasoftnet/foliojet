package jp.cssj.test.unit._9500_PROFILE;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** PDF/Xの明示OutputIntent ICCを完全解析し、不正な指定をfail closedにする試験。 */
public class OutputIntentValidationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String PDFX4 = "1.6X-4";

	public void testPdfXRejectsRgbMonitorProfile() throws Exception {
		final Conversion result = convert(PDFX4, "RGB test", srgbProfile());
		assertFailedWithPdfXOutputIntentError(result);
	}

	public void testPdfXRejectsBrokenProfile() throws Exception {
		final Conversion result = convert(PDFX4, "Broken test", new byte[] { 1, 7, 3, 9, 0, 4 });
		assertFailedWithPdfXOutputIntentError(result);
	}

	public void testPdfXRejectsBlankIdentifier() throws Exception {
		final Conversion result = convert(PDFX4, " \t ", cmykOutputProfile());
		assertFailedWithPdfXOutputIntentError(result);
	}

	public void testRegularPdfWarnsAndDiscardsRgbMonitorProfile() throws Exception {
		final Conversion result = convert("1.5", "RGB test", srgbProfile());
		assertNull("通常PDFの変換は成功すること", result.failure);
		assertTrue("不正な明示ICCはWARN_BAD_IO_PROPERTYで報告すること",
				result.hasMessage(MessageCodes.WARN_BAD_IO_PROPERTY));
	}

	public void testPdfXAcceptsCmykOutputProfile() throws Exception {
		final Conversion result = convert(PDFX4, "FOGRA39", cmykOutputProfile());
		assertNull("CMYK出力プロファイルならPDF/X-4変換が成功すること", result.failure);
		final String pdf = new String(result.pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue("埋め込んだCMYK ICCに/N 4が付くこと", pdf.contains("/N 4"));
	}

	public void testPdfXWarnsWhenProfileHasNoIdentifier() throws Exception {
		final Conversion result = convert(PDFX4, null, cmykOutputProfile());
		assertNull("識別子が未指定なら明示ICCを使わない従来動作を維持すること", result.failure);
		assertTrue("PDF/XでICCだけを指定した組み合わせは警告すること",
				result.hasMessage(MessageCodes.WARN_BAD_IO_PROPERTY));
	}

	private static void assertFailedWithPdfXOutputIntentError(final Conversion result) {
		assertNotNull("不正なPDF/X出力インテントで変換が失敗すること", result.failure);
		assertTrue("ERROR_PDFX_OUTPUT_INTENTを報告すること",
				result.hasMessage(MessageCodes.ERROR_PDFX_OUTPUT_INTENT));
	}

	private static byte[] srgbProfile() {
		final ICC_Profile profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
		assertEquals("試験用sRGBはmntr classであること", ICC_Profile.CLASS_DISPLAY, profile.getProfileClass());
		assertEquals("試験用sRGBはRGB色空間であること", ColorSpace.TYPE_RGB, profile.getColorSpaceType());
		return profile.getData();
	}

	private static byte[] cmykOutputProfile() throws Exception {
		try (InputStream in = PDFWriterImpl.class.getResourceAsStream(
				"/net/zamasoft/pdfg2d/pdf/impl/ISOcoated_v2_300_eci.icc")) {
			assertNotNull("ISO Coated v2 300% (ECI)プロファイルがクラスパスに必要です", in);
			final byte[] data = in.readAllBytes();
			final ICC_Profile profile = ICC_Profile.getInstance(data);
			assertEquals("試験用CMYK ICCはprtr classであること", ICC_Profile.CLASS_OUTPUT,
					profile.getProfileClass());
			assertEquals("試験用CMYK ICCはCMYK色空間であること", ColorSpace.TYPE_CMYK,
					profile.getColorSpaceType());
			assertEquals("試験用CMYK ICCは4成分であること", 4, profile.getNumComponents());
			return data;
		}
	}

	private static Conversion convert(final String version, final String identifier, final byte[] profile)
			throws Exception {
		final Path iccFile = Files.createTempFile("foliojet-output-intent-validation-", ".icc");
		try {
			Files.write(iccFile, profile);
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final Messages messages = new Messages();
			Exception failure = null;
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(messages);
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("output.type", "application/pdf");
				session.property("output.pdf.version", version);
				session.property("output.pdf.compression", "none");
				if (identifier != null) {
					session.property("output.pdf.output-intent.identifier", identifier);
				}
				session.property("output.pdf.output-intent.icc-profile", iccFile.toUri().toString());
				CTISessionHelper.transcodeFile(session, new File("files/unittest/9500-PROFILE/simple.html"),
						"text/html", null);
			} catch (final TranscoderException e) {
				failure = e;
			} finally {
				session.close();
			}
			return new Conversion(out.toByteArray(), List.copyOf(messages.codes), failure);
		} finally {
			Files.deleteIfExists(iccFile);
		}
	}

	private record Conversion(byte[] pdf, List<Short> messages, Exception failure) {
		boolean hasMessage(final short code) {
			return this.messages.contains(Short.valueOf(code));
		}
	}

	private static final class Messages implements MessageHandler {
		final List<Short> codes = new ArrayList<>();

		@Override
		public void message(final short code, final String[] args, final String message) {
			this.codes.add(Short.valueOf(code));
		}
	}
}
