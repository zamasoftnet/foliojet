package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.impl.GridBox;

/**
 * Retained実行計画のGridが、ホストのビルダーへ自分を組み込むための
 * 契約です(Grid G3d1、2026-07-31——consult-codex-2026-07-31-grid-g3.txt
 * Q3。{@link RetainedTable}と同型)。
 *
 * <p>
 * 通常フロー(BlockBuilder宿主)では{@code addGrid}が即時に
 * {@link #bind}を呼ぶ。TwoPass宿主ではownership ledgerに計画を登録し、
 * 親範囲への吸収時に手放す。範囲再生で同じソースから計画を再構築し、
 * 同じ{@link #bind}で配置する。
 * </p>
 *
 * @see Builder#addGrid(RetainedGrid)
 */
public interface RetainedGrid extends TwoPass {

	public GridBox getGridBox();

	/**
	 * 構築済みのGridをホストへ組み込みます(トラック幅解決→item bind→
	 * 行配置→親カーソル同期)。ホストのactive flowが当のGridBoxで
	 * ある間に呼ぶこと。
	 */
	public void bind(Builder host);

	/**
	 * 親のrange化に吸収されるとき、保持しているitem本文の所有を終端します
	 * (G3d3で使用。範囲再生が同じソースからGrid全体を再構築する)。
	 */
	public void abandonForParentRange();
}
