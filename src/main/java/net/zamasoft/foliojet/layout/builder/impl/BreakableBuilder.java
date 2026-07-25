package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.params.ClearMode;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 改ページ・改段の編成(自動/強制/段組)を担う抽象ビルダーです
 * (2026-07-19訂正: 旧javadocはRootBuilderからのコピー残りで
 * 「ドキュメント全体を構築します」という不正確な説明だった。実際には
 * ARCHITECTURE.md命名台帳§8で指摘の通り、ファイル本体の大半が
 * 自動/強制/段の切断編成に費やされている)。{@link RootBuilder}が
 * ドキュメントルート向けの具象サブクラス。
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

	protected PageBreakMode pageSide;

	protected int breakDepth = -1;

	/**
	 * 次ページに先送り可能な行数のカウントです。
	 */
	protected int widows = 0;

	/**
	 * 次のポイントでpage-break-afterによる改ページを適用するフラグです。
	 */
	protected PageBreakMode breakAfter = null;

	/**
	 * 直前での強制改ページを許可するフラグです。
	 */
	protected boolean canBreakBefore = true;

	/**
	 * ブロック間の自然改ページを許可するフラグです。
	 */
	protected boolean interflowBreak = true;

	/**
	 * 再レイアウト(破断残余の再開)の入れ子深さです。再生した内容が
	 * 新ページを溢れさせると再開の中で改ページが入れ子で起きるため、
	 * boolean では内側の終了が外側の再開文脈を解除してしまう
	 * (外部レビュー指摘)。
	 */
	private int restyleNesting = 0;

	protected final boolean isRestyling() {
		return this.restyleNesting > 0;
	}

	protected final void beginRestyling() {
		++this.restyleNesting;
	}

	protected final void endRestyling() {
		--this.restyleNesting;
		assert this.restyleNesting >= 0;
	}

	protected final java.util.EnumSet<FloatSide> breakFloats = java.util.EnumSet.noneOf(FloatSide.class);

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
		double last = this.pageAxis;
		final WritingMode tableFlow = tableBox.getTableParams().flow;
		last -= tableBox.getInnerPageExtent(tableFlow) + tableBox.getFrame().getFramePageEnd(tableFlow);
		if (tableBox.getTableHeader() != null) {
			last += tableBox.getTableHeader().getPageSize();
		}
		if (tableBox.getTableFooter() != null) {
			last += tableBox.getTableFooter().getPageSize();
		}
		// 走査は TableCutter に純化(C4-T3)
		final int groupCount = tableBox.getTableBodyCount();
		final double[][] rowSizes = new double[groupCount][];
		final PageBreakMode[] groupBefore = new PageBreakMode[groupCount];
		final PageBreakMode[] groupAfter = new PageBreakMode[groupCount];
		final PageBreakMode[][] rowBefore = new PageBreakMode[groupCount][];
		final PageBreakMode[][] rowAfter = new PageBreakMode[groupCount][];
		for (int g = 0; g < groupCount; ++g) {
			final TableRowGroupBox rowGroupBox = tableBox.getTableBody(g);
			groupBefore[g] = rowGroupBox.getTableRowGroupPos().pageBreakBefore;
			groupAfter[g] = rowGroupBox.getTableRowGroupPos().pageBreakAfter;
			final int rowCount = rowGroupBox.getTableRowCount();
			rowSizes[g] = new double[rowCount];
			rowBefore[g] = new PageBreakMode[rowCount];
			rowAfter[g] = new PageBreakMode[rowCount];
			for (int r = 0; r < rowCount; ++r) {
				final TableRowBox rowBox = rowGroupBox.getTableRow(r);
				rowSizes[g][r] = rowBox.getPageSize();
				rowBefore[g][r] = rowBox.getTableRowPos().pageBreakBefore;
				rowAfter[g][r] = rowBox.getTableRowPos().pageBreakAfter;
			}
		}
		final net.zamasoft.foliojet.layout.fragment.TableCutter.ForceBreakAt at = net.zamasoft.foliojet.layout.fragment.TableCutter
				.firstForceBreak(this.getPageLimit(), last, rowSizes, groupBefore, groupAfter, rowBefore, rowAfter);
		if (at == null) {
			return null;
		}
		final AbstractInnerTableBox box = at.row() >= 0 ? tableBox.getTableBody(at.rowGroup()).getTableRow(at.row())
				: tableBox.getTableBody(at.rowGroup());
		return new TableForceBreakMode(box, at.breakMode(), at.rowGroup(), at.row());
	}

	public void startFlowBlock(FlowBlockBox flowBox) {
		assert this.textBuilder == null : flowBox.getParams().element;

		boolean canBreakAfter = false;
		switch (flowBox.getType()) {
		case BLOCK:
			AbstractBlockBox blockBox = flowBox;
			// 境界前でのpage-break-afterの適用を許す
			if (this.getRootBox().getBlockParams().flow.isVertical()) {
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
				if (this.breakAfter != null && canBreakAfter) {
					// 前のpage-break-afterによる改ページ
					this.forceBreak(this.breakAfter);
				}
				switch (pos.pageBreakBefore) {
				case PageBreakMode.PAGE:
				case PageBreakMode.COLUMN:
					if (this.canBreakBefore) {
						this.forceBreak(pos.pageBreakBefore);
					}
					break;
				case PageBreakMode.VERSO:
				case PageBreakMode.RECTO:
					if (!this.isRestyling() && (this.canBreakBefore || pos.pageBreakBefore != this.pageSide)) {
						this.forceBreak(pos.pageBreakBefore);
					}
					break;
				case PageBreakMode.IF_VERSO:
					if (!this.isRestyling() && this.pageSide == PageBreakMode.VERSO) {
						this.forceBreak(PageBreakMode.RECTO);
					}
					break;
				case PageBreakMode.IF_RECTO:
					if (!this.isRestyling() && this.pageSide == PageBreakMode.RECTO) {
						this.forceBreak(PageBreakMode.VERSO);
					}
					break;
				case PageBreakMode.AVOID:
					this.interflowBreak = false;
					break;
				case PageBreakMode.AUTO:
					break;
				default:
					throw new IllegalStateException();
				}
			}
		}

		if (this.breakDepth == -1) {
			// 改ページ契約(2026-07-22、docs/history/2026-07-22-pagination
			// -contract-consultation.md参照): 軸違い(TB⇄RL/LR)だけでなく
			// 同軸内の方向違い(RL⇄LR)もこの祖先チェーンをatomicにする
			// (直交writing-mode表と同じ扱いを、通常フローのブロックにも
			// 一般化する)。WritingModeはenumなので参照比較で十分
			// (isVertical()だけの比較では軸が同じRL/LRの違いを見逃す)。
			if (this.getFlow().box.getBlockParams().flow != flowBox.getBlockParams().flow) {
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
		case ClearMode.NONE:
			return false;

		case ClearMode.START:
			if (this.breakFloats.contains(FloatSide.START)) {
				breakFloats = true;
			}
			break;

		case ClearMode.END:
			if (this.breakFloats.contains(FloatSide.END)) {
				breakFloats = true;
			}
			break;

		case ClearMode.BOTH:
			if (!this.breakFloats.isEmpty()) {
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
		// M3c: 改ページ検査(forceBreak等)がK-P蓄積とインターリーブ
		// しないよう、蓄積中なら検査より前にlegacyへ確定させる
		if (this.textSession != null) {
			this.textSession.abortToLegacy();
		}
		if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
			super.addBound(box);
			return;
		}

		PageBreakMode pageBreakBefore, pageBreakAfter;
		switch (box.getPos().getType()) {
		case FLOW: {
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
		case FLOAT: {
			// 浮動ボックス
			final FloatPos pos = (FloatPos) box.getPos();
			pageBreakBefore = pos.pageBreakBefore;
			pageBreakAfter = pos.pageBreakAfter;
			break;
		}
		case ABSOLUTE: {
			// 絶対配置
			super.addBound(box);
			return;
		}
		case TABLE: {
			pageBreakAfter = pageBreakBefore = PageBreakMode.AUTO;
			break;
		}
		default:
			throw new IllegalStateException();
		}

		// 直前での強制改ページチェック
		if (this.mode == MODE_PAGE_BREAK) {
			if (this.breakAfter != null) {
				// 前のpage-break-afterによる改ページ
				this.forceBreak(this.breakAfter);
			}
			switch (pageBreakBefore) {
			case PageBreakMode.PAGE:
			case PageBreakMode.COLUMN:
				if (this.canBreakBefore) {
					this.forceBreak(pageBreakBefore);
				}
				break;
			case PageBreakMode.VERSO:
			case PageBreakMode.RECTO:
				if (!this.isRestyling() && (this.canBreakBefore || pageBreakBefore != this.pageSide)) {
					this.forceBreak(pageBreakBefore);
				}
				break;
			case PageBreakMode.IF_VERSO:
				if (!this.isRestyling() && this.pageSide == PageBreakMode.VERSO) {
					this.forceBreak(PageBreakMode.RECTO);
				}
				break;
			case PageBreakMode.IF_RECTO:
				if (!this.isRestyling() && this.pageSide == PageBreakMode.RECTO) {
					this.forceBreak(PageBreakMode.VERSO);
				}
				break;
			case PageBreakMode.AUTO:
			case PageBreakMode.AVOID:
				break;
			default:
				throw new IllegalStateException(String.valueOf(pageBreakBefore));
			}
		}

		super.addBound(box);
		if (!this.isRestyling()) {
			switch (box.getType()) {
			case TABLE:
				TableBox tableBox = (TableBox) box;
				// 2026-07-21(M6b Phase B5e後始末): 従来はLayoutUtils
				// .needsIntrinsicSizing(TableBox)という別実装(旧4条件)で
				// 再判定していたが、これはTableBuildPlanner.plan()と
				// 完全に重複しており、B5eで追加したORTHOGONAL_WRITING_MODE
				// 条件がこちらには反映されない食い違いを生んでいた
				// (実測では実害なしを確認済みだったが、今後同種の条件が
				// 増えるたびに再発するリスクがあるため解消する)。単一の
				// 判定点(TableBuildPlanner.plan())へ統一する。
				if (TableBuildPlanner.plan(this, tableBox).mode() != TableBuildPlan.Mode.RETAINED) {
					// Incremental(fixedレイアウト等)の場合は
					// IncrementalTableBuilderが再配置する
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

					if (LayoutUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
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
				if (box.getPos().getType() != PosType.FLOW) {
					break;
				}
				for (;;) {
					if (LayoutUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
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
			case PageBreakMode.PAGE:
			case PageBreakMode.COLUMN:
				this.breakAfter = pageBreakAfter;
				break;
			case PageBreakMode.VERSO:
			case PageBreakMode.RECTO:
				if (pageBreakAfter != this.pageSide) {
					this.forceBreak(pageBreakAfter);
					break;
				}
				this.breakAfter = pageBreakAfter;
				break;
			case PageBreakMode.AVOID:
				this.interflowBreak = false;
				break;
			case PageBreakMode.AUTO:
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}

	/**
	 * 救済分割(visual rescue split)の残余断片をフローへ載せ、まだ
	 * はみ出していれば改ページします(2026-07-25新設、増分5)。
	 *
	 * <p>
	 * ループは{@code addBound()}の{@code case REPLACED}と同型です。
	 * {@code autoBreak()}が{@code false}(改ページ点なし)を返したら
	 * <b>必ず</b>抜けるため、ここで無限ループにはなりません。前進は
	 * {@code VisualRescuePlanner}が構造的に保証します(各改ページで
	 * 必ず正の量を消費する)。
	 * </p>
	 */
	@Override
	public void addRescueBound(final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox box) {
		super.addRescueBound(box);
		if (!this.isRestyling() && this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			for (;;) {
				if (LayoutUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
					break;
				}
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("page break [rescue fragment]");
				}
				if (!this.autoBreak()) {
					break;
				}
			}
		}
		this.canBreakBefore = true;
		this.interflowBreak = true;
	}

	protected final void requireTextBlock() {
		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1 && this.breakAfter != null) {
			// 直前での強制改ページチェック
			// 前のpage-break-afterによる改ページ
			this.forceBreak(this.breakAfter);
		}
		super.requireTextBlock();
	}

	public final void flush() {
		// M3c: K-P蓄積中はflushイベントを記録するだけ(行間改ページ検査は
		// セッション終了時の再生がこのメソッドを通るときに行われる)
		if (this.textSession != null && this.textSession.recordFlush()) {
			return;
		}
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
			if (LayoutUtils.compare(pageAxis, this.getPageLimit()) <= 0) {
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
			if (!this.interflowBreak || LayoutUtils.compare(pageLimit, this.pageAxis) >= 0) {
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
			if (LayoutUtils.compare(columnLimit, this.getPageLimit() - lastFrame) > 0) {
				final BreakMode mode = new AutoBreakMode(flow.box);
				final byte flags = IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_LAST;
				this.columnBreak(flow, mode, flags, lastFrame, 1);
			}
		}

		boolean canBreakAfter = false;
		switch (flow.box.getType()) {
		case RESCUE:
			// 2026-07-25(救済分割・増分5で確認): ここで見ているのは
			// flowStack の先頭=いま閉じようとしている「コンテナボックス」で
			// あり、救済断片がflowStackへ積まれることは構造的にない
			// (断片は分割不能な葉として flows に載るだけで、開かれない)。
			// したがってこの分岐は到達不能。仮に到達したら、切断面には装飾を
			// 付けない=断片自身が「境界直後の改ページを許す枠線」を持つ
			// ことはない、という設計判断を明示的に足すこと
			throw new IllegalStateException("救済断片はコンテナとして開かれない: " + flow.box);
		case BLOCK:
			AbstractBlockBox blockBox = (AbstractBlockBox) flow.box;
			// 境界直後でのpage-break-afterによる強制改ページを許す
			if (this.getRootBox().getBlockParams().flow.isVertical()) {
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
			if (this.breakAfter != null && canBreakAfter) {
				this.forceBreak(this.breakAfter);
			}
			// ルートボックス内の浮動ボックスを切断
			if (this.flowStack.size() == 1) {
				while (!this.breakFloats.isEmpty()) {
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
			if (pos.pageBreakAfter == PageBreakMode.AVOID) {
				this.interflowBreak = false;
			}
			// System.out.println(this.pageAxis+"/"+ pageLimit);
			if (this.interflowBreak) {
				// 一番下のボックスの境界下辺がページの内底辺をはみ出していた場合
				// 自動改ページ
				final double pageAxis = this.pageAxis - (this.poLastMargin + this.neLastMargin);
				// System.err.println(pageAxis+"/"+pageLimit);
				if (LayoutUtils.compare(pageAxis, pageLimit) > 0) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("page break [interflow]" + "/" + flowBox.getParams().element);
					}
					this.autoBreak();
				}
			}

			// 直後での強制改ページチェック
			if (this.mode == MODE_PAGE_BREAK) {
				switch (pos.pageBreakAfter) {
				case PageBreakMode.IF_VERSO:
					if (this.pageSide == PageBreakMode.VERSO) {
						this.forceBreak(PageBreakMode.RECTO);
					}
					break;
				case PageBreakMode.IF_RECTO:
					if (this.pageSide == PageBreakMode.RECTO) {
						this.forceBreak(PageBreakMode.VERSO);
					}
					break;
				case PageBreakMode.VERSO:
				case PageBreakMode.RECTO:
					if (pos.pageBreakAfter != this.pageSide) {
						this.forceBreak(pos.pageBreakAfter);
						break;
					}
				case PageBreakMode.PAGE:
				case PageBreakMode.COLUMN:
					this.breakAfter = pos.pageBreakAfter;
					break;
				}
			}
		}
	}

	protected void addStartFloat(IFloatBox box) {
		if (this.deferredByClear(box.getFloatPos().clear)) {
			this.commitFloatPlacement(this.deferByClear(box, FloatSide.START));
		} else {
			super.addStartFloat(box);
		}
	}

	protected void addEndFloat(IFloatBox box) {
		if (this.deferredByClear(box.getFloatPos().clear)) {
			this.commitFloatPlacement(this.deferByClear(box, FloatSide.END));
		} else {
			super.addEndFloat(box);
		}
	}

	/**
	 * clearによる先送り判定です(2026-07-23、排除域P1増分4——旧
	 * addStartFloat/addEndFloatが重複して持っていたswitchの一本化。
	 * 既に先送り済みのfloatをclear対象に指定しているfloatは、探索なしで
	 * 次フラグメントへ先送りする)。副作用なし。
	 */
	private boolean deferredByClear(final ClearMode clear) {
		switch (clear) {
		case ClearMode.NONE:
			return false;
		case ClearMode.START:
			return this.breakFloats.contains(FloatSide.START);
		case ClearMode.END:
			return this.breakFloats.contains(FloatSide.END);
		case ClearMode.BOTH:
			return !this.breakFloats.isEmpty();
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * clear先送りの配置計画を作ります(2026-07-23、排除域P1増分4——
	 * フラグメント境界(pageLimit)へ置いて次フラグメントへ送る。
	 * 副作用なし、commitは{@code commitFloatPlacement}の
	 * {@code MOVE_BY_CLEAR}分岐)。
	 */
	private FloatPlacementDelta deferByClear(final IFloatBox box, final FloatSide side) {
		final WritingMode progression = this.getRootBox().getBlockParams().flow;
		final double pageStart = this.getPageLimit();
		final double lineOffset = this.getFlow().lineAxis;
		return new FloatPlacementDelta(box, side,
				new AxisSpan(lineOffset, lineOffset + box.getLineExtent(progression)),
				new AxisSpan(pageStart, pageStart + box.getPageExtent(progression)), FloatCommitKind.MOVE_BY_CLEAR);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 2026-07-23(排除域P1増分2): 従来の{@code transferFloatToNextPage}
	 * (判定名だが{@code breakFloats.add}の副作用を持っていた)を、
	 * この副作用のない分類と{@link #recordBreakFloat}へ分解した。
	 * 分類は実測の物理位置(フラグメント境界へのはみ出し・ページ先頭に
	 * いるか)だけで決まる。
	 * </p>
	 */
	@Override
	FloatCommitKind classifyFloatPlacement(final IFloatBox box, double pageStart) {
		if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
			return FloatCommitKind.PLACED;
		}

		double pageAxis = pageStart;
		pageAxis += box.getPageExtent(this.getRootBox().getBlockParams().flow);
		if (LayoutUtils.compare(pageAxis, this.getPageLimit()) <= 0) {
			// 下部がはみ出していない場合は分割しない
			return FloatCommitKind.PLACED;
		}

		// ページをはみ出した浮動ボックスが存在する
		pageStart -= this.getFlow().box.getFrame().getFramePageStart(this.getRootBox().getBlockParams().flow);
		switch (box.getType()) {
		case BLOCK:
			final AbstractContainerBox containerBox = (AbstractContainerBox) box;
			if (containerBox.getBlockParams().pageBreakInside == PageBreakMode.AVOID
					&& LayoutUtils.compare(pageStart, 0) > 0) {
				// avoid指定でページ先頭でない場合は丸ごと移動
				return FloatCommitKind.MOVE_TO_NEXT;
			}
			// 切断(avoid指定でもページ先頭なら切断——§5.11の物理位置優先)
			return FloatCommitKind.SPLIT_AT_BREAK;

		case REPLACED:
			if (LayoutUtils.compare(pageStart, 0) <= 0) {
				// ページ先頭の場合残す
				return FloatCommitKind.PLACED;
			}
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("transfer float replaced: " + box.getParams().element);
			}
			// 丸ごと移動
			return FloatCommitKind.MOVE_TO_NEXT;
		default:
			throw new IllegalStateException();
		}
	}

	@Override
	void recordBreakFloat(final FloatSide side) {
		this.breakFloats.add(side);
	}

	/**
	 * {@link #getPageLimit()}のページ方向容量の下限です(2026-07-24に
	 * 定数化)。
	 */
	public static final double MIN_PAGE_LIMIT = 20;

	public double getPageLimit() {
		final AbstractContainerBox rootBox = this.getRootBox();
		final BlockParams params = rootBox.getBlockParams();
		double pageLimit = rootBox.getInnerPageExtent(params.flow);
		if (pageLimit < MIN_PAGE_LIMIT) {
			// 20ポイントより小さなページ高さは無視
			pageLimit = MIN_PAGE_LIMIT;
		}
		return pageLimit;
	}

	public void forceBreak(PageBreakMode breakType) {
		this.forceBreak(new ForceBreakMode(this.getFlowBox(), breakType));
	}

	/**
	 * 強制改ページ
	 * 
	 * @param breakMode
	 */
	public void forceBreak(ForceBreakMode breakMode) {
		if (breakMode.breakType == PageBreakMode.COLUMN) {
			// 改カラム可能なブロックを検索
			final ColumnBreakPoint columnBreak = this.findColumnBreak();
			if (columnBreak != null) {
				final double lastFrame = this.lastFrame(columnBreak.flow(), columnBreak.depth());
				// 2026-07-21: 従来はcolumnBreak()の戻り値(boolean)を無視して
				// 無条件にreturnしていた——newColumn()がno-cut(null)を返すと、
				// 改段も行われず、autoBreak()と違いPAGEへのfallbackもされない
				// サイレントno-opになっていた(ChatGPT Pro相談で発見・検証済み、
				// docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md)。
				// autoBreak()(887行目付近)と同じfallback規則に揃える。
				if (this.columnBreak(columnBreak.flow(), breakMode, IPageBreakableBox.FLAGS_FIRST, lastFrame,
						columnBreak.depth())) {
					return;
				}
				if (this.pageBreak(breakMode, IPageBreakableBox.FLAGS_FIRST)) {
					return;
				}
				throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
						"forced column break produced no column and no page fallback");
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
		// 改カラム可能なブロックを検索
		final ColumnBreakPoint columnBreak = this.findColumnBreak();

		final BreakMode mode;
		if (this.flowStack == null || this.flowStack.size() <= 1) {
			mode = BreakMode.DEFAULT_BREAK_MODE;
		} else {
			mode = new AutoBreakMode(this.getFlowBox());
			flags |= IPageBreakableBox.FLAGS_LAST;
		}
		if (columnBreak != null) {
			final double lastFrame = this.lastFrame(columnBreak.flow(), columnBreak.depth());
			if (this.columnBreak(columnBreak.flow(), mode, flags, lastFrame, columnBreak.depth())) {
				return true;
			}
		}
		return this.pageBreak(mode, flags);
	}

	protected abstract boolean pageBreak(BreakMode mode, byte flags);

	/**
	 * 改ページ・改段の共通前処理です。断片(ページ/段)をまたぐ際に
	 * リセットされる切断待ち状態を初期化します(M5)。
	 */
	protected final void beginBreak() {
		assert this.textBuilder == null;
		this.breakFloats.clear();
		this.breakAfter = null;
		this.canBreakBefore = false;
		this.interflowBreak = false;
	}

	/**
	 * 次の断片へ進む際のカーソル状態のリセットです。改ページと改段は
	 * 「断片容器(フラグメンテナ)があふれたので次の断片へ進む」という
	 * 同一操作であり(ARCHITECTURE.md §5)、リセットもここに一元化します(M5)。
	 *
	 * @param pageAxis 新しい断片のページ方向カーソル位置
	 * @param lineAxis 新しい断片の行方向カーソル位置
	 */
	protected final void resetFragmentCursor(final double pageAxis, final double lineAxis) {
		this.pageAxis = pageAxis;
		this.lineAxis = lineAxis;
		this.poLastMargin = 0;
		this.neLastMargin = 0;
		this.widows = 0;
		this.floatings = null;
		this.noteFloatingsChanged();
	}

	/**
	 * 改段可能な最も内側のフローと、その深さです(M5)。
	 */
	protected record ColumnBreakPoint(Flow flow, int depth) {
	}

	/**
	 * スタック上の改段可能な最も内側のフローを探します。
	 *
	 * @return 改段可能なフローがなければ null
	 */
	protected final ColumnBreakPoint findColumnBreak() {
		if (this.flowStack == null) {
			return null;
		}
		for (int i = this.flowStack.size() - 1; i >= 0; --i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			if (flow.box.canColumnBreak()) {
				return new ColumnBreakPoint(flow, this.flowStack.size() - i);
			}
		}
		return null;
	}

	/**
	 * COLUMN継続の相対open path(index 0 = owner)を捕捉します
	 * (2026-07-21新設、M6b Phase B4-Step3、観測のみ・未配線)。owner自身が
	 * flowStack内にある通常経路({@link #findColumnBreak()})と、
	 * {@code ColumnBuilder.contextFlow}がflowStack外にある経路の両方を
	 * 扱う(ChatGPT Pro相談、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
	 * 参照)。
	 */
	private java.util.List<AbstractContainerBox> captureColumnOpenPath(final Flow breakFlow) {
		if (this.flowStack != null) {
			final int ownerIndex = this.flowStack.indexOf(breakFlow);
			if (ownerIndex >= 0) {
				final java.util.List<AbstractContainerBox> boxes = new java.util.ArrayList<>(
						this.flowStack.size() - ownerIndex);
				for (int i = ownerIndex; i < this.flowStack.size(); ++i) {
					boxes.add(((Flow) this.flowStack.get(i)).box);
				}
				return boxes;
			}
		}
		if (breakFlow == this.contextFlow) {
			final java.util.List<AbstractContainerBox> boxes = new java.util.ArrayList<>();
			boxes.add(breakFlow.box);
			if (this.flowStack != null) {
				for (final Object entry : this.flowStack) {
					boxes.add(((Flow) entry).box);
				}
			}
			return boxes;
		}
		throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
				"column owner is neither in flowStack nor the contextFlow");
	}

	protected double lastFrame(Flow breakFlow, int depth) {
		// 下部の枠の幅を計算します。
		double lastFrame = 0;
		if (this.flowStack == null) {
			return lastFrame;
		}
		for (int i = this.flowStack.size() - depth; i >= 0; --i) {
			Flow flow = (Flow) this.flowStack.get(i);
			lastFrame += flow.box.getFrame().getFramePageEnd(breakFlow.box.getBlockParams().flow);
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
		// 2026-07-21: この改段(COLUMN)経路は、下の container.restyle(...,
		// OpenShape.of(depth), ...) が RootBuilder.pageBreak()の
		// BreakPlan/深さガード機構を完全に迂回する独立経路であり、
		// 2026-07-20時点では深さガードが一切存在しなかった(ChatGPT Pro
		// 相談で発見、docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-full-fix.md)。
		// depthはこの時点で既にパラメータとして確定しており、以降の
		// newColumn()呼び出しより前(状態変異が一切起きる前)に検査できる。
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.guardOpenDepth(depth, true);
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordLastColumnOwnerColumnCount(breakFlow.box.getColumnCount());
		this.beginBreak();

		// 2026-07-21(M6b Phase B4): 相対open pathを捕捉し、深さがdepth
		// パラメータと整合するかを検証する(Step3で観測用に導入、Step4で
		// 実際の切断(prepareColumnCut)へも渡すよう配線した)。
		final net.zamasoft.foliojet.layout.fragment.OpenPathScan columnScan;
		{
			final java.util.List<AbstractContainerBox> columnOpenPath = this.captureColumnOpenPath(breakFlow);
			if (columnOpenPath.size() != depth) {
				throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
						"column open path size=" + columnOpenPath.size() + " does not match depth=" + depth);
			}
			columnScan = net.zamasoft.foliojet.layout.fragment.OpenPathScan.captureColumn(columnOpenPath, mode);
			columnScan.snapshot().firstBarrier()
					.ifPresent(barrier -> net.zamasoft.foliojet.layout.fragment.ContinuationStats
							.recordColumnCapabilityScanStop(barrier.reason()));
		}

		final double pageAxis = this.getPageLimit() - breakFlow.pageAxis - lastFrame;

		// ページの先頭かどうかの判断
		if (LayoutUtils.compare(
				breakFlow.pageAxis - breakFlow.box.getFrame().getFramePageStart(breakFlow.box.getBlockParams().flow),
				0) > 0) {
			flags ^= IPageBreakableBox.FLAGS_FIRST;
		}

		final RootBuilder root = this.getPageContext();
		if (root == null) {
			// 2026-07-21(M6b Phase B4-Step4): 型付き経路(prepareColumnContinuation/
			// resumeColumn)はRootBuilderのメソッドとして実装されているため、
			// rootless文脈(M6c段バランスprobe等のTwoPassBuilder系)では
			// 使えない。旧来どおりlegacy経路(range stampingなし)のまま。
			final Container container = breakFlow.box.newColumn(pageAxis, mode, flags);
			if (container == null) {
				return false;
			}
			this.pruneFlowStackTo(breakFlow);
			this.resetFragmentCursor(breakFlow.pageAxis, breakFlow.lineAxis);
			// 2026-07-23(排除域P1増分1): 保持されたhidden flow分の空台帳を
			// 積み直す(これらのflowは再startFlowBlockされないため必須)。
			this.rebuildNoOverflowFloatingScopes();
			this.beginRestyling();
			net.zamasoft.foliojet.layout.fragment.ResumeTrace.begin("COLUMN");
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.beginContinuationPath(true);
			try {
				container.restyle(this, net.zamasoft.foliojet.layout.fragment.OpenShape.of(depth), false);
			} finally {
				this.endRestyling();
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.endContinuationPath();
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.end();
			}
			return true;
		}

		// 2026-07-21(M6b Phase B4-Step4): 相対open pathの収集可能プレフィックス
		// (自動改段のPLAIN_FLOWのみ、force改段では常に空——
		// ContinuationCapability.supportsColumnSplitThrough参照)を型付き
		// 継続として切断する。強制改段では常に空チェーンになるため、
		// この呼び出しはmode問わず安全(旧plan=nullと同じ結果になる)。
		final net.zamasoft.foliojet.layout.fragment.BreakPlan relativePlan = columnScan.toBreakPlan();
		final net.zamasoft.foliojet.layout.fragment.ColumnCutResult cutResult = breakFlow.box.prepareColumnCut(pageAxis,
				mode, flags, relativePlan);
		if (!(cutResult instanceof net.zamasoft.foliojet.layout.fragment.ColumnCutResult.Cut(
				final net.zamasoft.foliojet.layout.fragment.PreparedColumnCut prepared))) {
			// Keep/Move: 改段ポイントがない(旧newColumn()のnull相当)
			return false;
		}

		// 検証 → column commit → executor開始、の順序を守る
		// (検証失敗時にownerへcommitしていない状態で安全に止まれる)
		final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation = root.prepareColumnContinuation(
				breakFlow.box.getBlockParams().flow, prepared, columnScan.snapshot());
		breakFlow.box.commitPreparedColumn(prepared);

		this.pruneFlowStackTo(breakFlow);
		this.resetFragmentCursor(breakFlow.pageAxis, breakFlow.lineAxis);
		// 2026-07-23(排除域P1増分1): 保持されたhidden flow分の空台帳を
		// 積み直す(rootless経路と同じ)。
		this.rebuildNoOverflowFloatingScopes();
		root.resumeColumn(this, continuation);
		return true;
	}

	/** flowStackを{@code breakFlow}まで刈り込みます(改段先より内側を捨てる)。 */
	private void pruneFlowStackTo(final Flow breakFlow) {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow == breakFlow) {
					break;
				}
				this.flowStack.remove(i);
			}
		}
	}
}
