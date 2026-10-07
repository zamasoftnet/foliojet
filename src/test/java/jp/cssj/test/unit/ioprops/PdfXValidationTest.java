package jp.cssj.test.unit.ioprops;

import java.util.stream.Collectors;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight;
import net.zamasoft.pdfg2d.pdf.preflight.PdfXPreflight.Flavour;

/**
 * PDF/X-1a・PDF/X-3・PDF/X-4 の出力を pdfg2d の回帰プリフライト {@link PdfXPreflight} の
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

	/**
	 * PDF/X-3(2026-09-30): 透明は X-1a と同じく近似、RGB は X-4 と同じく ICCBased で残る。
	 */
	public void testPdfX3() throws Exception {
		validate("1.4X-3", Flavour.X3);
	}

	/**
	 * {@code <title>} の前後と途中の空白(2026-10-07)。HTML の document.title と同じく、前後を落として続く空白を
	 * 1 つにまとめた値が Info の Title と XMP の dc:title の両方に入る。以前は末尾の空白が残り、dc:title を空白を
	 * 落として読む検査(R4)と食い違った(全 HTML 掃過の 4000-BLOG/2650-text.html)。
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
