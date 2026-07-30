package net.zamasoft.foliojet.layout.fragment;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 継続の正本({@link Continuation}のPAGE入力/COLUMN入力)を直接検査します
 * (2026-07-24新設、E-3増分1。
 * docs/consultations/consult-e3-single-source-codex.md §3)。
 *
 * <p>
 * 旧program系(E-3増分6で撤去した{@code ResumeProgramCompiler}/
 * {@code ColumnResumeProgramCompiler}と{@code ContinuationVerifier}/
 * {@code ColumnContinuationVerifier})が担っていた不変条件——snapshot/
 * continuationの深さ式、有界のframe walk(循環・null・snapshot超過の拒否)、
 * prefixのserial順序・範囲の正当性、crossExtentの有限性、snapshotレベル
 * との対応——をここへ移植した。program撤去後の唯一の検証層である。
 * </p>
 *
 * <p>
 * 規約:
 * </p>
 * <ul>
 * <li>検証中に{@link FragmentRecipe#instantiate}を一切呼ばない(fragment
 * 生成・builder状態の変異なし)。</li>
 * <li>frame走査は再帰ではなく、snapshot深さを上限とする有界の反復
 * (merge gate。深さ5000程度でもJVM再帰なしに処理できる)。</li>
 * <li>失敗はすべて{@link ContinuationInvariantViolationException}
 * (既存compiler/verifierと同一の型付き例外)。</li>
 * </ul>
 */
public final class ContinuationValidator {
	private ContinuationValidator() {
	}

	/**
	 * 検証済みopen pathの形の要約です(検証walkの副産物)。COLUMN継続の
	 * 終端開き形の正本({@code ColumnContinuation.pathShape()})として実行に
	 * 使われる(かつてはtail policy——{@code WorklistTailGate}——の直接
	 * 導出にも使われていたが、2026-07-30のlegacy再帰撤去=増分4dでgateは
	 * 退役した)。
	 *
	 * @param firstOpenPathIndex 最初の未収集open path index(チェーンとして
	 *                           first-classに歩けるframeの直後の位置。全レベル
	 *                           収集済みなら{@code snapshot.depth()}に一致)
	 * @param terminalShape      終端frameの開き形
	 */
	public record PathShape(int firstOpenPathIndex, OpenShape terminalShape) {
	}

	/**
	 * PAGE継続を直接検証します(旧{@code ResumeProgramCompiler.compile}+
	 * {@code ContinuationVerifier.verify}の不変条件の移植)。
	 *
	 * @throws ContinuationInvariantViolationException 構造が破れている場合
	 */
	public static PathShape validatePage(final OpenPathSnapshot snapshot, final Continuation continuation) {
		if (snapshot == null || continuation == null || continuation.root() == null) {
			throw new ContinuationInvariantViolationException("PAGE continuation has null snapshot/root");
		}
		if (snapshot.depth() <= 0) {
			throw new ContinuationInvariantViolationException("snapshot depth must be positive");
		}
		if (continuation.depth() != snapshot.depth()) {
			throw new ContinuationInvariantViolationException(
					"snapshot depth=" + snapshot.depth() + ", continuation depth=" + continuation.depth());
		}

		final Set<Continuation.ContinuationFrame> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Continuation.ContinuationFrame frame = continuation.root();
		int index = 0;
		while (true) {
			checkFrame(snapshot, seen, frame, index);

			switch (frame.tail()) {
			case Continuation.OpenTail.Child(final Continuation.ContinuationFrame child) -> {
				frame = child;
				++index;
			}
			case Continuation.OpenTail.OpenTailShape(final OpenShape shape) -> {
				final int openDepth = shape.depth();
				final int frameCount = index + 1;
				if (frameCount + openDepth - 1 != continuation.depth()) {
					throw new ContinuationInvariantViolationException("depth invariant failed: frames=" + frameCount
							+ ", tailDepth=" + openDepth + ", continuationDepth=" + continuation.depth());
				}
				if (frameCount == snapshot.depth() && openDepth != 1) {
					throw new ContinuationInvariantViolationException(
							"fully collected path must end in OpenText, but openDepth=" + openDepth);
				}
				return new PathShape(frameCount, shape);
			}
			}
		}
	}

