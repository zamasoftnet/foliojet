package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;

/**
 * テーブルを構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface TableBuilder {
	public TableBox getTableBox();

	public void startInnerTable(AbstractInnerTableBox box);

	public void endInnerTable();

	public Builder newContext(AbstractContainerBox box);

	/**
	 * 表の終了処理を行います(A-2、2026-07-30)。実行計画の終端は実装ごとに
	 * 異なる(Incremental=残余のコミットとレイアウト終了、Retained=
	 * {@code host.addTable(this)}による全体bind)ため、呼び出し側が
	 * isIncrementalを問うて分岐するのではなく実装自身に委ねる
	 * (tell-don't-ask——{@link #prepareEnterCell}と同じ方針。
	 * 旧{@code isIncremental()}はこの移行で撤去した)。
	 */
	public void finish(Builder host);

	/**
	 * セル/キャプションに入る直前(newContext呼び出し前)に呼ばれます
	 * (C4-C深化、2026-07-19)。Incremental(OnePass)はインライン文脈を
	 * 閉じ直す必要があるが、Retained(TwoPass)は独立した内側ビルダーを
	 * 持つため何もしなくてよい——この判断をDocumentBuilder側で
	 * isIncremental()を問うのではなく、TableBuilder実装自身に委ねる
	 * (tell-don't-ask)。既定はRetained相当の no-op。
	 */
	public default void prepareEnterCell(TableBuilderHost host) {
	}

	/**
	 * カラム/行グループ/行に入る直前に呼ばれます(C4-C深化)。既定は
	 * no-op。
	 */
	public default void prepareEnterTrack(TableBuilderHost host) {
	}

	/**
	 * カラム/行グループ/行に入った直後に呼ばれます(C4-C深化)。既定は
	 * no-op。
	 */
	public default void afterEnterTrack(TableBuilderHost host) {
	}

	/**
	 * セル/キャプションのコンテナビルダーが閉じた直後(録画完了点)に
	 * 呼ばれます(E-6増分5a、2026-07-24)。Retained実装は、適格なセル本文を
	 * 「IntrinsicSizes数値+LayoutSource範囲参照(+lease)」保持へ
	 * seal(records解放)する。既定はno-op(Incremental表のセルは対象外——
	 * 保持窓が行単位で短く、close前ピークの縮小は別増分で扱う)。
	 *
	 * @param cellBuilder 閉じたセル/キャプションのコンテナビルダー
	 */
	public default void sealCellContext(Builder cellBuilder) {
	}
}
