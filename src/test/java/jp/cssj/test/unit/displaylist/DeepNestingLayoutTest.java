package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
 * Regression test for box-tree traversal in deeply nested documents
 * (ARCHITECTURE.md invariant 6, 2026-07-20; prevents recurrence of
 * {@code StackOverflowError} observed on real e-gov.go.jp legislation pages).
 *
 * <p>
 * Before the fix, {@code AbstractContainerBox.finishLayout} used polymorphic mutual recursion
 * ("local processing at each level → delegate to children", crossing overrides for each box type).
 * Nesting beyond 1000 levels caused {@code StackOverflowError}
 * (see "Separate follow-up" in the development record; an earlier attempted regression test also
 * depended on this then-unfixed bug and was deferred because it could not stand alone).
 * This test verifies the contract that iteration over an explicit worklist using
 * {@link net.zamasoft.foliojet.layout.box.FinishLayoutStep} resolves the issue.
 * </p>
 *
 * <p>
 * Update as of 2026-07-20: immediately after this fix, the {@code AbstractContainerBox.draw} family
 * (draw/drawFlows/drawFloatings/drawAbsolutes) still used polymorphic mutual recursion,
 * and a **different** {@code StackOverflowError} was confirmed at depth 1500
 * (finishLayout itself had been resolved without a depth limit).
 * Subsequently, all four families—frames ({@link net.zamasoft.foliojet.layout.box.FramesStep}),
 * draw ({@link net.zamasoft.foliojet.layout.box.DrawStep}),
 * textShape ({@link net.zamasoft.foliojet.layout.box.TextShapeStep}), and
 * getText ({@link net.zamasoft.foliojet.layout.box.GetTextStep})—were converted to the same iterative
 * pattern, and success was confirmed even after raising this test's depth to 5000.
 * Only the {@code restyle} family remains **unresolved** (it is deeply intertwined with the
 * continuation mechanism, BlockBuilder's state machine, and needs separate careful design;
 * user decision on 2026-07-20; see the RELIABILITY-PLAN.md ledger).
 * This boundary test intentionally avoids page splitting (see generateDeeplyNestedDivs below),
 * so the restyle family is not exercised and remains outside its scope.
 * </p>
 */
public class DeepNestingLayoutTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * Verifies completion from layout through PDF output at a depth far exceeding the limits resolved
	 * by making finishLayout/frames/draw/textShape/getText iterative
	 * (before the fixes, finishLayout caused StackOverflowError at around 1000 levels,
	 * and draw at around 1500).
	 */
	public void testDeeplyNestedDivsLayoutWithoutStackOverflow() throws Exception {
		final File doc = generateDeeplyNestedDivs("deep-nesting-5000", 5000);
		final File pdf = new File("local/unittest/display-list/deep-nesting-5000.pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
		assertTrue("PDFが出力されていません", pdf.length() > 0);
	}

	/**
	 * Generates a document with the specified number of nested {@code <div>} levels.
	 * Since it is not compared to a golden, generate it each time in local/unittest
	 * rather than placing it in files/unittest.
	 *
	 * @param name  generated filename (without extension)
	 * @param depth nesting depth
	 */
	private static File generateDeeplyNestedDivs(String name, int depth) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			// Do not add borders or padding: if each level has height, the document exceeds one page
			// and triggers continuation (OpenShape.depth, another known recursion issue),
			// preventing isolated verification of finishLayout iteration.
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			w.write("nested content");
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
	}
}
