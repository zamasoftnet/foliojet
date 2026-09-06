package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;

/**
 * Retained実行計画のFlexが、ホストのビルダーへ自分を組み込むための
 * 契約です(Flex F1f、2026-08-02——consult-codex-2026-08-02-flexbox.txt。
 * {@link RetainedGrid}と同型)。
 *
 * <p>
 * 通常フロー(BlockBuilder宿主)では{@code addFlex}が即時に
 * {@link #bind}を呼ぶ。TwoPass宿主ではownership ledgerに計画を登録し、
 * 親範囲への吸収時に手放す。範囲再生で同じソースから計画を再構築し、
 * 同じ{@link #bind}で配置する。
 * </p>
 *
 * @see Builder#addFlex(RetainedFlex)
 */
public interface RetainedFlex extends TwoPass {

	public FlexBox getFlexBox();

	/**
	 * 構築済みのFlexをホストへ組み込みます(§9.7解決→item bind→
	 * row配置→親カーソル同期)。ホストのactive flowが当のFlexBoxで
	 * ある間に呼ぶこと。
	 */
	public void bind(Builder host);

	/**
	 * 親のrange化に吸収されるとき、保持しているitem本文の所有を終端します
	 * (範囲再生が同じソースからFlex全体を再構築する)。
	 */
	public void abandonForParentRange();
}
