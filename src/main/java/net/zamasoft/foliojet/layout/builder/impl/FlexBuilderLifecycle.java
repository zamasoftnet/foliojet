package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.params.FlexDirection;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * Flex構築ライフサイクルの入口です(Flex F1d——{@code GridBuilderLifecycle}と
 * 同じ薄い形)。不適格は単一列フロー(F0)へ落とす。
 */
public final class FlexBuilderLifecycle {
	private FlexBuilderLifecycle() {
		// 静的ユーティリティ
	}

	/**
	 * row配置を適用できるFlexかを判定します(consult-codex-2026-08-02-
	 * flexbox.txt「段階的fallback規則」F1: TB+row+nowrapのみ)。
	 * 宿主はBlockBuilderに加えてTwoPass(F1f——実行計画をFlexEventとして
	 * 録画し幅確定後にbind)も適格(column=F4、reverse/wrap=F2/F5、
	 * 縦書き=F6)。
	 */
	public static boolean eligible(final FlexBox flexBox, final Builder builder) {
		final FlexParams params = flexBox.getFlexParams();
		if (params.flexDirection != FlexDirection.ROW || params.flexWrap == FlexWrap.WRAP_REVERSE) {
			return false;
		}
		if (params.flexWrap == FlexWrap.WRAP
				&& (params.flow.isVertical() ? params.size.getWidthType() : params.size.getHeightType())
						!= net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			// F2b: wrapはautoのcross sizeのみ(definite crossの余白分配=
			// align-contentはF3dで解禁)
			return false;
		}
		if (params.flow.isVertical()) {
			return false;
		}
		return builder instanceof BlockBuilder || builder instanceof TwoPassBlockBuilder;
	}

	/** FlexBuilderを開始します(適格判定済みであること)。 */
	public static FlexBuilder start(final Builder builder, final FlexBox flexBox) {
		return new FlexBuilder(builder, flexBox);
	}
}
