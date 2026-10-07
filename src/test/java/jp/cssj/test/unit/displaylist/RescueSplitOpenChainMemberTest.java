package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * Regression test for <b>never rescue-splitting an open box</b> (2026-09-16, reduced from seed 2003409).
 *
 * <p>
 * When an orthogonal-flow subtree and a table coexist inside `page-break-inside: avoid` in a small type area,
 * {@code FlowCutter.MoveResolution.RelaxInside} attempted to "geometrically split the trailing monolithic box"
 * but rescue-split an <b>open-chain member</b> ({@code div[page-break-inside:avoid]}).
 * On resume, {@code addRescueBound} returns a rescue remainder as a <b>closed box</b>, so
 * {@code startFlowBlock} was not called and {@code flowStack} was not rebuilt. However, the continuation
 * described that level as still open, so conversion failed with the
 * {@code RootBuilder.pageBreak} invariant "flowStack depth != continuation depth".
 * </p>
 *
 * <p>
 * This was the most common defect in the sweep (2,459 STRICT and 1,570 WILD cases among 6.4 million documents
 * for seeds 2,000,000–5,249,999), an existing defect reachable since generator v2 was introduced
 * (recorded in the {@code RandomDocumentFuzzTest} Javadoc since 2026-07-26).
 * The fix is "do not rescue-split an open-chain member; relax avoid at the boundary and cut inside
 * (= create continuation frames)." <b>avoid is impossible to honor for this box by definition</b>:
 * the break point is already inside it.
 * </p>
 */
public class RescueSplitOpenChainMemberTest extends TestCase {
	public RescueSplitOpenChainMemberTest(final String name) {
		super(name);
	}

	/** Pass all STRICT invariants (content preservation, placement within the paper, and reading order). */
	public void testOpenChainMemberIsNotRescueSplit() throws Exception {
		final File fixture = new File("files/fuzz-repro/rescue-split-open-chain-member.html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		final File work = new File("build/fuzz-regressions/rescue-split-open-chain-member.html");
		final File displayList = new File("build/fuzz-regressions/rescue-split-open-chain-member-dl");
		RandomDocumentFuzzTest.checkDocument(generated, work, displayList, true,
				"rescue-split-open-chain-member-regression");
	}
}
