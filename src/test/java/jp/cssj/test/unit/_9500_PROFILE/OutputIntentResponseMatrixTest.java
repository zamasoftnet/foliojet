package jp.cssj.test.unit._9500_PROFILE;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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

/**
 * Pins the response to every combination of output intent ICC profile, identifier and PDF version: the error
 * 380E and its detail, the warning, or the profile embedded with its component count (2026-10-08). Written before
 * the ICC checks of foliojet4 and pdfg2d were merged into {@code OutputIntent.checkProfile}, from the output of the
 * code before that change, so that the merge provably keeps the response to abnormal input.
 */
public class OutputIntentResponseMatrixTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String[] VERSIONS = { "1.4X-1", "1.4X-3", "1.6X-4", "1.5" };

	private static final String EXPECTED = """
			cmyk-output-v2 1.4X-1: OK intents=1 N=4 bytes=3747410
			cmyk-output-v2 1.4X-3: OK intents=1 N=4 bytes=3747410
			cmyk-output-v2 1.6X-4: OK intents=1 N=4 bytes=3747410
			cmyk-output-v2 1.5: OK intents=1 N=4 bytes=3747410
			cmyk-output-v4 1.4X-1: 380E output.pdf.output-intent.icc-profile PDF/X-1a and PDF/X-3 are based on PDF 1.4 and need an ICC version 2 profile, not version 4; failed 14350
			cmyk-output-v4 1.4X-3: 380E output.pdf.output-intent.icc-profile PDF/X-1a and PDF/X-3 are based on PDF 1.4 and need an ICC version 2 profile, not version 4; failed 14350
			cmyk-output-v4 1.6X-4: OK intents=1 N=4 bytes=3747410
			cmyk-output-v4 1.5: OK intents=1 N=4 bytes=3747410
			srgb-display 1.4X-1: 380E output.pdf.output-intent.icc-profile the ICC profile class is not output (prtr); failed 14350
			srgb-display 1.4X-3: 380E output.pdf.output-intent.icc-profile the ICC profile class is not output (prtr); failed 14350
			srgb-display 1.6X-4: 380E output.pdf.output-intent.icc-profile the ICC profile class is not output (prtr); failed 14350
			srgb-display 1.5: warn output.pdf.output-intent.icc-profile; OK intents=1 no-profile
			rgb-output 1.4X-1: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			rgb-output 1.4X-3: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			rgb-output 1.6X-4: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			rgb-output 1.5: OK intents=1 N=3 bytes=14086
			gray-output 1.4X-1: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			gray-output 1.4X-3: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			gray-output 1.6X-4: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			gray-output 1.5: OK intents=1 N=1 bytes=1138
			cmyk-display 1.4X-1: 380E output.pdf.output-intent.icc-profile the ICC profile class is not output (prtr); failed 14350
			cmyk-display 1.4X-3: 380E output.pdf.output-intent.icc-profile the ICC profile class is not output (prtr); failed 14350
			cmyk-display 1.6X-4: 380E output.pdf.output-intent.icc-profile the ICC profile class is not output (prtr); failed 14350
			cmyk-display 1.5: warn output.pdf.output-intent.icc-profile; OK intents=1 no-profile
			five-color-output 1.4X-1: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			five-color-output 1.4X-3: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			five-color-output 1.6X-4: 380E output.pdf.output-intent.icc-profile the ICC profile color space is not CMYK; failed 14350
			five-color-output 1.5: warn output.pdf.output-intent.icc-profile; OK intents=1 no-profile
			broken 1.4X-1: 380E output.pdf.output-intent.icc-profile the ICC profile cannot be read or parsed; failed 14350
			broken 1.4X-3: 380E output.pdf.output-intent.icc-profile the ICC profile cannot be read or parsed; failed 14350
			broken 1.6X-4: 380E output.pdf.output-intent.icc-profile the ICC profile cannot be read or parsed; failed 14350
			broken 1.5: warn output.pdf.output-intent.icc-profile; OK intents=1 no-profile
			blank-identifier 1.4X-1: 380E output.pdf.output-intent.identifier the output condition identifier is blank; failed 14350
			non-ascii-identifier 1.4X-1: 380E output.pdf.output-intent.identifier the output condition identifier and the registry name must be printable ASCII; failed 14350
			non-ascii-registry 1.4X-1: 380E output.pdf.output-intent.registry the output condition identifier and the registry name must be printable ASCII; failed 14350
			no-profile 1.4X-1: 380E output.pdf.output-intent.icc-profile PDF/X needs the ICC profile of the output condition (output.pdf.output-intent.icc-profile); failed 14350
			no-identifier 1.4X-1: warn output.pdf.output-intent.identifier; OK intents=1 N=4 bytes=3747410
			blank-identifier 1.4X-3: 380E output.pdf.output-intent.identifier the output condition identifier is blank; failed 14350
			non-ascii-identifier 1.4X-3: 380E output.pdf.output-intent.identifier the output condition identifier and the registry name must be printable ASCII; failed 14350
			non-ascii-registry 1.4X-3: 380E output.pdf.output-intent.registry the output condition identifier and the registry name must be printable ASCII; failed 14350
			no-profile 1.4X-3: 380E output.pdf.output-intent.icc-profile PDF/X needs the ICC profile of the output condition (output.pdf.output-intent.icc-profile); failed 14350
			no-identifier 1.4X-3: warn output.pdf.output-intent.identifier; OK intents=1 N=4 bytes=3747410
			blank-identifier 1.6X-4: 380E output.pdf.output-intent.identifier the output condition identifier is blank; failed 14350
			non-ascii-identifier 1.6X-4: 380E output.pdf.output-intent.identifier the output condition identifier and the registry name must be printable ASCII; failed 14350
			non-ascii-registry 1.6X-4: 380E output.pdf.output-intent.registry the output condition identifier and the registry name must be printable ASCII; failed 14350
			no-profile 1.6X-4: 380E output.pdf.output-intent.icc-profile PDF/X needs the ICC profile of the output condition (output.pdf.output-intent.icc-profile); failed 14350
			no-identifier 1.6X-4: warn output.pdf.output-intent.identifier; OK intents=1 N=4 bytes=3747410
			blank-identifier 1.5: OK intents=1 N=4 bytes=3747410
			non-ascii-identifier 1.5: OK intents=1 N=4 bytes=3747410
			non-ascii-registry 1.5: OK intents=1 N=4 bytes=3747410
			no-profile 1.5: OK intents=1 no-profile
			no-identifier 1.5: OK intents=1 N=3 bytes=6252
			""";

	public void testResponses() throws Exception {
		final StringBuilder actual = new StringBuilder();
		final String[][] profiles = { { "cmyk-output-v2" }, { "cmyk-output-v4" }, { "srgb-display" },
				{ "rgb-output" }, { "gray-output" }, { "cmyk-display" }, { "five-color-output" }, { "broken" } };
		for (final String[] profile : profiles) {
			for (final String version : VERSIONS) {
				actual.append(profile[0]).append(' ').append(version).append(": ")
						.append(convert(version, "FOGRA39", null, profile(profile[0]))).append('\n');
			}
		}
		for (final String version : VERSIONS) {
			actual.append("blank-identifier ").append(version).append(": ")
					.append(convert(version, " \t ", null, profile("cmyk-output-v2"))).append('\n');
			actual.append("non-ascii-identifier ").append(version).append(": ")
					.append(convert(version, "日本の印刷", null, profile("cmyk-output-v2"))).append('\n');
			actual.append("non-ascii-registry ").append(version).append(": ")
					.append(convert(version, "FOGRA39", "レジストリ", profile("cmyk-output-v2"))).append('\n');
			actual.append("no-profile ").append(version).append(": ")
					.append(convert(version, "FOGRA39", null, null)).append('\n');
			actual.append("no-identifier ").append(version).append(": ")
					.append(convert(version, null, null, profile("cmyk-output-v2"))).append('\n');
		}
		assertEquals(EXPECTED, actual.toString());
	}

	private static byte[] profile(final String name) throws Exception {
		switch (name) {
		case "cmyk-output-v2":
			return cmykOutput();
		case "cmyk-output-v4": {
			final byte[] data = cmykOutput();
			data[8] = 4;
			return data;
		}
		case "srgb-display":
			return ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
		case "rgb-output":
			return withClass(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData(), "prtr");
		case "gray-output":
			return withClass(ICC_Profile.getInstance(ColorSpace.CS_GRAY).getData(), "prtr");
		case "cmyk-display":
			return withClass(cmykOutput(), "mntr");
		case "five-color-output": {
			// A CMYK output profile relabeled as five colors (bytes 16 to 19): five components
			final byte[] data = cmykOutput();
			System.arraycopy("5CLR".getBytes(StandardCharsets.US_ASCII), 0, data, 16, 4);
			return data;
		}
		case "broken":
			return new byte[] { 1, 7, 3, 9, 0, 4 };
		default:
			throw new IllegalArgumentException(name);
		}
	}

	/** Rewrites the profile/device class of the header (bytes 12 to 15). */
	private static byte[] withClass(final byte[] data, final String signature) {
		final byte[] copy = data.clone();
		System.arraycopy(signature.getBytes(StandardCharsets.US_ASCII), 0, copy, 12, 4);
		return copy;
	}

	private static byte[] cmykOutput() throws Exception {
		try (InputStream in = PDFWriterImpl.class.getResourceAsStream(
				"/net/zamasoft/pdfg2d/pdf/impl/ISOcoated_v2_300_eci.icc")) {
			assertNotNull(in);
			return in.readAllBytes();
		}
	}

	/** One line: the messages (code, property, and the detail of 380E), then OK and the embedded profile. */
	private static String convert(final String version, final String identifier, final String registry,
			final byte[] profile) throws Exception {
		final Path iccFile = Files.createTempFile("foliojet-output-intent-matrix-", ".icc");
		try {
			if (profile != null) {
				Files.write(iccFile, profile);
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final List<String> messages = new ArrayList<>();
			final MessageHandler handler = (code, args, message) -> {
				if (code == MessageCodes.ERROR_PDFX_OUTPUT_INTENT) {
					messages.add("380E " + args[0] + " " + args[2]);
				} else if (code == MessageCodes.WARN_BAD_IO_PROPERTY) {
					messages.add("warn " + args[0]);
				}
			};
			String failure = null;
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(handler);
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("output.type", "application/pdf");
				session.property("output.pdf.version", version);
				session.property("output.pdf.compression", "none");
				if (identifier != null) {
					session.property("output.pdf.output-intent.identifier", identifier);
				}
				if (registry != null) {
					session.property("output.pdf.output-intent.registry", registry);
				}
				if (profile != null) {
					session.property("output.pdf.output-intent.icc-profile", iccFile.toUri().toString());
				}
				CTISessionHelper.transcodeFile(session, new File("files/unittest/9500-PROFILE/simple.html"),
						"text/html", null);
			} catch (final TranscoderException e) {
				failure = "failed " + e.getCode();
			} finally {
				session.close();
			}
			final StringBuilder line = new StringBuilder(String.join(", ", messages));
			if (failure != null) {
				return line.append(line.length() > 0 ? "; " : "").append(failure).toString();
			}
			try (var doc = org.apache.pdfbox.Loader.loadPDF(out.toByteArray())) {
				final var intents = doc.getDocumentCatalog().getOutputIntents();
				line.append(line.length() > 0 ? "; " : "").append("OK intents=").append(intents.size());
				if (!intents.isEmpty()) {
					final var dest = intents.get(0).getDestOutputIntent();
					line.append(dest == null ? " no-profile"
							: " N=" + dest.getInt(org.apache.pdfbox.cos.COSName.N) + " bytes=" + dest.getLength());
				}
			}
			return line.toString();
		} finally {
			Files.deleteIfExists(iccFile);
		}
	}
}
