package net.zamasoft.foliojet.layout.builder;

/**
 * Retained実行計画の表(表全体を保持してからコミットする——
 * {@code table-layout:auto}相当)が、ホストのビルダーへ自分を
 * 組み込むための契約です(A-2、2026-07-30)。
 *
 * <p>
 * それまで{@code Builder.addTable(TableBuilder)}は、全実装が
 * {@code RetainedTableBuilder}へハードキャストしてから
 * {@code prepareLayout()}/{@code bind()}を呼んでいた——Retainedしか
 * 到達しないという知識が呼び出し側の暗黙の前提だった。この前提を
 * 型に昇格し、キャストを排除する。
 * </p>
 *
 * @see TableBuilder#finish(Builder) 実行計画ごとの終端処理の入口
 */
public interface RetainedTable extends TableBuilder, TwoPass {
	/**
	 * 全行の読み取りが終わったあと、bindに先立って寸法・列幅を確定します。
	 */
	public void prepareLayout();

	/**
	 * 構築済みの表をホストへ組み込みます(実測済み内容の再駆動)。
	 */
	public void bind(Builder host);
}
