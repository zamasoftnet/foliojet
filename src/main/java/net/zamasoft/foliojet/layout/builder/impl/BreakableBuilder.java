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
import net.zamasoft.foliojet.layout.box.content.FloatMeasurement;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.DebugFlags;

/**
 * Abstract builder that coordinates page/column breaks (automatic, forced, and multi-column).
 * Correction on 2026-07-19: the old Javadoc was left over from RootBuilder and incorrectly
 * said "Builds the entire document". As noted in ARCHITECTURE.md naming ledger §8,
 * most of this file actually coordinates automatic/forced/column splits.
 * {@link RootBuilder} is the concrete subclass for the document root.
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
	 * Page break mode.
	 */
	protected byte mode;

	protected PageBreakMode pageSide;

	protected int breakDepth = -1;

	/**
	 * Count of lines that can be deferred to the next page.
	 */
	protected int widows = 0;

	/**
	 * Flag to apply a page-break-after break at the next point.
	 */
	protected PageBreakMode breakAfter = null;

	/**
	 * Flag allowing a forced break immediately before.
	 */
	protected boolean canBreakBefore = true;

	/**
	 * Flag allowing a natural page break between blocks.
	 */
	protected boolean interflowBreak = true;

	/**
	 * Nesting depth of relayout (resuming the remainder after a break).
	 * When replayed content overflows the new page, another page break occurs inside resume.
	 * With a boolean, completion of the inner resume would clear the outer resume context
	 * (external review finding).
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

	private IncompleteTableResult incompleteTable;

	/** Result of the latest accept/append/complete operation. UNSPLITTABLE if overflow remains after splitting. */
	public enum IncompleteTableStatus {
		UNSUPPORTED, UNSPLIT, MOVED, SPLIT, UNSPLITTABLE
	}

	/**
	 * Handle whose parent owns the placed final remainder.
	 * The caller must not addBound remainder() again or call splitTableBox().
	 * After addTableRow on body(), call rowsAppended() once.
	 * The final append can be accounted for by complete(), omitting rowsAppended().
	 * Each operation can replace the remainder/body group; always retrieve the next append target again.
	 * Do not interleave other flow content before complete(), or directly complete() the table.
	 * Always pass the final append to complete() if a negative end frame changes the page break result.
	 * If a large negative margin also affects earlier splits, the B-2b caller uses the whole plan
	 * to decide when to defer emission of accept/append notifications. Emitted splits cannot be undone.
	 */
	public final class IncompleteTableResult {
		private IncompleteTableStatus status;
		private TableBox remainder;
		private TableRowGroupBox body;
		private int rowCount;
		private int emittedFragments;
		private double bodySize, pageStart, placedPageAxis, beforeContentSize, beforePageSize;
		private Flow flow;
		private boolean completed;

		private IncompleteTableResult(final IncompleteTableStatus status) {
			this.status = status;
		}

		public IncompleteTableStatus status() {
			return this.status;
		}

		/** Actual fragments sent to the previous page by the latest accept/append/complete; excludes whole-box moves. */
		public int emittedFragments() {
			return this.emittedFragments;
		}

		public boolean isAccepted() {
			return this.status != IncompleteTableStatus.UNSUPPORTED;
		}

		/** Null only if unsupported. After completion, this is the last placed completed fragment. */
		public TableBox remainder() {
			return this.remainder;
		}

		public TableRowGroupBox body() {
			return this.body;
		}

		/**
		 * Whether post-notification splitting is determined within the visible range, including
		 * unnotified rows (B-2b-6). Used by B-2b to defer emission.
		 * pageAxis includes notified outer sizes, so use pageStart from placement as the starting
		 * point for remaining capacity. TableBox dry-runs the same split scan as the implementation
		 * after subtracting the frame/header.
		 */
		public boolean hasRowEmissionOverflow() {
			this.requireActive();
			return this.remainder.emissionCutDetermined(BreakableBuilder.this.getPageLimit() - this.pageStart);
		}

		/** Virtual whole-row plan for the current remainder. Null for standalone B-2a acceptance. */
		public net.zamasoft.foliojet.layout.box.impl.IncompleteTablePlan plan() {
			return this.remainder == null ? null : this.remainder.getIncompletePlan();
		}

		private void requireActive() {
			if (BreakableBuilder.this.incompleteTable != this || this.completed || this.remainder == null
					|| !this.remainder.isIncomplete() || BreakableBuilder.this.lastTableBox != this.remainder
					|| BreakableBuilder.this.getFlow() != this.flow
					|| Double.doubleToLongBits(BreakableBuilder.this.pageAxis) != Double.doubleToLongBits(this.placedPageAxis)
					|| BreakableBuilder.this.isRestyling() || BreakableBuilder.this.textSession != null
					|| BreakableBuilder.this.textBuilder != null) {
				throw new IllegalStateException("The incomplete table handle is not the active flow tail");
			}
		}

		/**
		 * Accounts only for appended rows; the parent performs needed page breaks and repositions
		 * the remainder. Checks before restoring the end frame, so use complete() instead for
		 * a final append whose restored frame changes the split result.
		 * Duplicate notifications without new rows, notifications before acceptance, and
		 * notifications after completion throw IllegalStateException.
		 */
		public IncompleteTableStatus rowsAppended() {
			this.requireActive();
			this.remainder.updateIncompleteBody(this.body, this.rowCount, this.bodySize);
			this.updateExtent();
			this.status = BreakableBuilder.this.breakIncompleteTable(this);
			this.rememberBody();
			BreakableBuilder.this.interflowBreak = false;
			return this.status;
		}

		/**
		 * Accounts for the unnotified final append, restores the end frame and trailing accounting,
		 * then checks the final page break exactly once. Appends are checked under the same conditions
		 * as rowsAppended(). May be called even if all rows were notified; they are not counted twice.
		 * If splitting from an earlier notification does not affect the completion result, direct
		 * complete() and complete() after rowsAppended() are equivalent.
		 * Call directly when a negative end frame cancels a split.
		 */
		public IncompleteTableStatus complete() {
			this.requireActive();
			if (this.body.getTableRowCount() != this.rowCount
					|| Double.doubleToLongBits(this.body.getPageSize()) != Double.doubleToLongBits(this.bodySize)) {
				this.remainder.updateIncompleteBody(this.body, this.rowCount, this.bodySize);
			}
			this.remainder.complete();
			this.completed = true;
			this.updateExtent();
			BreakableBuilder.this.poLastMargin = BreakableBuilder.this.neLastMargin = this.remainder.getFrame().margin.bottom;
			this.status = BreakableBuilder.this.breakIncompleteTable(this);
			this.rememberBody();
			BreakableBuilder.this.canBreakBefore = true;
			BreakableBuilder.this.interflowBreak = true;
			BreakableBuilder.this.applyBreakAfter(PageBreakMode.AUTO);
			BreakableBuilder.this.incompleteTable = null;
			return this.status;
		}

		private void updateExtent() {
			// Replace the outer size from the start position after initial margin collapse and float avoidance.
			// Avoid rounding in (oldCursor + (newExtent - oldExtent)) and duplicate placement.
			BreakableBuilder.this.pageAxis = this.pageStart + this.remainder.getHeight();
			if (this.flow.box instanceof FlowBlockBox flowBox) {
				flowBox.updateIncompleteTableExtent(BreakableBuilder.this.pageAxis - this.flow.pageAxis,
						this.beforeContentSize, this.beforePageSize);
			} else {
				this.flow.box.setPageAxis(BreakableBuilder.this.pageAxis - this.flow.pageAxis);
			}
			this.placedPageAxis = BreakableBuilder.this.pageAxis;
		}

		private void rememberBody() {
			this.body = this.remainder.getTableBody(0);
			this.rowCount = this.body.getTableRowCount();
			this.bodySize = this.body.getPageSize();
			this.placedPageAxis = BreakableBuilder.this.pageAxis;
		}
	}

	/** Actual host state shared by the post-Pass B check and the acceptance entry point. */
	final boolean supportsIncompleteTableIntake() {
		return this.mode == MODE_PAGE_BREAK && this.breakDepth == -1 && !this.isRestyling()
				&& this.textSession == null && this.textBuilder == null && this.canFragmentFurther();
	}

	/**
	 * Dedicated entry point for unfinished tables. BlockBuilder handles initial margin collapse
	 * and float avoidance. Forced/automatic splits use firstTableForceBreak / autoBreak
	 * (and TableBox.split beneath them), as for completed Retained tables.
	 * The parent also repositions the remainder. The caller must use the returned handle to
	 * append/complete, without calling splitTableBox().
	 *
	 * Accepts only normal horizontal-writing flow in MODE_PAGE_BREAK. Continuous mode is excluded
	 * because DocumentBuilder uses MODE_NO_BREAK. Requires one body group; excludes footers/collapse.
	 * B-2b checks Pass C plan eligibility and calls this entry point. Boxes carrying specified height
	 * other than body height are also excluded. Returns UNSUPPORTED without placement if ineligible.
	 * The caller can complete() the unaccepted box and then return to the old path.
	 */
	public final IncompleteTableResult acceptIncompleteTable(final TableBox tableBox) {
		if (!this.supportsIncompleteTableIntake() || this.getRootBox().getBlockParams().flow.isVertical()
				|| this.getFlowBox().getBlockParams().flow.isVertical() || tableBox.getTableParams().flow.isVertical()
				|| tableBox.getBlockBox().getPos().getType() != PosType.FLOW
				|| tableBox.getTableBodyCount() != 1 || tableBox.getTableFooter() != null
				|| tableBox.getTableParams().borderCollapse != net.zamasoft.foliojet.layout.box.params.TableParams.BORDER_SEPARATE
				|| Double.doubleToLongBits(tableBox.getInnerHeight()) != Double.doubleToLongBits(
						(tableBox.getTableHeader() == null ? 0 : tableBox.getTableHeader().getHeight())
								+ tableBox.getTableBody(0).getHeight())) {
			return new IncompleteTableResult(IncompleteTableStatus.UNSUPPORTED);
		}
		if (this.incompleteTable != null || (this.lastTableBox != null && this.lastTableBox.isIncomplete())
				|| !tableBox.isIncomplete() || tableBox.isFragmented()
				|| this.lastTableBox == tableBox) {
			throw new IllegalStateException("Expected a new incomplete table with no active intake");
		}
		// TablePos before/after are AUTO. As at the existing entry point, handle a preceding forced break first.
		final boolean namedTransition = this.resolveNamedPageTransition(tableBox);
		final boolean forcedBreak = this.breakAfter != null;
		if (forcedBreak) {
			this.forceBreak(this.breakAfter);
		}
		if (namedTransition && !forcedBreak) {
			this.namedTransitionBreak();
		}
		final IncompleteTableResult result = new IncompleteTableResult(IncompleteTableStatus.UNSPLIT);
		this.incompleteTable = result;
		super.addBound(tableBox);
		this.lastTableBox = tableBox;
		result.status = this.breakIncompleteTable(result);
		result.rememberBody();
		this.interflowBreak = false;
		return result;
	}

	@Override
	protected void incompleteTablePlaced(final TableBox tableBox, final double pageStart) {
		if (this.incompleteTable != null) {
			this.incompleteTable.remainder = tableBox;
			this.incompleteTable.pageStart = pageStart;
			this.incompleteTable.flow = this.getFlow();
			this.incompleteTable.beforePageSize = this.getFlowBox().getInnerHeight();
			if (this.getFlowBox() instanceof FlowBlockBox flowBox) {
				this.incompleteTable.beforeContentSize = flowBox.getContentSize();
			}
		}
	}

	/** Captures unfinished-table operation results without changing the existing Retained loop in addBound. */
	private IncompleteTableStatus breakIncompleteTable(final IncompleteTableResult result) {
		result.emittedFragments = 0;
		IncompleteTableStatus status = IncompleteTableStatus.UNSPLIT;
		for (;;) {
			this.checkAbort();
			final TableBox tableBox = result.remainder;
			final TableForceBreakMode force = this.firstTableForceBreak(tableBox);
			if (force != null) {
				this.lastTableBox = null;
				this.forceBreak(force);
			} else {
				if (LayoutUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
					return status;
				}
				this.lastTableBox = null;
				if (!this.autoBreak()) {
					this.lastTableBox = tableBox;
					return IncompleteTableStatus.UNSPLITTABLE;
				}
			}
			if (this.lastTableBox == null) {
				if (result.completed) {
					// If the completed table stays on the previous page with no remainder on the next, stop as in the old loop.
					++result.emittedFragments;
					if (this.getPageContext() != null) this.getPageContext().noteRetainedTableFragmentEmitted();
					return status;
				}
				throw new IllegalStateException("The parent lost the incomplete table remainder during a break");
			}
			if (this.lastTableBox != tableBox) {
				++result.emittedFragments;
				if (this.getPageContext() != null) {
					this.getPageContext().noteRetainedTableFragmentEmitted();
					// Root pageBreak returns after drawing the previous page and resuming the remainder.
					// MOVE needs the same box on the next page, so release it only if a different remainder is returned.
					tableBox.releaseDrawnRowFragment();
				}
				status = IncompleteTableStatus.SPLIT;
			} else if (force != null) {
				// If a forced inter-row split leaves the remainder unchanged, there is no progress. Do not keep retrying.
				return IncompleteTableStatus.UNSPLITTABLE;
			} else if (status == IncompleteTableStatus.UNSPLIT) {
				status = IncompleteTableStatus.MOVED;
			}
			result.remainder = this.lastTableBox;
		}
	}

	protected final void requireNoIncompleteTable() {
		if (this.incompleteTable != null || (this.lastTableBox != null && this.lastTableBox.isIncomplete())) {
			throw new IllegalStateException("The incomplete table must be completed before finish");
		}
	}

	@Override
	public void finish() {
		this.requireNoIncompleteTable();
		super.finish();
	}

	public BreakableBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox, byte mode) {
		super(layoutStack, contextBox);
		this.mode = mode;
	}

	/**
	 * Forced page break for an auto-layout table.
	 *  
	 * @param tableBox
	 * @return
	 */
	private TableForceBreakMode firstTableForceBreak(TableBox tableBox) {
		// *** Tables have headers and footers, so do not apply left/right page break specifications.
		if (tableBox.getTableBodyCount() <= 0) {
			return null;
		}
		double last = this.pageAxis;
		final WritingMode tableFlow = tableBox.getTableParams().flow;
		if (tableBox.isIncomplete() && tableBox.getIncompletePlan() != null) {
			last = tableBox.incompleteForceBreakStart(this.incompleteTable.pageStart);
		} else if (tableBox.isIncomplete()) {
			// Placement of an unfinished table does not add the end frame.
			last -= tableBox.getInnerPageExtent(tableFlow);
		} else {
			last -= tableBox.getInnerPageExtent(tableFlow) + tableBox.getFrame().getFramePageEnd(tableFlow);
		}
		if (tableBox.getTableHeader() != null) {
			last += tableBox.getTableHeader().getPageSize();
		}
		if (tableBox.getTableFooter() != null) {
			last += tableBox.getTableFooter().getPageSize();
		}
		// Scan extracted into pure TableCutter logic (C4-T3).
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

	/**
	 * Whether named page transitions are supported (N2a: true only for {@code RootBuilder},
	 * which owns page context). {@code ColumnBuilder} for column splitting, etc. does not
	 * participate in page-name decisions.
	 */
	protected boolean supportsNamedPages() {
		return false;
	}

	/** Current page name (N2a; only when {@link #supportsNamedPages} is true). */
	protected String currentPageName() {
		return null;
	}

	/** Sets the page name starting with the next generated page (N2a). */
	protected void setNextPageName(final String pageName) {
	}

	/**
	 * Issues a page break for a page-name transition (N2b). The {@code namedTransition} flag
	 * omits the closing page from output if it is blank.
	 */
	private void namedTransitionBreak() {
		this.forceBreak(new ForceBreakMode(this.getFlowBox(), PageBreakMode.PAGE, true));
	}

	/**
	 * Decides page-break-before (2026-08-01: unified the switches duplicated verbatim in
	 * {@code startFlowBlock} and {@code addBound}). Returns the mode requiring a forced break.
	 * The caller retains AVOID side effects (clearing {@code interflowBreak}, only on the
	 * {@code startFlowBlock} path).
	 *
	 * @return mode for {@code forceBreak}, or {@code null} if no break is needed
	 */
	private PageBreakMode resolveForcedBreakBefore(final PageBreakMode pageBreakBefore) {
		switch (pageBreakBefore) {
		case PageBreakMode.PAGE:
		case PageBreakMode.COLUMN:
			if (this.canBreakBefore) {
				return pageBreakBefore;
			}
			return null;
		case PageBreakMode.VERSO:
		case PageBreakMode.RECTO:
			if (!this.isRestyling() && (this.canBreakBefore || pageBreakBefore != this.pageSide)) {
				return pageBreakBefore;
			}
			return null;
		case PageBreakMode.IF_VERSO:
			if (!this.isRestyling() && this.pageSide == PageBreakMode.VERSO) {
				return PageBreakMode.RECTO;
			}
			return null;
		case PageBreakMode.IF_RECTO:
			if (!this.isRestyling() && this.pageSide == PageBreakMode.RECTO) {
				return PageBreakMode.VERSO;
			}
			return null;
		case PageBreakMode.AUTO:
		case PageBreakMode.AVOID:
			return null;
		default:
			throw new IllegalStateException(String.valueOf(pageBreakBefore));
		}
	}

	/**
	 * Decides page-break-after (2026-08-01: unified the inconsistent switches in
	 * {@code addBound} and {@code endFlowBlock}). Defer PAGE/COLUMN and same-side
	 * VERSO/RECTO until the next boundary ({@code breakAfter}); break immediately for
	 * opposite-side VERSO/RECTO and IF_*. Previously, {@code addBound} lacked
	 * IF_VERSO/IF_RECTO cases, so valid CSS values (such as {@code page-break-after: if-recto}
	 * on floats) caused IllegalStateException. Resolved by adopting the
	 * {@code endFlowBlock} decision rules. The caller retains the AVOID side effect
	 * (clearing {@code interflowBreak}).
	 */
	private void applyBreakAfter(final PageBreakMode pageBreakAfter) {
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
		case PageBreakMode.AUTO:
		case PageBreakMode.AVOID:
			break;
		default:
			throw new IllegalStateException(String.valueOf(pageBreakAfter));
		}
	}

	/**
	 * Decides page-name transitions at class-A boundaries (named pages N2a:
	 * consult-codex-2026-07-31-named-pages.txt Q2). If the name changes, switch it first
	 * and return true. After processing explicit breaks, the caller issues one transition
	 * break if no break has occurred yet, avoiding a double break when combined with an
	 * author break. Synthetic boxes (element==null) do not participate in the boundary.
	 */
	private boolean resolveNamedPageTransition(final net.zamasoft.foliojet.layout.box.IBox box) {
		if (!this.supportsNamedPages() || this.isRestyling() || box.getParams().element == null
				|| !(box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.AbstractBlockLevelPos pos)) {
			return false;
		}
		if (java.util.Objects.equals(pos.pageName, this.currentPageName())) {
			return false;
		}
		this.setNextPageName(pos.pageName);
		return true;
	}

	public void startFlowBlock(FlowBlockBox flowBox) {
		this.requireNoOpenTextBuilder(flowBox.getParams().element);

		boolean canBreakAfter = false;
		switch (flowBox.getType()) {
		case BLOCK:
			AbstractBlockBox blockBox = flowBox;
			// Allow page-break-after before the boundary.
			if (this.getRootBox().getBlockParams().flow.isVertical()) {
				canBreakAfter = !blockBox.getFrame().frame.border.getRight().isNull();
			} else {
				canBreakAfter = !blockBox.getFrame().frame.border.getTop().isNull();
			}
			break;
		}

		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			// Page break due to clear
			final FlowPos pos = (FlowPos) flowBox.getPos();
			while (this.breakByClear(pos))
				;

			// Check forced page break immediately before.
			if (this.mode == MODE_PAGE_BREAK) {
				// Named pages N2a: switch the name first (all subsequent breaks
				// create pages with the new name).
				final boolean namedTransition = this.resolveNamedPageTransition(flowBox);
				boolean forcedBreak = false;
				if (this.breakAfter != null && canBreakAfter) {
					// Page break from the preceding page-break-after
					this.forceBreak(this.breakAfter);
					forcedBreak = true;
				}
				final PageBreakMode forced = this.resolveForcedBreakBefore(pos.pageBreakBefore);
				if (forced != null) {
					this.forceBreak(forced);
					forcedBreak = true;
				} else if (pos.pageBreakBefore == PageBreakMode.AVOID) {
					this.interflowBreak = false;
				}
				if (namedTransition && !forcedBreak) {
					// If no explicit break occurs, the transition issues one itself, even at page start
					// (canBreakBefore=false). If the old page is blank, drawPage’s namedTransition check
					// drops it without consuming a page side or counter.
					// This is equivalent to replacing the unresolved old-name page with a new-name page
					// (N2b). If it already has visible ink, retain it as a page.
					this.namedTransitionBreak();
				}
			}
		}

		if (this.breakDepth == -1) {
			// Pagination contract (2026-07-22, development record
			// -contract-consultation.md): both axis changes (TB⇄RL/LR) and
			// direction changes on the same axis (RL⇄LR) make this ancestor chain atomic.
			// Generalize the treatment of orthogonal-writing-mode tables to normal-flow
			// blocks as well. PaginationContract is the authoritative decision point.
			if (net.zamasoft.foliojet.layout.fragment.PaginationContract.isChainAtomicBoundary(
					this.getFlow().box.getBlockParams().flow, flowBox)) {
				this.breakDepth = 0;
			}
		} else {
			++this.breakDepth;
		}
		super.startFlowBlock(flowBox);
		// Non-multicolumn boxes (most body/cell boxes) cannot be hosts. Exit here to avoid
		// running eligibility checks for every cell in an 8,000-row table.
		if (flowBox.getColumnCount() > 1) {
			final RootBuilder footnoteRoot = this.getPageContext();
			if (footnoteRoot != null) footnoteRoot.openFootnoteColumn(this, this.getFlow());
		}
		if (canBreakAfter) {
			this.canBreakBefore = true;
			this.interflowBreak = true;
		}
	}

	private final boolean breakByClear(final FlowPos pos) {
		// Page break due to block clear
		this.checkAbort();
		this.requireNoOpenTextBuilder("(no context)");
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

		// Extend the height to force a split.
		double savePageAxis = this.pageAxis;
		this.pageAxis = this.getPageLimit() + 1;
		boolean breaked = this.autoBreak();
		if (!breaked) {
			this.pageAxis = savePageAxis;
		}
		if (this.textBuilder != null) {
			this.endTextBlock();
		}
		// **Return false if the page break fails** (2026-07-29).
		//
		// The caller ({@link #startFlowBlock}) loops with
		// {@code while (this.breakByClear(pos));}. If the break fails,
		// rewinding `pageAxis` restores **exactly the state before the call**,
		// so returning true here repeats the same call forever.
		// `this.breakFloats` is unchanged too, so the branch result is identical each time.
		//
		// Observed (seed 213026): looped for 120 seconds without emitting a page,
		// with 17,522 abandoned attempts inside. `autoBreak` returns false
		// when the progress guarantee guard detects float livelock
		// ({@code ContinuationStats.guardBreakProgress}) and
		// abandons the page break.
		return breaked;
	}

	public final void addBound(IBox box) {
		// M3c: prevent page break checks (forceBreak, etc.) from interleaving with K-P accumulation;
		// if accumulating, finalize via legacy before checking.
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
			// Normal flow
			this.requireNoOpenTextBuilder("(no context)");
			final FlowPos pos = (FlowPos) box.getPos();
			pageBreakBefore = pos.pageBreakBefore;
			pageBreakAfter = pos.pageBreakAfter;
			// Page break due to clear
			while (this.breakByClear(pos))
				;
			break;
		}
		case FLOAT: {
			// Floating box
			final FloatPos pos = (FloatPos) box.getPos();
			pageBreakBefore = pos.pageBreakBefore;
			pageBreakAfter = pos.pageBreakAfter;
			break;
		}
		case ABSOLUTE: {
			// Absolute positioning
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

		// Check forced page break immediately before.
		if (this.mode == MODE_PAGE_BREAK) {
			// Named pages N2a: same decision as startFlowBlock (floats/tables/replaced elements).
			final boolean namedTransition = this.resolveNamedPageTransition(box);
			boolean forcedBreak = false;
			if (this.breakAfter != null) {
				// Page break from the preceding page-break-after
				this.forceBreak(this.breakAfter);
				forcedBreak = true;
			}
			final PageBreakMode forced = this.resolveForcedBreakBefore(pageBreakBefore);
			if (forced != null) {
				this.forceBreak(forced);
				forcedBreak = true;
			}
			if (namedTransition && !forcedBreak) {
				this.namedTransitionBreak();
			}
		}

		super.addBound(box);
		if (!this.isRestyling()) {
			switch (box.getType()) {
			case TABLE:
				// The table may end up split: the rest of the code reads the box that remains in the flow
				box = this.breakTable((TableBox) box);
				break;
			case BLOCK:
				break;
			case RESCUE:
				// 2026-07-25 (rescue splitting, increment 7): rescue fragments pass through addBound
				// <b>only for float remainders</b> (normal-flow remainders
				// use the dedicated addRescueBound() entry point). Floats do not advance
				// the page-axis cursor, so no automatic page break check is needed here.
				assert box.getPos().getType() == PosType.FLOAT : box;
				break;
			case REPLACED: {
				if (box.getPos().getType() != PosType.FLOW) {
					break;
				}
				for (;;) {
					this.checkAbort();
					if (LayoutUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
						break;
					}
					// Automatic page break
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

		if (box instanceof TableBox tableBox && tableBox.isIncomplete()) {
			// Subsequent rows are not yet accepted, so the boundary/avoid immediately after the table is not final.
			this.interflowBreak = false;
			return;
		}
		this.canBreakBefore = true;
		this.interflowBreak = true;

		// Check forced page break immediately after.
		if (this.mode == MODE_PAGE_BREAK) {
			if (pageBreakAfter == PageBreakMode.AVOID) {
				this.interflowBreak = false;
			}
			this.applyBreakAfter(pageBreakAfter);
		}
	}

	/**
	 * Breaks the page inside a table just added to the flow until what remains fits (2026-10-08, extracted from
	 * {@link #addBound}), and returns the table box that remains in the flow: the table itself, or the remainder of
	 * its last split. Each split replaces the remainder through {@code lastTableBox}, which the page break sets.
	 *
	 * <p>
	 * Exits, in the order of the original loop (each returns the current table box):
	 * </p>
	 * <ol>
	 * <li>a complete table that is not retained (IncrementalTableBuilder repositions it itself)</li>
	 * <li>(entry) an incomplete table is kept as {@code lastTableBox} even without a split</li>
	 * <li>a forced break in the table: break and continue with the remainder the break left in {@code lastTableBox}</li>
	 * <li>it fits</li>
	 * <li>no break point, incomplete table: keep it as {@code lastTableBox} (headers and footers may not fit)</li>
	 * <li>no break point, complete table: {@code lastTableBox} stays {@code null}</li>
	 * <li>broken, but no remainder</li>
	 * <li>broken: continue with the remainder</li>
	 * </ol>
	 */
	private TableBox breakTable(TableBox tableBox) {
		// 2026-07-21 (M6b Phase B5e cleanup): previously, a separate implementation,
		// LayoutUtils.needsIntrinsicSizing(TableBox), rechecked the old four conditions.
		// It duplicated TableBuildPlanner.plan() completely,
		// but lacked the ORTHOGONAL_WRITING_MODE condition added in B5e,
		// creating an inconsistency between the two.
		// Measurements had confirmed no actual harm, but adding similar conditions
		// would risk repeating the problem, so eliminate it by using the single
		// decision point, TableBuildPlanner.plan().
		if (!tableBox.isIncomplete()
				&& TableBuildPlanner.plan(this, tableBox).mode() != TableBuildPlan.Mode.RETAINED) {
			// For Incremental (fixed layout, etc.),
			// IncrementalTableBuilder handles repositioning.
			return tableBox;
		}
		if (tableBox.isIncomplete()) {
			// Retain the current remainder even without splitting. Existing repositioning code updates it on a split.
			this.lastTableBox = tableBox;
		}
		for (;;) {
			this.checkAbort();
			// Check forced table page breaks
			if (this.mode == MODE_PAGE_BREAK) {
				final TableForceBreakMode mode = this.firstTableForceBreak(tableBox);
				if (mode != null) {
					this.forceBreak(mode);
					tableBox = this.lastTableBox;
					continue;
				}
			}

			if (LayoutUtils.compare(this.pageAxis, this.getPageLimit()) <= 0) {
				return tableBox;
			}

			// Automatic page break
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("page break [in table]");
			}
			this.lastTableBox = null;
			if (!this.autoBreak()) {
				// Table headers and footers may not fit.
				if (tableBox.isIncomplete()) {
					this.lastTableBox = tableBox;
				}
				return tableBox;
			}
			if (this.lastTableBox == null) {
				return tableBox;
			}
			tableBox = this.lastTableBox;
		}
	}

	/**
	 * Places a visual rescue split remainder in the flow and breaks the page if it still
	 * overflows (introduced 2026-07-25, increment 5).
	 *
	 * <p>
	 * The loop has the same structure as {@code case REPLACED} in {@code addBound()}.
	 * It <b>always</b> exits if {@code autoBreak()} returns {@code false} (no page break point),
	 * so it cannot loop forever here. {@code VisualRescuePlanner} structurally guarantees
	 * progress (each page break consumes a strictly positive amount).
	 * </p>
	 */
	@Override
	public void addRescueBound(final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox box) {
		super.addRescueBound(box);
		if (!this.isRestyling() && this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			for (;;) {
				this.checkAbort();
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
			// Check forced page break immediately before.
			// Page break from the preceding page-break-after
			this.forceBreak(this.breakAfter);
		}
		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			for (;;) {
				final double pageLimit = this.getPageLimit();
				if (TextBuilder.hasFirstLineBandInFragment(this, pageLimit) || !this.canFragmentFurther()) {
					break;
				}
				this.checkAbort();
				// Page float exclusions prevent the first line from fitting in the type area.
				// Before creating an empty TextBlockBox, use normal fragment splitting,
				// then recheck on the next page with its replaced page exclusions.
				final double savedPageAxis = this.pageAxis;
				this.pageAxis = pageLimit + 1;
				if (!this.autoBreak()) {
					this.pageAxis = savedPageAxis;
					break;
				}
				if (this.textBuilder != null) {
					// **Close the text block created by page break processing** (2026-09-17).
					// By the depth convention (OpenShape.of), the continuation always ends in open text, so resume
					// returns with the trailing TextBlockBox open. This point is reached when no text is open
					// (textBuilder==null), so that is **a text block (fragment) already closed earlier**.
					// The logical paragraph need not have ended:
					// flush() also temporarily closes text that has a continuation. The continuation arrives as the
					// event in the current call, at the new block created next. `endTextBlock()`, `breakByClear()`, and float splitting
					// loops all had this cleanup, but this page break alone lacked it.
					// super.requireTextBlock() then failed conversion because "the text builder remained open
					// at a block boundary" (six wild sweep cases: pages where page float
					// exclusions prevented the first line from fitting). Reusing it open is wrong:
					// the incoming control would enter the previous block, duplicating inline starts.
					this.endTextBlock();
				}
			}
		}
		super.requireTextBlock();
	}

	public final void flush() {
		// M3c: while K-P accumulates, only record flush events (inter-line page break checks
		// run when replay at session end passes through this method).
		if (this.textSession != null && this.textSession.recordFlush()) {
			return;
		}
		if (this.textBuilder == null) {
			// If the text block is empty (textBuilder has not been created),
			// flush does nothing. Same null guard as BlockBuilder.flush().
			// Reachable example: a `div` containing only a soft hyphen (U+00AD) directly.
			// StyledTextUnitizer creates a textShaper, but WordHyphenator
			// silently drops the Marker (hyphens:manual with no fontMetrics),
			// so no glyph/control reaches the builder; the shaper close chain
			// calls only flush(). BlockBuilder was fixed on 2026-07-24,
			// but this equivalent path remained unchanged (2026-07-25).
			return;
		}
		while (this.textBuilder.flush()) {
			// Line break occurred
			if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
				continue;
			}

			// Check inter-line page breaks after a line break.
			TextBuilder tbb = this.textBuilder;
			double pageAxis = this.pageAxis;
			pageAxis += this.textBuilder.getPageAxis();
			if (pageAxis > 0) {
				this.canBreakBefore = true;
				this.interflowBreak = true;
			}

			// Automatic page break
			if (LayoutUtils.compare(pageAxis, this.getPageLimit()) <= 0) {
				// No overflow yet
				continue;
			}
			++this.widows;

			final BlockParams params = this.textBuilder.textBlockBox.getBlockParams();
			if (this.widows < Math.max(2, params.widows)) {
				// Insufficient widows
				continue;
			}

			if (!this.canFragmentFurther()) {
				// No more fragments can be created (multi-column layout exhausted its columns).
				// Closing the text block here leaves it unable to reopen,
				// and this.textBuilder would be used while null. **Do not close it**;
				// let content overflow in place (2026-07-28).
				continue;
			}

			// Close the current fragment because the type area is full, not because the body has ended.
			// If SoftHyphen is at this fragment’s line end, materialize
			// hyphenate-character as for normal wrapping.
			super.endTextBlock(true);

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("page break [interline]/" + pageAxis + "/" + this.widows);
			}
			final boolean broke = this.autoBreak();
			if (!broke || this.textBuilder == null) {
				// RootBuilder.pageBreak() returns false when the split target is KEEP/MOVE
				// and no page break point can be created. The original TextBuilder
				// has already been finalized into the current fragment by endTextBlock(),
				// so do not create an empty continuation TextBuilder. If another run actually arrives,
				// startTextRun() lazily creates it as usual. Even on a successful page break,
				// resume creates no TextBuilder if the split line has no remaining events.
				// In that case too, finish flush without synthesizing a nonexistent run.
				return;
			}

			// Restore TextRun
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
			// Automatic page break
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("page break [after text]" + pageLimit + "/" + this.pageAxis);
			}
			if (this.autoBreak()) {
				if (this.textBuilder != null) {
					// Close the text block created by page break processing.
					this.endTextBlock();
				}
			}
		}
	}

	public final void endFlowBlock() {
		try (var retained = this.takeRetainedFlow()) {
			this.endBreakableFlowBlock();
		}
	}

	private void endBreakableFlowBlock() {
		this.requireNoOpenTextBuilder("(no context)");
		assert !this.flowStack.isEmpty();
		Flow flow = (Flow) this.flowStack.get(this.flowStack.size() - 1);

		if (this.breakDepth == -1 && flow.box.canColumnBreak()) {
			// Break the page if the bottom border of the multi-column layout extends beyond the page.
			// Measure along the page axis: the physical height in vertical writing is the line axis, which made
			// portrait pages try a refused break at every close and missed real overflows on square or landscape
			// pages (writing-mode-column2.html, 2026-10-08).
			final double columnLimit = flow.pageAxis + flow.box.getInnerPageExtent(flow.box.getBlockParams().flow);
			// Calculate the bottom frame width.
			final double lastFrame = this.lastFrame(flow, 1);
			if (LayoutUtils.compare(columnLimit, this.getPageOwnerLimit() - lastFrame) > 0) {
				final BreakMode mode = new AutoBreakMode(flow.box, this.getPageOwnerLimit());
				final byte flags = IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_LAST;
				// A refused column break (no break point, Keep/Move) has still reset the pending break state
				// (beginBreak). Restore it, or the interflow check below is skipped and the multicol stays on this
				// page however far it overflows: fit sweep seed 12465506 (2026-10-08), a vertical-rl multicol whose
				// last child is a float after an orthogonal box wider than the paper put the whole document on one
				// page, off the paper; an in-flow block after the float made it break.
				final boolean interflow = this.interflowBreak;
				final boolean canBreak = this.canBreakBefore;
				final PageBreakMode pendingAfter = this.breakAfter;
				final java.util.EnumSet<FloatSide> pendingFloats = java.util.EnumSet.copyOf(this.breakFloats);
				if (!this.columnBreak(flow, mode, flags, lastFrame, 1)) {
					this.interflowBreak = interflow;
					this.canBreakBefore = canBreak;
					this.breakAfter = pendingAfter;
					this.breakFloats.addAll(pendingFloats);
				}
			}
		}

		boolean canBreakAfter = false;
		switch (flow.box.getType()) {
		case RESCUE:
			// 2026-07-25 (confirmed in rescue splitting, increment 5): this checks
			// the first box in flowStack, the "container box" currently being closed.
			// Rescue fragments structurally cannot be pushed onto flowStack
			// (fragments are only placed in flows as indivisible leaves; they are never opened).
			// Thus this branch is unreachable. If it ever becomes reachable, explicitly add
			// the design rule that split edges have no decoration, so the fragment itself
			// never has a "border permitting a page break immediately after the boundary".
			throw new IllegalStateException("救済断片はコンテナとして開かれない: " + flow.box);
		case BLOCK:
			AbstractBlockBox blockBox = (AbstractBlockBox) flow.box;
			// Allow forced page-break-after immediately after the boundary.
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
			if (blockBox instanceof net.zamasoft.foliojet.layout.box.PageAtomicBox) {
				// **Always enable the trailing overflow check when closing flex/grid**
				// (2026-08-17). Their content is built by TwoPass recording (MODE_NO_BREAK)
				// and takes the early return at the start of addBound(), so unlike normal blocks
				// they close without ever setting interflowBreak. For these boxes, whose bind advances
				// the cursor all at once, the following interflow check is
				// **the only automatic page break opportunity**. If preceding content leaves
				// the flag false (observed in the pandoc manual’s
				// .container{display:flex} after nav), the check is skipped,
				// piling the entire 130,000 pt body onto one page and overflowing off the sheet.
				// A preceding normal addBound makes it work by chance, so the document-structure
				// dependency obscures the defect.
				this.canBreakBefore = true;
				this.interflowBreak = true;
			}
			break;
		}
		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			// Check forced page breaks immediately before the trailing boundary.
			if (this.breakAfter != null && canBreakAfter) {
				this.forceBreak(this.breakAfter);
			}
			// Split floating boxes inside the root box.
			if (this.flowStack.size() == 1) {
				while (!this.breakFloats.isEmpty()) {
					this.checkAbort();
					if (!this.canFragmentFurther()) {
						// No more fragments can be created (multi-column layout exhausted its columns).
						// Exiting without clearing reservations causes an **infinite loop**
						// (only beginBreak() clears breakFloats).
						// Leave floats in the last column and let them overflow.
						// (2026-07-28)
						this.breakFloats.clear();
						break;
					}
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("page break [floats]");
					}
					// Extend the height to ensure a split.
					this.pageAxis = this.getPageLimit() + 1;
					this.autoBreak();
					if (this.textBuilder != null) {
						// If a split float still overflows the next fragment, continuation replay
						// opens a text block and reserves breakFloats again.
						// The next loop iteration again breaks the column at a block boundary,
						// so close it immediately after each split, as in breakByClear().
						this.endTextBlock();
					}
				}
			}

			// Retrieve the flow object after the page break.
			flow = (Flow) this.flowStack.get(this.flowStack.size() - 1);
			if (this.textBuilder != null) {
				// Close the text block created by page break processing.
				this.endTextBlock();
			}
		}

		final RootBuilder footnoteRoot = flow.box.getColumnCount() > 1 ? this.getPageContext() : null;
		final boolean closesColumnOwner = footnoteRoot != null
				&& footnoteRoot.isEligibleFootnoteColumnOwner(this, flow.box);
		if (footnoteRoot != null) footnoteRoot.closeFootnoteColumn(flow.box);
		super.endFlowBlock();
		// After balance resolves the multi-column height, check whether the collected column notes fit (increment 6).
		if (footnoteRoot != null && closesColumnOwner) footnoteRoot.settleRecoveredFootnotes(this.pageAxis);
		if (this.breakDepth != -1) {
			--this.breakDepth;
		}
		this.afterFlowBlockClosed();

		if (this.mode != MODE_NO_BREAK && this.breakDepth == -1) {
			final double pageLimit = closesColumnOwner ? this.getPageOwnerLimit() : this.getPageLimit();
			FlowBlockBox flowBox = (FlowBlockBox) flow.box;

			final FlowPos pos = (FlowPos) flowBox.getPos();
			if (pos.pageBreakAfter == PageBreakMode.AVOID) {
				this.interflowBreak = false;
			}
			if (this.interflowBreak) {
				// If the lowest box’s bottom border extends beyond the page’s inner bottom edge
				// Automatic page break. **If the closed box is flex/grid (PageAtomicBox),
				// repeat until it fits** (2026-08-17). Previously this ran only once,
				// so an indivisible box spanning multiple pages, such as
				// a real document with body{display:flex;flex-direction:column},
				// received one rescue split, then its remainder stayed on page 2
				// without another check, leaving overflow at the end. Like the for(;;)
				// in TABLE addBound, this is the only automatic page break opportunity
				// for these boxes, so it must also handle repetition. Normal blocks have separate
				// per-line checks, so keep their single attempt (measurements confirmed that an unconditional
				// loop breaks blank-page suppression and existing fuzz behavior).
				// Exit when autoBreak returns false (for example, abandonment by the progress guarantee guard),
				// so the loop cannot run forever.
				final boolean repeat = flowBox instanceof net.zamasoft.foliojet.layout.box.PageAtomicBox;
				for (;;) {
					final double pageAxis = this.pageAxis - (this.poLastMargin + this.neLastMargin);
					if (LayoutUtils.compare(pageAxis, pageLimit) <= 0 || !this.paintsBeyondPage(flow, flowBox, pageLimit)) {
						break;
					}
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("page break [interflow]" + "/" + flowBox.getParams().element);
					}
					this.checkAbort();
					if (!this.autoBreak() || !repeat || this.flowStack.isEmpty()) {
						// autoBreak can empty flowStack during a page break
						// (observed in fuzzing: retrieving from the empty stack causes IndexOutOfBounds).
						break;
					}
					// Retrieve the flow again after the page break (the split recreated it).
					// **Retrieve the box again too** (2026-08-19): the trailing box after splitting is
					// a continuation fragment (a different instance). The old reference (retained side)
					// already fits, so paintsBeyondPage returns false,
					// ending the check while the remainder still exceeds the sheet (observed in stripe-docs
					// with over 3,000 pt piled up at the end).
					flow = (Flow) this.flowStack.get(this.flowStack.size() - 1);
					flowBox = (FlowBlockBox) flow.box;
				}
			}

			// Check forced page break immediately after (AVOID already cleared interflowBreak
			// above).
			if (this.mode == MODE_PAGE_BREAK) {
				this.applyBreakAfter(pos.pageBreakAfter);
			}
		}
	}

	/**
	 * Hook immediately after closing a flow block and restoring {@link #breakDepth}.
	 * Does nothing by default. RootBuilder attempts to translate top floats on the current
	 * page before the inter-block overflow check.
	 */
	protected void afterFlowBlockClosed() {
		// Do nothing for builders that do not own the whole page, such as ColumnBuilder.
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
	 * Checks deferral due to clear (2026-07-23, exclusion area P1 increment 4: unifies the
	 * switches duplicated in the old addStartFloat/addEndFloat). A float that clears
	 * an already deferred float is deferred to the next fragment without searching.
	 * No side effects.
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
	 * Creates a placement plan for deferral due to clear (2026-07-23, exclusion area P1
	 * increment 4). Place at the fragment boundary (pageLimit) and send to the next fragment.
	 * No side effects; {@code commitFloatPlacement}'s {@code MOVE_BY_CLEAR} branch commits it.
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
	 * Returns whether this float is at the effective start of the current page/column.
	 *
	 * <p>
	 * Being at the start within its own owner is insufficient. Combine, from the outside inward,
	 * whether each open owner is the first flow of its parent fragment, matching the
	 * {@code FLAGS_FIRST && fragmentHead} condition used in splitting (2026-09-04).
	 * </p>
	 */
	final boolean isFloatAtFragmentStart(final double pageStart) {
		boolean ancestorsFirst = true;
		for (int i = 1; i < this.getFlowCount(); ++i) {
			final Flow parent = this.getFlow(i - 1);
			final Flow child = this.getFlow(i);
			// Boxes in flowStack are AbstractContainerBox, but those pushed as flows are
			// IFlowBox (FlowBlockBox); treat anything else as not at the start.
			ancestorsFirst = FloatMeasurement.isFragmentStart(ancestorsFirst,
					child.box instanceof net.zamasoft.foliojet.layout.box.IFlowBox childFlow
							&& parent.box.getContainer().isFirstFlow(childFlow));
			if (!ancestorsFirst) {
				return false;
			}
		}
		final Flow owner = this.getFlow();
		return FloatMeasurement.isFragmentStart(ancestorsFirst,
				LayoutUtils.compare(pageStart - owner.pageAxis, 0) <= 0);
	}

	/** Effective end applied only to indivisible floats. Normally the fragment end itself. */
	protected double getUnsplittableFloatPageLimit() {
		return this.getPageLimit();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 2026-07-23 (exclusion area P1 increment 2): split the former
	 * {@code transferFloatToNextPage} (named as a predicate but with the side effect
	 * {@code breakFloats.add}) into this side-effect-free classification and
	 * {@link #recordBreakFloat}. Classification depends only on measured physical position
	 * (overflow beyond the fragment boundary and whether it is at page start).
	 * </p>
	 */
	@Override
	FloatCommitKind classifyFloatPlacement(final IFloatBox box, double pageStart) {
		if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("float placed unconditionally (no-break scope): " + box.getParams().element
						+ " mode=" + this.mode + " breakDepth=" + this.breakDepth + " builder=" + this.getClass().getSimpleName());
			}
			return FloatCommitKind.PLACED;
		}
		if (this.findColumnBreak() != null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("float placed unconditionally (column band): " + box.getParams().element + " builder="
						+ this.getClass().getSimpleName());
			}
			// Floats in multi-column layout (2026-07-26). The page axis here uses
			// **coordinates of the "band" before column splitting**, so comparison with the page limit
			// is meaningless: the band is supposed to be as long as the combined columns.
			//
			// The column’s {@link ColumnBuilder} classifies using its own limit (= column length).
			// If both record it, the root reservation remains even after the column handles it correctly,
			// causing the float splitting loop in {@link #endFlowBlock()} to
			// **create a page with nothing to draw**.
			//
			// Observed (2026-07-26, 6,000 seeds): trailing blank pages decreased from 47 cases to 32.
			// Measurements confirmed "band coordinates were compared with the page limit":
			// the same float was classified twice, at {@code pageStart=176.08}
			// (limit 190) on the root side and {@code pageStart=58.24} (limit 58.24)
			// on the column side.
			return FloatCommitKind.PLACED;
		}

		// 2026-07-28: this long checked only "box geometry" (getPageExtent).
		// **Even if the box fits on the page, its content can overflow the box and the sheet**.
		// This occurs for floats with explicit page-axis dimensions
		// (width in vertical writing, height in horizontal writing): content beyond the specified size
		// is still drawn with overflow:visible. Declaring it "fits" from geometry alone
		// does not reserve a split, so content extends off the sheet
		// **without a single page break** (local/shrink/strict-149858-min.html:
		// content of float:right;width:0pt reaches x=-145 on paper 120 pt wide).
		//
		// Only {@link #paintsNothingBeyondPage} is needed: "does the overflow contain anything
		// that remains on paper?" This is the correct measurement and can be smaller or larger than
		// geometry. The old geometry check merely duplicated cases where this measurement equaled
		// geometry (geometry>limit, measured<=limit uses the check below;
		// geometry<=limit, measured<=limit likewise returns PLACED).
		final WritingMode ownerFlow = this.getFlow().box.getBlockParams().flow;
		final double localStart = pageStart - this.getFlow().pageAxis;
		final boolean first = this.isFloatAtFragmentStart(pageStart);
		final BoxType boxType = box.getType();
		final PageBreakMode pageBreakInside = boxType == BoxType.BLOCK
				? ((AbstractContainerBox) box).getBlockParams().pageBreakInside
				: null;
		if (FloatMeasurement.isUnsplittable(boxType,
				FloatMeasurement.sameWritingAxis(ownerFlow, box), pageBreakInside, first)) {
			final double physicalPageLimit = this.getPageLimit();
			final double pageLimit = this.getUnsplittableFloatPageLimit();
			final double occupiedEnd = pageStart + FloatMeasurement.occupiedPageExtent(box, ownerFlow);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("unsplittable float: " + box.getParams().element + " pageStart=" + pageStart + " localStart="
						+ localStart + " first=" + first + " occupiedEnd=" + occupiedEnd + " pageLimit=" + pageLimit + " builder="
						+ this.getClass().getSimpleName());
			}
			if (LayoutUtils.compare(pageLimit, physicalPageLimit) < 0
					&& LayoutUtils.compare(occupiedEnd, pageLimit) > 0) {
				// Do not apply the painted-sliver tolerance to the actual placement band of a 2-D bottom float.
				// An atomic float cannot split in the middle; even a slight intrusion moves it intact.
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("transfer unsplittable float before reserved bottom band: " + box.getParams().element);
				}
				return FloatCommitKind.MOVE_TO_NEXT;
			}
			if (first) {
				// Even if indivisible, retain it with overflow when at page start.
				return FloatCommitKind.PLACED;
			}
			// Classify indivisible floats with the **same measure** as page-break-time {@code FloatSplitPlan.classify}:
			// occupied size = max(geometry, measured paint extent)
			// (2026-09-04). Looking only at painted extent marks an illustration whose margins alone
			// exceed the sheet (real cti.li document: orthogonal figure with `margin: 0 1.5em 1.2em`)
			// as PLACED here. After its exclusion area shortens lines, the page break
			// Moves it intact, leaving an empty footprint.
			if (FloatMeasurement.fitsPageUnsplittable(occupiedEnd, pageLimit)) {
				return FloatCommitKind.PLACED;
			}
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("transfer unsplittable float: " + box.getParams().element);
			}
			return FloatCommitKind.MOVE_TO_NEXT;
		}
		if (this.paintsNothingBeyondPage(box, pageStart)) {
			return FloatCommitKind.PLACED;
		}
		if (first && this.getPageContext() != null
				&& !this.getPageContext().fragmentStartFloatSplitProgresses(box.getParams().element,
						FloatMeasurement.occupiedPageExtent(box, ownerFlow))) {
			// No shrinkage since the last page-start split means another split makes no progress.
			// Place it with overflow (RootBuilder.fragmentStartFloatSplitProgresses).
			LOG.warning("float does not shrink across pages; placed overflowing: " + box.getParams().element);
			if (box instanceof net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBlock) {
				// Also inform page-break-time classification (FloatSplitPlan.classify). PLACED alone
				// allows another split at the actual page break, producing remainders of the same size.
				floatBlock.markSplitMakesNoProgress();
			}
			return FloatCommitKind.PLACED;
		}
		// Split only same-axis BLOCK floats (even avoid is splittable at page start).
		return FloatCommitKind.SPLIT_AT_BREAK;
	}

	/**
	 * Returns whether <b>the part of this float beyond the page contains nothing that
	 * remains on paper</b> (introduced 2026-07-26).
	 *
	 * <p>
	 * <b>The direct cause relevant to the absolute requirement "no unintended blank pages".</b>
	 * An overflowing float reserves a split, and the float splitting loop in
	 * {@link #endFlowBlock()} <b>always creates a page</b>. But when <b>only the box</b>
	 * overflows (content fits on this page, with no border/background), the fragment draws
	 * nothing and <b>merely adds one blank page</b>. Minimal example:
	 * </p>
	 *
	 * <pre>
	 * &lt;!-- 60x60 pt paper, writing-mode:vertical-rl --&gt;
	 * &lt;div style="float:left;width:79pt"&gt;T9&lt;/div&gt;
	 * </pre>
	 *
	 * <p>
	 * In vertical writing, {@code width} is on the page axis. Content "T9" fits on page 1,
	 * but the box overflows by 19 pt. Those 19 pt contain nothing.
	 * </p>
	 *
	 * <p>
	 * <b>Err on the conservative side</b>: treat any of the following as "paints" and
	 * split as before:
	 * </p>
	 * <ul>
	 * <li>Not a container (replaced elements, etc.; content cannot be queried)</li>
	 * <li>Visible border/background (the fragment has something to paint)</li>
	 * <li>Writing mode differs from page progression (page axes do not match, so cannot compare)</li>
	 * </ul>
	 *
	 * <p>
	 * <b>2026-07-27</b>: replaced {@code getContentSize()} with
	 * {@link Container#paintedPageEnd()} for measurement. {@code getContentSize()}
	 * <b>excludes nested floats</b>, so for boxes containing floats the check used to give up
	 * because it could not rule out something beyond the page. But generator documents
	 * commonly have <b>floats whose content is itself floats</b> (5 of 18 blank-page cases
	 * in a 20,000-document sweep). {@code paintedPageEnd()} correctly counts both nested
	 * floats and "space after content" in boxes without borders, so giving up is no longer needed.
	 * </p>
	 */
	private boolean paintsNothingBeyondPage(final IFloatBox box, final double pageStart) {
		final WritingMode progression = this.getRootBox().getBlockParams().flow;
		final double contentEnd = pageStart + box.paintedPageExtent(progression);
		// Treat overflow under 1 pt as "paints nothing" (2026-08-10).
		// Accumulated 0.75-step fractions from px→pt conversion could leave a visually meaningless sliver
		// (observed 0.5625 pt at pc.watch.impress.co.jp) beyond the page limit, triggering
		// a SPLIT reservation → forced page break from a later clear → relocation of the whole float
		// to the next page (when only an empty height box remained with no split point).
		// This moved the entire body down 1–2 pages. The check still measures painted extent
		// (paintedPageExtent), so small geometry with content extending off the sheet (the 2026-07-28
		// defect protected by OffPageFloatTest) still splits as before.
		return contentEnd - this.getPageLimit() < 1.0;
	}

	/**
	 * Returns whether the just-closed block <b>paints anything beyond the inner page end</b>
	 * (introduced 2026-07-27).
	 *
	 * <p>
	 * Inter-block automatic page breaks (interflow) in {@link #endFlowBlock()} previously checked
	 * only the <b>cursor position</b>, i.e. box geometry. Yet real cases have a box that extends
	 * beyond the page with nothing painted in the overflow. A typical example is multi-column
	 * height resolved larger than the available capacity, leaving 6 pt with neither content
	 * nor borders. A page break here produces a continuation fragment with <b>nothing to paint</b>,
	 * <b>merely adding one blank page</b> (violates css-break-3 §4.4:
	 * "each fragmentainer takes a nonzero amount of content").
	 * </p>
	 *
	 * <p>
	 * Uses the same principle and measurement as float {@link #paintsNothingBeyondPage}
	 * ({@link net.zamasoft.foliojet.layout.box.IBox#paintedPageExtent}).
	 * For boxes with borders/backgrounds or a different writing mode, keep using geometric
	 * dimensions (conservative: break the page).
	 * </p>
	 */
	private boolean paintsBeyondPage(final Flow flow, final FlowBlockBox flowBox, final double pageLimit) {
		final WritingMode progression = this.getRootBox().getBlockParams().flow;
		final double painted = flowBox.paintedPageExtent(progression);
		if (LayoutUtils.compare(painted, 0) <= 0) {
			// A box that paints nothing cannot justify a page break, regardless of position.
			return false;
		}
		return LayoutUtils.compare(flow.pageAxis + painted, pageLimit) > 0;
	}

	@Override
	void recordBreakFloat(final FloatSide side) {
		this.breakFloats.add(side);
	}

	/**
	 * Lower bound on page-axis capacity from {@link #getPageLimit()} (made a constant on 2026-07-24).
	 */
	public static final double MIN_PAGE_LIMIT = 20;

	/** Capacity for content overflow, splits, and tables. At Root, includes reservations for the current column. */
	public double getPageLimit() {
		final AbstractContainerBox rootBox = this.getRootBox();
		final BlockParams params = rootBox.getBlockParams();
		double pageLimit = rootBox.getInnerPageExtent(params.flow);
		if (pageLimit < MIN_PAGE_LIMIT) {
			// Ignore page heights below 20 points.
			pageLimit = MIN_PAGE_LIMIT;
		}
		return pageLimit;
	}

	/** Basis for box dimensions and owner closure checks. A local ColumnBuilder uses its own capacity. */
	public double getPageOwnerLimit() {
		return this.getPageLimit();
	}

	public void forceBreak(PageBreakMode breakType) {
		this.forceBreak(new ForceBreakMode(this.getFlowBox(), breakType));
	}

	/**
	 * Forced page break
	 *  
	 * @param breakMode
	 */
	public void forceBreak(ForceBreakMode breakMode) {
		if (breakMode.breakType == PageBreakMode.COLUMN) {
			// Find a block that permits a column break.
			final ColumnBreakPoint columnBreak = this.findColumnBreak();
			if (columnBreak != null) {
				final double lastFrame = this.lastFrame(columnBreak.flow(), columnBreak.depth());
				// 2026-07-21: previously ignored the boolean returned by columnBreak()
				// and returned unconditionally. When newColumn() returned no-cut (null),
				// no column break occurred and, unlike autoBreak(), there was no PAGE fallback,
				// causing a silent no-op (found and verified in a ChatGPT Pro consultation,
				// design consultation).
				// Use the same fallback rules as {@link #autoBreak()}.
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
		this.requireNoOpenTextBuilder("(no context)");
	}

	/**
	 * Automatic page break
	 *  
	 * @return
	 */
	/** Boxes open at the break (flowStack boxes, outer → inner). */
	protected final java.util.List<net.zamasoft.foliojet.layout.box.IBox> openFlowBoxes() {
		final java.util.List<net.zamasoft.foliojet.layout.box.IBox> boxes = new java.util.ArrayList<>();
		if (this.flowStack != null) {
			for (final Flow flow : this.flowStack) {
				boxes.add(flow.box);
			}
		}
		return boxes;
	}

	private boolean autoBreak() {
		if (DebugFlags.BREAK_TRACE) {
			System.err.println("[break] pageAxis=" + this.pageAxis + " flow="
					+ this.getFlowBox().getParams().element + " stack="
					+ (this.flowStack == null ? 0 : this.flowStack.size()));
		}
		byte flags = IPageBreakableBox.FLAGS_FIRST;
		// Find a block that permits a column break.
		final ColumnBreakPoint columnBreak = this.findColumnBreak();

		final BreakMode mode;
		if (this.flowStack == null || this.flowStack.size() <= 1) {
			mode = AutoBreakMode.withCapacity(this.getPageLimit());
		} else {
			mode = new AutoBreakMode(this.getFlowBox(), this.getPageLimit());
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
	 * Returns whether this builder <b>can still create a fragment (page/column)</b>
	 * (introduced 2026-07-28).
	 *
	 * <p>
	 * <b>"A requested page break always happens" is assumed throughout this file</b>,
	 * and violating it breaks something ({@code RootBuilder.pageBreak()} has long returned
	 * {@code false} for "no page break point"; the assumption itself was false).
	 * The inter-line page break in {@link #flush()} uses {@link #textBuilder} immediately
	 * afterward without checking, and the float splitting loop in {@link #endFlowBlock()}
	 * keeps running until {@code breakFloats} is empty. This hook <b>asks before jumping</b>,
	 * instead of checking {@code pageBreak()}'s result afterward and patching things up.
	 * </p>
	 *
	 * <p>
	 * Defaults to {@code true}: {@link RootBuilder} can create arbitrarily many sheets
	 * (if it cannot, {@code pageBreak()} returns {@code false}, handled by the caller as before).
	 * Only {@link ColumnBuilder} returns {@code false} when it exhausts {@code column-count}.
	 * </p>
	 */
	protected boolean canFragmentFurther() {
		return true;
	}

	/**
	 * Shared preparation for page/column breaks. Initializes pending split state that
	 * resets when crossing a fragment (page/column) boundary (M5).
	 */
	protected final void beginBreak() {
		this.requireNoOpenTextBuilder("(no context)");
		this.breakFloats.clear();
		this.breakAfter = null;
		this.canBreakBefore = false;
		this.interflowBreak = false;
	}

	/**
	 * Resets cursor state when advancing to the next fragment. Page and column breaks
	 * are the same operation: "the fragment container (fragmentainer) overflowed, so advance
	 * to the next fragment" (ARCHITECTURE.md §5). Centralize their reset here too (M5).
	 *
	 * @param pageAxis page-axis cursor position in the new fragment
	 * @param lineAxis line-axis cursor position in the new fragment
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
	 * Innermost flow allowing column breaks, and its depth (M5).
	 */
	protected record ColumnBreakPoint(Flow flow, int depth) {
	}

	/**
	 * Finds the innermost flow on the stack that allows column breaks.
	 *
	 * @return null if no flow allows a column break
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
	 * Captures the relative open path for a COLUMN continuation (index 0 = owner)
	 * (introduced 2026-07-21, M6b Phase B4-Step3; wired as of 2026-07-25).
	 * Handles both the normal path where the owner itself is in flowStack
	 * ({@link #findColumnBreak()}) and the path where {@code ColumnBuilder.contextFlow}
	 * is outside flowStack (see ChatGPT Pro consultation,
	 * design consultation).
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
		// Calculate the bottom frame width.
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
	 * Performs a column break.
	 *  
	 * @param breakFlow
	 * @param mode
	 * @param flags
	 * @param depth
	 * @return
	 */
	protected boolean columnBreak(final Flow breakFlow, final BreakMode mode, byte flags, final double lastFrame,
			int depth) {
		// Builders without page context, such as table cell remeasurement, have no RootBuilder
		// to register/resume continuation fragments. There are multiple entry points, so use this
		// as the final shared barrier; return automatic column breaks as "no column break point".
		// Leave forced column breaks to the fail-closed check in forceBreak.
		if (this.getPageContext() == null) {
			this.beginBreak();
			return false;
		}
		// 2026-07-21: this COLUMN break path is independent of and bypasses
		// the BreakPlan mechanism in RootBuilder.pageBreak() (found in a ChatGPT Pro consultation,
		// design consultation).
		// 2026-07-30 (increment 4c): worklist unification retired the depth-64 exception guard;
		// only maximum-depth recording for observation remains.
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenDepth(depth, true);
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordLastColumnOwnerColumnCount(breakFlow.box.getColumnCount());
		this.beginBreak();

		// 2026-07-21 (M6b Phase B4): capture the relative open path and verify that its depth
		// matches the depth parameter (introduced for observation in Step3; wired in Step4
		// to also pass it to the actual split, prepareColumnCut).
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
			// **Do not break columns if an open column cannot be restacked on resume** (2026-09-16).
			// Resume restacks only the approved prefix and columns supported by native multi-column descent.
			// `ContinuationCapability.MULTICOL` collectively means "not a plain FlowBlockBox",
			// including grid/flex boxes as well as real multi-column boxes. Pruning and continuing
			// while the former remain a barrier causes failures elsewhere later:
			// `endBreakableFlowBlock` accesses an empty stack, or a float loses its host
			// (wild sweep seeds 2375324 and 2678725).
			// Allow nested multi-column layout: native descent handles it (`MulticolWorklistScopeTest`).
			// Refusing the column break makes the caller fall back to a page break
			// (`autoBreak`/`forceBreak` receive false and call `pageBreak`).
			final java.util.Optional<net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.CapabilityBarrier> barrier = columnScan
					.snapshot().firstBarrier();
			if (barrier.isPresent()) {
				final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.OpenLevelDescriptor level = columnScan
						.snapshot().levels().get(barrier.get().openPathIndex());
				if (level.columnCount() <= 1) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("column break declined: barrier at " + barrier.get().openPathIndex() + " is "
								+ level.boxClass().getSimpleName() + " (" + barrier.get().reason() + ")");
					}
					return false;
				}
			}
		}

		final double contentLimit = this.getPageLimit() - breakFlow.pageAxis - lastFrame;
		final double ownerExtent = this.getPageOwnerLimit() - breakFlow.pageAxis - lastFrame;

		// Determine whether this is at page start.
		if (LayoutUtils.compare(
				breakFlow.pageAxis - breakFlow.box.getFrame().getFramePageStart(breakFlow.box.getBlockParams().flow),
				0) > 0) {
			flags ^= IPageBreakableBox.FLAGS_FIRST;
		}

		final RootBuilder root = this.getPageContext();

		// 2026-07-21 (M6b Phase B4-Step4): split the collectable prefix of the relative open path
		// (PLAIN_FLOW only for automatic column breaks; always empty for forced breaks —
		// see ContinuationCapability.supportsColumnSplitThrough) as a typed
		// continuation. Forced column breaks always have an empty chain,
		// so this call is safe for any mode (same result as the old plan=null).
		final net.zamasoft.foliojet.layout.fragment.BreakPlan relativePlan = columnScan.toBreakPlan();
		final net.zamasoft.foliojet.layout.fragment.ColumnCutResult cutResult;
		// Snapshot open boxes during the split (do not rescue open boxes omitted from the plan either; OpenBoxes).
		try (var open = net.zamasoft.foliojet.layout.fragment.OpenBoxes.scope(this.openFlowBoxes())) {
			cutResult = breakFlow.box.prepareColumnCut(contentLimit, ownerExtent, mode, flags, relativePlan);
		}
		if (!(cutResult instanceof net.zamasoft.foliojet.layout.fragment.ColumnCutResult.Cut(
				final net.zamasoft.foliojet.layout.fragment.PreparedColumnCut prepared))) {
			// Keep/Move: no column break point (equivalent to null from the old newColumn()).
			return false;
		}

		// Preserve the order: validate → column commit → start executor
		// (validation failure can safely stop before committing to the owner).
		final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation = root.prepareColumnContinuation(
				breakFlow.box.getBlockParams().flow, prepared, columnScan.snapshot());
		breakFlow.box.commitPreparedColumn(prepared);
		// Exclude local ColumnBuilders for balance/fixed-height multi-column layout from page column-break history.
		boolean pageColumn = true;
		for (LayoutStack stack = this; stack != null; stack = stack.getParentBuilder()) {
			if (stack instanceof ColumnBuilder) {
				pageColumn = false;
				break;
			}
		}
		if (pageColumn) root.columnCommitted(this, breakFlow, prepared);

		// Save the owner position before pruning for the post-resume depth check (below).
		final int ownerStackIndex = this.flowStack == null ? -1 : this.flowStack.indexOf(breakFlow);
		this.pruneFlowStackTo(breakFlow);
		this.resetFragmentCursor(breakFlow.pageAxis, breakFlow.lineAxis);
		// 2026-07-23 (exclusion area P1 increment 1): restack empty ledgers for retained
		// hidden flows (same as the rootless path).
		this.rebuildNoOverflowFloatingScopes();
		root.resumeColumn(this, continuation);
		// **Check relative open depth after resume** (2026-09-16). The PAGE path checks
		// "flowStack depth ≠ continuation depth" in `RootBuilder.pageBreak`, but column breaks
		// lacked the same check. Even when an open box became a closed remainder (rescue split)
		// and was not restacked, it returned true and continued (missing check
		// confirmed by codex review). If the pre-break relative depth is not restored, content ownership
		// and end-event correspondence are broken, so stop by failing closed.
		// Resume recreates the owner Flow, so identity lookup cannot find it
		// (calling `captureColumnOpenPath` again fails with "owner is in neither flowStack nor
		// contextFlow"). Count by index.
		final int resumedDepth = ownerStackIndex >= 0
				? (this.flowStack == null ? 0 : this.flowStack.size()) - ownerStackIndex
				: 1 + (this.flowStack == null ? 0 : this.flowStack.size());
		if (resumedDepth != depth) {
			throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					"column resume failed (relative open depth=" + resumedDepth + ", expected=" + depth
							+ ")\n  改段種別: " + mode + "\n  開き鎖の分類: "
							+ columnScan.snapshot().levels().stream()
									.map(l -> "[" + l.index() + "]" + l.boxClass().getSimpleName() + ":"
											+ (l.role() instanceof net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.OpenLevelRole.Ancestor a
													? String.valueOf(a.capability())
													: "anchor"))
									.reduce((x, y) -> x + " / " + y).orElse("-"));
		}
		return true;
	}

	/** Prunes flowStack to {@code breakFlow} (discards flows inside the column break destination). */
	private void pruneFlowStackTo(final Flow breakFlow) {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow == breakFlow) {
					break;
				}
				this.flowStack.remove(i);
				// As endFlowBlock() does: resume's startFlowBlock() counts the flow again (2026-10-09)
				if (this.breakDepth != -1) {
					--this.breakDepth;
				}
			}
		}
	}
}
