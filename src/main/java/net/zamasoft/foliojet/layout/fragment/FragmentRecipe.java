package net.zamasoft.foliojet.layout.fragment;

/**
 * 断片ボックスの再構成レシピです(C1d-B)。
 *
 * <p>
 * 切断時に前断片から必要な値をキャプチャして作られ、resume が断片状態と
 * 残余コンテナから継続断片ボックスを構成します。従来この構成は前断片
 * ボックスへの仮想呼び出し(splitPage オーバーライド)だったため、
 * 継続記述が mutable な旧ボックスを factory として保持していました。
 * レシピ化により継続はボックス identity から独立します。
 * </p>
 *
 * <p>
 * 実装の規約: 必要な値(params/pos の参照+サブタイプ固有の解決済み
 * 状態)をキャプチャし、<b>前断片ボックス(this)への参照を保持しない</b>
 * こと。{@code prev::splitPage} のようなメソッド参照は不可(mutable な
 * 旧ボックスを掴み続け、名前を変えた prev 依存になる — 外部レビュー指摘)。
 * </p>
 */
public interface FragmentRecipe {
	/**
	 * 継続断片ボックスを構成します。
	 *
	 * @param state     断片状態
	 * @param container 継続断片の内容
	 * @return 継続断片ボックス
	 */
	net.zamasoft.foliojet.layout.box.AbstractBlockBox instantiate(FragmentState state,
			net.zamasoft.foliojet.layout.box.content.Container container);
}
