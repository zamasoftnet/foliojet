package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

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
 * </p>
 */
public class PdfAValidationTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
		VeraGreenfieldFoundryProvider.initialise();
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGO4w8AAAAKYAN3rxP+VAAAAAElFTkSuQmCC";

	/** 生成画像・メッシュ・透明・埋め込みフォント・画像を 1 頁に集めた文書。 */
	private static String html() {
		return "<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"UTF-8\"><title>PDF/A</title><style>"
				+ "@page{size:120mm 120mm;margin:8mm}body{margin:0;font:11pt serif}"
				+ ".shadow{width:40mm;height:12mm;background:#fc6;box-shadow:2mm 2mm 3mm rgba(0,0,0,.5)}"
				+ ".filtered{width:40mm;height:12mm;padding:2mm;background:#f36;filter:grayscale(1) blur(1pt)}"
				+ ".conic{width:30mm;height:30mm;background:conic-gradient(red, blue, red)}"
				+ ".fade{width:40mm;height:8mm;background:linear-gradient(to right, rgba(0,0,255,0), #00f)}"
				+ ".half{opacity:.5}img{width:8mm;height:8mm}"
				+ "</style></head><body>"
				+ "<p>日本語の本文と Latin text。</p>"
				+ "<div class=\"shadow\">shadow</div>"
				+ "<div class=\"filtered\">filtered <img alt=\"dot\" src=\"" + PNG + "\"></div>"
				+ "<div class=\"conic\"></div>"
				+ "<div class=\"fade\"></div>"
				+ "<p class=\"half\">half <img alt=\"dot\" src=\"" + PNG + "\"></p>"
				+ "</body></html>";
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
		final byte[] pdf = convert(html(), version, tagged);
		final java.io.File dir = new java.io.File("build/tmp");
		dir.mkdirs();
		java.nio.file.Files.write(new java.io.File(dir, "pdfa-validation-" + version + ".pdf").toPath(), pdf);
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

	private static byte[] convert(final String html, final String version, final boolean tagged) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			session.property("output.pdf.version", version);
			if (tagged) {
				session.property("output.pdf.tagged", "true");
				session.property("output.pdf.tagged.lang", "ja");
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///pdfa-validation.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
