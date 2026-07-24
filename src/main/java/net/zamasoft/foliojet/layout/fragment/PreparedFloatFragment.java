package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * 分割されたブロックfloatの継続断片の材料です(2026-07-24新設、
 * 排除域A-3a。{@code docs/consultations/consult-exclusion-p2-design-codex.txt}
 * §3の型)。
 *
 * <p>
 * 従来は{@code containerBox.split}が残余boxを即時構築
 * ({@code splitPage}→{@code recipe.instantiate})していたが、A-3aでは
 * 切断時に前断片のmutation({@code splitPageState})を一度だけ行い、
 * 残余boxの構築材料(recipe・state・残余コンテナ・crossExtent)をこの型で
 * 運び、受け側{@code Floatings}へ接続する時点で一度だけ
 * {@link #materialize()}する。
 * </p>
 *
 * <p>
 * {@code materialize()}は<b>一回限定</b>——boxの構築は
 * {@code container.setBox}の副作用(コンテナのbox参照の付け替え)を持つ
 * ため、二重実行は前断片・残余の配線を壊す。二回目の呼び出しは
 * {@link IllegalStateException}。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class PreparedFloatFragment {
	private final int serial;
	private final FragmentRecipe recipe;
	private final FragmentState state;
	private final Container remainder;
	private final double crossExtent;
	private boolean materialized = false;

	/**
	 * @param serial      呼び出し側({@code Floatings})が管理するfloatの
	 *                    識別子。残余のFloatingへそのまま引き継がれる
	 * @param recipe      断片ボックスの再構成レシピ(前断片のmutation前に
	 *                    取得したもの)
	 * @param state       断片状態({@code splitPageState}の実出力——
	 *                    再計算ではない)
	 * @param remainder   残余コンテナ(切断で分離済みの内容)
	 * @param crossExtent 切断時点の交差軸寸法({@code splitPage}と同じく
	 *                    前断片mutation前のraw width/height)
	 */
	public PreparedFloatFragment(final int serial, final FragmentRecipe recipe, final FragmentState state,
			final Container remainder, final double crossExtent) {
		this.serial = serial;
		this.recipe = recipe;
		this.state = state;
		this.remainder = remainder;
		this.crossExtent = crossExtent;
	}

	public int serial() {
		return this.serial;
	}

	/**
	 * 継続断片boxを構築します(一回限定)。構築は旧即時経路
	 * ({@code splitPage})と同一の
	 * {@link AbstractBlockBox#continueFragment(FragmentRecipe, FragmentState, Container, double)}
	 * による。
	 *
	 * @return 継続断片box
	 * @throws IllegalStateException 二回目の呼び出し
	 */
	public IFloatBox materialize() {
		if (this.materialized) {
			throw new IllegalStateException("PreparedFloatFragmentは一回しかmaterializeできない: serial=" + this.serial);
		}
		this.materialized = true;
		return (IFloatBox) AbstractBlockBox.continueFragment(this.recipe, this.state, this.remainder,
				this.crossExtent);
	}
}
