package net.zamasoft.foliojet.style.builder.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractBlockBox;
import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IFloatBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.style.box.content.Container;
import net.zamasoft.foliojet.style.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.style.box.impl.TableBox;
import net.zamasoft.foliojet.style.box.impl.TableRowBox;
import net.zamasoft.foliojet.style.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.FloatPos;
import net.zamasoft.foliojet.style.box.params.FlowPos;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.style.box.params.TableRowPos;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.builder.LayoutStack;
import net.zamasoft.foliojet.style.util.StyleUtils;

/**
 * ドキュメント全体を構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BreakableBuilder.java 1561 2018-07-04 11:44:21Z miyabe $
 */
public abstract class BreakableBuilder extends BlockBuilder {
	private static final Logger LOG = Logger.getLogger(BreakableBuilder.class.getName());

	public static final byte MODE_NO_BREAK = 0;
	public static final byte MODE_AUTO = 1;
	public static final byte MODE_PAGE_BREAK = 2;

	/**
	 * 改ページモードです。
	 */
	protected byte mode;

	protected byte pageSide;

	protected int breakDepth = -1;

	/**
	 * 次ページに先送り可能な行数のカウントです。
	 */
	protected int widows = 0;

	/**
	 * 次のポイントでpage-break-afterによる改ページを適用するフラグです。
	 */
	protected byte breakAfter = -1;

	/**
	 * 直前での強制改ページを許可するフラグです。
	 */
	protected boolean canBreakBefore = true;

	/**
	 * ブロック間の自然改ページを許可するフラグです。
	 */
	protected boolean interflowBreak = true;

	/**
	 * 再レイアウト中のフラグです。
	 */
	protected boolean restyling = false;

	protected byte breakFloats = 0;

	protected TableBox lastTableBox;

