package jp.cssj.test.unit.ioprops;

import java.util.stream.Collectors;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight.Flavour;

/**
 * Validate PDF/X-1a, PDF/X-3, and PDF/X-4 output against every rule in pdfg2d's regression preflight,
 * {@link PdfXPreflight} (2026-09-05, color management I4).
 *
 * <p>
 * veraPDF cannot validate PDF/X, so fix positive cases using our own rules (R1–R13).
 * pdfg2d's {@code PdfXPreflightTest} covers negative cases for each rule.
 * The fixture is the same document as {@link PdfAValidationTest} (generated images, meshes,
 * transparency, embedded fonts, PNG/JPEG). Check together that X-1a approximates transparency
 * with stepped painting (2822) and converts RGB to the output intent's CMYK, while X-4 retains
 * RGB as ICCBased and sets {@code /DefaultRGB}. The final check is the user's Acrobat Pro Preflight
 * ({@code build/tmp/pdfx-validation-*.pdf}).
 * </p>
 */
public class PdfXValidationTest extends TestCase {

	public void testPdfX1a() throws Exception {
		validate("1.4X-1", Flavour.X1A);
	}

	public void testPdfX4() throws Exception {
		validate("1.6X-4", Flavour.X4);
	}

	/**
	 * PDF/X-3 (2026-09-30): approximate transparency as in X-1a and retain RGB as ICCBased as in X-4.
	 */
	public void testPdfX3() throws Exception {
		validate("1.4X-3", Flavour.X3);
	}

	/**
	 * Whitespace before, after, and within {@code <title>} (2026-10-07). As with HTML document.title,
	 * trim leading/trailing whitespace and collapse consecutive whitespace to one space; use this value
	 * for both Info Title and XMP dc:title. Previously, trailing whitespace remained, conflicting with
	 * check R4, which trims dc:title when reading (4000-BLOG/2650-text.html in the full HTML sweep).
	 */
	public void testPdfX4TitleWhitespace() throws Exception {
		final byte[] pdf = validate("1.6X-4", Flavour.X4, " PDF/X\n\t title ");
		try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			assertEquals("PDF/X title", doc.getDocumentInformation().getTitle());
		}
	}

	private static void validate(final String version, final Flavour flavour) throws Exception {
		validate(version, flavour, "PDF/X");
	}

	private static byte[] validate(final String version, final Flavour flavour, final String title) throws Exception {
		final byte[] pdf = PdfConversions.convert(PdfConversions.fixtureHtml(title), version, false,
				"pdfx-validation-" + version);
		final var violations = PdfXPreflight.check(pdf, flavour);
		if (!violations.isEmpty()) {
			fail("PdfXPreflight " + flavour + " (" + version + ") violations:\n" + violations.stream()
					.map(v -> v.rule() + " " + v.message()).distinct().collect(Collectors.joining("\n")));
		}
		return pdf;
	}
}
