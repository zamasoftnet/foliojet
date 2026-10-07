package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that <b>single-SVG output can preserve characters as {@code <text>}</b>
 * (B-1, user request on 2026-08-29).
 *
 * <p>
 * With {@code output.svg.text: keep}, a WOFF2 subset is embedded in the SVG using {@code data:},
 * making the single image self-contained. The default ({@code outline}) still converts everything
 * to shapes as before.
 * </p>
 */
public class SvgKeepTextTest extends TestCase {
	private static final String FILE = "files/unittest/0480-svg-text/keep-text.html";

	/** The default remains outlines: no {@code <text>} appears. */
	public void testOutlineByDefault() throws Exception {
		final String svg = convert(null);
		assertTrue("SVGになっていません", svg.contains("<svg"));
		assertFalse("既定でテキストが残っています", svg.contains("<text"));
		assertFalse("既定で@font-faceが出ています", svg.contains("@font-face"));
	}

	/** With keep, {@code <text>} remains and WOFF2 is embedded using {@code data:}. */
	public void testKeepEmbedsFontAndText() throws Exception {
		final String svg = convert("keep");
		assertTrue("テキストが残っていません", svg.contains("<text"));
		assertTrue("WOFF2が埋め込まれていません", svg.contains("src:url('data:font/woff2;base64,"));
		assertFalse("外部参照が残っています", svg.contains("url('../"));
		// Subset glyphs use private-use code points, so the original text appears in
		// aria-label / data-copper-text (used for read-aloud and search).
		assertTrue("元の文字列が残っていません", svg.contains("aria-label=\"日本語のテキスト\""));
		assertTrue("data-copper-textが付いていません", svg.contains("data-copper-text=\"日本語のテキスト\""));
	}

	/**
	 * <b>Every</b> subset referenced by {@code <text>} is inside the SVG.
	 * Even one external subset would turn its characters into missing-glyph boxes.
	 */
	public void testEveryReferencedSubsetIsEmbedded() throws Exception {
		final String svg = convert("keep");
		final java.util.Set<String> used = matches(svg, "font-family=\"(CopperSubset[0-9]+)\"");
		final java.util.Set<String> declared = matches(svg, "@font-face\\{font-family:'(CopperSubset[0-9]+)'");
		assertFalse("サブセットが1つも使われていません", used.isEmpty());
		assertEquals("参照と@font-faceが食い違っています", declared, used);
		assertEquals("@font-faceと埋め込みの数が合いません", declared.size(), count(svg, "data:font/woff2"));
	}

	/**
	 * Latin text covered by the Core 14 fonts remains <b>actual characters</b>.
	 * Subsetting would use private-use code points, making copied text unreadable.
	 */
	public void testCoreFontsStayRealText() throws Exception {
		final String svg = convert("keep");
		assertTrue("欧文が本物の文字で残っていません", svg.contains(">Copper</text>"));
	}

	private static java.util.Set<String> matches(final String s, final String regex) {
		final java.util.Set<String> found = new java.util.LinkedHashSet<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(s);
		while (m.find()) {
			found.add(m.group(1));
		}
		return found;
	}

	private static int count(final String s, final String needle) {
		int n = 0;
		for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) {
			++n;
		}
		return n;
	}

	private static String convert(final String textMode) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "image/svg+xml");
			if (textMode != null) {
				session.property("output.svg.text", textMode);
			}
			CTISessionHelper.transcodeFile(session, new File(FILE), "text/html", null);
		} finally {
			session.close();
		}
		final String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
		// Save the result with -Dfoliojet.svgdump=<prefix> (for viewing in a browser).
		final String dump = System.getProperty("foliojet.svgdump");
		if (dump != null) {
			java.nio.file.Files.writeString(java.nio.file.Path.of(dump + "-" + textMode + ".svg"), svg,
					StandardCharsets.UTF_8);
		}
		return svg;
	}
}