	public BreakableBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox, byte mode) {
		super(layoutStack, contextBox);
		this.mode = mode;
	}

	/**
	 * 自動テーブルの強制改ページ。
	 * 
	 * @param tableBox
	 * @return
	 */
	private TableForceBreakMode firstTableForceBreak(TableBox tableBox) {
		// *** テーブルにはヘッダとフッタがあるので、左右指定した改ページは適用しない
		if (tableBox.getTableBodyCount() <= 0) {
			return null;
		}
		double pageLimit = this.getPageLimit();
		double last = this.pageAxis;
		if (StyleUtils.isVertical(tableBox.getTableParams().flow)) {
			last -= tableBox.getInnerWidth() + tableBox.getFrame().getFrameLeft();
		} else {
			last -= tableBox.getInnerHeight() + tableBox.getFrame().getFrameBottom();
		}
		if (tableBox.getTableHeader() != null) {
			last += tableBox.getTableHeader().getPageSize();
		}
		if (tableBox.getTableFooter() != null) {
			last += tableBox.getTableFooter().getPageSize();
		}
		byte breakMode = 0;
		AbstractInnerTableBox box = null;
		int rowGroup = 0, row = -1;
		LOOP: for (; rowGroup < tableBox.getTableBodyCount(); ++rowGroup) {
			TableRowGroupBox rowGroupBox = tableBox.getTableBody(rowGroup);
			if (rowGroup > 0) {
				TableRowGroupPos pos = rowGroupBox.getTableRowGroupPos();
				breakMode = pos.pageBreakBefore;
				if (breakMode == Types.PAGE_BREAK_PAGE || breakMode == Types.PAGE_BREAK_COLUMN) {
					// 行グループの直前の改ページ
					--rowGroup;
					box = tableBox.getTableBody(rowGroup);
					row = -1;
					break LOOP;
				}
			}
			for (row = 0; row < rowGroupBox.getTableRowCount(); ++row) {
				TableRowBox rowBox = rowGroupBox.getTableRow(row);
				last += rowBox.getPageSize();
				if (StyleUtils.compare(last, pageLimit) > 0) {
					break LOOP;
				}
				TableRowPos pos = rowBox.getTableRowPos();
				if (rowGroup > 0 || row > 0) {
					breakMode = pos.pageBreakBefore;
					if (breakMode == Types.PAGE_BREAK_PAGE || breakMode == Types.PAGE_BREAK_COLUMN) {
						// 行の直前の改ページ
						--row;
						if (row >= 0) {
							box = rowGroupBox.getTableRow(row);
						} else {
							--rowGroup;
							box = tableBox.getTableBody(rowGroup);
						}
						break LOOP;
					}
				}
				if (rowGroup == tableBox.getTableBodyCount() - 1 && row == rowGroupBox.getTableRowCount() - 1) {
					// 末尾の場合はループから抜ける
					break;
				}
				breakMode = pos.pageBreakAfter;
				if (breakMode == Types.PAGE_BREAK_PAGE || breakMode == Types.PAGE_BREAK_COLUMN) {
					// 行の直後の改ページ
					if (row < rowGroupBox.getTableRowCount() - 1) {
						box = rowBox;
					} else {
						box = rowGroupBox;
						row = -1;
					}
					break LOOP;
				}
			}
			if (rowGroup < tableBox.getTableBodyCount() - 1) {
				TableRowGroupPos pos = rowGroupBox.getTableRowGroupPos();
				breakMode = pos.pageBreakAfter;
				if (breakMode == Types.PAGE_BREAK_PAGE || breakMode == Types.PAGE_BREAK_COLUMN) {
					// 行グループの直後に改ページ
					box = rowGroupBox;
					row = -1;
					break LOOP;
				}
			}
		}
		if (box == null) {
			return null;
		}
		return new TableForceBreakMode(box, breakMode, rowGroup, row);
	}

	public void startFlowBlock(FlowBlockBox flowBox) {
		assert this.textBuilder == null : flowBox.getParams().element;

		boolean canBreakAfter = false;
		switch (flowBox.getType()) {
		case BLOCK:
			AbstractBlockBox blockBox = flowBox;
			// 境界前でのpage-break-afterの適用を許す
			if (StyleUtils.isVertical(this.getRootBox().getBlockParams().flow)) {
				canBreakAfter = !blockBox.getFrame().frame.border.getRight().isNull();
			} else {
				canBreakAfter = !blockBox.getFrame().frame.border.getTop().isNull();
			}
			break;
		}

		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			// clearによる改ページ
			final FlowPos pos = (FlowPos) flowBox.getPos();
			while (this.breakByClear(pos))
				;

			// 直前での強制改ページチェック
			if (this.mode == MODE_PAGE_BREAK) {
				if (this.breakAfter != -1 && canBreakAfter) {
					// 前のpage-break-afterによる改ページ
					this.forceBreak(this.breakAfter);
				}
				switch (pos.pageBreakBefore) {
				case Types.PAGE_BREAK_PAGE:
				case Types.PAGE_BREAK_COLUMN:
					if (this.canBreakBefore) {
						this.forceBreak(pos.pageBreakBefore);
					}
					break;
				case Types.PAGE_BREAK_VERSO:
				case Types.PAGE_BREAK_RECTO:
					if (!this.restyling && (this.canBreakBefore || pos.pageBreakBefore != this.pageSide)) {
						this.forceBreak(pos.pageBreakBefore);
					}
					break;
				case Types.PAGE_BREAK_IF_VERSO:
					if (!this.restyling && this.pageSide == Types.PAGE_BREAK_VERSO) {
						this.forceBreak(Types.PAGE_BREAK_RECTO);
					}
					break;
				case Types.PAGE_BREAK_IF_RECTO:
					if (!this.restyling && this.pageSide == Types.PAGE_BREAK_RECTO) {
						this.forceBreak(Types.PAGE_BREAK_VERSO);
					}
					break;
				case Types.PAGE_BREAK_AVOID:
					this.interflowBreak = false;
					break;
				case Types.PAGE_BREAK_AUTO:
					break;
				default:
					throw new IllegalStateException();
				}
			}
		}

		if (this.breakDepth == -1) {
			if (StyleUtils.isVertical(this.getFlow().box.getBlockParams().flow) != StyleUtils
					.isVertical(flowBox.getBlockParams().flow)) {
				this.breakDepth = 0;
			}
		} else {
			++this.breakDepth;
		}
		super.startFlowBlock(flowBox);
		if (canBreakAfter) {
			this.canBreakBefore = true;
			this.interflowBreak = true;
		}
	}

	private final boolean breakByClear(final FlowPos pos) {
		// ブロックのclearによる改ページ
		assert this.textBuilder == null;
		boolean breakFloats = false;
		switch (pos.clear) {
		case Types.CLEAR_NONE:
			return false;

		case Types.CLEAR_START:
			if ((this.breakFloats & Types.FLOATING_START) != 0) {
				breakFloats = true;
			}
			break;

		case Types.CLEAR_END:
			if ((this.breakFloats & Types.FLOATING_END) != 0) {
				breakFloats = true;
			}
			break;

		case Types.CLEAR_BOTH:
			if (this.breakFloats != 0) {
				breakFloats = true;
			}
			break;

		default:
			throw new IllegalStateException();
		}
		if (!breakFloats) {
			return false;
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("page break [block clear]");
		}

		// 切断させるため高さを拡張
		double savePageAxis = this.pageAxis;
		this.pageAxis = this.getPageLimit() + 1;
		boolean breaked = this.autoBreak();
		if (!breaked) {
			this.pageAxis = savePageAxis;
		}
		if (this.textBuilder != null) {
			this.endTextBlock();
		}
		return true;
	}

	public final void addBound(IBox box) {
		if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
			super.addBound(box);
			return;
		}

		byte pageBreakBefore, pageBreakAfter;
		switch (box.getPos().getType()) {
		case Pos.TYPE_FLOW: {
			// 通常のフロー
			assert this.textBuilder == null;
			final FlowPos pos = (FlowPos) box.getPos();
			pageBreakBefore = pos.pageBreakBefore;
			pageBreakAfter = pos.pageBreakAfter;
			// clearによる改ページ
			while (this.breakByClear(pos))
				;
			break;
		}
		case Pos.TYPE_FLOAT: {
			// 浮動ボックス
			final FloatPos pos = (FloatPos) box.getPos();
			pageBreakBefore = pos.pageBreakBefore;
			pageBreakAfter = pos.pageBreakAfter;
			break;
		}
		case Pos.TYPE_ABSOLUTE: {
			// 絶対配置
			super.addBound(box);
			return;
		}
		case Pos.TYPE_TABLE: {
			pageBreakAfter = pageBreakBefore = Types.PAGE_BREAK_AUTO;
			break;
		}
		default:
			throw new IllegalStateException();
		}

		// 直前での強制改ページチェック
		if (this.mode == MODE_PAGE_BREAK) {
			if (this.breakAfter != -1) {
				// 前のpage-break-afterによる改ページ
				this.forceBreak(this.breakAfter);
			}
			switch (pageBreakBefore) {
			case Types.PAGE_BREAK_PAGE:
			case Types.PAGE_BREAK_COLUMN:
				if (this.canBreakBefore) {
					this.forceBreak(pageBreakBefore);
				}
				break;
			case Types.PAGE_BREAK_VERSO:
			case Types.PAGE_BREAK_RECTO:
				if (!this.restyling && (this.canBreakBefore || pageBreakBefore != this.pageSide)) {
					this.forceBreak(pageBreakBefore);
				}
				break;
			case Types.PAGE_BREAK_IF_VERSO:
				if (!this.restyling && this.pageSide == Types.PAGE_BREAK_VERSO) {
					this.forceBreak(Types.PAGE_BREAK_RECTO);
				}
				break;
			case Types.PAGE_BREAK_IF_RECTO:
				if (!this.restyling && this.pageSide == Types.PAGE_BREAK_RECTO) {
					this.forceBreak(Types.PAGE_BREAK_VERSO);
				}
				break;
			case Types.PAGE_BREAK_AUTO:
			case Types.PAGE_BREAK_AVOID:
				break;
			default:
				throw new IllegalStateException(String.valueOf(pageBreakBefore));
			}
		}

		super.addBound(box);
		if (!this.restyling) {
			switch (box.getType()) {
			case TABLE:
				TableBox tableBox = (TableBox) box;
				if (!StyleUtils.isTwoPassTable(tableBox)) {
					// fixedレイアウトの場合は
					// OnePassTableBuilderが再配置する
					break;
				}
				for (;;) {
					// テーブルの強制改ページチェック
					if (this.mode == MODE_PAGE_BREAK) {
						TableForceBreakMode mode = this.firstTableForceBreak(tableBox);
						if (mode != null) {
							this.forceBreak(mode);
							box = tableBox = this.lastTableBox;
							continue;
						}
					}

					if (StyleUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
						break;
					}

					// 自動改ページ
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("page break [in table]");
					}
					this.lastTableBox = null;
					if (!this.autoBreak()) {
						// テーブルのヘッダとフッタがおさまらないケースがある
						break;
					}
					if (this.lastTableBox == null) {
						break;
					}
					box = tableBox = this.lastTableBox;
					continue;
				}
				break;
			case BLOCK:
				break;
			case REPLACED: {
				if (box.getPos().getType() != Pos.TYPE_FLOW) {
					break;
				}
				for (;;) {
					if (StyleUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
						break;
					}
					// 自動改ページ
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("page break [interflow image]");
					}
					if (!this.autoBreak()) {
						break;
					}
					continue;

				}
				break;
			}
			default:
				throw new IllegalStateException();
			}
		} else {
			if (box.getType() == BoxType.TABLE) {
				this.lastTableBox = (TableBox) box;
			}
		}

		this.canBreakBefore = true;
		this.interflowBreak = true;

		// 直後での強制改ページチェック
		if (this.mode == MODE_PAGE_BREAK) {
			switch (pageBreakAfter) {
			case Types.PAGE_BREAK_PAGE:
			case Types.PAGE_BREAK_COLUMN:
				this.breakAfter = pageBreakAfter;
				break;
			case Types.PAGE_BREAK_VERSO:
			case Types.PAGE_BREAK_RECTO:
				if (pageBreakAfter != this.pageSide) {
					this.forceBreak(pageBreakAfter);
					break;
				}
				this.breakAfter = pageBreakAfter;
				break;
			case Types.PAGE_BREAK_AVOID:
				this.interflowBreak = false;
				break;
			case Types.PAGE_BREAK_AUTO:
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}

	protected final void requireTextBlock() {
		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1 && this.breakAfter != -1) {
			// 直前での強制改ページチェック
			// 前のpage-break-afterによる改ページ
			this.forceBreak(this.breakAfter);
		}
		super.requireTextBlock();
	}

	public final void flush() {
		// if (this.textBuilder == null) {
		// // テキストブロック内にabsoluteしかない場合に発生することがある
		// return;
		// }
		while (this.textBuilder.flush()) {
			// 改行発生
			if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
				continue;
			}

			// 改行された場合の行間改ページチェック
			TextBuilder tbb = this.textBuilder;
			double pageAxis = this.pageAxis;
			pageAxis += this.textBuilder.getPageAxis();
			if (pageAxis > 0) {
				this.canBreakBefore = true;
				this.interflowBreak = true;
			}

			// 自動改ページ
			if (StyleUtils.compare(pageAxis, this.getPageLimit()) <= 0) {
				// まだはみ出していない
				continue;
			}
			++this.widows;

			final BlockParams params = this.textBuilder.textBlockBox.getBlockParams();
			if (this.widows < Math.max(2, params.widows)) {
				// widowsが足りない
				continue;
			}

			super.endTextBlock();

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("page break [interline]/" + pageAxis + "/" + this.widows);
			}
			this.autoBreak();
			assert this.textBuilder != null : String.valueOf(this);

			// TextRunを復帰
			this.textBuilder.startTextRun(tbb.fontStyle, tbb.fontMetrics);
		}
	}

	public final void endTextBlock() {
		super.endTextBlock();
		if (this.pageAxis > 0) {
			this.canBreakBefore = true;
			this.interflowBreak = true;
		}

		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			double pageLimit = this.getPageLimit();
			if (!this.interflowBreak || StyleUtils.compare(pageLimit, this.pageAxis) >= 0) {
				return;
			}
			// 自動改ページ
			// System.err.println(pageLimit+"/"+this.pageAxis);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("page break [after text]" + pageLimit + "/" + this.pageAxis);
			}
			if (this.autoBreak()) {
				if (this.textBuilder != null) {
					// 改ページ処理でつくられたテキストブロックを終了
					this.endTextBlock();
				}
			}
		}
	}

	public final void endFlowBlock() {
		assert this.textBuilder == null;
		assert !this.flowStack.isEmpty();
		Flow flow = (Flow) this.flowStack.get(this.flowStack.size() - 1);

		if (this.breakDepth == -1 && flow.box.canColumnBreak()) {
			// マルチカラムの下の境界がページをはみ出ていたら改ページ
			final double columnLimit = flow.pageAxis + flow.box.getInnerHeight();
			// 下部の枠の幅を計算します。
			final double lastFrame = this.lastFrame(flow, 1);
			// System.err.println(columnLimit+"/"+
			// this.getPageLimit()+"/"+this.flowStack.size());
			if (StyleUtils.compare(columnLimit, this.getPageLimit() - lastFrame) > 0) {
				final BreakMode mode = new AutoBreakMode(flow.box);
				final byte flags = IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_LAST;
				this.columnBreak(flow, mode, flags, lastFrame, 1);
			}
		}

		boolean canBreakAfter = false;
		switch (flow.box.getType()) {
		case BLOCK:
			AbstractBlockBox blockBox = (AbstractBlockBox) flow.box;
			// 境界直後でのpage-break-afterによる強制改ページを許す
			if (StyleUtils.isVertical(this.getRootBox().getBlockParams().flow)) {
				if (!blockBox.getFrame().frame.border.getLeft().isNull()) {
					this.canBreakBefore = true;
					this.interflowBreak = true;
					canBreakAfter = true;
				}
			} else {
				if (!blockBox.getFrame().frame.border.getBottom().isNull()) {
					this.canBreakBefore = true;
					this.interflowBreak = true;
					canBreakAfter = true;
				}
			}
			break;
		}
		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			// 末尾の境界直前での強制改ページチェック
			if (this.breakAfter != -1 && canBreakAfter) {
				this.forceBreak(this.breakAfter);
			}
			// ルートボックス内の浮動ボックスを切断
			if (this.flowStack.size() == 1) {
				while (this.breakFloats != 0) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("page break [floats]");
					}
					// 必ず切断させるため高さを拡張
					this.pageAxis = this.getPageLimit() + 1;
					this.autoBreak();
				}
			}

			// 改ページ後のフローのオブジェクトを取得する
			flow = (Flow) this.flowStack.get(this.flowStack.size() - 1);
			if (this.textBuilder != null) {
				// 改ページ処理でつくられたテキストブロックを終了
				this.endTextBlock();
			}
		}

		super.endFlowBlock();
		if (this.breakDepth != -1) {
			--this.breakDepth;
		}

		// System.out.println(this.nobreak);
		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			final double pageLimit = this.getPageLimit();
			final FlowBlockBox flowBox = (FlowBlockBox) flow.box;

			final FlowPos pos = (FlowPos) flowBox.getPos();
			if (pos.pageBreakAfter == Types.PAGE_BREAK_AVOID) {
				this.interflowBreak = false;
			}
			// System.out.println(this.pageAxis+"/"+ pageLimit);
			if (this.interflowBreak) {
				// 一番下のボックスの境界下辺がページの内底辺をはみ出していた場合
				// 自動改ページ
				final double pageAxis = this.pageAxis - (this.poLastMargin + this.neLastMargin);
				// System.err.println(pageAxis+"/"+pageLimit);
				if (StyleUtils.compare(pageAxis, pageLimit) > 0) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("page break [interflow]" + "/" + flowBox.getParams().element);
					}
					this.autoBreak();
				}
			}

			// 直後での強制改ページチェック
			if (this.mode == MODE_PAGE_BREAK) {
				switch (pos.pageBreakAfter) {
				case Types.PAGE_BREAK_IF_VERSO:
					if (this.pageSide == Types.PAGE_BREAK_VERSO) {
						this.forceBreak(Types.PAGE_BREAK_RECTO);
					}
					break;
				case Types.PAGE_BREAK_IF_RECTO:
					if (this.pageSide == Types.PAGE_BREAK_RECTO) {
						this.forceBreak(Types.PAGE_BREAK_VERSO);
					}
					break;
				case Types.PAGE_BREAK_VERSO:
				case Types.PAGE_BREAK_RECTO:
					if (pos.pageBreakAfter != this.pageSide) {
						this.forceBreak(pos.pageBreakAfter);
						break;
					}
				case Types.PAGE_BREAK_PAGE:
				case Types.PAGE_BREAK_COLUMN:
					this.breakAfter = pos.pageBreakAfter;
					break;
				}
			}
		}
	}

	protected void addStartFloat(IFloatBox box) {
		// clearによる先送りチェック
		boolean breakFloats = false;
		switch (box.getFloatPos().clear) {
		case Types.CLEAR_NONE:
			break;

		case Types.CLEAR_START:
			if ((this.breakFloats & Types.FLOATING_START) != 0) {
				breakFloats = true;
			}
			break;

		case Types.CLEAR_END:
			if ((this.breakFloats & Types.FLOATING_END) != 0) {
				breakFloats = true;
			}
			break;

		case Types.CLEAR_BOTH:
			if (this.breakFloats != 0) {
				breakFloats = true;
			}
			break;

		default:
			throw new IllegalStateException();
		}
		if (breakFloats) {
			boolean vertical = StyleUtils.isVertical(this.getRootBox().getBlockParams().flow);
			this.breakFloats |= box.getFloatPos().floating;
			double pageStart = this.getPageLimit();
			Flow flow = this.getFlow();
			flow.box.addFloating(box, 0, pageStart - flow.pageAxis);
			// 上位ボックスの幅の拡張
			double pageAxis = pageStart;
			if (vertical) {
				pageAxis += box.getWidth();
			} else {
				pageAxis += box.getHeight();
			}
			int i;
			if (this.flowStack != null) {
				i = this.flowStack.size() - 1;
			} else {
				i = -1;
			}
			if (i == -1) {
				AbstractContainerBox rootBox = this.getRootBox();
				rootBox.setPageAxis(pageAxis);
			}
		} else {
			super.addStartFloat(box);
		}
	}

	protected void addEndFloat(IFloatBox box) {
		// clearによる先送りチェック
		boolean breakFloats = false;
		switch (box.getFloatPos().clear) {
		case Types.CLEAR_NONE:
			break;

		case Types.CLEAR_START:
			if ((this.breakFloats & Types.FLOATING_START) != 0) {
				breakFloats = true;
			}
			break;

		case Types.CLEAR_END:
			if ((this.breakFloats & Types.FLOATING_END) != 0) {
				breakFloats = true;
			}
			break;

		case Types.CLEAR_BOTH:
			if (this.breakFloats != 0) {
				breakFloats = true;
			}
			break;

		default:
			throw new IllegalStateException();
		}
		if (breakFloats) {
			boolean vertical = StyleUtils.isVertical(this.getRootBox().getBlockParams().flow);
			this.breakFloats |= box.getFloatPos().floating;
			double pageStart = this.getPageLimit();
			Flow flow = this.getFlow();
			flow.box.addFloating(box, 0, pageStart - flow.pageAxis);
			// 上位ボックスの幅の拡張
			double pageAxis = pageStart;
			if (vertical) {
				pageAxis += box.getWidth();
			} else {
				pageAxis += box.getHeight();
			}
			int i;
			if (this.flowStack != null) {
				i = this.flowStack.size() - 1;
			} else {
				i = -1;
			}
			if (i == -1) {
				AbstractContainerBox rootBox = this.getRootBox();
				rootBox.setPageAxis(pageAxis);
			}
		} else {
			super.addEndFloat(box);
		}
	}

	boolean transferFloatToNextPage(final IFloatBox box, double pageStart) {
		if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
			return false;
		}

		double pageAxis = pageStart;
		if (StyleUtils.isVertical(this.getRootBox().getBlockParams().flow)) {
			pageAxis += box.getWidth();
		} else {
			pageAxis += box.getHeight();
		}
		if (StyleUtils.compare(pageAxis, this.getPageLimit()) <= 0) {
			// 下部がはみ出していない場合は分割しない
			return false;
		}

		// ページをはみ出した浮動ボックスが存在する
		if (StyleUtils.isVertical(this.getRootBox().getBlockParams().flow)) {
			pageStart -= this.getFlow().box.getFrame().getFrameRight();
		}
		else {
			pageStart -= this.getFlow().box.getFrame().getFrameTop();
		}
		boolean transfer;
		switch (box.getType()) {
		case BLOCK:
			this.breakFloats |= box.getFloatPos().floating;
			final AbstractContainerBox containerBox = (AbstractContainerBox) box;
			if (containerBox.getBlockParams().pageBreakInside == Types.PAGE_BREAK_AVOID) {
				if (StyleUtils.compare(pageStart, 0) <= 0) {
					// ページ先頭の場合切断
					transfer = false;
				} else {
					// 丸ごと移動
					transfer = true;
				}
			} else {
				// 切断
				transfer = false;
			}
			// if (transfer) {
			// System.err.println("transfer float block: "
			// + box.getParams().augmentation);
			// } else {
			// System.err.println("split float block: "
			// + box.getParams().augmentation);
			// }
			break;

		case REPLACED:
			if (StyleUtils.compare(pageStart, 0) <= 0) {
				// ページ先頭の場合残す
				transfer = false;
				break;
			}
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("transfer float replaced: " + box.getParams().element);
			}
			this.breakFloats |= box.getFloatPos().floating;
			// 丸ごと移動
			transfer = true;
			break;
		default:
			throw new IllegalStateException();
		}
		return transfer;
	}

	public double getPageLimit() {
		final AbstractContainerBox rootBox = this.getRootBox();
		final BlockParams params = rootBox.getBlockParams();
		double pageLimit;
		if (StyleUtils.isVertical(params.flow)) {
			pageLimit = rootBox.getInnerWidth();
		} else {
			pageLimit = rootBox.getInnerHeight();
		}
		if (pageLimit < 20) {
			// 20ポイントより小さなページ高さは無視
			pageLimit = 20;
		}
		return pageLimit;
	}

	public void forceBreak(byte breakType) {
		this.forceBreak(new ForceBreakMode(this.getFlowBox(), breakType));
	}

	/**
	 * 強制改ページ
	 * 
	 * @param breakMode
	 */
	public void forceBreak(ForceBreakMode breakMode) {
		if (breakMode.breakType == Types.PAGE_BREAK_COLUMN) {
			// 改カラム可能なブロックを検索
			Flow columnBreak = null;
			int depth = 0;
			if (this.flowStack != null) {
				for (int i = this.flowStack.size() - 1; i >= 0; --i) {
					final Flow flow = (Flow) this.flowStack.get(i);
					++depth;
					if (flow.box.canColumnBreak()) {
						columnBreak = flow;
						break;
					}
				}
			}
			if (columnBreak != null) {
				final double lastFrame = this.lastFrame(columnBreak, depth);
				this.columnBreak(columnBreak, breakMode, IPageBreakableBox.FLAGS_FIRST, lastFrame, depth);
				return;
			}
		}

		boolean breaked = this.pageBreak(breakMode, IPageBreakableBox.FLAGS_FIRST);
		assert breaked;
		assert this.textBuilder == null;
	}

	/**
	 * 自動改ページ
	 * 
	 * @return
	 */
	private boolean autoBreak() {
		byte flags = IPageBreakableBox.FLAGS_FIRST;
		Flow columnBreak = null;
		int depth = 0;
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				++depth;
				// 改カラム可能なブロックを検索
				if (flow.box.canColumnBreak()) {
					columnBreak = flow;
					break;
				}
			}
		}

		final BreakMode mode;
		if (this.flowStack == null || this.flowStack.size() <= 1) {
			mode = BreakMode.DEFAULT_BREAK_MODE;
		} else {
			mode = new AutoBreakMode(this.getFlowBox());
			flags |= IPageBreakableBox.FLAGS_LAST;
		}
		if (columnBreak != null) {
			final double lastFrame = this.lastFrame(columnBreak, depth);
			if (this.columnBreak(columnBreak, mode, flags, lastFrame, depth)) {
				return true;
			}
			// マルチカラムがページの下の方にある場合は改ページする
			// final double pageAxis = this.getPageLimit() -
			// columnBreak.pageAxis
			// - lastFrame;
			// if (StyleUtils.compare(pageAxis, 0) > 0) {
			// return false;
			// }
		}
		return this.pageBreak(mode, flags);
	}

	protected abstract boolean pageBreak(BreakMode mode, byte flags);

	protected double lastFrame(Flow breakFlow, int depth) {
		// 下部の枠の幅を計算します。
		double lastFrame = 0;
		if (this.flowStack == null) {
			return lastFrame;
		}
		if (StyleUtils.isVertical(breakFlow.box.getBlockParams().flow)) {
			for (int i = this.flowStack.size() - depth; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				lastFrame += flow.box.getFrame().getFrameLeft();
			}
		} else {
			for (int i = this.flowStack.size() - depth; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				lastFrame += flow.box.getFrame().getFrameBottom();
			}
		}
		return lastFrame;
	}

	/**
	 * 改段を実行します。
	 * 
	 * @param breakFlow
	 * @param mode
	 * @param flags
	 * @param depth
	 * @return
	 */
	protected boolean columnBreak(final Flow breakFlow, final BreakMode mode, byte flags, final double lastFrame,
			int depth) {
		assert this.textBuilder == null;
		this.breakFloats = 0;
		this.breakAfter = -1;
		this.canBreakBefore = false;
		this.interflowBreak = false;

		final double pageAxis = this.getPageLimit() - breakFlow.pageAxis - lastFrame;

		// ページの先頭かどうかの判断
		if (StyleUtils.isVertical(breakFlow.box.getBlockParams().flow)) {
			if (StyleUtils.compare(breakFlow.pageAxis - breakFlow.box.getFrame().getFrameRight(), 0) > 0) {
				flags ^= IPageBreakableBox.FLAGS_FIRST;
			}
		} else {
			if (StyleUtils.compare(breakFlow.pageAxis - breakFlow.box.getFrame().getFrameTop(), 0) > 0) {
				flags ^= IPageBreakableBox.FLAGS_FIRST;
			}
		}

		final Container container = breakFlow.box.newColumn(pageAxis, mode, flags);
		// assert container != null;
		if (container == null) {
			return false;
		}

		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow == breakFlow) {
					break;
				}
				this.flowStack.remove(i);
			}
		}

		this.pageAxis = breakFlow.pageAxis;
		this.lineAxis = breakFlow.lineAxis;
		this.poLastMargin = 0;
		this.neLastMargin = 0;
		this.widows = 0;
		this.floatings = null;
		this.restyling = true;
		container.restyle(this, depth, false);
		this.restyling = false;
		return true;
	}
}
