package jp.cssj.test.unit.ioprops;

import java.util.stream.Collectors;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight.Flavour;

/**
 * PDF/X-1a・PDF/X-4 の出力を pdfg2d の回帰プリフライト {@link PdfXPreflight} の
 * 全規則で検証します(2026-09-05、色管理 I4)。
 *
 * <p>
 * veraPDF は PDF/X を検証できないので、自前の規則(R1〜R13)で positive を固定する。
 * 各規則の negative は pdfg2d 側の {@code PdfXPreflightTest} が担保する。fixture は
 * {@link PdfAValidationTest} と同じ文書(生成画像・メッシュ・透明・埋め込みフォント・
 * PNG/JPEG)で、X-1a では透明が段階塗りの近似(2822)に、RGB が出力インテントの CMYK に
 * 落ちること、X-4 では RGB が ICCBased で残り {@code /DefaultRGB} が置かれることを
 * まとめて見る。最終確認はユーザーの Acrobat Pro Preflight
 * ({@code build/tmp/pdfx-validation-*.pdf})。
 * </p>
 */
public class PdfXValidationTest extends TestCase {

	public void testPdfX1a() throws Exception {
		validate("1.4X-1", Flavour.X1A);
	}

	public void testPdfX4() throws Exception {
		validate("1.6X-4", Flavour.X4);
	}

	private static void validate(final String version, final Flavour flavour) throws Exception {
		final byte[] pdf = PdfConversions.convert(PdfConversions.fixtureHtml("PDF/X"), version, false,
				"pdfx-validation-" + version);
		final var violations = PdfXPreflight.check(pdf, flavour);
		if (!violations.isEmpty()) {
			fail("PdfXPreflight " + flavour + " (" + version + ") violations:\n" + violations.stream()
					.map(v -> v.rule() + " " + v.message()).distinct().collect(Collectors.joining("\n")));
		}
	}
}
