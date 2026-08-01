package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;

/**
 * レイアウトコンテキストです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Builder.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public interface Builder extends GlyphHandler, LayoutStack {
	public boolean isMain();

	/**
	 * shrink-to-fitを実行中のコンテキストはtrueを返します。
	 * 
	 * @return
	 */
	public boolean isTwoPass();

	/**
	 * 通常のフローのブロックを開始します。
	 * 
	 * @param flowContainer
	 */
	public void startFlowBlock(FlowBlockBox flowContainer);

	/**
	 * 通常のフローのブロックを終了します。
	 */
	public void endFlowBlock();

	/**
	 * 構築済みのボックスを追加します。
	 * 
	 * @param box
	 */
	public void addBound(IBox box);

	/**
	 * 構築済みのRetained表を追加します(A-2、2026-07-30: 実装が
	 * RetainedTableBuilderへキャストする暗黙の前提を型へ昇格)。
	 * 呼び出し元は{@link TableBuilder#finish(Builder)}のRetained実装のみ。
	 */
	public void addTable(RetainedTable tableBuilder);

	/**
	 * 構築済みのGrid実行計画を追加します(Grid G3d1、2026-07-31——
	 * {@link #addTable}と同型)。BlockBuilderは即時bind、TwoPassは
	 * 録画({@code GridEvent})+固有寸法contributionへ。
	 */
	public void addGrid(RetainedGrid gridBuilder);

	/** Flex実行計画の組み込みです(Flex F1f——addGridと同型)。 */
	public void addFlex(RetainedFlex flexBuilder);

	/**
	 * 新しいレイアウトコンテキストを返します。
	 * 
	 * @param stfBox
	 * @return
	 */
	public Builder newBuilder(AbstractBlockBox stfBox);

	/**
	 * テキストボックスがあれば終了します。
	 */
	public void endTextBlock();
}
