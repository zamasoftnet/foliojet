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
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code output.pdf.encryption.length} with the AES cipher of v4 encryption
 * ({@code output.pdf.encryption.v4.cfm=aesv2}).
 *
 * <p>
 * AESV2 is AES-128. The length was checked against the 40 to 128 bit range of Arcfour only, so a length of 40
 * gave the cipher a 10 byte key and the conversion stopped with "Invalid AES key length". Another length is now
 * reported and 128 bits are used.
 * </p>
 */
public class PdfEncryptionLengthTest extends TestCase {
	private static final String HTML = "<!doctype html><p>KEEP ME</p>";

	/** AESV2 with a length of 40: a warning, and a document the password opens. */
	public void testAesv2WithShortLengthUses128Bits() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final byte[] pdf = convert(props("output.pdf.version", "1.6", "output.pdf.encryption", "v4",
				"output.pdf.encryption.v4.cfm", "aesv2", "output.pdf.encryption.length", "40",
				"output.pdf.encryption.user-password", "test"), warnings);
		assertTrue("the unusable length must be reported: " + warnings,
				warnings.contains("output.pdf.encryption.length"));
		try (PDDocument doc = Loader.loadPDF(pdf, "test")) {
			assertTrue(doc.isEncrypted());
			assertEquals(128, doc.getEncryption().getLength());
			assertTrue(new PDFTextStripper().getText(doc).contains("KEEP ME"));
		}
	}

	/** AESV2 with its own length: no warning. */
	public void testAesv2With128Bits() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final byte[] pdf = convert(props("output.pdf.version", "1.6", "output.pdf.encryption", "v4",
				"output.pdf.encryption.v4.cfm", "aesv2", "output.pdf.encryption.length", "128",
				"output.pdf.encryption.user-password", "test"), warnings);
		assertFalse("128 bits is the length of AESV2: " + warnings,
				warnings.contains("output.pdf.encryption.length"));
		try (PDDocument doc = Loader.loadPDF(pdf, "test")) {
			assertEquals(128, doc.getEncryption().getLength());
			assertTrue(new PDFTextStripper().getText(doc).contains("KEEP ME"));
		}
	}

	/** v4 is 128 bits with Arcfour too: a warning, and a document the password opens. */
	public void testArcfourOfV4Uses128Bits() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final byte[] pdf = convert(props("output.pdf.version", "1.6", "output.pdf.encryption", "v4",
				"output.pdf.encryption.length", "40", "output.pdf.encryption.user-password", "test"), warnings);
		assertTrue("the length v4 does not take must be reported: " + warnings,
				warnings.contains("output.pdf.encryption.length"));
		try (PDDocument doc = Loader.loadPDF(pdf, "test")) {
			assertTrue(doc.isEncrypted());
			assertEquals(128, doc.getEncryption().getLength());
			assertTrue(new PDFTextStripper().getText(doc).contains("KEEP ME"));
		}
	}

	/**
	 * v2 with every key length opens and reads. The key derivation hashed 16 bytes where the key length was
	 * asked for, which is right only for 128 bits: shorter keys gave files no reader could open.
	 */
	public void testV2KeyLengthsOpen() throws Exception {
		for (final String bits : new String[] { "40", "64", "96", "128" }) {
			final List<String> warnings = new ArrayList<>();
			final byte[] pdf = convert(props("output.pdf.version", "1.4", "output.pdf.encryption", "v2",
					"output.pdf.encryption.length", bits, "output.pdf.encryption.user-password", "test"), warnings);
			try (PDDocument doc = Loader.loadPDF(pdf, "test")) {
				assertEquals(Integer.parseInt(bits), doc.getEncryption().getLength());
				assertTrue(bits + " bits", new PDFTextStripper().getText(doc).contains("KEEP ME"));
			}
		}
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	/** Converts, collecting the property names of the "cannot use this value on this PDF" warnings. */
	private static byte[] convert(final Map<String, String> properties, final List<String> warnings) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler((code, args, mes) -> {
				if (code == MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY && args != null && args.length > 0) {
					warnings.add(args[0]);
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			for (final Map.Entry<String, String> e : properties.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///encryption.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
