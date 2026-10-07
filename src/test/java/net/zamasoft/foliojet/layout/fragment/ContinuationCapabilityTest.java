package net.zamasoft.foliojet.layout.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * Direct unit tests for {@link ContinuationCapability#classify}
 * (added 2026-07-21, M6b Phase B B1). Construct target boxes directly, without real documents, to verify
 * each classification branch. The classification logic alone locks down the basis (RL/LR mismatch and
 * horizontal/vertical orthogonality) for the correction raised in the ChatGPT Pro consultation that
 * "multi-column layout is not the only trigger." The previously tested {@code RubyBodyBox} subtype =
 * {@code FLOW_SUBTYPE} classification was removed when ruby became annotated text (specification decision
 * on 2026-07-25), eliminating the target.
 *
 * <p>
 * {@link ContinuationCapability#UNSUPPORTED_BOX} (tables and other boxes that are not {@code
 * FlowBlockBox}) is not tested here because constructing {@code TableBox} is expensive.
 * The {@code !(b instanceof FlowBlockBox)} branch itself is self-evident, and real-document regression
 * coverage comes indirectly from {@code OpenChainCollectablePrefixTest#testTableLeafNeverTriggersOpenChain},
 * which approaches it from another angle (a table leaf never reaches OpenChain itself).
 * </p>
 */
public class ContinuationCapabilityTest extends TestCase {
	/**
	 * The {@code AbstractBlockBox} constructor requires {@code assert params
	 * .fontStyle != null} (normally a product of CSS style resolution).
	 * This test examines only classification logic, so the value itself does not matter;
	 * it only needs to be non-null.
	 */
	private static final FontStyle DUMMY_FONT_STYLE = new FontStyle() {
		public Direction getDirection() {
			return Direction.LTR;
		}

		public Weight getWeight() {
			return Weight.W_400;
		}

		public Style getStyle() {
			return Style.NORMAL;
		}

		public net.zamasoft.pdfg2d.gc.font.FontFamilyList getFamily() {
			return null;
		}

		public double getSize() {
			return 10;
		}

		public net.zamasoft.pdfg2d.gc.font.FontPolicyList getPolicy() {
			return null;
		}
	};

	private static BlockParams blockParams(final WritingMode flow) {
		final BlockParams params = new BlockParams();
		params.flow = flow;
		params.fontStyle = DUMMY_FONT_STYLE;
		return params;
	}

	private static FlowBlockBox plainFlowBlockBox(final WritingMode flow) {
		return new FlowBlockBox(blockParams(flow), new FlowPos());
	}

	private static MulticolumnBlockBox multicolumnBlockBox(final WritingMode flow) {
		return new MulticolumnBlockBox(blockParams(flow), new FlowPos());
	}

	public void testPlainFlowBlockBoxMatchingRootIsCollectable() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.TB);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.TB);
		assertEquals(ContinuationCapability.PLAIN_FLOW, c);
		assertTrue(c.isCollectable());
	}

	public void testMulticolumnBlockBoxIsMulticol() {
		final AbstractContainerBox b = multicolumnBlockBox(WritingMode.TB);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.TB);
		assertEquals(ContinuationCapability.MULTICOL, c);
		assertFalse(c.isCollectable());
	}

	/**
	 * The same axis as the root (vertical writing) but a different direction ({@code vertical-lr} within
	 * {@code vertical-rl} ancestors) is classified as {@code SAME_AXIS_DIRECTION_CHANGE}.
	 * Actual internal splitting eligibility and automatic page-break barriers both check only matching
	 * {@code isVertical()}, so this is a case where splitting itself is possible but preflight alone
	 * is unnecessarily strict.
	 */
	public void testVerticalRlToVerticalLrIsSameAxisDirectionChange() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.LR);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.RL);
		assertEquals(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE, c);
		assertFalse(c.isCollectable());
	}

	/** A different axis from the root (horizontal ⇄ vertical writing) is ORTHOGONAL_FLOW. */
	public void testHorizontalToVerticalIsOrthogonalFlow() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.RL);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.TB);
		assertEquals(ContinuationCapability.ORTHOGONAL_FLOW, c);
		assertFalse(c.isCollectable());
	}

	/** The same vertical writing direction as the root (RL→RL) is a match and can be collected. */
	public void testMatchingVerticalDirectionIsCollectable() {
		final AbstractContainerBox b = plainFlowBlockBox(WritingMode.RL);
		final ContinuationCapability c = ContinuationCapability.classify(b, WritingMode.RL);
		assertEquals(ContinuationCapability.PLAIN_FLOW, c);
		assertTrue(c.isCollectable());
	}

	/**
	 * In B3a (2026-07-21), {@code MULTICOL} became collectable only for automatic PAGE breaks.
	 * Forced breaks ({@code ForceBreakMode}) were deferred because the forced-break branch of
	 * {@code FlowContainer.splitPageAxis} unconditionally turned {@code KEEP}/{@code MOVE} from a selected
	 * chain member into {@code AssertionError("force break failed")}.
	 * B3b-2 (2026-07-21) replaced this raw {@code AssertionError} with normal KEEP/MOVE handling, so
	 * B3b-1 (2026-07-21) made collection independent of mode: both automatic and forced page breaks
	 * follow the same {@code splitForContinuation} path.
	 */
	public void testMulticolSupportsPageSplitThroughRegardlessOfMode() {
		final ContinuationCapability multicol = ContinuationCapability.MULTICOL;
		assertTrue("自動改ページではMULTICOLを収集可能にするはずです",
				multicol.supportsPageSplitThrough(
						new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(plainFlowBlockBox(WritingMode.TB))));
		assertTrue("B3b-1(2026-07-21)以降、強制改ページでもMULTICOLを収集可能にするはずです",
				multicol.supportsPageSplitThrough(new net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode(
						plainFlowBlockBox(WritingMode.TB), net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE)));
	}

	/** {@code PLAIN_FLOW} is always collectable, regardless of mode. */
	public void testPlainFlowSupportsPageSplitThroughRegardlessOfMode() {
		final ContinuationCapability plain = ContinuationCapability.PLAIN_FLOW;
		assertTrue(plain.supportsPageSplitThrough(
				new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(plainFlowBlockBox(WritingMode.TB))));
		assertTrue(plain.supportsPageSplitThrough(new net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode(
				plainFlowBlockBox(WritingMode.TB), net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE)));
	}

	/**
	 * {@code ORTHOGONAL_FLOW}/{@code UNSUPPORTED_BOX}/
	 * {@code SAME_AXIS_DIRECTION_CHANGE} are never collectable, regardless of mode
	 * (designated atomic by the page-break contract of 2026-07-22; see the development record).
	 * B5b temporarily made {@code SAME_AXIS_DIRECTION_CHANGE} collectable, but that was withdrawn.
	 * {@code MULTICOL} remains collectable regardless of mode.
	 */
	public void testOrthogonalAndUnsupportedNeverSupportPageSplitThrough() {
		final net.zamasoft.foliojet.layout.box.content.BreakMode auto = new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(
				plainFlowBlockBox(WritingMode.TB));
		final net.zamasoft.foliojet.layout.box.content.BreakMode force = new net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode(
				plainFlowBlockBox(WritingMode.TB), net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE);
		assertFalse(ContinuationCapability.ORTHOGONAL_FLOW.supportsPageSplitThrough(auto));
		assertFalse(ContinuationCapability.UNSUPPORTED_BOX.supportsPageSplitThrough(auto));
		assertFalse("改ページ契約(2026-07-22)によりSAME_AXIS_DIRECTION_CHANGEはatomic対象です",
				ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE.supportsPageSplitThrough(auto));
		assertFalse("強制改ページでも同様にatomic対象です",
				ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE.supportsPageSplitThrough(force));
	}

	/**
	 * B5 (2026-07-21): {@code classify()} checks the axis before the subtype, so a
	 * {@code MulticolumnBlockBox} with an orthogonal writing-mode is classified as
	 * {@code ORTHOGONAL_FLOW}, not {@code MULTICOL}
	 * (fixed misclassification in the old implementation, found during a design consultation with codex).
	 */
	public void testOrthogonalMulticolClassifiesAsOrthogonalFlowNotSubtype() {
		final AbstractContainerBox orthogonalMulticol = multicolumnBlockBox(WritingMode.RL);
		assertEquals(ContinuationCapability.ORTHOGONAL_FLOW,
				ContinuationCapability.classify(orthogonalMulticol, WritingMode.TB));

		final AbstractContainerBox sameAxisMulticol = multicolumnBlockBox(WritingMode.LR);
		assertEquals(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE,
				ContinuationCapability.classify(sameAxisMulticol, WritingMode.RL));
	}

	/**
	 * M6b Phase B4/B5 remaining work (task #73): directly verify that when another multi-column layout
	 * appears inside the selected COLUMN owner (a multi-column layout), {@link OpenPathScan#captureColumn}
	 * detects the descendant as a {@code MULTICOL} barrier and excludes it from collection (approvedBoxes).
	 *
	 * <p>
	 * The initial design used an HTML fixture (real-document rendering) to confirm that a descendant
	 * multicol remains a barrier inside an outer owner. However, the actual semantics of
	 * {@code AbstractContainerBox#canColumnBreak()} (if {@code isSpecifiedPageSize()} is true,
	 * {@code columnCount>=2} alone unconditionally returns {@code true}), combined with the
	 * innermost-first search in {@code findColumnBreak()}, always selected the innermost owner for
	 * nested multi-column layouts with specified heights. Thus the configuration "outer owner with
	 * an inner descendant remaining a barrier" could not arise naturally in HTML (the old test,
	 * {@code testDescendantMulticolInsideColumnOwnerStaysBarrier}, was also confirmed to fail in practice).
	 * Therefore, this was replaced with a standalone call to {@link OpenPathScan#captureColumn} on
	 * directly constructed boxes, testing that the classification logic itself correctly detects
	 * barriers, rather than reachability through CSS.
	 * </p>
	 */
	public void testCaptureColumnTreatsDescendantMulticolAsBarrier() {
		final AbstractContainerBox owner = multicolumnBlockBox(WritingMode.TB);
		final AbstractContainerBox descendant = multicolumnBlockBox(WritingMode.TB);
		final OpenPathScan scan = OpenPathScan.captureColumn(java.util.List.of(owner, descendant),
				new net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode(descendant));

		assertTrue("descendantのMULTICOLはowner内側で収集不能(barrier)のはずです",
				scan.snapshot().firstBarrier().isPresent());
		final OpenPathSnapshot.CapabilityBarrier barrier = scan.snapshot().firstBarrier().get();
		assertEquals(1, barrier.openPathIndex());
		assertEquals(ContinuationCapability.MULTICOL, barrier.reason());
		assertTrue("barrier以降は収集(approvedBoxes)されないはずです", scan.approvedBoxes().isEmpty());
	}
}
