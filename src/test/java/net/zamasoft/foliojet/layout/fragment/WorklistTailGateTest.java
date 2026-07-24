package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.BoxSubtype;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link WorklistTailGate}のprogram経由判定({@code of(ContinuationProgram)})
 * と直接導出({@code of(OpenPathSnapshot, ContinuationValidator.PathShape)})
 * の一致を固定する一時的な比較テストです(2026-07-24新設、E-3増分3)。
 * program系(compiler/{@code ResumeTail})が撤去される増分4〜6の後は、
 * 旧判定側のアサートを直接導出の期待値固定へ置き換える。
 */
public class WorklistTailGateTest extends TestCase {

	private static final FragmentRecipe RECIPE = (state, container) -> {
		throw new AssertionError("gate derivation must not instantiate fragments");
	};

	private static OpenPathSnapshot.OpenLevelDescriptor level(final int index,
			final OpenPathSnapshot.OpenLevelRole role) {
		return new OpenPathSnapshot.OpenLevelDescriptor(index, FlowBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 1,
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

	private static NextColumnTarget dummyTarget() {
		return new NextColumnTarget(null, null, 0, 0, 0, 0, null);
	}

	private static void assertPageGateAgreement(final WorklistTailGate expected, final OpenPathSnapshot snapshot,
			final Continuation continuation) {
		final PageResumeProgram program = ResumeProgramCompiler.compile(snapshot, continuation);
		final ContinuationValidator.PathShape pathShape = ContinuationValidator.validatePage(snapshot, continuation);

		final WorklistTailGate legacy = WorklistTailGate.of(program);
		final WorklistTailGate direct = WorklistTailGate.of(snapshot, pathShape);

		assertEquals("program経由の旧判定が期待値と一致しません", expected, legacy);
		assertEquals("直接導出が旧判定と一致しません", legacy, direct);
	}

	private static void assertColumnGateAgreement(final WorklistTailGate expected, final OpenPathSnapshot snapshot,
			final Continuation.ContinuationFrame childFrame) {
		final ColumnAnchor anchor = new ColumnAnchor(null, List.of());
		final ColumnResumeProgram program = ColumnResumeProgramCompiler.compileColumn(dummyTarget(), anchor, snapshot,
				childFrame, Map.of());
		final ContinuationValidator.PathShape pathShape = ContinuationValidator.validateColumn(anchor, snapshot,
				childFrame);

		final WorklistTailGate legacy = WorklistTailGate.of(program);
		final WorklistTailGate direct = WorklistTailGate.of(snapshot, pathShape);

		assertEquals("program経由の旧判定が期待値と一致しません", expected, legacy);
		assertEquals("直接導出が旧判定と一致しません", legacy, direct);
	}

	/** full collection: 全レベル収集済み(終端は開きテキストのみ)。 */
	public void testFullCollectionAgreesOnNoLegacyOpenTail() {
		final OpenPathSnapshot snapshot = pageSnapshot(
				new ContinuationCapability[] { ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW },
				Optional.empty());
		assertPageGateAgreement(WorklistTailGate.NO_LEGACY_OPEN_TAIL, snapshot, fullyCollected(3));
	}

	/** barrier: capability障壁で停止、残存tailに非PLAIN_FLOWレベルが含まれる。 */
	public void testCapabilityBarrierAgreesOnLegacyRecursion() {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>();
		descriptors.add(level(0, new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)));
		for (int i = 1; i < 4; ++i) {
			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, FlowBlockBox.class, BoxSubtype.RUBY_BODY,
					WritingMode.TB, 1, i,
					new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.FLOW_SUBTYPE)));
		}
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors,
				Optional.of(new OpenPathSnapshot.CapabilityBarrier(1, ContinuationCapability.FLOW_SUBTYPE)));

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(4)));
		assertPageGateAgreement(WorklistTailGate.LEGACY_RECURSION, snapshot, new Continuation(4, frame, Map.of()));
	}

	/**
	 * split-stopped: capability上は全承認(barrierなし・全レベルPLAIN_FLOW)
	 * だがsplitがKEEP/MOVEで止まった——残存tailは全てPLAIN_FLOWなので
	 * worklist適格。
	 */
	public void testSplitStoppedPlainFlowAgreesOnWorklistEligible() {
		final OpenPathSnapshot snapshot = pageSnapshot(
				new ContinuationCapability[] { ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW },
				Optional.empty());
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(3)));
		assertPageGateAgreement(WorklistTailGate.WORKLIST_ELIGIBLE, snapshot, new Continuation(3, frame, Map.of()));
	}

	/** split-stopped(非PLAIN_FLOW残存): 段組levelが残るためlegacy再帰。 */
	public void testSplitStoppedWithMulticolAgreesOnLegacyRecursion() {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = List.of(
				level(0, new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)),
				new OpenPathSnapshot.OpenLevelDescriptor(1, MulticolumnBlockBox.class, BoxSubtype.NONE, WritingMode.TB,
						2, 1, new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.MULTICOL)),
				level(2, new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW)));
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(3)));
		assertPageGateAgreement(WorklistTailGate.LEGACY_RECURSION, snapshot, new Continuation(3, frame, Map.of()));
	}

	/** COLUMN childなし(snapshot depth==1): 開きテキストのみで終端。 */
	public void testColumnNoChildDepth1AgreesOnNoLegacyOpenTail() {
		assertColumnGateAgreement(WorklistTailGate.NO_LEGACY_OPEN_TAIL, columnSnapshot(), null);
	}

	/** COLUMN childなし(snapshot depth&gt;1、全PLAIN_FLOW): worklist適格。 */
	public void testColumnNoChildPlainFlowAgreesOnWorklistEligible() {
		assertColumnGateAgreement(WorklistTailGate.WORKLIST_ELIGIBLE,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW), null);
	}

	/** COLUMN childなし(snapshot depth&gt;1、非PLAIN_FLOW残存): legacy再帰。 */
	public void testColumnNoChildFlowSubtypeAgreesOnLegacyRecursion() {
		assertColumnGateAgreement(WorklistTailGate.LEGACY_RECURSION,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.FLOW_SUBTYPE), null);
	}

	/** COLUMN貫通チェーン完全収集: 終端は開きテキストのみ。 */
	public void testColumnFullyCollectedChainAgreesOnNoLegacyOpenTail() {
		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0, List.of(),
				new Continuation.OpenTail.Child(frame));
		assertColumnGateAgreement(WorklistTailGate.NO_LEGACY_OPEN_TAIL,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW), frame);
	}

	/** COLUMN貫通チェーン途中停止(残存PLAIN_FLOW): worklist適格。 */
	public void testColumnPartialChainPlainFlowAgreesOnWorklistEligible() {
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(RECIPE, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(2)));
		assertColumnGateAgreement(WorklistTailGate.WORKLIST_ELIGIBLE,
				columnSnapshot(ContinuationCapability.PLAIN_FLOW, ContinuationCapability.PLAIN_FLOW), frame);
	}
}
