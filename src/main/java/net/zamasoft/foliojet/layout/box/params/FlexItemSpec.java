package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.css.value.FlexBasisValue;

/**
 * Flex itemの伸縮・整列指定です(Flex F1a、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2)。{@link GridItemSpec}と同じく
 * {@link FlowPos}に1参照として載り、FlowPosTemplate経由でソース再生・
 * レシピにも運ばれる(再生決定性)。全既定は{@link #DEFAULT} singletonを
 * 共有するため、非Flex要素の常時保持コストは参照1個。
 *
 * <p>
 * {@code minWidthAuto}/{@code minHeightAuto}は自動最小サイズ(§4.5)の
 * 復元用——{@code BlockParams.minSize}は通常ブロック用にautoを0へ落とす
 * ため、著者がmin-width/min-heightを宣言していない事実をここで保つ。
 * alignSelfはF3c、orderはF5aで解析される(それまで既定値)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record FlexItemSpec(double grow, double shrink, FlexBasisValue basis, BoxAlignment alignSelf, int order,
		boolean minWidthAuto, boolean minHeightAuto) {

	/** 全既定(grow 0・shrink 1・basis auto・alignSelf auto・order 0・min両auto)。 */
	public static final FlexItemSpec DEFAULT = new FlexItemSpec(0, 1, FlexBasisValue.AUTO_VALUE,
			BoxAlignment.AUTO, 0, true, true);

	public static FlexItemSpec of(final double grow, final double shrink, final FlexBasisValue basis,
			final BoxAlignment alignSelf, final int order, final boolean minWidthAuto,
			final boolean minHeightAuto) {
		if (grow == 0 && shrink == 1 && basis.isAuto() && alignSelf == BoxAlignment.AUTO && order == 0
				&& minWidthAuto && minHeightAuto) {
			return DEFAULT;
		}
		return new FlexItemSpec(grow, shrink, basis, alignSelf, order, minWidthAuto, minHeightAuto);
	}

	public boolean isDefault() {
		return this == DEFAULT;
	}
}
