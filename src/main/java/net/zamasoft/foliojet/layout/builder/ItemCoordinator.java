package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 「直接子をitem化して終端で一括配置する」構築coordinatorの共通契約です
 * (2026-08-02——Grid/Flexで鏡像実装されていたDocumentBuilderのフック
 * (直下待ち判定・匿名item・element itemの畳み込み・終端finish)を
 * 一般化する。{@code TableBuilder}は行/セルの独自プロトコルを持つため
 * 対象外)。
 *
 * <p>
 * 実装は{@code DocumentBuilder.builderStack}に積まれるが{@code Builder}
 * ではない。itemの中身は{@code requireAnonymousItem()}等が返すitem builder
 * (通常は{@code TwoPassBlockBuilder})が受け、coordinator自身は録画の
 * 保持と終端の配置だけを担う。
 * </p>
 */
public interface ItemCoordinator {

	/** coordinatorが構築中のコンテナboxです(直下待ち判定のboxStack照合用)。 */
	public IBox getItemHostBox();

	/** itemが開いているか(elementまたは匿名)。 */
	public boolean hasOpenItem();

	/** element itemが開いているか。 */
	public boolean hasOpenElementItem();

	/**
	 * 直接テキスト/インライン用の匿名itemを開きます。既に匿名itemが
	 * 開いていればnull(積み直し不要)。
	 */
	public Builder requireAnonymousItem();

	/** 開いているitemを確定します(録画完了点)。 */
	public void itemClosed();

	/** コンテナ終端です(実行計画としてホストへ渡す)。 */
	public void finish();
}
