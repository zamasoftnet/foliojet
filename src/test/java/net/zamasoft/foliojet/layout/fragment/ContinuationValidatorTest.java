package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link ContinuationValidator}の直接単体テストです(2026-07-24新設、
 * E-3増分1)。旧{@code ResumeProgramCompilerTest}の深さ・不変条件テストを
 * 正本(Continuation/COLUMN入力)の直接検証として移植した——programを
 * 経由せず、合成した入力を直接検証する。いずれのテストも
 * {@code FragmentRecipe.instantiate()}が一度も呼ばれないこと(validatorは
 * fragmentを実際に構築しない)を固定する。
 */
public class ContinuationValidatorTest extends TestCase {

	private static FragmentRecipe throwingRecipe(final AtomicInteger calls) {
		return (state, container) -> {
			calls.incrementAndGet();
			throw new AssertionError("validator must not instantiate fragments");
		};
	}

	private static OpenPathSnapshot plainSnapshot(final int depth, final OpenPathSnapshot.AnchorKind anchorKind) {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>(depth);
		for (int i = 0; i < depth; ++i) {
			final OpenPathSnapshot.OpenLevelRole role = i == 0 ? new OpenPathSnapshot.OpenLevelRole.Anchor(anchorKind)
					: new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW);
			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, FlowBlockBox.class,
					WritingMode.TB, 1, i, role));
		}
		return new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());
	}

	private static ColumnAnchor emptyAnchor() {
		return new ColumnAnchor(null, List.of());
	}

	/**
	 * 深さ5000のframe chainをJVM再帰なしの有界反復で検証できることを確認
	 * する(既存{@code ResumeProgramCompilerTest}の深さ上限テストの移植)。
	 */
	public void testValidatesDepth5000WithoutJvmRecursionOrFragmentInstantiation() {
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
		final OpenPathSnapshot snapshot = plainSnapshot(depth, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		final ContinuationValidator.PathShape shape = ContinuationValidator.validatePage(snapshot, continuation);

		assertEquals("完全に収集可能なチェーンはfirstOpenPathIndex==depthになるはずです", depth,
				shape.firstOpenPathIndex());
		assertEquals(1, shape.terminalShape().depth());
		assertEquals("validatorはFragmentRecipeを一切呼んではいけません", 0, recipeCalls.get());
	}

	/**
	 * 宣言された深さ(continuation.depth()/snapshot)と実際のframe chain+
	 * 終端OpenShapeの深さが食い違う場合、{@link
	 * ContinuationInvariantViolationException}で拒否されること、かつ
	 * その判定でfragmentが構築されないことを確認する。
	 */
	public void testRejectsDepthMismatchWithoutInstantiatingRecipe() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		// 宣言depth=20だが、実際は1フレーム+終端深さ10相当(意図的にmalformed)
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(10)));
		final Continuation continuation = new Continuation(20, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(20, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		try {
			ContinuationValidator.validatePage(snapshot, continuation);
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
		final OpenPathSnapshot snapshot = plainSnapshot(3, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		try {
			ContinuationValidator.validatePage(snapshot, continuation);
			fail("snapshot/continuationのdepth不一致は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * PAGE終端式: 収集不能な破断(チェーンなし、root frameのtailが全深さの
	 * OpenTailShape)は{@code firstOpenPathIndex=1}+終端深さ=snapshot深さ
	 * になる(旧{@code ResumeTail.LegacyOpen}相当)。
	 */
	public void testPageUncollectedBreakTerminalShape() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(4)));
		final Continuation continuation = new Continuation(4, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(4, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		final ContinuationValidator.PathShape shape = ContinuationValidator.validatePage(snapshot, continuation);

		assertEquals(1, shape.firstOpenPathIndex());
		assertEquals(4, shape.terminalShape().depth());
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * 段組(MULTICOL)levelを含むchainが完全に収集され、開きテキストまで
	 * 貫通するケース(旧{@code ResumeProgramCompilerTest}のB3aテストの
	 * 移植——段組levelがfirst-classに歩ける)。
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
				new OpenPathSnapshot.OpenLevelDescriptor(0, FlowBlockBox.class, WritingMode.TB, 1, 0,
						new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT)),
				new OpenPathSnapshot.OpenLevelDescriptor(1, MulticolumnBlockBox.class,
						WritingMode.TB, 2, 1,
						new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.MULTICOL)),
				new OpenPathSnapshot.OpenLevelDescriptor(2, FlowBlockBox.class, WritingMode.TB, 1, 2,
						new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW)));
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());

		final ContinuationValidator.PathShape shape = ContinuationValidator.validatePage(snapshot, continuation);

		assertEquals(3, shape.firstOpenPathIndex());
		assertEquals(1, shape.terminalShape().depth());
		assertEquals(0, recipeCalls.get());
	}

	/** prefixのserial順序が破れている場合は拒否する(verifierからの移植)。 */
	public void testRejectsNonIncreasingPrefixSerial() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		final List<Continuation.SourceRange> badPrefix = List.of(new Continuation.SourceRange(5, 0, 10),
				new Continuation.SourceRange(5, 11, 20));
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				badPrefix, new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		final Continuation continuation = new Continuation(1, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(1, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		try {
			ContinuationValidator.validatePage(snapshot, continuation);
			fail("serial非増加のprefixは拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
		assertEquals(0, recipeCalls.get());
	}

	/** crossExtentが非有限の場合は拒否する(verifierからの移植)。 */
	public void testRejectsNonFiniteCrossExtent() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null,
				Double.NaN, List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		final Continuation continuation = new Continuation(1, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(1, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		try {
			ContinuationValidator.validatePage(snapshot, continuation);
			fail("非有限のcrossExtentは拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * COLUMN終端式: {@code childFrame==null}かつsnapshot depth==1(owner
	 * 自身がsnapshotの唯一のレベル=子孫なし)は開きテキスト1単位で終端する
	 * (旧{@code ResumeTail.OpenText}相当。ColumnResumeProgramCompilerの
	 * 規約の移植)。
	 */
	public void testColumnNullChildFrameDepth1EndsInOpenText() {
		final OpenPathSnapshot snapshot = plainSnapshot(1, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		final ContinuationValidator.PathShape shape = ContinuationValidator.validateColumn(emptyAnchor(), snapshot,
				null);

		assertEquals(1, shape.firstOpenPathIndex());
		assertEquals(1, shape.terminalShape().depth());
	}

	/**
	 * COLUMN終端式: {@code childFrame==null}かつsnapshot depth&gt;1は、
	 * index 1から始まる深さsnapshot.depth()の開き(旧{@code ResumeTail
	 * .LegacyOpen(1, snapshotDepth)}相当)になる。
	 */
	public void testColumnNullChildFrameDeepSnapshotKeepsFullOpenDepth() {
		final OpenPathSnapshot snapshot = plainSnapshot(3, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		final ContinuationValidator.PathShape shape = ContinuationValidator.validateColumn(emptyAnchor(), snapshot,
				null);

		assertEquals(1, shape.firstOpenPathIndex());
		assertEquals(3, shape.terminalShape().depth());
	}

	/**
	 * COLUMN終端式: 貫通したチェーンが完全に収集された場合
	 * ({@code chainFrames + 1 == snapshotDepth}、終端は開きテキスト)。
	 */
	public void testColumnFullyCollectedChainEndsInOpenText() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.Child(frame));
		final OpenPathSnapshot snapshot = plainSnapshot(3, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		final ContinuationValidator.PathShape shape = ContinuationValidator.validateColumn(emptyAnchor(), snapshot,
				frame);

		assertEquals(3, shape.firstOpenPathIndex());
		assertEquals(1, shape.terminalShape().depth());
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * COLUMN深さ式({@code chainFrames + tailDepth == snapshotDepth}、
	 * PAGEの{@code -1}補正なし)が破れている場合は拒否する。
	 */
	public void testColumnRejectsDepthMismatch() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		// chainFrames=1 + tailDepth=3 != snapshotDepth=3(意図的にmalformed)
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(3)));
		final OpenPathSnapshot snapshot = plainSnapshot(3, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		try {
			ContinuationValidator.validateColumn(emptyAnchor(), snapshot, frame);
			fail("COLUMN depth不整合はContinuationInvariantViolationExceptionになるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
		assertEquals(0, recipeCalls.get());
	}

	/** frame chainがsnapshot深さを超える場合は有界walkが拒否する。 */
	public void testColumnRejectsChainExceedingSnapshotDepth() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		for (int i = 0; i < 3; ++i) {
			frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
					new Continuation.OpenTail.Child(frame));
		}
		// チェーン4フレームに対しsnapshot depth=2(index 1しか許容しない)
		final OpenPathSnapshot snapshot = plainSnapshot(2, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		try {
			ContinuationValidator.validateColumn(emptyAnchor(), snapshot, frame);
			fail("snapshot深さを超えるチェーンは拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
		assertEquals(0, recipeCalls.get());
	}

	/** 実fragment署名の直接照合(E-3増分2): 一致は通過、不一致は型付き例外。 */
	public void testFragmentSignatureCheck() {
		final OpenPathSnapshot snapshot = plainSnapshot(2, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		// 一致(snapshotのdescriptorと同一の署名)
		ContinuationValidator.checkFragmentSignature(snapshot, 0,
				new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.TB, 1));

		// class不一致
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 0, new OpenPathSnapshot.FragmentSignature(
					MulticolumnBlockBox.class, WritingMode.TB, 1));
			fail("class不一致の署名は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}

		// writing-mode不一致
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 1,
					new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.RL, 1));
			fail("writing-mode不一致の署名は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}

		// column-count不一致
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 1,
					new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.TB, 2));
			fail("column-count不一致の署名は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}

		// index範囲外
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 2,
					new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.TB, 1));
			fail("snapshot深さ超過のindexは拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// 期待通り
		}
	}
}
