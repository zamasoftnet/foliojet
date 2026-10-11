package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A regression of 19132 (33528938) the STRICT sweep found (2026-10-11, seeds 11587591, 11641771, 11671375, 11712899,
 * 11724383 and 11729648, all the same): a wrapping column flex whose width is auto went into columns where a block
 * builder found the line size of its ancestors settled, up to the builder's root. Laid out inside a box measured from its
 * content (an orthogonal box, a box with an intrinsic size keyword, the flex itself in the other writing mode), the
 * content was recorded by the measuring builder, which never places such a column, and the flex streamed its content;
 * replayed into a block builder rooted at that box, it was placed and opened anonymous items the recording had no
 * anchors for, and the conversion failed (ContinuationInvariantViolationException, TwoPass NO_RANGE). An auto-width
 * column wrap is one column again, whatever builder lays it out (FlexColumnWrapFillTest).
 */
public class Regression19132Test extends TestCase {
	public void testColumnWrapFlexInMeasuredRoot() throws Exception {
		final String name = "column-wrap-flex-measured-root";
		final File fixture = new File("files/fuzz-repro/" + name + ".html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/" + name + ".html");
		final File displayList = new File("build/fuzz-regressions/" + name + "-dl");
		try {
			RandomDocumentFuzzTest.checkDocument(generated, work, displayList, true, name + "-regression");
		} catch (final Throwable t) {
			final String kind = RandomDocumentFuzzTest.classify(t);
			assertTrue(name + ": " + kind + " : " + t, kind.startsWith("(除外)"));
		}
	}

	/**
	 * The same in the simplest documents: in an orthogonal block, the flex itself orthogonal, under max-content; and in a
	 * balanced multicol, laid out by the main flow first and again by the balancing builder (codex review).
	 */
	public void testColumnWrapFlexInContentSizedBoxes() throws Exception {
		final String flex = "flex-direction: column; flex-wrap: wrap";
		assertConverts("<div style=\"writing-mode: vertical-rl\"><div style=\"display: flex; " + flex
				+ "; width: 60pt\">text <span>a</span><div>item</div></div></div>");
		assertConverts("<div style=\"display: flex; writing-mode: vertical-rl; " + flex
				+ "; width: 60pt\">text <span>a</span><div>item</div></div>");
		assertConverts("<div style=\"width: max-content\"><div style=\"display: flex; " + flex
				+ "; height: 60pt\">text <span>a</span><div>item</div></div></div>");
		assertConverts("<div style=\"column-count: 2; column-fill: balance; width: 240pt\"><div style=\"display: flex; "
				+ flex + "; height: 60pt\">text <span>a</span><div>item</div></div></div>");
	}

	private static void assertConverts(final String body) throws Exception {
		final File dir = Files.createTempDirectory("regression-19132").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 0 } body { margin: 0; font: 10pt serif }</style></head><body>"
				+ body + "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final String page = Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8);
		for (final String word : new String[] { "text", "a", "item" }) {
			assertTrue(word + " がない:\n" + page, page.contains("Text[\"" + word + "\""));
		}
	}
}
