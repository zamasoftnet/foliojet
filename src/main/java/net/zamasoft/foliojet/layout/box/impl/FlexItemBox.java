package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * Flexアイテムのボックスです(Flex F1d、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2)。
 *
 * <p>
 * {@code GridItemBox}(中立合成wrapper)と違い、plainなブロック直下子では
 * **authored childのBlockParams/FlowPosを引き継いで生成**し、元の外箱は
 * 構築しない——将来のstretch(F3c)でauthoredの背景・枠がitemサイズへ
 * 追随するため(答申の最重要プロトタイプ条件)。匿名テキスト・置換要素・
 * 非plain子(表・入れ子コンテナ等)のみ中立paramsのwrapperになる。
 * 合成経路でもauthored経路でもsource protocolへは露出させない
 * (記録・再生時は子イベントから決定的に再合成される)。
 * </p>
 */
public class FlexItemBox extends FlowBlockBox {

	public FlexItemBox(final BlockParams params, final FlowPos pos) {
		super(params, pos);
	}

	/**
	 * 線方向のitem開始位置(Flexコンテナ内辺原点、自然位置からの相対)を
	 * 設定します(F6: 縦書きでは物理Y)。
	 */
	public void setFlexLineOffset(final double lineOffset, final boolean vertical) {
		if (vertical) {
			this.offsetY = lineOffset;
		} else {
			this.offsetX = lineOffset;
		}
	}

	/** 確定した線方向内寸(content-box)を設定します(bind直前に呼ぶ。縦書き=高さ)。 */
	public void setFlexMainSize(final double mainSize, final boolean vertical) {
		if (vertical) {
			this.height = mainSize;
		} else {
			this.width = mainSize;
		}
	}
}
