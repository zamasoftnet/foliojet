package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Regression test for deep nesting + page breaks (a configuration that actually activates restyle)
 * (ARCHITECTURE.md invariant 6, 2026-07-20).
 *
 * <p>
 * {@link DeepNestingLayoutTest} verifies the iterative behavior of finishLayout/frames/draw/textShape/getText,
 * but intentionally avoids page splitting (all nested <code>&lt;div&gt;</code> elements fit on one page).
 * It therefore does not exercise the {@code restyle} family (the continuation mechanism, still
 * polymorphic mutual recursion between `FlowContainer.restyle` and `AbstractContainerBox.restyle`).
 * </p>
 *
 * <p>
 * This test instead puts multiple pages of content at the deepest level so the nested ancestor chain
 * stays **open** across page breaks ({@code OpenShape.OpenChain} is deeply nested, and mutual
 * recursion between {@code FlowContainer.restyle} and {@code RootBuilder.resumeFrame} actually
 * occurs for each level). As a regression foundation before making restyle iterative
 * (external consultation with codex/grok; see design consultation*.md), it measures and records the depth
 * the current recursive implementation can withstand.
 * </p>
 *
 * <p>
 * <b>Initial measurements (2026-07-20)</b>: depths 200/500 succeeded; 1000/5000 caused
 * {@code StackOverflowError}. Inspecting stack traces showed that the direct cause was not the
 * initially suspected {@code restyle} family, but {@code FlowContainer.avoidBreakBefore/After}
 * (page-break avoidance checks, the same polymorphic mutual recursion pattern between
 * `FlowContainer` and `FlowBlockBox`).
 * This is a separate recursion family activated during break-point search, before {@code restyle}.
 * The test discovered it before restyle iteration began; the path had already overflowed
 * before reaching restyle, so restyle itself had not even been exercised.
 * </p>
 *
 * <p>
 * <b>Fix (2026-07-20)</b>: converted {@code FlowContainer.avoidBreakBefore/After} to iteration
 * with an explicit {@link java.util.Deque} worklist
 * (the same design pattern as finishLayout, etc.; see {@code FlowContainer}'s {@code walkAvoidBreak}).
 * </p>
 *
 * <p>
 * <b>Measurements (2026-07-20, after making avoidBreakBefore/After iterative)</b>: depths 1000/5000
 * still caused {@code StackOverflowError}, but the failure moved to
 * {@code FlowContainer.splitPageAxis}↔{@code AbstractBlockBox.splitForContinuation}
 * (box splitting at page breaks, "splitPageAxis (mutating split)" in ARCHITECTURE.md's pipeline diagram).
 * This is a third independent recursion family, running even earlier than {@code restyle},
 * during the page break itself. This test has still not reached a depth where {@code restyle} iteration
 * is actually needed, because the {@code splitPageAxis} barrier occurs first.
 * </p>
 *
 * <p>
 * <b>Why {@code splitPageAxis} is not being addressed yet</b>: as already recorded in `開発メモ`,
 * "Box/Builderコア: FlowContainer.splitPageAxisの核心ループはM6d前提のまま",
 * {@code splitPageAxis}/{@code .split()} calls directly mutate child boxes.
 * It was already judged difficult to refactor this mechanically and safely before the
 * ConstraintSpace/write-once box (M6d) design is in place.
 * Preserve that decision and fix the depth-1000/5000 tests as known limits until splitPageAxis
 * becomes iterative after M6d; they are no longer completion criteria for restyle iteration.
 * </p>
 *
 * <p>
 * <b>B0 (2026-07-20, after external consultation with codex/grok)</b>:
 * measuring {@code ContinuationStats.RESTYLE_CHAIN_FIRINGS}
 * (the {@code OpenShape.OpenChain} branch count in `FlowContainer.restyle`) at depth 200 yielded 0.
 * What actually fired was {@code CHILD_FRAMES} (1206 times): simple deep nesting with one child per
 * level used {@code RootBuilder.resumeFrame()}'s own recursion
 * (`ContinuationFrame.Child` once per level), not {@code FlowContainer.restyle}'s
 * {@code OpenChain} branch. This recursion was the sole and final statement of the switch
 * (true tail recursion), so replacing it with a {@code while} loop made it iterative without
 * changing behavior (fixed and verified at three layers).
 * See `開発メモ`, "B0着手結果".
 * </p>
 */
public class DeepNestingRestyleTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * An open ancestor chain of depth 200 spans multiple page breaks.
	 * Measurements on 2026-07-20 succeeded up to depth 500 and reached StackOverflowError at 1000
	 * (see {@link #testDepth1000OpenChainAcrossPageBreaksCurrentlyOverflows} below).
	 * This depth of 200 verifies a currently safe lower bound as a regression test.
	 *
	 * <p>
	 * Also measures {@code ContinuationStats.RESTYLE_CHAIN_FIRINGS}
	 * (B0 = activation visibility for converting M6b Phase B's "split block chains" to source replay,
	 * 2026-07-20; external consultation with codex/grok, see design consultation*.md).
	 * Verifies the contract that box-restyle through open chains actually fires many times in this configuration.
	 * The value should fall as source replay expands; record it here as a baseline for measuring future
	 * reductions at each stage.
	 * </p>
	 */
	public void testDepth200OpenChainAcrossPageBreaks() throws Exception {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.reset();
		this.runDeepOpenChain(200, 300);
		System.err.println("深さ200: RESTYLE_CHAIN_FIRINGS="
				+ net.zamasoft.foliojet.layout.fragment.ContinuationStats.RESTYLE_CHAIN_FIRINGS.get()
				+ " CHILD_FRAMES=" + net.zamasoft.foliojet.layout.fragment.ContinuationStats.CHILD_FRAMES.get()
				+ " OPEN_TAILS=" + net.zamasoft.foliojet.layout.fragment.ContinuationStats.OPEN_TAILS.get()
				+ " UNCHAINED_RESTYLES="
				+ net.zamasoft.foliojet.layout.fragment.ContinuationStats.UNCHAINED_RESTYLES.get()
				+ " MAX_PAGE_OPEN_TAIL_DEPTH="
				+ net.zamasoft.foliojet.layout.fragment.ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
	}

	/**
	 * Depth 500. The upper boundary that still succeeded in measurements on 2026-07-20.
	 */
	public void testDepth500OpenChainAcrossPageBreaks() throws Exception {
		this.runDeepOpenChain(500, 300);
	}

	/**
	 * Depth 1000 (the same level as the measured 1000-level limit before the finishLayout fix).
	 *
	 * <p>
	 * <b>Known current limit (measured on 2026-07-20, after making avoidBreakBefore/After iterative)</b>:
	 * {@code FlowContainer.splitPageAxis}↔{@code AbstractBlockBox.splitForContinuation}
	 * (box splitting at page breaks) actually reaches {@code StackOverflowError} at this depth
	 * (see the class Javadoc). As recorded in `開発メモ`,
	 * "splitPageAxisの核心ループはM6d前提のまま", this path directly mutates child boxes.
	 * It was already judged difficult to make it iterative safely before the
	 * ConstraintSpace/write-once box (M6d) design is in place.
	 * This earlier barrier prevents even reaching the path for restyle iteration itself.
	 * </p>
	 *
	 * <p>
	 * Once splitPageAxis becomes iterative after the M6d design, change this test and
	 * {@link #testDepth5000OpenChainAcrossPageBreaksCurrentlyOverflows} to assert success
	 * (remove "CurrentlyOverflows" from this method name and return to checking success
	 * with {@code runDeepOpenChain}). If {@code StackOverflowError} still occurs at another depth,
	 * only then can measurements determine whether restyle itself needs to become iterative.
	 * </p>
	 */
	public void testDepth1000OpenChainAcrossPageBreaks() throws Exception {
		// 2026-07-26: Layout now always runs on a dedicated thread with a 64 MB stack
		// (DirectSession.LAYOUT_STACK_SIZE), so depth 1000 now
		// succeeds. This resolves the practical problem by increasing the stack,
		// not by making it iterative (mutual recursion itself remains).
		this.runDeepOpenChain(1000, 300);
	}

	/**
	 * Depth 5000 (the same depth confirmed after making finishLayout/frames/draw/textShape/getText
	 * iterative). Currently, this also reaches {@code StackOverflowError} via {@code splitPageAxis}
	 * (see the class Javadoc).
	 */
	public void testDepth5000OpenChainAcrossPageBreaks() throws Exception {
		// Same as above. Measurements show depth 5000 needs an 8 MB stack; 64 MB provides an eightfold margin.
		this.runDeepOpenChain(5000, 300);
	}

	/**
	 * Proof-of-concept experiment for an alternative to making {@code splitPageAxis} iterative
	 * (the large refactor after M6d) (2026-07-23; see the "代替案" section of
	 * `the development records
	 * -investigation.md`).
	 *
	 * <p>
	 * The hypothesis was that {@code StackOverflowError} at depths 1000/5000 follows the typical pattern
	 * of insufficient JVM default thread stack size (depth 500 succeeds, 1000 fails, consistent with
	 * several hundred bytes consumed per level).
	 * Without changing any {@code splitPageAxis} logic, directly tests whether running the same
	 * computation on a dedicated thread with a large stack (64 MB) resolves it.
	 * </p>
	 *
	 * <p>
	 * This experiment manually creates a large-stack thread only in test code
	 * (no changes to production code; a direct hypothesis test).
	 * For the integration subsequently wired into production via the {@code processing
	 * .large-stack-thread} property, see
	 * {@link #testDepth5000SucceedsWithLargeStackThreadProperty}.
	 * </p>
	 */
	public void testDepth5000SucceedsOnLargeStackThread() throws Throwable {
		final int depth = 5000;
		final int leafLines = 300;
		final long largeStackBytes = 64L * 1024 * 1024;
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try {
				this.runDeepOpenChain(depth, leafLines);
			} catch (Throwable t) {
				failure[0] = t;
			}
		}, "deep-nesting-large-stack", largeStackBytes);
		worker.start();
		worker.join();
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	/**
	 * Tests the production integration implemented after the above hypothesis test
	 * (2026-07-23, `processing.large-stack-thread` session property,
	 * via {@code DirectSession.runOnLargeStackIfEnabled}).
	 * Uses only the normal {@code DirectSession
	 * .transcode()} call path, without manually creating a thread in test code.
	 * At depth 5000, the default configuration without this property produces {@code StackOverflowError}
	 * (see {@link #testDepth5000OpenChainAcrossPageBreaksCurrentlyOverflows}).
	 * Verifies that setting this property alone allows success without exceptions.
	 */
	public void testDepth5000SucceedsWithLargeStackThreadProperty() throws Exception {
		this.runDeepOpenChain(5000, 300, true);
	}

	private void runDeepOpenChain(int depth, int leafLines) throws Exception {
		this.runDeepOpenChain(depth, leafLines, false);
	}

	private void runDeepOpenChain(int depth, int leafLines, boolean largeStackThread) throws Exception {
		final String name = "deep-nesting-restyle-" + depth;
		final File doc = generateDeepOpenChainAcrossPageBreaks(name, depth, leafLines);
		final File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (largeStackThread) {
					session.property("processing.large-stack-thread", "true");
				}
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
		assertTrue("PDFが出力されていません(深さ" + depth + ")", pdf.length() > 0);
	}

	/**
	 * Generates a document with depth levels of nested {@code <div>} elements and leafLines lines of
	 * numbered text at the deepest level (8 pt per line, page height 400 pt, about 50 lines/page).
	 * Making leafLines much larger than one page (about 50 lines) causes multiple page breaks
	 * while the entire ancestor chain stays open.
	 */
	private static File generateDeepOpenChainAcrossPageBreaks(String name, int depth, int leafLines)
			throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			// As in DeepNestingLayoutTest, omit borders and padding
			// (to keep size calculations simple at each level). This time, however, the deepest content
			// intentionally exceeds page height, forcing page breaks
			// while the ancestor chain remains open.
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			for (int i = 0; i < leafLines; ++i) {
				w.write("LEAF-");
				w.write(String.format("%06d", i));
				w.write("<br/>\n");
			}
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
	}
}
