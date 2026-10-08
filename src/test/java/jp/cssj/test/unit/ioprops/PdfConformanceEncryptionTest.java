package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * PDF/A and PDF/X prohibit encryption: every encryption is left out with a warning (2026-10-08). v1, v2 and v4 used
 * to check only PDF/A-1b and the PDF/X variants on PDF 1.4, so PDF/A-2 to 4 and PDF/X-4 and 6 failed with
 * "Encryption cannot be used in PDF/A" (found while fixing the manual errata; the manual promised a warning).
 */
public class PdfConformanceEncryptionTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File DOCUMENT = new File("files/unittest/3080-MODERN-CSS/calc.html");

	private static final String[] CONFORMANCE = { "1.4A-1", "1.7A-2", "1.7A-3", "2.0A-4", "1.4X-1", "1.4X-3",
			"1.6X-4", "2.0X-6" };

	private static final String[] ENCRYPTION = { "v1", "v2", "v4", "v5" };

	public void testEncryptionIsLeftOutWithAWarning() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final String version : CONFORMANCE) {
			for (final String encryption : ENCRYPTION) {
				final List<Short> codes = new ArrayList<>();
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setMessageHandler((code, args, mes) -> codes.add(code));
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("output.pdf.compression", "none");
					session.property("output.pdf.version", version);
					session.property("output.pdf.encryption", encryption);
					session.property("output.pdf.encryption.user-password", "u");
					CTISessionHelper.transcodeFile(session, DOCUMENT, "text/html", null);
				} catch (final Exception e) {
					failures.add(version + "+" + encryption + ": " + e);
					continue;
				} finally {
					session.close();
				}
				final String pdf = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
				if (!pdf.startsWith("%PDF") || pdf.contains("/Encrypt")) {
					failures.add(version + "+" + encryption + ": encrypted or no PDF");
				} else if (!codes.contains(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY)) {
					failures.add(version + "+" + encryption + ": no warning");
				}
			}
		}
		assertTrue(String.join("\n", failures), failures.isEmpty());
	}
}
