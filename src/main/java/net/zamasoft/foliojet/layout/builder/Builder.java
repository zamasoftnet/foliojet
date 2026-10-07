package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;

/**
 * Layout context.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Builder.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public interface Builder extends GlyphHandler, LayoutStack {
	public boolean isMain();

	/**
	 * Returns true for a context currently performing shrink-to-fit.
	 *
	 * @return
	 */
	public boolean isTwoPass();

	/**
	 * Starts a normal-flow block.
	 *
	 * @param flowContainer
	 */
	public void startFlowBlock(FlowBlockBox flowContainer);

	/**
	 * Ends a normal-flow block.
	 */
	public void endFlowBlock();

	/**
	 * Adds a constructed box.
	 *
	 * @param box
	 */
	public void addBound(IBox box);

	/**
	 * Adds a constructed Retained table (A-2, 2026-07-30: promotes the implementation's implicit
	 * assumption of a cast to RetainedTableBuilder into the type system).
	 * Called only by the Retained implementation of {@link TableBuilder#finish(Builder)}.
	 */
	public void addTable(RetainedTable tableBuilder);

	/**
	 * Adds a constructed Grid execution plan (Grid G3d1, 2026-07-31; analogous to {@link #addTable}).
	 * BlockBuilder binds immediately; TwoPass records it ({@code GridEvent}) and adds an intrinsic-size
	 * contribution.
	 */
	public void addGrid(RetainedGrid gridBuilder);

	/** Incorporates a Flex execution plan (Flex F1f; analogous to addGrid). */
	public void addFlex(RetainedFlex flexBuilder);

	/**
	 * Returns a new layout context.
	 *
	 * @param stfBox
	 * @return
	 */
	public Builder newBuilder(AbstractBlockBox stfBox);

	/**
	 * Ends the text box if one exists.
	 */
	public void endTextBlock();
}