	/**
	 * COLUMN継続の入力(owner anchor+owner内側の子孫チェーン)を直接検証
	 * します(旧{@code ColumnResumeProgramCompiler.compileColumn}+
	 * {@code ColumnContinuationVerifier.verify}の不変条件の移植)。
	 *
	 * <p>
	 * PAGEとの深さ式の違い(owner=index 0はfragment levelではないため
	 * {@code chainFrames + tailDepth == snapshotDepth})、および
	 * {@code childFrame == null}(owner直下に開いた子孫が全くない、または
	 * 貫通しなかった)が正規のケースであることは、既存compiler/verifierの
	 * 規約をそのまま引き継ぐ。
	 * </p>
	 *
	 * @param anchor     owner直下の残余
	 * @param snapshot   破断時の相対open pathスナップショット(index 0 = owner)
	 * @param childFrame owner直下で貫通した場合の継続フレーム(貫通しなければnull)
	 * @throws ContinuationInvariantViolationException 構造が破れている場合
	 */
	public static PathShape validateColumn(final ColumnAnchor anchor, final OpenPathSnapshot snapshot,
			final Continuation.ContinuationFrame childFrame) {
		if (anchor == null || snapshot == null) {
			throw new ContinuationInvariantViolationException("COLUMN continuation has null snapshot/anchor");
		}
		if (snapshot.depth() <= 0) {
			throw new ContinuationInvariantViolationException("snapshot depth must be positive");
		}

		if (childFrame == null) {
			// 子孫を貫通しなかった正規のケース。owner自身の開きは暗黙の
			// OpenText 1単位として別勘定される(ColumnResumeProgramCompilerの
			// 規約と同一): depth==1ならOpenText、それ以外は深さsnapshot.depth()
			// のlegacy開きが index 1 から始まる。
			return new PathShape(1, OpenShape.of(snapshot.depth()));
		}

		final Set<Continuation.ContinuationFrame> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Continuation.ContinuationFrame frame = childFrame;
		int index = 1;
		while (true) {
			checkFrame(snapshot, seen, frame, index);

			switch (frame.tail()) {
			case Continuation.OpenTail.Child(final Continuation.ContinuationFrame child) -> {
				frame = child;
				++index;
			}
			case Continuation.OpenTail.OpenTailShape(final OpenShape shape) -> {
				final int openDepth = shape.depth();
				final int chainFrames = index; // index 1..index を走査済み
				if (chainFrames + openDepth != snapshot.depth()) {
					throw new ContinuationInvariantViolationException("COLUMN depth invariant failed: levels="
							+ chainFrames + ", tailDepth=" + openDepth + ", snapshotDepth=" + snapshot.depth());
				}
				final int firstOpenPathIndex = 1 + chainFrames;
				if (firstOpenPathIndex == snapshot.depth() && openDepth != 1) {
					throw new ContinuationInvariantViolationException(
							"fully collected path must end in OpenText, but openDepth=" + openDepth);
				}
				return new PathShape(firstOpenPathIndex, shape);
			}
			}
		}
	}

	/**
	 * instantiate直後の実fragmentの署名(class/writing-mode/
	 * column-count)を、破断時snapshotの対応レベルと直接照合します
	 * (E-3増分2。shadow({@code ResumeProgramTrace})の{@code
	 * ResumeOp.Instantiate}照合が持っていた唯一の独立価値の直接化)。
	 * builder状態の変異(startFlowBlock/restyle)より前に呼ぶこと。
	 *
	 * @throws ContinuationInvariantViolationException 署名が一致しない場合
	 */
	public static void checkFragmentSignature(final OpenPathSnapshot snapshot, final int openPathIndex,
			final OpenPathSnapshot.FragmentSignature actual) {
		if (openPathIndex < 0 || openPathIndex >= snapshot.depth()) {
			throw new ContinuationInvariantViolationException(
					"fragment openPathIndex=" + openPathIndex + " out of snapshot depth=" + snapshot.depth());
		}
		final OpenPathSnapshot.FragmentSignature expected = snapshot.levels().get(openPathIndex).fragmentSignature();
		if (!expected.equals(actual)) {
			throw new ContinuationInvariantViolationException("fragment signature mismatch at openPathIndex="
					+ openPathIndex + ": expected=" + expected + ", actual=" + actual);
		}
	}

	/**
	 * frame walkの1ステップ分の共通検証です(null・循環・snapshot深さ超過・
	 * snapshotレベル対応・crossExtent・prefix順序/範囲)。
	 */
	private static void checkFrame(final OpenPathSnapshot snapshot, final Set<Continuation.ContinuationFrame> seen,
			final Continuation.ContinuationFrame frame, final int index) {
		if (frame == null) {
			throw new ContinuationInvariantViolationException("null frame at index " + index);
		}
		if (!seen.add(frame)) {
			throw new ContinuationInvariantViolationException("cyclic frame chain at index " + index);
		}
		if (index >= snapshot.depth()) {
			throw new ContinuationInvariantViolationException("frame chain exceeds snapshot depth");
		}

		final OpenPathSnapshot.OpenLevelDescriptor descriptor = snapshot.levels().get(index);
		if (descriptor.index() != index) {
			throw new ContinuationInvariantViolationException(
					"level " + index + " descriptor index=" + descriptor.index());
		}
		if (!Double.isFinite(frame.crossExtent()) || frame.crossExtent() < 0) {
			throw new ContinuationInvariantViolationException(
					"level " + index + " has invalid crossExtent=" + frame.crossExtent());
		}

		int lastSerial = -1;
		final List<Continuation.SourceRange> prefix = frame.prefixItems();
		for (final Continuation.SourceRange range : prefix) {
			if (range.serial() <= lastSerial) {
				throw new ContinuationInvariantViolationException(
						"level " + index + " prefix serial not strictly increasing: " + range.serial());
			}
			lastSerial = range.serial();
			if (range.fromId() < 0 || range.toId() < range.fromId()) {
				throw new ContinuationInvariantViolationException(
						"level " + index + " has invalid source range: " + range);
			}
		}
	}
}
