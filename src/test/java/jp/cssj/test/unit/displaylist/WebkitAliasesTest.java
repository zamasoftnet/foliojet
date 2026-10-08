package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * The {@code -webkit-} names that Chrome accepts for writing-mode, text-orientation, text-emphasis-*, line-break,
 * text-combine and ruby-position lay out as the standard names, and a declaration warning comes once per conversion
 * (2026-10-08, EPUB sweep D-24: e-book style sheets write the -webkit- names next to the -epub- and standard ones,
 * and one book with 150 items logged 4950 "unsupported" warnings).
 */
public class WebkitAliasesTest extends TestCase {
	private record Output(List<String> warnings, String displayList) {
	}

	private static Output convert(final String name, final String style, final String body, final int passCount)
			throws Exception {
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ja\"><head>"
				+ "<meta charset=\"UTF-8\"/><style>" + style + "</style></head><body>" + body + "</body></html>";
		final java.io.File dir = new java.io.File("local/webkit-aliases/" + name);
		dir.mkdirs();
		final java.io.File[] old = dir.listFiles();
		if (old != null) {
			for (final java.io.File f : old) {
				f.delete();
			}
		}
		final List<String> warnings = new ArrayList<>();
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setMessageHandler(new MessageHandler() {
					@Override
					public void message(final short code, final String[] args, final String message) {
						if (code == MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY
								|| code == MessageCodes.WARN_BAD_CSS_ARGMENTS) {
							warnings.add(message);
						}
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("processing.pass-count", String.valueOf(passCount));
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
						URI.create("file:///webkit-aliases.xhtml"), "application/xhtml+xml", null);
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
		return new Output(warnings, dl.toString());
	}

	private static final String BODY = "<p>生没年<span class=\"u\">＝AB</span>と<span class=\"e\">強調</span>"
			+ "<ruby>漢<rt>かん</rt></ruby><span class=\"t\">12</span></p><p class=\"w\">「ああ」いいい</p>";

	public void testWebkitAliases() throws Exception {
		final Output webkit = convert("webkit", "body { -webkit-writing-mode: vertical-rl }"
				+ " .u { -webkit-text-orientation: upright } .e { -webkit-text-emphasis-style: filled sesame;"
				+ " -webkit-text-emphasis-color: red; -webkit-text-emphasis-position: over right }"
				+ " .w { -webkit-line-break: strict } ruby { -webkit-ruby-position: before }"
				+ " .t { -webkit-text-combine: horizontal }", BODY, 1);
		final Output standard = convert("standard", "body { writing-mode: vertical-rl }"
				+ " .u { text-orientation: upright } .e { text-emphasis-style: filled sesame;"
				+ " text-emphasis-color: red; text-emphasis-position: over right }"
				+ " .w { line-break: strict } ruby { ruby-position: over }"
				+ " .t { -epub-text-combine: horizontal }", BODY, 1);
		assertEquals("警告", List.of(), webkit.warnings());
		assertEquals(standard.displayList(), webkit.displayList());
		final Output shorthand = convert("webkit-shorthand", "body { writing-mode: vertical-rl }"
				+ " .e { -webkit-text-emphasis: filled sesame red; text-emphasis-position: over right }", BODY, 1);
		final Output shorthandStandard = convert("standard-shorthand", "body { writing-mode: vertical-rl }"
				+ " .e { text-emphasis: filled sesame red; text-emphasis-position: over right }", BODY, 1);
		assertEquals("警告", List.of(), shorthand.warnings());
		assertEquals(shorthandStandard.displayList(), shorthand.displayList());
	}

	/** The same unsupported declaration in two rules and over two passes: one warning each. */
	public void testDeclarationWarningsOncePerConversion() throws Exception {
		final String style = "p { -webkit-word-break: break-all; color: nonsense }"
				+ " div { -webkit-word-break: break-all; color: nonsense }";
		for (final int passes : new int[] { 1, 2 }) {
			final Output out = convert("once-" + passes, style, "<div><p>text</p></div>", passes);
			assertEquals(passes + " パス: " + out.warnings(), 2, out.warnings().size());
		}
	}
}
