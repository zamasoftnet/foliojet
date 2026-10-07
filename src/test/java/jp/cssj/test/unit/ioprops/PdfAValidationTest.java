package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.util.stream.Collectors;

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import junit.framework.TestCase;

/**
 * Validate output for each PDF/A version with veraPDF (2026-09-03).
 *
 * <p>
 * veraPDF validates only PDF/A and PDF/UA and has no PDF/X profiles
 * (veraPDF-validation-profiles contains only PDF_A and PDF_UA).
 * Use validation against PDF/A-2 and later, which allow transparency, as a substitute for constraints
 * shared with PDF/X-4: ICCBased sRGB in generated images (shadow blur and filter rasterization),
 * conic-gradient type 4 meshes, transparency groups, and embedded fonts.
 * PDF/A-1 prohibits transparency, so check whether the existing approximation path (2822) conforms as-is.
 * For PDF/X, see {@link PdfXValidationTest} (pdfg2d's {@code PdfXPreflight}).
 * </p>
 */
public class PdfAValidationTest extends TestCase {
	static {
		VeraGreenfieldFoundryProvider.initialise();
	}

	public void testPdfA1b() throws Exception {
		validate("1.4A-1", PDFAFlavour.PDFA_1_B, false);
	}

	public void testPdfA2b() throws Exception {
		validate("1.7A-2", PDFAFlavour.PDFA_2_B, false);
	}

	public void testPdfA2u() throws Exception {
		validate("1.7A-2u", PDFAFlavour.PDFA_2_U, false);
	}

	public void testPdfA2a() throws Exception {
		validate("1.7A-2a", PDFAFlavour.PDFA_2_A, true);
	}

	public void testPdfA3b() throws Exception {
		validate("1.7A-3", PDFAFlavour.PDFA_3_B, false);
	}

	public void testPdfA4() throws Exception {
		validate("2.0A-4", PDFAFlavour.PDFA_4, false);
	}

	private static void validate(final String version, final PDFAFlavour flavour, final boolean tagged)
			throws Exception {
		final byte[] pdf = PdfConversions.convert(PdfConversions.fixtureHtml("PDF/A"), version, tagged,
				"pdfa-validation-" + version);
		try (final var parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf), flavour);
				final var validator = Foundries.defaultInstance().createValidator(flavour, false)) {
			final var result = validator.validate(parser);
			if (!result.isCompliant()) {
				final String failures = result.getTestAssertions().stream()
						.filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
						.map(a -> a.getRuleId() + " " + a.getMessage() + " @ " + a.getLocation().getContext())
						.distinct().collect(Collectors.joining("\n"));
				fail("veraPDF " + flavour + " (" + version + ") failures:\n" + failures);
			}
		}
	}
}
