package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.util.stream.Collectors;

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import junit.framework.TestCase;

/**
 * PDF/A 各版の出力を veraPDF で検証します(2026-09-03)。
 *
 * <p>
 * veraPDF は PDF/A と PDF/UA だけを検証でき、PDF/X の profile を持たない
 * (veraPDF-validation-profiles は PDF_A と PDF_UA のみ)。生成画像
 * (影のぼかし・filter のラスタ化)の ICCBased sRGB、conic-gradient の
 * type 4 メッシュ、透明グループ、埋め込みフォントといった PDF/X-4 と
 * 共通する制約は、透明を許す PDF/A-2 以降の検証で代替する。PDF/A-1 は
 * 透明不可なので従来の近似経路(2822)がそのまま適合するかを見る。
 * PDF/X は {@link PdfXValidationTest}(pdfg2d の {@code PdfXPreflight})。
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
