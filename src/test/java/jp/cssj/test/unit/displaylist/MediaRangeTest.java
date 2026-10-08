package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.css.parser.AtRulePreludeRewriter;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * The range syntax of Media Queries 4 ({@code (width <= 996px)}, {@code (400px < width <= 700px)}), 2026-10-09.
 *
 * <p>
 * ph-css 8.2.1 cannot read it and dropped the whole {@code @media} block, so rules that match the page did not
 * apply (Docusaurus writes its breakpoints this way). {@link AtRulePreludeRewriter} rewrites each range feature into the
 * Level 3 form; the strict comparisons and aspect-ratio are evaluated by {@code CSSStyleSheetBuilder}. The expected
 * margins are Chrome 151's for {@code files/unittest/3070-AT-RULE/media-range.html} with an 800x600 viewport (the page
 * here is 800px x 600px).
 * </p>
 */
public class MediaRangeTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=([-0-9.]+) y=([-0-9.]+) Text\\[\"([^\"]*)\"");

	public void testSameAsChrome() throws Exception {
		final Map<String, Double> x = textX("files/unittest/3070-AT-RULE/media-range.html", "media-range");
		final double[] chromePx = { 10, 0, 30, 0, 50, 0, 70, 80, 0, 100, 110, 0, 130, 0, 150, 160, 170, 180 };
		for (int i = 0; i < chromePx.length; ++i) {
			final String text = "R" + (i + 1);
			assertNotNull(text + " is not drawn: " + x.keySet(), x.get(text));
			assertEquals(text, chromePx[i] * 0.75, x.get(text), 0.5);
		}
	}

	public void testRewrite() {
		assertEquals("@media (max-width: 996px){a{b:c}}",
				AtRulePreludeRewriter.rewrite("@media (width <= 996px){a{b:c}}", s -> false));
		assertEquals("@media screen and (-foliojet-gt-width: 400px) and (max-width: 700px){}",
				AtRulePreludeRewriter.rewrite("@media screen and (400px < width <= 700px){}", s -> false));
		assertEquals("@media (min-height: 30em), print{}",
				AtRulePreludeRewriter.rewrite("@media (30em <= height), print{}", s -> false));
		assertEquals("@media (-foliojet-lt-aspect-ratio: 16/9){}",
				AtRulePreludeRewriter.rewrite("@media (aspect-ratio<16/9){}", s -> false));
		// Other features do not match, as before; the Level 3 forms are left alone.
		assertEquals("@media (-foliojet-unknown: 0){}",
				AtRulePreludeRewriter.rewrite("@media (resolution >= 2dppx){}", s -> false));
		final String untouched = "@media (min-width: 400px) and (orientation: landscape){a{b:c}}";
		assertSame(untouched, AtRulePreludeRewriter.rewrite(untouched, s -> false));
	}

	/** The x of the first drawing of each text, converted with the page set to 800px x 600px. */
	private static Map<String, Double> textX(final String fixture, final String name) throws Exception {
		final File dir = new File("local/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.property("output.page-width", "800px");
				session.property("output.page-height", "600px");
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeFile(session, new File(fixture), "text/html", null);
			} finally {
				session.close();
			}
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		java.util.Arrays.sort(pages);
		final Map<String, Double> out = new LinkedHashMap<>();
		for (final File page : pages) {
			final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				out.putIfAbsent(m.group(3), Double.parseDouble(m.group(1)));
			}
		}
		return out;
	}
}
