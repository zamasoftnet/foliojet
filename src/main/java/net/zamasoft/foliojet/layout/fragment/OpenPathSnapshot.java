package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Optional;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.BoxSubtype;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * 破断時の{@code flowStack}(開いた祖先チェーン)を、mutableなボックス
 * identityから独立に観測したスナップショットです(2026-07-21新設、
 * M6b Phase B B2)。{@link ContinuationCapability#classify}による分類は
 * ここで一度だけ行い(破断後・resume後に再分類しない)、
 * {@link ResumeProgramCompiler}はこの結果をそのまま運ぶ
 * (ChatGPT Pro相談で確認、
 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b2-resume-program.md)。
 *
 * @param rootFlow    ルートボックスの書字方向
 * @param levels      flowStackの各レベル(index 0 = root)
 * @param firstBarrier 最初に収集不能と判定されたレベル(全レベルが
 *                     収集可能なら空)
 */
public record OpenPathSnapshot(WritingMode rootFlow, List<OpenLevelDescriptor> levels,
		Optional<CapabilityBarrier> firstBarrier) {

	public OpenPathSnapshot {
		levels = List.copyOf(levels);
	}

	/** flowStackの深さ(全レベル数、rootを含む)。 */
	public int depth() {
		return this.levels.size();
	}

	/** 収集不能と判定された最初のレベルの位置と理由です。 */
	public record CapabilityBarrier(int openPathIndex, ContinuationCapability reason) {
	}

	/** レベルがrootか、分類済みの祖先かを表します。 */
	public sealed interface OpenLevelRole {
		record Root() implements OpenLevelRole {
		}

		record Ancestor(ContinuationCapability capability) implements OpenLevelRole {
		}
	}

	/**
	 * flowStackの1レベルの、mutableなボックスから独立した記述です。
	 *
	 * @param index       flowStack上の位置(0 = root)
	 * @param boxClass    実行時クラス(段組・RubyBodyBox等のサブタイプ判定に使う)
	 * @param subtype     {@link net.zamasoft.foliojet.layout.box.IBox#getSubtype()}
	 * @param flow        書字方向
	 * @param columnCount 段組数
	 * @param sourceAnchor split前のソースアンカー(診断用。resume後の新
	 *                     fragmentは旧anchorを継承しないため、照合の主キー
	 *                     には使わない)
	 * @param role        rootか分類済み祖先か
	 */
	public record OpenLevelDescriptor(int index, Class<? extends AbstractContainerBox> boxClass, BoxSubtype subtype,
			WritingMode flow, int columnCount, long sourceAnchor, OpenLevelRole role) {

		/** resume時の実fragmentと照合するための署名。 */
		public FragmentSignature fragmentSignature() {
			return new FragmentSignature(this.boxClass, this.subtype, this.flow, this.columnCount);
		}
	}

	/**
	 * fragment identityの照合キーです(class/subtype/flow/columnCount)。
	 * {@code sourceAnchor}は含めない(新fragmentは旧anchorを継承しないため)。
	 */
	public record FragmentSignature(Class<? extends AbstractContainerBox> boxClass, BoxSubtype subtype,
			WritingMode flow, int columnCount) {

		public static FragmentSignature from(final AbstractContainerBox box) {
			return new FragmentSignature(box.getClass(), box.getSubtype(), box.getBlockParams().flow,
					box.getColumnCount());
		}
	}
}
