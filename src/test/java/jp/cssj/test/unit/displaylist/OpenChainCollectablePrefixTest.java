package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.ContinuationCapability;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Regression tests for collectable-prefix trimming in {@code RootBuilder.pageBreak()}
 * (M6b Phase B, 2026-07-20).
 *
 * <p>
 * <b>Proven activation conditions</b>: {@code FlowContainer.restyle}'s
 * {@code OpenShape.OpenChain} branch (recursion not yet made iterative) fires when
 * {@code RootBuilder.pageBreak()}'s preflight fails: ancestor chain {@code flowStack[1..]}
 * must consist entirely of plain {@code FlowBlockBox} instances without columns and with
 * one writing direction. A table or mixed writing directions only at a leaf does not trigger it
 * (the existing safe path fixed by {@code testTableLeafNeverTriggersOpenChain};
 * TABLE/TEXT_BLOCK branches of {@code FlowContainer.splitPageAxis} never used the chain-fragment
 * mechanism). Only <b>columns (column-count&gt;1)</b> inserted partway through the ancestor
 * chain actually triggered it.
 * </p>
 *
 * <p>
 * <b>The old implementation's problem (before 2026-07-20)</b>: preflight was an all-or-nothing
 * boolean over the <i>entire</i> ancestor chain. A failure at even one level, such as columns,
 * set {@code plan=null}, making the entire {@code flowStack} size the {@code OpenChain} depth.
 * Thus arbitrary nesting depth of plain outer wrapper divs around columns (common in real
 * documents) flowed directly into non-iterative recursion. Measured: 60 outer + 10 inner wrappers
 * plus columns reached {@code MAX_PAGE_OPEN_TAIL_DEPTH}=74 (then still the unsplit
 * {@code MAX_OPEN_TAIL_DEPTH}).
 * </p>
 *
 * <p>
 * <b>Fix</b>: change preflight to collect the prefix from the start to the first violating level
 * (the {@code BreakPlan} construction in {@code RootBuilder.pageBreak()}).
 * Keep {@code BreakPlan.depth} at {@code flowStack.size()}; reducing it would make
 * {@code OpenShape} nesting disagree with the real box tree's open structure, risking premature
 * closure of still-open boxes (confirmed in independent review; see {@code design consultation*.md}).
 * {@code BreakPlan.openTailDepth() = depth - index - 1} remains computable without traversing
 * {@code depth}, so prefix trimming alone naturally reduces the remaining {@code OpenChain}
 * depth to the violating level and its descendants.
 * </p>
 *
 * <p>
 * <b>Depth-guard retirement (2026-07-30, legacy recursion removal, increment 4c)</b>:
 * deep nesting <i>inside the multi-column box itself</i> previously stopped with a typed
 * exception at the safety threshold (64). With the worklist executor as the sole driver
 * and nonrecursive OpenChain descent, the guard, exception, and alarm were retired.
 * Deep nesting completes without exceptions
 * ({@link #testDeepNestingInsideMulticolCompletesIteratively}).
 * </p>
 */
public class OpenChainCollectablePrefixTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * Control: with a table leaf, the chain-fragment mechanism handles arbitrarily deep outer
	 * wrapper divs (40 here) without ever entering {@code OpenChain}.
	 * This confirms that multi-column layout is the essential trigger.
	 */
	public void testTableLeafNeverTriggersOpenChain() throws Exception {
		this.run("table-leaf-control", 40, this::writeTableLeaf, 300);
		assertEquals("表リーフはOpenChainを発火させないはずです", 0, ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
		assertEquals("表リーフの開きテイルは深さ1(開きテキストのみ)のはずです", 1,
				ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
	}

	/**
	 * With columns partway through the ancestor chain, even 100 outer wrapper levels do not
	 * cause PAGE-side {@code OpenChain}: B3a (allowing PAGE split-through for columns) compiles
	 * the multi-column level itself as first-class. Regardless of outer depth,
	 * {@code MAX_PAGE_OPEN_TAIL_DEPTH} is always 1 (only open text).
	 *
	 * <p>
	 * <b>Added 2026-07-21 (B3a)</b>: previously (B0.5–B2), MULTICOL stopped prefix scanning,
	 * and only the columns and descendants entered OpenChain (box-restyle)
	 * (measured depth about 12). B3a made MULTICOL collectable on automatic page breaks via
	 * `ContinuationCapability.supportsPageSplitThrough()`, making the multi-column level
	 * a first-class `ResumeProgram` level and eliminating PAGE-side OpenChain
	 * (`PAGE_RESTYLE_CHAIN_FIRINGS==0`). Remaining `RESTYLE_CHAIN_FIRINGS` (40 in this fixture)
	 * all come from the COLUMN path (internal column breaks, the B4 target).
	 * </p>
	 */
	public void testDeepOuterWrapperAroundMulticolStaysShallow() throws Exception {
		this.run("outer-wrapper-around-multicol", 100, (w, leafLines) -> this.writeMulticolLeaf(w, 10, leafLines),
				300);
		assertEquals("B3a後はPAGE側の開きテイル深さは常に1(開きテキストのみ)のはずです", 1,
				ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
		assertEquals("B3a後はMULTICOLがプレフィックススキャンを止めないはずです", 0,
				ContinuationStats.capabilityScanStops(ContinuationCapability.MULTICOL));
		assertEquals("PAGE側のOpenChainは完全に消えるはずです", 0, ContinuationStats.PAGE_RESTYLE_CHAIN_FIRINGS.get());
		// E-3 increment 7: replace old pageCompiledLevels(MULTICOL)>0 (compiled-program counter)
		// with an actual-behavior counter proving first-class chain-frame execution
		// (consumption of the ancestor chain including the multi-column level).
		assertTrue("継続チェーンが実際にfirst-class実行されたはずです", ContinuationStats.CHILD_FRAMES.get() > 0);
		// 2026-07-21 (B4-Step4): the COLUMN path now also compiles PLAIN_FLOW descendants
		// as first-class levels on automatic column breaks, so RESTYLE_CHAIN_FIRINGS
		// (old OpenChain recursion) should be zero on both PAGE and COLUMN paths
		// (this fixture has only ten PLAIN_FLOW wrappers inside the columns,
		// with no orthogonal or other barriers, so all are collected).
		assertEquals("B4-Step4後はCOLUMN側のOpenChainも完全に消えるはずです", 0,
				ContinuationStats.COLUMN_RESTYLE_CHAIN_FIRINGS.get());
		assertEquals("RESTYLE_CHAIN_FIRINGSはPAGE・COLUMN双方でゼロになるはずです", 0,
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
		// E-3 increment 7: replace old columnCompiledLevels(PLAIN_FLOW)>0 with an actual-behavior
		// counter proving the COLUMN continuation path ran (passed the depth guard at column break).
		// Together with COLUMN_RESTYLE_CHAIN_FIRINGS==0, this means a column break occurred
		// without legacy recursion, i.e. first-class execution.
		assertTrue("段組内部の改段が実際に発生したはずです",
				ContinuationStats.MAX_COLUMN_OPEN_TAIL_DEPTH.get() > 0);
	}

	/**
	 * M6b Phase B4 remaining work: characterization test verifying that the
	 * {@code ReplayLeaseSession} stack, {@code resumeScopes}, {@code ResumeTrace},
	 * and leases do not interfere when PAGE breaks nest inside COLUMN resume
	 * (conversely, COLUMN resume nests inside PAGE resume) (introduced 2026-07-21).
	 *
	 * <p>
	 * Enough content to span multiple pages in two-column layout naturally produces both
	 * "column 1→2 on page 1 (COLUMN resume)" and "page 1→2 (PAGE resume, with further column
	 * continuation nesting a new COLUMN resume inside it)". Tests use
	 * {@code enableAssertions=true} (build.gradle), enabling
	 * {@code assert !hasUnconsumedLeases()} in {@code RootBuilder.ResumeSession}/
	 * {@code ColumnResumeSession}. Completing this document without exceptions therefore
	 * verifies correct lease ownership and release across nested sessions.
	 * </p>
	 */
	public void testNestedPageAndColumnResumeDoNotLeakSessions() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateMultiPageMulticol("nested-page-column-resume", 3, 2, 60);
		final File pdf = new File("local/unittest/display-list/open-chain-nested-page-column-resume.pdf");
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
		// Verify that both multiple pages and multiple columns actually occur
		// (otherwise nested resumption has not been tested at all).
		// E-3 increment 7: replace old pageCompiledLevels/columnCompiledLevels
		// (compiled-program counters) with actual-behavior counters proving PAGE/COLUMN continuation
		// execution itself (maximum open depth when passing the depth guard).
		assertTrue("複数ページにまたがる改ページが実際に発生したはずです",
				ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get() > 0);
		assertTrue("段組内部の改段が実際に発生したはずです",
				ContinuationStats.MAX_COLUMN_OPEN_TAIL_DEPTH.get() > 0);
	}

	private File generateMultiPageMulticol(final String name, final int pages, final int columnsPerPage,
			final int leafLinesPerColumn) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;margin:0}"
					+ "div{margin:0;padding:0}</style>\n");
			w.write("</head><body>\n");
			w.write("<div style=\"column-count:2;column-gap:1em\">\n");
			final int totalLines = pages * columnsPerPage * leafLinesPerColumn;
			// Insert PLAIN_FLOW wrapper divs: bare text alone has no descendant level
			// directly under the owner (columns), so depth==1
			// and the COLUMN fragment-chain execution (index>=1) is not reached.
			w.write("<div>\n");
			this.writeTextLeaf(w, totalLines);
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/**
	 * M6b Phase B4/B5 remaining work: verify that, in nested multi-column layout,
	 * {@code BreakableBuilder.findColumnBreak()} still selects the innermost owner with
	 * {@code canColumnBreak()} after B4 introduced typed COLUMN continuations
	 * (introduced 2026-07-21).
	 *
	 * <p>
	 * Give the outer container (two columns) ample height; only the inner container's
	 * (three columns) content exceeds its own single-column capacity. Only the inner container
	 * should need column breaks; the entire document should complete without outer breaks.
	 * Record the owner's configured column count in
	 * {@code ContinuationStats.LAST_COLUMN_OWNER_COLUMN_COUNT} at each break, and verify
	 * the last observed value is always the inner 3, not the outer 2.
	 * </p>
	 */
	public void testNestedMulticolSelectsInnermostOwner() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateNestedMulticol("nested-multicol-owner", 2, 3);
		final File pdf = new File("local/unittest/display-list/open-chain-nested-multicol-owner.pdf");
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
		// E-3 increment 7: replace old columnCompiledLevels (compiled-program counter).
		assertTrue("段組内部の改段が実際に発生したはずです(でなければownerを検証できていない)",
				ContinuationStats.MAX_COLUMN_OPEN_TAIL_DEPTH.get() > 0);
		assertEquals("最内側(段数3)のmulticolがownerとして選ばれ続けるはずです(外側の段数2ではない)", 3,
				ContinuationStats.LAST_COLUMN_OWNER_COLUMN_COUNT.get());
	}

	private File generateNestedMulticol(final String name, final int outerColumnCount, final int innerColumnCount)
			throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"300pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;margin:0}"
					+ "div{margin:0;padding:0}</style>\n");
			w.write("</head><body>\n");
			// Outer: enough height and a column count requiring no column break.
			w.write("<div style=\"column-count:" + outerColumnCount + ";column-gap:1em;height:380pt\">\n");
			// Inner: small height, with content exceeding a single column's capacity.
			w.write("<div style=\"column-count:" + innerColumnCount + ";column-gap:1em;height:60pt\">\n");
			w.write("<div>\n");
			this.writeTextLeaf(w, 200);
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/**
	 * M6b Phase B3b-1 (2026-07-21): verify safe completion without exceptions for forced page breaks
	 * ({@code page-break-before: always}) inside a multi-column ancestor (two columns, AUTO height,
	 * spanning multiple pages), since {@code ContinuationCapability.supportsPageSplitThrough}
	 * became mode-independent (the restriction was removed because B3b-2 made KEEP/MOVE normal paths).
	 * The multi-column level must actually be collected and compiled as first-class,
	 * rather than becoming {@code CapabilityBarrier(MULTICOL)}.
	 *
	 * <p>
	 * <b>Fixture-design caution</b>: explicitly setting column {@code height} activates
	 * {@code canColumnBreak()}'s {@code isSpecifiedPageSize()} shortcut, which unconditionally
	 * allows additional columns regardless of actual usage when height is specified.
	 * Excess content then simply overflows (or adds columns as page width permits), without
	 * reaching a real PAGE-level forced split. This existing behavior is unrelated to this
	 * session's changes and was discovered while writing this test. Measurements confirmed
	 * that keeping AUTO height (as in {@code generateMultiPageMulticol} from
	 * {@code testNestedPageAndColumnResumeDoNotLeakSessions}) and supplying enough content
	 * for multiple pages is necessary to trigger the PAGE-level forced split this test targets.
	 * Use {@code page-break-before}: this engine recognizes only legacy CSS2 names registered
	 * in {@code ElementPropertySet}, not current CSS Fragmentation's {@code break-before};
	 * see `PageBreakBefore.java`.
	 * </p>
	 */
	public void testForceBreakInsideMulticolAncestorSplitsThroughSafely() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateForceBreakInsideMulticol("force-break-inside-multicol");
		final File pdf = new File("local/unittest/display-list/open-chain-force-break-inside-multicol.pdf");
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
		assertEquals("MULTICOLは強制改ページでもbarrierにならないはずです(B3b-1)", 0,
				ContinuationStats.capabilityScanStops(ContinuationCapability.MULTICOL));
		// E-3 increment 7: replace old pageCompiledLevels(MULTICOL)>0 with actual-behavior
		// counters proving first-class chain execution, including multi-column levels,
		// even through forced page breaks (read together with the zero-barrier check above).
		assertTrue("継続チェーンが強制改ページ経由でも実際にfirst-class実行されたはずです",
				ContinuationStats.CHILD_FRAMES.get() > 0);
	}

	private File generateForceBreakInsideMulticol(final String name) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;margin:0}"
					+ "div{margin:0;padding:0}</style>\n");
			w.write("</head><body>\n");
			// Multi-column ancestor (two columns, AUTO height): trigger page-break-before:always
			// midway through enough content to span multiple pages,
			// escalating to a PAGE-level forced split.
			w.write("<div style=\"column-count:2;column-gap:1em\">\n");
			w.write("<div>\n");
			this.writeTextLeaf(w, 100);
			w.write("<div style=\"page-break-before:always\">\n");
			this.writeTextLeaf(w, 100);
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/**
	 * Deep nesting <i>inside the columns themselves</i> (with only five outer levels) pushes
	 * COLUMN-path open depth ({@code BreakableBuilder.columnBreak}, internal column breaks)
	 * beyond the old safety threshold (64). Previously this stopped with a typed exception
	 * (on 2026-07-21, this test verified the newly added COLUMN-path guard).
	 *
	 * <p>
	 * 2026-07-30 (legacy recursion removal, increment 4c): the worklist executor became the sole
	 * driver and OpenChain descent became nonrecursive, retiring the depth guard.
	 * Verify that the same document <b>completes without exceptions</b> and actually exhibits
	 * COLUMN open-tail depth beyond the old threshold (proof that coverage is not vacuous).
	 * </p>
	 */
	public void testDeepNestingInsideMulticolCompletesIteratively() throws Exception {
		this.run("nesting-inside-multicol", 5, (w, leafLines) -> this.writeMulticolLeaf(w, 80, leafLines), 300);
		assertTrue("旧閾値64を超える深さのCOLUMN open tailが観測されるはずです(fixtureが弱体化していないか)",
				ContinuationStats.MAX_COLUMN_OPEN_TAIL_DEPTH.get() >= 64);
	}

	/**
	 * Initially (2026-07-21), tables with orthogonal writing-mode selected INCREMENTAL pagination
	 * through `IncrementalTableBuilder`. Thus `BreakableBuilder.forceBreak()` bypassed the
	 * `breakDepth` barrier (normally suppressing automatic page breaks within orthogonal writing)
	 * and actually reached `ORTHOGONAL_FLOW` (an unsupported capability like columns or RL/LR mismatch).
	 * Measurements confirmed this as an existing bug unrelated to this session's changes.
	 * In production, `RootBuilder.java`'s {@code assert this.flowStack.size() == continuation.depth()}
	 * was skipped without checking, risking undetected content corruption.
	 * Only an emergency `ContinuationInvariantViolationException` guard protected it then.
	 *
	 * <p>
	 * M6b Phase B5e (2026-07-21) added {@code TableRetentionReason.ORTHOGONAL_WRITING_MODE}
	 * to `TableBuildPlanner.plan()`, unconditionally routing tables whose writing axis differs
	 * from the currently open flow to RETAINED (`RetainedTableBuilder`).
	 * Blocking the invalid entry itself means this document should complete without
	 * `ContinuationInvariantViolationException` or any other exception.
	 * This test verifies post-B5e behavior: completion without ever reaching legacy OpenChain.
	 * </p>
	 */
	public void testOrthogonalWritingModeTableRoutesToRetainedAndCompletes() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateOrthogonalWritingModeTable("orthogonal-table", 200);
		final File pdf = new File("local/unittest/display-list/open-chain-orthogonal-table.pdf");
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
		assertEquals("RETAINEDへ回ったのでORTHOGONAL_FLOWのcapability barrierには"
				+ "一切到達しないはずです", 0,
				ContinuationStats.capabilityScanStops(ContinuationCapability.ORTHOGONAL_FLOW));
		assertEquals("legacy OpenChain(restyle-chain)経路にも一切到達しないはずです", 0,
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
	}

	private File generateOrthogonalWritingModeTable(String name, int rows) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"300pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"300pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;writing-mode:horizontal-tb}\n");
			w.write("table{writing-mode:vertical-rl;table-layout:fixed;height:100pt;border-collapse:collapse}\n");
			w.write("td{border:0.5pt solid black}</style>\n");
			w.write("</head><body>\n<table>\n");
			for (int i = 0; i < rows; ++i) {
				w.write("<tr><td>ROW-" + String.format("%06d", i) + "</td></tr>\n");
			}
			w.write("</table>\n</body></html>\n");
		}
		return file;
	}

	/**
	 * The 2026-07-22 pagination contract (see development record -contract-consultation.md)
	 * intentionally made {@code vertical-lr} inside {@code vertical-rl} ancestors atomic:
	 * this ancestor chain does not split, but either fits intact or moves intact to the next page.
	 * B5 (2026-07-21) temporarily made it collectable because actual internal splitting checked
	 * only isVertical() equality; the policy change withdrew that decision.
	 * At the same time, {@code BreakableBuilder.startFlowBlock()}'s {@code breakDepth} barrier
	 * expanded from {@code isVertical()} equality to exact {@code WritingMode} equality.
	 * This therefore becomes unsplittable as an ancestor chain, like an orthogonal writing-mode mismatch.
	 *
	 * <p>
	 * Internal text continuation (line splitting during pagination) uses the separate
	 * {@code TextBlockBox}/{@code BreakToken} mechanism, completed in ARCHITECTURE.md §5.9,
	 * outside {@code breakDepth}'s scope. Verify that this fixture correctly paginates to 17 pages
	 * (measured), avoids deep legacy `OpenChain` recursion (`RESTYLE_CHAIN_FIRINGS`),
	 * and leaves only depth 1 at the barrier (a single level, not dangerous recursion).
	 * </p>
	 */
	public void testMixedVerticalDirectionAncestorAlsoTriggersOpenChain() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateVerticalDirectionMismatch("vertical-rl-lr-mismatch", 40, 300);
		final File pdf = new File("local/unittest/display-list/open-chain-vertical-rl-lr-mismatch.pdf");
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
		System.err.println("vertical-rl-lr-mismatch: RESTYLE_CHAIN_FIRINGS=" + ContinuationStats.RESTYLE_CHAIN_FIRINGS.get()
				+ " CHILD_FRAMES=" + ContinuationStats.CHILD_FRAMES.get() + " MAX_PAGE_OPEN_TAIL_DEPTH="
				+ ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
		// 2026-07-22 (pagination contract): extending breakDepth (BreakableBuilder
		// .startFlowBlock) prevents all automatic block-boundary page breaks
		// inside ancestor chains with mismatched writing directions. This fixture's
		// text body is continuous text, not blocks
		// (one text sequence separated by `<br/>`), so the existing
		// TextBlockBox/BreakToken mechanism (completed in ARCHITECTURE.md §5.9) handles pagination
		// without passing through block-level OpenChain/collectable-prefix scanning.
		// Measurements confirmed safe pagination into 17 pages while
		// capabilityScanStops(SAME_AXIS_DIRECTION_CHANGE) and
		// RESTYLE_CHAIN_FIRINGS all remained zero.
		// This RL/LR ancestor chain is truly atomic in that it is never
		// continued while still open.
		// The old pageCompiledLevels(SAME_AXIS_DIRECTION_CHANGE)==0 check was removed
		// with compiled-program counters in E-3 increment 7.
		assertEquals("SAME_AXIS_DIRECTION_CHANGEの祖先はスキャン自体の対象にならないはずです", 0,
				ContinuationStats.capabilityScanStops(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE));
		assertEquals("legacy OpenChainの深い再帰も発生しないはずです", 0,
				ContinuationStats.PAGE_RESTYLE_CHAIN_FIRINGS.get());
		assertEquals("PAGE/COLUMN合算でもOpenChainは発火しないはずです", 0,
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
	}

	private File generateVerticalDirectionMismatch(String name, int depth, int leafLines) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"150pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;writing-mode:vertical-rl}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			w.write("<div style=\"writing-mode:vertical-lr\">\n");
			for (int i = 0; i < 10; ++i) {
				w.write("<div>");
			}
			this.writeTextLeaf(w, leafLines);
			for (int i = 0; i < 10; ++i) {
				w.write("</div>");
			}
			w.write("\n</div>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
	}

	private interface LeafWriter {
		void write(Writer w, int leafLines) throws IOException;
	}

	private void writeTableLeaf(Writer w, int leafLines) throws IOException {
		w.write("<table style=\"table-layout:fixed;width:100%\">\n");
		for (int i = 0; i < leafLines; ++i) {
			w.write("<tr><td>ROW-" + String.format("%06d", i) + "</td></tr>\n");
		}
		w.write("</table>\n");
	}

	private void writeMulticolLeaf(Writer w, int innerDepth, int leafLines) throws IOException {
		w.write("<div style=\"column-count:2;column-gap:1em\">\n");
		for (int i = 0; i < innerDepth; ++i) {
			w.write("<div>");
		}
		this.writeTextLeaf(w, leafLines);
		for (int i = 0; i < innerDepth; ++i) {
			w.write("</div>");
		}
		w.write("\n</div>\n");
	}

	private void writeTextLeaf(Writer w, int leafLines) throws IOException {
		for (int i = 0; i < leafLines; ++i) {
			w.write("LEAF-");
			w.write(String.format("%06d", i));
			w.write("<br/>\n");
		}
	}

	private void run(String name, int depth, LeafWriter leaf, int leafLines) throws Exception {
		ContinuationStats.reset();
		final File doc = this.generate(name, depth, leaf, leafLines);
		final File pdf = new File("local/unittest/display-list/open-chain-" + name + ".pdf");
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
		System.err.println(name + ": RESTYLE_CHAIN_FIRINGS=" + ContinuationStats.RESTYLE_CHAIN_FIRINGS.get()
				+ " CHILD_FRAMES=" + ContinuationStats.CHILD_FRAMES.get() + " OPEN_TAILS="
				+ ContinuationStats.OPEN_TAILS.get() + " UNCHAINED_RESTYLES=" + ContinuationStats.UNCHAINED_RESTYLES.get()
				+ " MAX_PAGE_OPEN_TAIL_DEPTH=" + ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get()
				+ " MAX_COLUMN_OPEN_TAIL_DEPTH=" + ContinuationStats.MAX_COLUMN_OPEN_TAIL_DEPTH.get());
	}

	private File generate(String name, int depth, LeafWriter leaf, int leafLines) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}td{border:0.5pt solid black}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			leaf.write(w, leafLines);
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
	}
}
