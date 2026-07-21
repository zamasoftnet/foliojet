package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.BoxSubtype;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link ResumeProgramCompiler}の直接単体テストです(2026-07-21新設、
 * M6b Phase B B2)。実文書を経由せず、合成した{@link Continuation}を
 * 直接コンパイルして検証する。いずれのテストも
 * {@code FragmentRecipe.instantiate()}が一度も呼ばれないこと
 * (compilerはfragmentを実際に構築しない、shadowであり第二executorでは
 * ない)を固定する。
 */
public class ResumeProgramCompilerTest extends TestCase {

	private static FragmentRecipe throwingRecipe(final AtomicInteger calls) {
		return (state, container) -> {
			calls.incrementAndGet();
			throw new AssertionError("compiler must not instantiate fragments");
		};
	}

	private static OpenPathSnapshot plainSnapshot(final int depth) {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>(depth);
		for (int i = 0; i < depth; ++i) {
			final OpenPathSnapshot.OpenLevelRole role = i == 0 ? new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)
					: new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW);
			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, FlowBlockBox.class, BoxSubtype.NONE,
					WritingMode.TB, 1, i, role));
		}
		return new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());
	}

	/**
	 * 深さ5000のframe chainを、明示的な反復構築(内側から外側へ)+
	 * {@link ResumeProgramCompiler}の反復コンパイルの両方でJVM再帰なしに
	 * 処理できることを確認する。既存の反復化済み経路
	 * (resumeFrame/OpenShape.depth等)と同じ設計方針の検証。
	 */
	public void testCompilesDepth5000WithoutJvmRecursionOrFragmentInstantiation() {
		final int depth = 5000;
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		for (int i = depth - 2; i >= 0; --i) {
			frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
					new Continuation.OpenTail.Child(frame));
		}

		final Continuation continuation = new Continuation(depth, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(depth);

		final PageResumeProgram program = ResumeProgramCompiler.compile(snapshot, continuation);

		assertEquals(depth, program.fragmentLevels().size());
		assertTrue("完全に収集可能なチェーンはOpenTextで終端するはずです",
				program.tail() instanceof ResumeTail.OpenText);
		assertEquals("compilerはFragmentRecipeを一切呼んではいけません", 0, recipeCalls.get());

		// expectedOpsの導出自体も副作用なし(fragment生成なし)で完了できる
		final List<ResumeOp> ops = ResumeOp.expectedOps(program);
		assertFalse(ops.isEmpty());
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * 宣言された深さ(continuation.depth()/snapshot)と実際のframe chain+
	 * 終端OpenShapeの深さが食い違う場合、{@link
	 * ContinuationInvariantViolationException}で拒否されること、かつ
	 * その判定の前にfragmentが構築されないことを確認する。
	 */
	public void testRejectsDepthMismatchWithoutInstantiatingRecipe() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		// 宣言depth=20だが、実際は1フレーム+終端深さ10相当(意図的にmalformed)
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(10)));
		final Continuation continuation = new Continuation(20, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(20);

		try {
			ResumeProgramCompiler.compile(snapshot, continuation);
			fail("depth不整合はContinuationInvariantViolationExceptionになるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
		assertEquals(0, recipeCalls.get());
	}

	/** snapshotとcontinuationのdepthそのものが食い違う場合も同様に拒否する。 */
	public void testRejectsSnapshotContinuationDepthMismatch() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		final Continuation continuation = new Continuation(5, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(3);

		try {
			ResumeProgramCompiler.compile(snapshot, continuation);
			fail("snapshot/continuationのdepth不一致は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * FLOW_SUBTYPE(RubyBodyBox等)でスキャンが途中停止したケースは
	 * LegacyOpen(CapabilityBarrier)になる。
	 *
	 * <p>
	 * <b>2026-07-21(B3a)追記</b>: 以前はここで{@code MULTICOL}を使って
	 * いたが、B3aで{@code MULTICOL}はPAGE自動改ページでは収集可能
	 * (barrierにならない)になったため、依然として非収集な
	 * {@code FLOW_SUBTYPE}へ差し替えた。
	 * </p>
	 */
	public void testCapabilityBarrierProducesLegacyOpenTail() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		// 深さ4(root含む)、rootの1つ先(index=1)でFLOW_SUBTYPEにより収集停止。
		// root frame自体の tail が OpenTailShape(depth=4) になる
		// (chain-fragmentが1段も貫通しなかった収集不能な破断)。
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(4)));
		final Continuation continuation = new Continuation(4, frame, Map.of());

		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>();
		descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(0, FlowBlockBox.class, BoxSubtype.NONE,
				WritingMode.TB, 1, 0, new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)));
		for (int i = 1; i < 4; ++i) {
			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, FlowBlockBox.class, BoxSubtype.RUBY_BODY,
					WritingMode.TB, 1, i,
					new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.FLOW_SUBTYPE)));
		}
		final OpenPathSnapshot.CapabilityBarrier barrier = new OpenPathSnapshot.CapabilityBarrier(1,
				ContinuationCapability.FLOW_SUBTYPE);
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors,
				Optional.of(barrier));

		final PageResumeProgram program = ResumeProgramCompiler.compile(snapshot, continuation);

		assertEquals(1, program.fragmentLevels().size());
		assertTrue(program.tail() instanceof ResumeTail.LegacyOpen);
		final ResumeTail.LegacyOpen legacyOpen = (ResumeTail.LegacyOpen) program.tail();
		assertEquals(1, legacyOpen.firstOpenPathIndex());
		assertTrue(legacyOpen.cause() instanceof LegacyTailCause.CapabilityBarrier);
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * 段組(MULTICOL)levelを含むchainが完全に収集され、OpenTextまで
	 * 貫通するケース(B3a、段組levelがfirst-classにコンパイルされる)。
	 *
	 * <pre>
	 * snapshot: root - MULTICOL - PLAIN_FLOW
	 * continuation: root frame -&gt; Child(multicol frame) -&gt; Child(plain frame) -&gt; OpenTailShape(TEXT)
	 * </pre>
	 */
	public void testFullyCollectedChainIncludingMulticolEndsInOpenText() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.Child(frame)); // PLAIN_FLOW level
		frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.Child(frame)); // MULTICOL level

		final Continuation continuation = new Continuation(3, frame, Map.of());

		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = List.of(
				new OpenPathSnapshot.OpenLevelDescriptor(0, FlowBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 1, 0,
						new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)),
				new OpenPathSnapshot.OpenLevelDescriptor(1, MulticolumnBlockBox.class, BoxSubtype.NONE,
						WritingMode.TB, 2, 1,
						new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.MULTICOL)),
				new OpenPathSnapshot.OpenLevelDescriptor(2, FlowBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 1, 2,
						new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW)));
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());

		final PageResumeProgram program = ResumeProgramCompiler.compile(snapshot, continuation);

		assertEquals(3, program.fragmentLevels().size());
		assertTrue(program.fragmentLevels().get(1)
				.descriptor().role() instanceof OpenPathSnapshot.OpenLevelRole.Ancestor(
						final ContinuationCapability capability) && capability == ContinuationCapability.MULTICOL);
		assertTrue(program.tail() instanceof ResumeTail.OpenText);
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * 段組自体で{@code splitForContinuation}がKEEP/MOVEを返し、frame生成が
	 * 途中で止まったケース(B3a、{@code SplitStopped}——capability上は
	 * 承認されたが実際のsplitが貫通しなかった)。
	 *
	 * <pre>
	 * snapshot: root - MULTICOL - PLAIN_FLOW(未到達)
	 * continuation: root frame -&gt; OpenTailShape(depth=3)(MULTICOLで停止)
	 * </pre>
	 */
	public void testMulticolSplitStoppedProducesLegacyOpenTail() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(3)));
		final Continuation continuation = new Continuation(3, frame, Map.of());

		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = List.of(
				new OpenPathSnapshot.OpenLevelDescriptor(0, FlowBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 1, 0,
						new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)),
				new OpenPathSnapshot.OpenLevelDescriptor(1, MulticolumnBlockBox.class, BoxSubtype.NONE,
						WritingMode.TB, 2, 1,
						new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.MULTICOL)),
				new OpenPathSnapshot.OpenLevelDescriptor(2, FlowBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 1, 2,
						new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW)));
		// firstBarrierなし(MULTICOL自体はcapability上は承認されている)——
		// SplitStoppedになるべきケース
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());

		final PageResumeProgram program = ResumeProgramCompiler.compile(snapshot, continuation);

		assertEquals(1, program.fragmentLevels().size());
		assertTrue(program.tail() instanceof ResumeTail.LegacyOpen);
		final ResumeTail.LegacyOpen legacyOpen = (ResumeTail.LegacyOpen) program.tail();
		assertEquals(1, legacyOpen.firstOpenPathIndex());
		assertTrue("capability上は承認済みなのでSplitStoppedになるはずです(CapabilityBarrierではない)",
				legacyOpen.cause() instanceof LegacyTailCause.SplitStopped);
		assertEquals(0, recipeCalls.get());
	}
}
