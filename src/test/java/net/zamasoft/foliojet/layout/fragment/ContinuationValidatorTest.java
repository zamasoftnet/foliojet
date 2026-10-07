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
 * Direct unit tests for {@link ContinuationValidator} (added 2026-07-24, E-3 increment 1).
 * Ports the depth and invariant tests from the old {@code ResumeProgramCompilerTest} to direct
 * validation of the source of truth (Continuation/COLUMN inputs), validating synthetic inputs
 * without going through a program. Every test locks down that {@code FragmentRecipe.instantiate()}
 * is never called (the validator does not actually construct fragments).
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
	 * Verifies that a frame chain of depth 5000 can be validated using bounded iteration without JVM
	 * recursion (ported from the depth-limit test in the existing {@code ResumeProgramCompilerTest}).
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
	 * Verifies that a mismatch between the declared depth (continuation.depth()/snapshot) and the actual
	 * frame chain + terminal OpenShape depth is rejected with {@link
	 * ContinuationInvariantViolationException}, and that this check constructs no fragments.
	 */
	public void testRejectsDepthMismatchWithoutInstantiatingRecipe() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		// Declared depth=20, but actually one frame + terminal depth 10 (deliberately malformed).
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(10)));
		final Continuation continuation = new Continuation(20, frame, Map.of());
		final OpenPathSnapshot snapshot = plainSnapshot(20, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		try {
			ContinuationValidator.validatePage(snapshot, continuation);
			fail("depth不整合はContinuationInvariantViolationExceptionになるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// As expected.
		}
		assertEquals(0, recipeCalls.get());
	}

	/** Also reject a mismatch between the depths of the snapshot and continuation themselves. */
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
			// As expected.
		}
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * PAGE terminal formula: an uncollectable break (no chain; the root frame's tail is an OpenTailShape
	 * covering the full depth) gives {@code firstOpenPathIndex=1} + terminal depth = snapshot depth
	 * (equivalent to the old {@code ResumeTail.LegacyOpen}).
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
	 * A chain containing a multi-column (MULTICOL) level is fully collected through to open text
	 * (ported from the B3a test in the old {@code ResumeProgramCompilerTest}; multi-column levels
	 * can be traversed as first-class members).
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

	/** Reject a violation of prefix serial order (ported from the verifier). */
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
			// As expected.
		}
		assertEquals(0, recipeCalls.get());
	}

	/** Reject a non-finite crossExtent (ported from the verifier). */
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
			// As expected.
		}
		assertEquals(0, recipeCalls.get());
	}

	/**
	 * COLUMN terminal formula: {@code childFrame==null} and snapshot depth==1 (the owner itself is the
	 * snapshot's only level, with no descendants) terminate in one unit of open text (equivalent to the
	 * old {@code ResumeTail.OpenText}; ports the ColumnResumeProgramCompiler convention).
	 */
	public void testColumnNullChildFrameDepth1EndsInOpenText() {
		final OpenPathSnapshot snapshot = plainSnapshot(1, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		final ContinuationValidator.PathShape shape = ContinuationValidator.validateColumn(emptyAnchor(), snapshot,
				null);

		assertEquals(1, shape.firstOpenPathIndex());
		assertEquals(1, shape.terminalShape().depth());
	}

	/**
	 * COLUMN terminal formula: {@code childFrame==null} and snapshot depth&gt;1 give an open structure
	 * of depth snapshot.depth(), starting at index 1 (equivalent to the old {@code ResumeTail
	 * .LegacyOpen(1, snapshotDepth)}).
	 */
	public void testColumnNullChildFrameDeepSnapshotKeepsFullOpenDepth() {
		final OpenPathSnapshot snapshot = plainSnapshot(3, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		final ContinuationValidator.PathShape shape = ContinuationValidator.validateColumn(emptyAnchor(), snapshot,
				null);

		assertEquals(1, shape.firstOpenPathIndex());
		assertEquals(3, shape.terminalShape().depth());
	}

	/**
	 * COLUMN terminal formula: a chain traversed all the way through is fully collected
	 * ({@code chainFrames + 1 == snapshotDepth}, terminating in open text).
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
	 * Reject a violation of the COLUMN depth formula
	 * ({@code chainFrames + tailDepth == snapshotDepth}, without PAGE's {@code -1} adjustment).
	 */
	public void testColumnRejectsDepthMismatch() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		// chainFrames=1 + tailDepth=3 != snapshotDepth=3 (deliberately malformed).
		final Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0,
				List.of(), new Continuation.OpenTail.OpenTailShape(OpenShape.of(3)));
		final OpenPathSnapshot snapshot = plainSnapshot(3, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		try {
			ContinuationValidator.validateColumn(emptyAnchor(), snapshot, frame);
			fail("COLUMN depth不整合はContinuationInvariantViolationExceptionになるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// As expected.
		}
		assertEquals(0, recipeCalls.get());
	}

	/** A bounded walk rejects a frame chain that exceeds the snapshot depth. */
	public void testColumnRejectsChainExceedingSnapshotDepth() {
		final AtomicInteger recipeCalls = new AtomicInteger();
		final FragmentRecipe recipe = throwingRecipe(recipeCalls);

		Continuation.ContinuationFrame frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
				new Continuation.OpenTail.OpenTailShape(OpenShape.TEXT));
		for (int i = 0; i < 3; ++i) {
			frame = new Continuation.ContinuationFrame(recipe, null, null, 0, List.of(),
					new Continuation.OpenTail.Child(frame));
		}
		// A chain of four frames with snapshot depth=2 (only index 1 is allowed).
		final OpenPathSnapshot snapshot = plainSnapshot(2, OpenPathSnapshot.AnchorKind.COLUMN_OWNER);

		try {
			ContinuationValidator.validateColumn(emptyAnchor(), snapshot, frame);
			fail("snapshot深さを超えるチェーンは拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// As expected.
		}
		assertEquals(0, recipeCalls.get());
	}

	/** Direct actual-fragment signature check (E-3 increment 2): matches pass; mismatches throw a typed exception. */
	public void testFragmentSignatureCheck() {
		final OpenPathSnapshot snapshot = plainSnapshot(2, OpenPathSnapshot.AnchorKind.PAGE_ROOT);

		// Match (same signature as the snapshot descriptor).
		ContinuationValidator.checkFragmentSignature(snapshot, 0,
				new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.TB, 1));

		// Class mismatch.
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 0, new OpenPathSnapshot.FragmentSignature(
					MulticolumnBlockBox.class, WritingMode.TB, 1));
			fail("class不一致の署名は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// As expected.
		}

		// writing-mode mismatch.
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 1,
					new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.RL, 1));
			fail("writing-mode不一致の署名は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// As expected.
		}

		// column-count mismatch.
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 1,
					new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.TB, 2));
			fail("column-count不一致の署名は拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// As expected.
		}

		// Index out of range.
		try {
			ContinuationValidator.checkFragmentSignature(snapshot, 2,
					new OpenPathSnapshot.FragmentSignature(FlowBlockBox.class, WritingMode.TB, 1));
			fail("snapshot深さ超過のindexは拒否されるはずです");
		} catch (ContinuationInvariantViolationException expected) {
			// As expected.
		}
	}
}
