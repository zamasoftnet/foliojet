package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link WorklistTailGate}の直接導出({@code of(OpenPathSnapshot,
 * ContinuationValidator.PathShape)})の期待値を固定するテストです
 * (2026-07-24新設、E-3増分3。増分3〜5の間はprogram経由の旧判定
 * {@code of(ContinuationProgram)}との一致比較として運用し、全ケース一致を
 * 確認済み——増分6のprogram系撤去に伴い、直接導出の期待値固定へ書き換えた)。
 * 網羅ケース: full collection / capability barrier / split-stopped
 * (PLAIN_FLOW残存・非PLAIN_FLOW残存)/ COLUMN childなし(depth 1・
 * plain・非plain)/ COLUMN貫通チェーン(完全収集・途中停止)。
 */
public class WorklistTailGateTest extends TestCase {

	private static final FragmentRecipe RECIPE = (state, container) -> {
		throw new AssertionError("gate derivation must not instantiate fragments");
	};

	private static OpenPathSnapshot.OpenLevelDescriptor level(final int index,
			final OpenPathSnapshot.OpenLevelRole role) {
		return new OpenPathSnapshot.OpenLevelDescriptor(index, FlowBlockBox.class, WritingMode.TB, 1,
				index, role);
	}

	private static OpenPathSnapshot pageSnapshot(final ContinuationCapability[] ancestorCapabilities,
			final Optional<OpenPathSnapshot.CapabilityBarrier> barrier) {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>();
		descriptors.add(level(0, new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)));
		for (int i = 0; i < ancestorCapabilities.length; ++i) {
			descriptors.add(level(i + 1, new OpenPathSnapshot.OpenLevelRole.Ancestor(ancestorCapabilities[i])));
		}
		return new OpenPathSnapshot(WritingMode.TB, descriptors, barrier);
	}

	private static OpenPathSnapshot columnSnapshot(final ContinuationCapability... ancestorCapabilities) {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>();
		descriptors.add(level(0, new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.COLUMN_OWNER)));
		for (int i = 0; i < ancestorCapabilities.length; ++i) {
			descriptors.add(level(i + 1, new OpenPathSnapshot.OpenLevelRole.Ancestor(ancestorCapabilities[i])));
		}
		return new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());
	}

	/** 深さdepthの完全収集チェーン(終端は開きテキスト)を作る。 */
	private static Continuation fullyCollected(final int depth) {
		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		for (int i = depth - 2; i >= 0; --i) {
			frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0, List.of(),
					new Continuation.OpenTail.Child(frame));
		}
		return new Continuation(depth, frame, Map.of());
	}

	private static void assertPageGate(final WorklistTailGate expected, final OpenPathSnapshot snapshot,
			final Continuation continuation) {
		final ContinuationValidator.PathShape pathShape = ContinuationValidator.validatePage(snapshot, continuation);
		assertEquals(expected, WorklistTailGate.of(snapshot, pathShape));
	}

	private static void assertColumnGate(final WorklistTailGate expected, final OpenPathSnapshot snapshot,
			final Continuation.ContinuationFrame childFrame) {
		final ColumnAnchor anchor = new ColumnAnchor(null, List.of());
		final ContinuationValidator.PathShape pathShape = ContinuationValidator.validateColumn(anchor, snapshot,
				childFrame);
		assertEquals(expected, WorklistTailGate.of(snapshot, pathShape));
	}

	/** full collection: 全レベル収集済み(終端は開きテキストのみ)。 */
	public void testFullCollectionIsNoLegacyOpenTail() {
		final OpenPathSnapshot snapshot = pageSnapshot(
				new ContinuationCapability[] { ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW },
				Optional.empty());
		assertPageGate(WorklistTailGate.NO_LEGACY_OPEN_TAIL, snapshot, fullyCollected(3));
	}

	/** barrier: capability障壁で停止、残存tailに非PLAIN_FLOWレベルが含まれる。 */
	public void testCapabilityBarrierIsLegacyRecursion() {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>();
		descriptors.add(level(0, new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)));
		for (int i = 1; i < 4; ++i) {
			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, FlowBlockBox.class,
					WritingMode.RL, 1, i,
					new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.ORTHOGONAL_FLOW)));
		}
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors,
				Optional.of(new OpenPathSnapshot.CapabilityBarrier(1, ContinuationCapability.ORTHOGONAL_FLOW)));

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(4)));
		assertPageGate(WorklistTailGate.LEGACY_RECURSION, snapshot, new Continuation(4, frame, Map.of()));
	}

	/**
	 * split-stopped: capability上は全承認(barrierなし・全レベルPLAIN_FLOW)
	 * だがsplitがKEEP/MOVEで止まった——残存tailは全てPLAIN_FLOWなので
	 * worklist適格。
	 */
	public void testSplitStoppedPlainFlowIsWorklistEligible() {
		final OpenPathSnapshot snapshot = pageSnapshot(
				new ContinuationCapability[] { ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW },
				Optional.empty());
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(3)));
		assertPageGate(WorklistTailGate.WORKLIST_ELIGIBLE, snapshot, new Continuation(3, frame, Map.of()));
	}

	/** split-stopped(非PLAIN_FLOW残存): 段組levelが残るためlegacy再帰。 */
	public void testSplitStoppedWithMulticolIsLegacyRecursion() {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = List.of(
				level(0, new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)),
				new OpenPathSnapshot.OpenLevelDescriptor(1, MulticolumnBlockBox.class, WritingMode.TB,
						2, 1, new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.MULTICOL)),
				level(2, new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW)));
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(3)));
		assertPageGate(WorklistTailGate.LEGACY_RECURSION, snapshot, new Continuation(3, frame, Map.of()));
	}

	/** COLUMN childなし(snapshot depth==1): 開きテキストのみで終端。 */
	public void testColumnNoChildDepth1IsNoLegacyOpenTail() {
		assertColumnGate(WorklistTailGate.NO_LEGACY_OPEN_TAIL, columnSnapshot(), null);
	}

	/** COLUMN childなし(snapshot depth&gt;1、全PLAIN_FLOW): worklist適格。 */
	public void testColumnNoChildPlainFlowIsWorklistEligible() {
		assertColumnGate(WorklistTailGate.WORKLIST_ELIGIBLE,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW), null);
	}

	/** COLUMN childなし(snapshot depth&gt;1、非PLAIN_FLOW残存): legacy再帰。 */
	public void testColumnNoChildNonPlainFlowIsLegacyRecursion() {
		assertColumnGate(WorklistTailGate.LEGACY_RECURSION,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.MULTICOL), null);
	}

	/** COLUMN貫通チェーン完全収集: 終端は開きテキストのみ。 */
	public void testColumnFullyCollectedChainIsNoLegacyOpenTail() {
		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0, List.of(),
				new Continuation.OpenTail.Child(frame));
		assertColumnGate(WorklistTailGate.NO_LEGACY_OPEN_TAIL,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW), frame);
	}

	/** COLUMN貫通チェーン途中停止(残存PLAIN_FLOW): worklist適格。 */
	public void testColumnPartialChainPlainFlowIsWorklistEligible() {
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(2)));
		assertColumnGate(WorklistTailGate.WORKLIST_ELIGIBLE,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW), frame);
	}
}
