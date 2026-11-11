package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Nested shrink-to-fit boxes are measured once per range, not once per layout of each ancestor (2026-11-11).
 *
 * <p>
 * A shrink-to-fit box is measured by laying its content out twice (at an infinite and at a zero line size) and is then
 * bound, and a shrink-to-fit box inside it did the same in each of those three layouts: the innermost content was laid
 * out 3^depth times. 12 nested inline-blocks took 19 seconds, and each further pair of levels nine times more.
 * {@code MeasuredIntrinsics} now shares the results of the nested measurements. At depth 14 the old code takes about
 * three minutes (measured: 19 seconds at depth 12, nine times more per two levels); the shared measurement takes well
 * under a second, so the limit is far from both.
 * </p>
 */
public class NestedShrinkToFitMeasureTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final int DEPTH = 14;
	private static final long LIMIT_MILLIS = 60_000;

	public void testNestedInlineBlocksAreNotMeasuredExponentially() throws Exception {
		this.convert("nested-inline-block", ".s{display:inline-block;padding:1pt;border:0.5pt solid #999}", "<span class=\"s\">",
				"</span>");
	}

	public void testNestedFitContentBlocksAreNotMeasuredExponentially() throws Exception {
		this.convert("nested-fit-content", ".s{width:fit-content;padding:1pt;border:0.5pt solid #999}", "<div class=\"s\">", "</div>");
	}

	private void convert(final String name, final String css, final String open, final String close) throws Exception {
		final StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>")
				.append("@page{size:400pt 600pt;margin:20pt}body{font:9pt serif}").append(css).append("</style></head><body>");
		html.append(open.repeat(DEPTH)).append("<p>The quick brown fox jumps over the lazy dog. The quick brown fox.</p>")
				.append(close.repeat(DEPTH)).append("</body></html>");
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File doc = new File(dir, name + "-" + DEPTH + ".html");
		Files.writeString(doc.toPath(), html, StandardCharsets.UTF_8);
		final File pdf = new File("local/unittest/display-list/" + name + "-" + DEPTH + ".pdf");
		pdf.getParentFile().mkdirs();
		final long start = System.nanoTime();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
		final long millis = (System.nanoTime() - start) / 1_000_000;
		assertTrue("no PDF", pdf.length() > 0);
		assertTrue(name + " at depth " + DEPTH + " took " + millis + "ms: the nested measurements are not shared", millis < LIMIT_MILLIS);
	}
}
