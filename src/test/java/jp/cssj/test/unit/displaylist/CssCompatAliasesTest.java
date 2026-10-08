package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.FontWeightValue;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * CSS that other formatters and EPUB 3 accept (2026-10-08, EPUB brush-up D-3, D-17, D-18).
 *
 * <p>
 * The EPUB 3 {@code -epub-} names of text-orientation, line-break, word-break, hyphens, text-transform,
 * text-emphasis-position, text-underline-position, ruby-position and text-combine-horizontal were "unsupported", so the
 * Electronic Book Publishing Association template's {@code .upright} did not stand characters upright. Their old values
 * ({@code sideways-right}, {@code vertical-right}, ruby {@code before}/{@code after}) go with them. {@code font-weight}
 * takes any number from 1 to 1000 (CSS Fonts 4), and {@code target-counter()} takes the {@code attr(href url)} that the
 * GCPM examples use.
 * </p>
 */
public class CssCompatAliasesTest extends TestCase {
	private record Output(byte[] pdf, List<String> warnings, String displayList) {
	}

	private static Output convert(final String name, final String style, final String body) throws Exception {
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ja\"><head>"
				+ "<meta charset=\"UTF-8\"/><style>" + style + "</style></head><body>" + body + "</body></html>";
		final java.io.File dir = new java.io.File("local/css-compat-aliases/" + name);
		dir.mkdirs();
		final java.io.File[] old = dir.listFiles();
		if (old != null) {
			for (final java.io.File f : old) {
				f.delete();
			}
		}
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<String> warnings = new ArrayList<>();
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(new MessageHandler() {
					@Override
					public void message(final short code, final String[] args, final String message) {
						if (code == MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY || code == MessageCodes.WARN_BAD_CSS_SYNTAX
								|| code == MessageCodes.WARN_BAD_CSS_ARGMENTS) {
							warnings.add(message);
						}
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///css-compat.xhtml"), "application/xhtml+xml", null);
			} finally {
				session.close();
			}
		}
		final java.io.File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		java.util.Arrays.sort(pages);
		final StringBuilder dl = new StringBuilder();
		for (final java.io.File page : pages) {
			dl.append(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return new Output(out.toByteArray(), warnings, dl.toString());
	}

	private static final String BODY = "<p>生没年<span class=\"u\">＝AB</span>と<span class=\"s\">ab</span>。"
			+ "<ruby>漢<rt>かん</rt></ruby><span class=\"t\">12</span></p><p class=\"w\">ああああ</p>";

	/** The {@code -epub-} names lay out exactly as the standard names. */
	public void testEpubAliases() throws Exception {
		final Output epub = convert("epub", "body { writing-mode: vertical-rl } .u { -epub-text-orientation: upright }"
				+ " .s { -epub-text-orientation: sideways-right } .w { -epub-word-break: break-all; -epub-line-break: strict;"
				+ " -epub-hyphens: auto; -epub-text-transform: none } ruby { -epub-ruby-position: over }"
				+ " .t { -epub-text-combine-horizontal: all; -epub-text-emphasis-position: over right;"
				+ " -epub-text-underline-position: left }", BODY);
		final Output standard = convert("standard", "body { writing-mode: vertical-rl } .u { text-orientation: upright }"
				+ " .s { text-orientation: sideways } .w { word-break: break-all; line-break: strict;"
				+ " hyphens: auto; text-transform: none } ruby { ruby-position: over }"
				+ " .t { text-combine-upright: all; text-emphasis-position: over right;"
				+ " text-underline-position: left }", BODY);
		assertEquals("警告", List.of(), epub.warnings());
		assertEquals(standard.displayList(), epub.displayList());
	}

	/** Old keyword values: text-orientation vertical-right is mixed; ruby-position before/after are over/under. */
	public void testLegacyValues() throws Exception {
		final Output legacy = convert("legacy", "body { writing-mode: vertical-rl } .u { text-orientation: vertical-right }"
				+ " ruby { ruby-position: after }", "<p><span class=\"u\">AB</span><ruby>漢<rt>かん</rt></ruby></p>");
		final Output standard = convert("legacy-standard", "body { writing-mode: vertical-rl } .u { text-orientation: mixed }"
				+ " ruby { ruby-position: under }", "<p><span class=\"u\">AB</span><ruby>漢<rt>かん</rt></ruby></p>");
		assertEquals("警告", List.of(), legacy.warnings());
		assertEquals(standard.displayList(), legacy.displayList());
	}

	/** Any number from 1 to 1000; the step chosen keeps the CSS Fonts 4 matching order. */
	public void testFontWeightNumbers() throws Exception {
		assertSame(FontWeightValue.W200_VALUE, FontValueUtils.toFontWeight(new CssToken.Num(250, true)));
		assertSame(FontWeightValue.W200_VALUE, FontValueUtils.toFontWeight(new CssToken.Num(250.5, false)));
		assertSame(FontWeightValue.W100_VALUE, FontValueUtils.toFontWeight(new CssToken.Num(1, true)));
		assertSame(FontWeightValue.W400_VALUE, FontValueUtils.toFontWeight(new CssToken.Num(400, true)));
		assertSame(FontWeightValue.W500_VALUE, FontValueUtils.toFontWeight(new CssToken.Num(450, true)));
		assertSame(FontWeightValue.W700_VALUE, FontValueUtils.toFontWeight(new CssToken.Num(650, true)));
		assertSame(FontWeightValue.W900_VALUE, FontValueUtils.toFontWeight(new CssToken.Num(1000, true)));
		assertNull(FontValueUtils.toFontWeight(new CssToken.Num(0, true)));
		assertNull(FontValueUtils.toFontWeight(new CssToken.Num(1001, true)));
		assertEquals("警告", List.of(), convert("weight", "p { font-weight: 250 }", "<p>W</p>").warnings());
	}

	/** {@code attr(href url)} names the target as {@code attr(href)} does. */
	public void testTargetCounterAttrUrl() throws Exception {
		final Output out = convert("target", "a::after { content: \" p.\" target-counter(attr(href url), page) }"
				+ " h1 { break-before: page }", "<p><a href=\"#x\">to x</a></p><h1 id=\"x\">X</h1>");
		assertEquals("警告", List.of(), out.warnings());
		try (PDDocument doc = Loader.loadPDF(out.pdf())) {
			final PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			stripper.setEndPage(1);
			final String text = stripper.getText(doc);
			assertTrue(text, java.util.regex.Pattern.compile("to x p\\.\\s*2").matcher(text).find());
		}
	}
}
