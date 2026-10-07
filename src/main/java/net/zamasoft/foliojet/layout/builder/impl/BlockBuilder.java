package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

import net.zamasoft.foliojet.layout.box.content.BreakToken;
import net.zamasoft.foliojet.layout.box.content.FloatMeasurement;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import net.zamasoft.foliojet.layout.box.params.ClearMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.constraint.AxisSpan;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineAbsoluteQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.foliojet.layout.util.DebugFlags;

public class BlockBuilder implements Builder, LayoutContext {
	private static final Logger LOG = Logger.getLogger(BlockBuilder.class.getName());

	protected LayoutStack layoutStack;

	protected Flow contextFlow;

	/**
	 * Stack of containing blocks.
	 */
	protected List<Flow> flowStack = null;

	protected TextBuilder textBuilder = null;

	private RetainedTextLimit.Scope retainedContext;
	private RetainedTextLimit.Scope retainedRoot;
	private java.util.Map<Integer, RetainedTextLimit.Scope> retainedFlows;

	/** Container-owned paragraph event queue, created only when enabled. */
	private net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session bidiParagraph;

	/**
	 * Text run currently open in the upstream {@code GlyphHandler}.
	 *
	 * <p>Fragmentation between lines may close {@link #textBuilder}, while the same run in the shaper
	 * can continue sending glyphs. In that case, the next glyph lazily reopens a new {@link TextBuilder}
	 * and run, so the run's font state is retained independently of the builder's lifetime.</p>
	 */
	private FontStyle openRunFontStyle = null;
	private FontMetrics openRunFontMetrics = null;

	/**
	 * Checks that <b>no text builder remains open</b> at a block boundary
	 * (2026-07-26, promoted from an assertion to fail-closed behavior).
	 *
	 * <p>
	 * Measurements confirmed that violating this invariant <b>silently loses content in production</b>:
	 * with assertions disabled, random-generation strict seed 890 converted successfully while losing
	 * three paragraphs (an entire {@code column-count:3} block) from the output.
	 * With assertions enabled, the same document fails explicitly.
	 * </p>
	 *
	 * <p>
	 * For securities reports and similar documents, incorrect output is far more dangerous than no output,
	 * so <b>throw an exception in both tests and production</b>. The check is a null comparison with
	 * zero cost. The depth check in {@code RootBuilder.pageBreak} was promoted for the same reason
	 * on 2026-07-21; this is the second such change.
	 * </p>
	 *
	 * @param context context to include in the exception message (element, etc.)
	 */
	protected final void requireNoOpenTextBuilder(final Object context) {
		if (this.textBuilder != null) {
			throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					"text builder still open at a block boundary: " + context);
		}
	}

	/**
	 * Accumulation session for Knuth-Plass line breaking ({@code text-wrap-style: pretty})
	 * (2026-07-23, M3c increment 3). Started by {@link #requireTextBlock()} only when opted in and the
	 * paragraph is eligible. During recording, accumulates text events instead of delivering them to
	 * {@link #textBuilder}. Always null in the default (legacy) mode, preserving behavior.
	 */
	TotalFitSession textSession = null;

	/**
	 * Continuation state of the next text block.
	 */
	protected BreakToken breakToken = BreakToken.NONE;

	protected double poLastMargin = 0, neLastMargin = 0;

	/**
	 * Position of the normal-flow box being placed, relative to the context box.
	 */
	protected double lineAxis = 0, pageAxis = 0;

	/**
	 * Floating boxes to add at the start of the next line or flow.
	 */
	protected List<IFloatBox> toAddFloatings = null;

	/**
	 * Floating boxes already added.
	 */
	protected List<Floating> floatings = null;
	/** Independent float registries created by overflow:hidden or writing-mode changes. */
	private List<List<Floating>> noOverflowFloatings = null;
	/** Flow boxes owning those registries, in the same order as {@link #noOverflowFloatings}. */
	private List<AbstractContainerBox> independentFloatScopeOwners = null;

	/**
	 * Generation counter for the {@link #floatings} registry (2026-07-24, E-5: eliminates the O(N)
	 * snapshot rebuild per query identified in the codex architecture review). Every registry mutation
	 * entry point calls {@link #noteFloatingsChanged()} to increment it:
	 * <ul>
	 * <li>{@link #addFloating(LayoutContext.Floating)}: adds an entry</li>
	 * <li>{@link #addFloating(IFloatBox)}: stable {@code FLOAT_COMP} sort (order affects
	 * {@code FloatExclusion.order} and snapshot content)</li>
	 * <li>{@link #endFlowBlock()}: {@code removeAll} when popping an overflow:hidden scope</li>
	 * <li>{@code BreakableBuilder.resetFragmentCursor()}: registry reset at a fragment boundary
	 * ({@code floatings = null})</li>
	 * </ul>
	 * Each {@link LayoutContext.Floating} entry is immutable, with all fields final. The snapshot's
	 * {@code box.getFloatPos().floating} also never changes after registry insertion
	 * ({@code StyleBuilder} and {@code FloatPosTemplate} write only during construction, before layout).
	 * Thus, counting only list additions, removals, and reordering in the generation is sufficient.
	 */
	private int floatingsGeneration = 0;

	/** Cache for {@link #snapshotExclusions()} (immutable, so it can be shared). */
	private ExclusionSpace cachedExclusions = null;

	/**
	 * Number of times {@link #startFlowBlock} narrowed a box around a page-float band (2026-10-05).
	 * RootBuilder uses this to distinguish whether a placed grid/flex narrowed to avoid the band or entered it.
	 */
	int pageFloatNarrowings = 0;

	/** Generation at which {@link #cachedExclusions} was built. */
	private int cachedExclusionsGeneration = -1;

	/**
	 * Records a change to the {@link #floatings} registry (E-5, generation-cache invalidation).
	 * Always call this when adding a mutation operation: a missed call creates a stale snapshot and
	 * an actual behavior bug. When in doubt, err on the safe side (an extra increment only causes
	 * more rebuilds; correctness is preserved).
	 */
	final void noteFloatingsChanged() {
		++this.floatingsGeneration;
		this.cachedExclusions = null;
	}

	/**
	 * Translates the normal float registry and the float registries shared by each independent BFC
	 * along the page axis. The same {@link Floating} can appear with identical identity in multiple
	 * registries, so creates the identity-based old → new mapping only once and replaces every reference
	 * with the same new instance. Preserves aliases across registries while also invalidating the
	 * exclusion-area snapshot cache.
	 *
	 * @param dy translation along the page axis
	 */
	public final void shiftFloatLedgers(final double dy) {
		final java.util.IdentityHashMap<Floating, Floating> shifted = new java.util.IdentityHashMap<>();
		if (this.floatings != null) {
			for (final Floating floating : this.floatings) {
				shifted.put(floating, floating.shiftedPageAxis(dy));
			}
		}
		if (this.noOverflowFloatings != null) {
			for (final List<Floating> ledger : this.noOverflowFloatings) {
				for (final Floating floating : ledger) {
					shifted.computeIfAbsent(floating, key -> key.shiftedPageAxis(dy));
				}
			}
		}
		if (this.floatings != null) {
			this.replaceShiftedFloatings(this.floatings, shifted);
		}
		if (this.noOverflowFloatings != null) {
			for (final List<Floating> ledger : this.noOverflowFloatings) {
				this.replaceShiftedFloatings(ledger, shifted);
			}
		}
		this.noteFloatingsChanged();
	}

	/**
	 * Returns the maximum page-axis end in the currently active normal float registry and the registries
	 * of open independent BFCs. Only the maximum is needed, so duplicate identities do not affect the
	 * result even if an entry appears in both.
	 *
	 * @return 0 if no floats are placed; otherwise, their maximum page-axis end
	 */
	protected final double maxActiveFloatingPageEnd() {
		double pageEnd = 0;
		if (this.floatings != null) {
			for (final Floating floating : this.floatings) {
				pageEnd = Math.max(pageEnd, floating.pageEnd);
			}
		}
		if (this.noOverflowFloatings != null) {
			for (final List<Floating> ledger : this.noOverflowFloatings) {
				for (final Floating floating : ledger) {
					pageEnd = Math.max(pageEnd, floating.pageEnd);
				}
			}
		}
		return pageEnd;
	}

	private void replaceShiftedFloatings(final List<Floating> ledger,
			final java.util.IdentityHashMap<Floating, Floating> shifted) {
		for (int i = 0; i < ledger.size(); ++i) {
			final Floating replacement = shifted.get(ledger.get(i));
			if (replacement != null) {
				ledger.set(i, replacement);
			}
		}
	}

	/**
	 * Translates the page-axis positions of open normal flows and the current cursor by the same amount.
	 * Preserves each flow's box, line-axis position, frame amount, and {@code line-clamp} state.
	 * {@link #contextFlow} is the page's own {@code (0, 0)} reference outside flowStack and is not moved.
	 *
	 * @param dy translation along the page axis
	 */
	public final void shiftFlowStack(final double dy) {
		if (this.flowStack != null) {
			for (int i = 0; i < this.flowStack.size(); ++i) {
				this.flowStack.set(i, this.flowStack.get(i).shiftedPageAxis(dy));
			}
		}
		this.pageAxis += dy;
	}

	/**
	 * Sorts floating boxes by their page-direction bottom edge, starting nearest the page start.
	 */
	private static Comparator<Object> FLOAT_COMP = new Comparator<Object>() {
		public int compare(Object o1, Object o2) {
			double a, b;
			if (o1 instanceof LayoutContext.Floating) {
				LayoutContext.Floating c1 = (LayoutContext.Floating) o1;
				a = c1.pageEnd;
			} else {
				Double c1 = (Double) o1;
				a = c1.doubleValue();
			}
			if (o2 instanceof LayoutContext.Floating) {
				LayoutContext.Floating c2 = (LayoutContext.Floating) o2;
				b = c2.pageEnd;
			} else {
				Double c2 = (Double) o2;
				b = c2.doubleValue();
			}
			return (a > b) ? 1 : ((a == b) ? 0 : -1);
		}
	};

	public BlockBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox) {
		this.layoutStack = layoutStack;
		if (contextBox != null) {
			this.contextFlow = new Flow(contextBox, 0, 0);
			if (!(this instanceof ColumnBuilder) && retainsFlowContent(contextBox)) {
				this.retainedRoot = this.enterRetained(contextBox);
			}
		}
	}

	private static boolean retainsFlowContent(final AbstractContainerBox box) {
		return box instanceof net.zamasoft.foliojet.layout.box.impl.GridBox
				|| box instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox
				|| (box.getColumnCount() > 1 && box.getBlockParams().columns.fill == Columns.FILL_BALANCE);
	}

	private RetainedTextLimit.Scope enterRetained(final AbstractContainerBox box) {
		final RetainedTextLimit limit = RetainedTextLimit.get(this);
		return limit == null ? null : limit.enter(RetainedTextLimit.elementName(box.getParams(), "block"));
	}

	private void beginRetainedContext(final AbstractContainerBox box) {
		this.retainedContext = this.retainedRoot == null ? this.enterRetained(box) : this.retainedRoot;
		this.retainedRoot = null;
	}

	/** The caller closes fixed-width independent builders in finally after placing them in the parent. */
	public void finishRetainedContext() {
		if (this.retainedContext != null) {
			this.retainedContext.close();
			this.retainedContext = null;
		}
	}

	public Builder getParentBuilder() {
		return (Builder) this.layoutStack;
	}

	public AbstractContainerBox getFixedWidthContextBox() {
		AbstractContainerBox box = this.getRootBox();
		if (box.getBlockParams().size.getWidthType() != LengthType.AUTO) {
			if (!box.getBlockParams().size.getWidthType().needsReference() || !box.getType().isTableInternal()) {
				return box;
			}
		}
		// Absolute boxes sized on the page axis by both ends (2026-10-04): the reference for inner % sizes.
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute
				&& absolute.getBlockParams().flow.isVertical() && absolute.isPageAxisDefinite()) {
			return box;
		}
		if (this.layoutStack == null) {
			return null;
		}
		switch (box.getPos().getType()) {
		case FLOW:
		case FLOAT:
		case INLINE:
		case TABLE_CELL:
			return this.layoutStack.getFixedWidthFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedWidthContextBox();
		default:
			throw new IllegalStateException();
		}
	}

	public AbstractContainerBox getFixedHeightContextBox() {
		AbstractContainerBox box = this.getRootBox();
		if (box.getBlockParams().size.getHeightType() != LengthType.AUTO) {
			if (!box.getBlockParams().size.getHeightType().needsReference() || !box.getType().isTableInternal()) {
				return box;
			}
		}
		// Absolute boxes sized on the page axis by both ends (2026-10-04): the reference for inner % sizes.
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute
				&& !absolute.getBlockParams().flow.isVertical() && absolute.isPageAxisDefinite()) {
			return box;
		}
		if (this.layoutStack == null) {
			return null;
		}
		switch (box.getPos().getType()) {
		case FLOW:
		case FLOAT:
		case INLINE:
		case TABLE_CELL:
			return this.layoutStack.getFixedHeightFlowBox();

		case ABSOLUTE:
			return this.layoutStack.getFixedHeightContextBox();
		default:
			throw new IllegalStateException(String.valueOf(box.getClass()));
		}
	}

	public double getFixedWidth() {
		double frameWidth = 0;
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				frameWidth += flow.box.getFrame().getFrameWidth();
				if (!params.flow.isVertical()) {
					// Horizontal writing
					return flow.box.getWidth() - frameWidth;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// Width is specified.
					return flow.box.getWidth() - frameWidth;
				}
			}
		}
		// Use the context's inner size (2026-10-05). getWidth included page margins, so horizontal tables in vertical writing
		// used paper width and overflowed the type area (jigensha report). Height/TwoPassBlockBuilder already used inner sizes.
		AbstractContainerBox box = this.getFixedWidthContextBox();
		return box == null ? 0 : box.getInnerWidth() - frameWidth;
	}

	public AbstractContainerBox getFixedWidthFlowBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				if (!params.flow.isVertical()) {
					// Horizontal writing
					return flow.box;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// Width is specified.
					return flow.box;
				}
			}
		}
		return this.getFixedWidthContextBox();
	}

	public AbstractContainerBox getFixedHeightFlowBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				final BlockParams params = flow.box.getBlockParams();
				if (params.flow.isVertical()) {
					// Vertical writing
					return flow.box;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// Width is specified.
					return flow.box;
				}
			}
		}
		return this.getFixedHeightContextBox();
	}

	public double getFixedHeight() {
		double frameHeight = 0;
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				BlockParams params = flow.box.getBlockParams();
				frameHeight += flow.box.getFrame().getFrameHeight();
				if (params.flow.isVertical()) {
					// Vertical writing
					return flow.box.getHeight() - frameHeight;
				}
				if (flow.box.isSpecifiedPageSize()) {
					// Width is specified.
					return flow.box.getHeight() - frameHeight;
				}
			}
		}
		AbstractContainerBox box = this.getFixedHeightContextBox();
		return box == null ? 0 : box.getInnerHeight() - frameHeight;
	}

	/**
	 * Page context (root builder). <b>May be absent</b>: builders for table-cell relayout
	 * (created by {@code TableRowBox} via {@code new BlockBuilder(null, ...)}) are not tied to a type area.
	 * Callers already allow null (e.g., the "relayout builder without page context" branch of
	 * {@code optimizedTextEnabled}); only this method was not null-safe
	 * (2026-08-02, discovered by an NPE during the sweep).
	 */
	public RootBuilder getPageContext() {
		return this.layoutStack == null ? null : this.layoutStack.getPageContext();
	}

	final void noteBidiLine(final net.zamasoft.foliojet.layout.box.impl.TextBlockBox block,
			final net.zamasoft.foliojet.layout.box.AbstractLineBox line) {
		if (this.bidiParagraph == null) {
			this.bidiParagraph = new net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session();
		}
		this.bidiParagraph.line(block, line);
	}

	final void seedBidiReplayPrefix(
			final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix prefix) {
		if (prefix.isEmpty()) {
			return;
		}
		if (this.bidiParagraph == null) {
			this.bidiParagraph = new net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session();
		}
		this.bidiParagraph.replayPrefix(prefix);
	}

	/** Records outer ordering boundaries such as float/absolute/bound in the paragraph queue. */
	public final void noteBidiBarrier(final Object payload) {
		if (this.bidiParagraph == null) {
			this.bidiParagraph = new net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.Session();
		}
		this.bidiParagraph.barrier(payload);
	}

	final void resolveBidiParagraph(final BlockParams params) {
		if (this.bidiParagraph == null) {
			return;
		}
		this.bidiParagraph.resolve(params);
		this.bidiParagraph = null;
	}

	/** Does not end the paragraph at a page break; generates only the finalized page's drawing tree ahead of time. */
	final void previewBidiParagraph(final BlockParams params) {
		if (this.bidiParagraph != null) {
			this.bidiParagraph.preview(params);
		}
	}

	/**
	 * <b>Cooperative interruption point</b> (added 2026-07-27). Called at the start of long-running loops.
	 *
	 * <p>
	 * <b>Page boundaries alone are insufficient.</b> The only way to stop a conversion externally is
	 * {@code abort()}, which merely sets a flag; nothing happens unless the engine reads it.
	 * Previously, it was read only in {@code UserAgent.nextPage()}, at page boundaries, so
	 * <b>documents whose single-page processing never finished could never be stopped</b>.
	 * </p>
	 *
	 * <p>
	 * Checks only {@code ABORT_FORCE}. {@code ABORT_NORMAL} means "stop cleanly at the next page boundary",
	 * so it must not trigger in the middle of a page.
	 * </p>
	 */
	protected final void checkAbort() {
		// Builders without page context (for table-cell relayout) have no object from which
		// to read the abort flag. Loops requiring external interruption run on the type-area
		// side, so this can be a no-op (2026-08-02, sweep NPE).
		final RootBuilder root = this.getPageContext();
		if (root == null) {
			return;
		}
		root.getPageGenerator().getUserAgent().checkAbort(jp.cssj.cti2.CTISession.ABORT_FORCE);
	}

	private int getFloatingCount() {
		return this.floatings == null ? 0 : this.floatings.size();
	}

	private Floating getFloating(int index) {
		return (Floating) this.floatings.get(index);
	}

	public void setPageAxis(double pageAxis) {
		this.pageAxis = pageAxis;
	}

	public double getPageAxis() {
		return this.pageAxis;
	}

	public boolean isTwoPass() {
		return false;
	}

	public boolean isMain() {
		return false;
	}

	/**
	 * Returns the first box with position specified, starting at the current flow and searching ancestors.
	 */
	public AbstractContainerBox getContextBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				AbstractContainerBox box = flow.box;
				if (box.isContextBox()) {
					return flow.box;
				}
			}
		}
		AbstractContainerBox box = this.contextFlow.box;
		if (this.layoutStack == null) {
			return box;
		}
		if (!box.isContextBox()) {
			return this.layoutStack.getContextBox();
		}
		return box;
	}

	public AbstractContainerBox getMulticolumnBox() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow.box.getColumnCount() > 1) {
					return flow.box;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the first box with position specified between the current flow and root.
	 *
	 * @return
	 */
	Flow getSubContextFlow() {
		if (this.flowStack != null) {
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				Flow flow = (Flow) this.flowStack.get(i);
				AbstractContainerBox box = flow.box;
				if (box.isContextBox()) {
					return flow;
				}
			}
		}
		return this.contextFlow;
	}

	public AbstractContainerBox getRootBox() {
		return this.contextFlow.box;
	}

	public AbstractContainerBox getFlowBox() {
		return this.getFlow().box;
	}

	public Flow getFlow() {
		if (this.flowStack == null || this.flowStack.isEmpty()) {
			return this.contextFlow;
		}
		return (Flow) this.flowStack.get(this.flowStack.size() - 1);
	}

	/**
	 * Returns whether any open flows remain inside contextFlow that need closing.
	 *
	 * <p>
	 * Even while restyling a closed subtree, content overflowing a column can cause nested COLUMN
	 * continuations. When the owner is {@link #contextFlow}, {@code pruneFlowStackTo(contextFlow)}
	 * consumes flows pushed by the restyle caller, and a COLUMN continuation may repush the same
	 * structural depth with different box identities. Each Java call frame of the closed restyle must
	 * close the new topmost flow one level at a time. Only if the continuation consumes all inner flows
	 * without repushing anything do the remaining old call frames have nothing to close
	 * (2026-08-25, extreme strict seed 4540).
	 * </p>
	 */
	public final boolean hasOpenFlow() {
		return this.flowStack != null && !this.flowStack.isEmpty();
	}

	public int getFlowCount() {
		return this.flowStack == null ? 1 : this.flowStack.size() + 1;
	}

	public Flow getFlow(int index) {
		if (index == 0) {
			return this.contextFlow;
		}
		return (Flow) this.flowStack.get(index - 1);
	}

	public void startFlowBlock(final FlowBlockBox flowBox) {
		this.startFlowBlock(flowBox, 0, 0);
	}

	/**
	 * Opens a normal-flow block with inward insets in the line direction.
	 *
	 * <p>
	 * Entry point dedicated to table captions. Per CSS 2.1 §17.4, the table element's {@code margin}
	 * belongs to the <b>wrapper box</b>, not the table itself, and the caption's containing block is the
	 * wrapper's content box, i.e., the <b>table's border box</b>. copper4 uses the table's margin box
	 * as the wrapper content width, so placing them as is makes the caption extend outward by the table
	 * margins (2026-08-30, the defect where Wikipedia thumbnail captions shifted left of the figure).
	 * Inset here to align with the table's border box.
	 * </p>
	 *
	 * @param insetStart inset width on the line-start side
	 * @param insetEnd   inset width on the line-end side
	 */
	public void startFlowBlock(final FlowBlockBox flowBox, final double insetStart, final double insetEnd) {
		this.requireNoOpenTextBuilder("(no context)");
		AbstractContainerBox containerBox = this.getFlowBox();
		final BlockParams cParams = containerBox.getBlockParams();
		final AxisSpan containerBand = new AxisSpan(this.lineAxis, this.lineAxis + containerBox.getLineSize());
		// Line-direction band narrowed by ordinary floats (before insets).
		AxisSpan floatBand = containerBand;
		final boolean avoidsFloats = avoidsFloats(flowBox);
		if (avoidsFloats) {
			// Boxes with independent formatting contexts avoid floating boxes (CSS 2.1 §9.5: the border box must not
			// overlap a float's margin box. Exclusions are unified through ExclusionSpace queries; 2026-07-23, P0 complete).
			// Includes multi-column layout, flex/grid (2026-08-27: an adjacent flex list overlapped a float label in asahi.com's footer),
			// flow-root, and non-visible overflow boxes (2026-10-05: backgrounds and borders extended under floats;
			// Chrome narrows the whole box). The band is determined by the exclusion area at container start.
			floatBand = this.snapshotExclusions().narrowLineBandForMulticol(this.pageAxis, containerBand);
		}
		if (narrower(floatBand, containerBand) || flowBox.getColumnCount() > 1
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.GridBox) {
			this.calculateSizeIn(flowBox, floatBand, insetStart, insetEnd);
		} else {
			// For boxes that were not narrowed, pass the container's line size unchanged. Subtracting band endpoints
			// shifted it by 1 ulp, changing flow-root/overflow box-width digests (multi-column/flex/grid already used band values).
			this.calculateSizeIn(flowBox, 0, containerBox.getLineSize(), insetStart, insetEnd);
		}
		if (avoidsFloats && narrower(floatBand, containerBand)) {
			// If it cannot fit beside floats, move down until it fits (CSS 2.1 §9.5, 2026-10-05; Chrome does the same).
			// Previously, specified-width boxes were placed beside floats and overflowed the type area; boxes after full-width
			// floats collapsed to width 0 (msn's weather box below the tabs). The lowered position is the frame start.
			final ExclusionSpace snapshot = this.snapshotExclusions();
			double at = Double.NaN;
			while (narrower(floatBand, containerBand) && !this.fitsBeside(flowBox, cParams, floatBand)) {
				final double next = snapshot.nextPageEndAfter(Double.isNaN(at) ? this.pageAxis : at);
				if (Double.isNaN(next)) {
					break;
				}
				at = next;
				floatBand = snapshot.narrowLineBandForMulticol(at, containerBand);
				this.calculateSizeIn(flowBox, floatBand, insetStart, insetEnd);
			}
			if (!Double.isNaN(at)) {
				this.poLastMargin = this.neLastMargin = 0;
				this.pageAxis = at
						- (cParams.flow.isVertical() ? flowBox.getFrame().margin.right : flowBox.getFrame().margin.top);
			}
		}
		final FlowPos pos = flowBox.getFlowPos();

		if (establishesIndependentFloatScope(flowBox, cParams)) {
			// Both overflow:hidden and writing-mode changes establish independent BFCs
			// and prevent inner floats from leaking into the parent's exclusion area.
			if (this.noOverflowFloatings == null) {
				this.noOverflowFloatings = new ArrayList<List<Floating>>();
				this.independentFloatScopeOwners = new ArrayList<AbstractContainerBox>();
			}
			this.noOverflowFloatings.add(new ArrayList<Floating>());
			this.independentFloatScopeOwners.add(flowBox);
		}

		final AbsoluteRectFrame frame = flowBox.getFrame();

		if (pos.clear != ClearMode.NONE && this.getFloatingCount() > 0) {
			// If clear is specified
			final double marginStart;
			if (cParams.flow.isVertical()) {
				marginStart = frame.margin.right;
			} else {
				marginStart = frame.margin.top;
			}
			final double pageStart = this.pageAxis - marginStart;
			final ExclusionSpace snapshot = this.snapshotExclusions();
			final FloatExclusion found = snapshot.findClearBoundary(pageStart, marginStart, pos.clear);
			if (found != null) {
				// Place below floating boxes.
				this.poLastMargin = this.neLastMargin = 0;
				this.pageAxis = found.pageSpan().end() - marginStart;
			}
		}

		if (avoidsFloats) {
			// Also avoid page-float bands (2026-10-05). Inner lines were laid out without page exclusions (e.g., grid),
			// or un-narrowed boxes' backgrounds/borders overlapped figures (flow-root; speech balloons/columns in jigensha's vertical books).
			// Narrow using bands intersecting the minimum occupied extent: from the frame start after margin collapse
			// through block-direction borders, padding, and one line (boxes just after figures began slightly before
			// the band and escaped checks based only on their start). For tables/grid/flex entering the band farther on,
			// RootBuilder switches that page to a one-dimensional reservation and splits before the band.
			final double borderStart = this.collapsedBorderStart(frame, cParams);
			final double minExtent = frame.getBorderPageExtent(cParams.flow) + flowBox.getBlockParams().lineHeight;
			final AxisSpan pageBand = this.pageFloatExclusionsForLineLayout().narrowLineBandOver(borderStart,
					borderStart + minExtent, floatBand);
			if (pageBand.extent() > 0 && (LayoutUtils.compare(pageBand.start(), floatBand.start()) != 0
					|| LayoutUtils.compare(pageBand.end(), floatBand.end()) != 0)) {
				++this.pageFloatNarrowings;
				this.calculateSizeIn(flowBox, pageBand, insetStart, insetEnd);
			}
		}

		// Collapse start-position margins.
		// SPEC CSS 2.1 8.3.1
		// For positive margins, use the larger one.
		// For a positive and a negative margin, add both.
		// For two negative margins, use the one with the larger absolute value.
		LayoutContext.Flow parentFlow = this.getFlow(this.getFlowCount() - 1);
		double marginStart, frameStart, frameHead;
		boolean bordered;
		if (cParams.flow.isVertical()) {
			// Vertical writing
			marginStart = frame.margin.right;
			frameHead = frame.getFrameTop();
			frameStart = frame.getFrameRight();
			bordered = frame.padding.right > 0 || !frame.frame.border.getRight().isNull();
		} else {
			// Horizontal-writing flow
			marginStart = frame.margin.top;
			frameHead = frame.getFrameLeft();
			frameStart = frame.getFrameTop();
			bordered = frame.padding.top > 0 || !frame.frame.border.getTop().isNull();
		}
		if (marginStart >= 0) {
			if (marginStart > this.poLastMargin) {
				this.pageAxis -= this.poLastMargin;
				this.poLastMargin = marginStart;
			} else {
				this.pageAxis -= marginStart;
			}
		} else {
			if (marginStart < this.neLastMargin) {
				this.pageAxis -= this.neLastMargin;
				this.neLastMargin = marginStart;
			} else {
				this.pageAxis -= marginStart;
			}
		}
		if (bordered || sealsMargins(flowBox)) {
			this.poLastMargin = this.neLastMargin = 0;
		}

		this.lineAxis += frameHead;
		parentFlow.box.addFlow(flowBox, this.pageAxis - parentFlow.pageAxis);
		this.pageAxis += frameStart;

		if (this.flowStack == null) {
			this.flowStack = new ArrayList<Flow>();
		}
		final Flow flow = new Flow(flowBox, this.lineAxis, this.pageAxis, frameHead);
		this.flowStack.add(flow);
		if (retainsFlowContent(flowBox)) {
			if (this.retainedFlows == null) this.retainedFlows = new java.util.HashMap<>();
			if (!this.retainedFlows.containsKey(this.flowStack.size())) {
				this.retainedFlows.put(this.flowStack.size(), this.enterRetained(flowBox));
			}
		}
		this.breakToken = BreakToken.NONE;
	}

			/**
	 * Returns a snapshot converting the current {@link #floatings} to {@link ExclusionSpace}
	 * (added 2026-07-23; used for actual layout since P0 Step4, and shared by {@code TextBuilder}).
	 * {@code this.floatings} is already sorted by {@code FLOAT_COMP} (ascending pageEnd, insertion order
	 * for ties), so O(N) batch construction suffices by passing the order unchanged to
	 * {@link ExclusionSpace#copyOfSorted} (2026-07-23, removed the O(N²) cost noted in codex review).
	 *
	 * <p>
	 * If the registry has not changed since construction (matching {@link #floatingsGeneration}),
	 * returns the cached immutable snapshot without rebuilding
	 * (2026-07-24, E-5: eliminates O(N) rebuilding per query).
	 * </p>
	 */
	ExclusionSpace snapshotExclusions() {
		final int count = this.getFloatingCount();
		if (count == 0) {
			return ExclusionSpace.EMPTY;
		}
		if (this.cachedExclusions != null && this.cachedExclusionsGeneration == this.floatingsGeneration) {
			return this.cachedExclusions;
		}
		final List<FloatExclusion> exclusions = new ArrayList<>(count);
		// Context needed to resolve shape-outside (2026-08-29). Writing mode comes from the root box;
		// the shape-margin percentage reference is the containing block's line-direction width. Cached
		// while the registry is unchanged, so shapes are not rebuilt for every line.
		final BlockParams rootParams = this.getRootBox().getBlockParams();
		final WritingMode progression = rootParams.flow;
		final double containingLineSize = this.getFlowBox().getLineSize();
		for (int i = 0; i < count; ++i) {
			final LayoutContext.Floating floating = this.getFloating(i);
			final FloatPos floatingPos = floating.box.getFloatPos();
			final net.zamasoft.foliojet.layout.constraint.ExclusionShape shape = floatingPos.shapeOutside == null
					? null
					: FloatShapeResolver.resolve(floating.box, floating.lineStart, floating.pageStart, rootParams,
							containingLineSize);
			exclusions.add(new FloatExclusion(i, floatingPos.floating,
					new AxisSpan(floating.pageStart, floating.pageEnd),
					new AxisSpan(floating.lineStart, floating.lineEnd), shape));
		}
		final ExclusionSpace snapshot = ExclusionSpace.copyOfSorted(exclusions);
		this.cachedExclusions = snapshot;
		this.cachedExclusionsGeneration = this.floatingsGeneration;
		return snapshot;
	}

	/**
	 * Scans normal floats and page floats separately for line layout and combines them into one
	 * available band.
	 *
	 * <p>
	 * Bottom page floats start beyond the current position, so do not mix them into the normal-float
	 * snapshot, whose scan stops at the first future start. The normal set uses the existing
	 * {@link ExclusionSpace#scanLineBand}; only the page set is fully scanned, taking the maximum
	 * lineStart and minimum lineEnd/maxPageSize. clear, BFC avoidance, and normal-float placement
	 * continue to consult only {@link #snapshotExclusions()}.
	 * </p>
	 */
	final ExclusionSpace.LineScan scanLineBandForLineLayout(final double pageStart, final double lineHeight,
			final double lineStart0, final double lineEnd0) {
		final ExclusionSpace ordinarySpace = this.snapshotExclusions();
		final ExclusionSpace.LineScan ordinary = ordinarySpace.scanLineBand(pageStart, lineHeight, lineStart0,
				lineEnd0);
		final ExclusionSpace pageSpace = this.pageFloatExclusionsForLineLayout();
		if (pageSpace.isEmpty()) {
			// For documents with only normal floats, return the existing path unchanged, including its result object.
			return ordinary;
		}
		final ExclusionSpace.LineScan page = pageSpace.scanLineBandFully(pageStart, lineHeight, lineStart0, lineEnd0);
		final int startOrder = LayoutUtils.compare(page.lineStart(), ordinary.lineStart());
		final int endOrder = LayoutUtils.compare(page.lineEnd(), ordinary.lineEnd());
		final boolean maxPageSizeSet = ordinary.maxPageSizeSet() || page.maxPageSizeSet();
		final double maxPageSize;
		if (!ordinary.maxPageSizeSet()) {
			maxPageSize = page.maxPageSize();
		} else if (!page.maxPageSizeSet()) {
			maxPageSize = ordinary.maxPageSize();
		} else {
			maxPageSize = Math.min(ordinary.maxPageSize(), page.maxPageSize());
		}
		return new ExclusionSpace.LineScan(
				startOrder > 0 ? page.startExclusion()
						: startOrder < 0 ? ordinary.startExclusion()
								: laterEnding(ordinary.startExclusion(), page.startExclusion()),
				endOrder < 0 ? page.endExclusion()
						: endOrder > 0 ? ordinary.endExclusion()
								: laterEnding(ordinary.endExclusion(), page.endExclusion()),
				Math.max(ordinary.lineStart(), page.lineStart()), Math.min(ordinary.lineEnd(), page.lineEnd()),
				maxPageSizeSet, maxPageSize);
	}

	/** Of exclusion areas producing the same line boundary, returns the one whose boundary persists longest. */
	private static FloatExclusion laterEnding(final FloatExclusion a, final FloatExclusion b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		final int endOrder = Double.compare(a.pageSpan().end(), b.pageSpan().end());
		if (endOrder != 0) {
			return endOrder > 0 ? a : b;
		}
		return a.order() >= b.order() ? a : b;
	}

	final boolean hasLineExclusions() {
		return !this.snapshotExclusions().isEmpty() || !this.pageFloatExclusionsForLineLayout().isEmpty();
	}

	/** Page-float exclusion areas scanned separately only for line layout in Root coordinates. */
	protected ExclusionSpace pageFloatExclusionsForLineLayout() {
		return ExclusionSpace.EMPTY;
	}

	protected final RetainedTextLimit.Scope takeRetainedFlow() {
		return this.retainedFlows == null ? null
				: this.retainedFlows.remove(this.flowStack == null ? 0 : this.flowStack.size());
	}

	public void endFlowBlock() {
		final var retained = this.takeRetainedFlow();
		try (retained) {
			this.endFlowBlockContent();
		}
	}

	private void endFlowBlockContent() {
		this.requireNoOpenTextBuilder("(no context)");
		final Flow flow = (Flow) this.flowStack.remove(this.flowStack.size() - 1);
		final FlowBlockBox flowBox = (FlowBlockBox) flow.box;
		final BlockParams params = flowBox.getBlockParams();
		final Flow parentFlow = this.getFlow();
		final BlockParams parentParams = parentFlow.box.getBlockParams();

		if (flowBox.getColumnCount() > 1 && params.columns.fill == Columns.FILL_BALANCE) {
			// Column balancing
			this.pageAxis = flow.pageAxis;
			flowBox.balance(this);
		}
		if (establishesIndependentFloatScope(flowBox, parentParams)) {
			// Remove floats inside independent BFCs from the parent's exclusion area.
			assert this.independentFloatScopeOwners.get(this.independentFloatScopeOwners.size() - 1) == flowBox;
			this.independentFloatScopeOwners.remove(this.independentFloatScopeOwners.size() - 1);
			final List<Floating> floatings = this.noOverflowFloatings.remove(this.noOverflowFloatings.size() - 1);
			// CSS 2.1 §10.6.7: An auto-height BFC includes the bottom margin-box edge of floats belonging
			// to that BFC. Using only the normal-flow cursor to determine height uses the short body text
			// after a page break (55.2 pt) as the height, clipping an image float moved to the start
			// (150 pt) via overflow:hidden (a real Yahoo! News example).
			// The placement registry's pageEnd uses this builder's page-axis coordinates, so before
			// removing the scope, extend the auto box's cursor and dimensions to the float bottom.
			if (!flowBox.isSpecifiedPageSize()) {
				for (int i = 0; i < floatings.size(); ++i) {
					this.pageAxis = Math.max(this.pageAxis, floatings.get(i).pageEnd);
				}
				flowBox.setPageAxis(this.pageAxis - flow.pageAxis);
			}
			if (this.floatings != null) {
				this.floatings.removeAll(floatings);
				this.noteFloatingsChanged();
			}
		}

		final AbsoluteRectFrame frame = flowBox.getFrame();
		final double marginEnd, frameEnd;
		boolean bordered;
		if (parentParams.flow.isVertical()) {
			// Vertical writing
			marginEnd = frame.margin.left;
			bordered = frame.padding.left > 0 || !frame.frame.border.getLeft().isNull()
					|| sealsMargins(flowBox);
			double width = flowBox.getInnerWidth();
			if (flowBox.getContentSize() != width || bordered) {
				this.pageAxis = flow.pageAxis + width;
				if (params.size.getWidthType() == LengthType.ABSOLUTE && width > 0) {
					bordered = true;
				}
			}
			frameEnd = frame.getFrameLeft();
		} else {
			// Horizontal writing
			marginEnd = frame.margin.bottom;
			bordered = frame.padding.bottom > 0 || !frame.frame.border.getBottom().isNull()
					|| sealsMargins(flowBox);
			double height = flowBox.getInnerHeight();
			if (flowBox.getContentSize() != height || bordered) {
				this.pageAxis = flow.pageAxis + height;
				if (params.size.getHeightType() == LengthType.ABSOLUTE && height > 0) {
					bordered = true;
				}
			}
			frameEnd = frame.getFrameBottom();
		}
		if (bordered) {
			if (marginEnd >= 0) {
				this.poLastMargin = marginEnd;
				this.neLastMargin = 0;
			} else {
				this.poLastMargin = 0;
				this.neLastMargin = marginEnd;
			}
		} else {
			if (marginEnd >= 0) {
				if (marginEnd > this.poLastMargin) {
					this.pageAxis -= this.poLastMargin;
					this.poLastMargin = marginEnd;
				} else {
					this.pageAxis -= marginEnd;
				}
			} else {
				if (marginEnd < this.neLastMargin) {
					this.pageAxis -= this.neLastMargin;
					this.neLastMargin = marginEnd;
				} else {
					this.pageAxis -= marginEnd;
				}
			}
		}
		this.pageAxis += frameEnd;

		parentFlow.box.setPageAxis(this.pageAxis - parentFlow.pageAxis);
		// **Undo exactly the amount pushed.** Do not reread it from frame:
		// margin:auto is resolved inside the flow, so the values can differ, such as
		// subtracting 106.75 after pushing 0 (see the description of Flow.frameHead).
		this.lineAxis -= flow.frameHead;
	}

	/**
	 * Resolves normal-flow box auto margins and table alignment (align) into physical margins
	 * (pure calculation extracted from addBound, 2026-07-30; preserves the former operation order).
	 * Writes to {@code amargin}.
	 *
	 * <p>
	 * Note: As in the old code, the distribution formula uses the supplied {@code lineSize} minus
	 * frameSize. The caller's {@code lineSize} is unchanged; the old implementation also never read
	 * {@code lineSize} after this calculation.
	 * </p>
	 */
	private static void resolveAutoMargins(final boolean vertical, final AbsoluteRectFrame frame, final Insets margin,
			final AbsoluteInsets amargin, final double cLineSize, double lineSize, final double xMarginStart,
			final double xMarginEnd, final Align align) {
		double frameSize, marginStart, marginEnd;
		if (vertical) {
			frameSize = frame.getFrameHeight();
			marginStart = margin.getTopType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.top;
			marginEnd = margin.getBottomType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.bottom;
		} else {
			frameSize = frame.getFrameWidth();
			marginStart = margin.getLeftType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.left;
			marginEnd = margin.getRightType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.right;
		}
		lineSize -= frameSize;
		// **Set auto margins to 0 for boxes wider than their containing block** (2026-08-03).
		//
		// CSS 2.1 §10.3.3: If width is specified and the total exceeds the containing block,
		// {@code direction: ltr} ignores the specified {@code margin-right}.
		// Thus, <b>the box aligns to the start and overflows at the end</b>. Previously, the remainder
		// was mechanically divided by 2, so a negative remainder created a <b>negative start margin</b>,
		// pushing the left half of the content off the sheet and clipping it.
		//
		// Centering a fixed-width type area with {@code margin: 0 auto} is extremely common in practice
		// (found in the third wave, using Statistics Bureau of Japan pages; PLAN §3).
		// A type area wider than the paper overflows anyway, but <b>preserving the start side keeps it readable</b>.
		final double autoRemainder = cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
		if (autoRemainder < 0 && (LayoutUtils.isNone(marginStart) || LayoutUtils.isNone(marginEnd))) {
			marginStart = LayoutUtils.isNone(marginStart) ? 0 : marginStart;
			marginEnd = LayoutUtils.isNone(marginEnd) ? 0 : marginEnd;
		} else if (LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
			// Make left and right margins equal.
			marginStart = marginEnd = autoRemainder / 2.0;
		} else if (LayoutUtils.isNone(marginStart)) {
			// Left is undetermined.
			marginStart = autoRemainder;
		} else if (LayoutUtils.isNone(marginEnd)) {
			// Right is undetermined.
			marginEnd = autoRemainder;
		} else {
			// Over-constrained
			switch (align) {
			case Align.START:
				// Align left
				marginEnd = 0;
				break;
			case Align.END:
				// Align right
				marginStart += cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
				break;
			case Align.CENTER:
				// Center
				double remainder = cLineSize - lineSize - frameSize - xMarginStart - xMarginEnd;
				remainder /= 2.0;
				marginStart += remainder;
				marginEnd += remainder;
				break;
			default:
				throw new IllegalStateException();
			}
		}
		if (vertical) {
			amargin.top = marginStart + xMarginStart;
			amargin.bottom = marginEnd + xMarginEnd;
		} else {
			amargin.left = marginStart + xMarginStart;
			amargin.right = marginEnd + xMarginEnd;
		}
	}

	/** Characters pending shaping/hyphenation. Deliver for measurement only when reading the static position. */
	java.util.function.Consumer<net.zamasoft.pdfg2d.gc.text.GlyphHandler> pendingText = measurement -> { };

	public void addBound(IBox box) {
		// M3c: Floats and absolute positioning read TextBuilder's live state (lineAxis/pageAxis),
		// so finalize into legacy mode first if K-P accumulation is active.
		if (this.textSession != null) {
			this.textSession.abortToLegacy();
		}
		this.noteBidiBarrier(box);
		switch (box.getPos().getType()) {
		case FLOW, TABLE -> this.addFlowBound(box);
		case FLOAT -> this.addFloatBound(box);
		case ABSOLUTE -> this.addAbsoluteBound(box);
		default -> throw new IllegalStateException();
		}
	}

	/** Adds normal flow (blocks, replaced elements, tables) at the current position; also collapses margins and avoids floats. */
	private void addFlowBound(final IBox box) {
		this.requireNoOpenTextBuilder("(no context)");
		IFlowBox flowBox = (IFlowBox) box;

		Flow flow = this.getFlow();
		BlockParams params = flow.box.getBlockParams();
		boolean vertical = params.flow.isVertical();
		AbsoluteInsets amargin;
		AbsoluteRectFrame frame;
		ClearMode clear;
		Align align;
		switch (box.getType()) {
		case REPLACED: {
			AbstractReplacedBox replacedBox = (AbstractReplacedBox) flowBox;
			LayoutUtils.calculateReplacedSize(this, replacedBox);
			frame = replacedBox.getFrame();
			FlowPos pos = (FlowPos) flowBox.getPos();
			clear = pos.clear;
			align = pos.align;
		}
			break;
		case BLOCK: {
			AbstractBlockBox blockBox = (AbstractBlockBox) flowBox;
			frame = blockBox.getFrame();
			FlowPos pos = (FlowPos) flowBox.getPos();
			clear = pos.clear;
			// Table-alignment resolution is box-local (shared pos is immutable after recording).
			align = blockBox instanceof FlowBlockBox fb ? fb.getResolvedAlign() : pos.align;
		}
			break;
		case TABLE: {
			TableBox tableBox = (TableBox) flowBox;
			frame = tableBox.getFrame();
			clear = ClearMode.NONE;
			align = null;
		}
			break;
		default:
			throw new IllegalStateException();
		}
		Insets margin = frame.frame.margin;
		amargin = frame.margin;
		double lineSize = box.getLineExtent(params.flow);
		final double cLineSize = flow.box.getLineSize();
		final double lineStop = this.lineAxis + cLineSize;
		double xMarginStart = 0, lineEnd = lineStop, xMarginEnd = 0;
		if (this.getFloatingCount() > 0) {
			// Check clear and prevent replaced boxes and tables from overlapping floating boxes.
			// *** Note that CLEAR_NONE is also checked. ***
			final double pageStart;
			final double marginAdjust;
			if (vertical) {
				marginAdjust = amargin.right;
			} else {
				marginAdjust = amargin.top;
			}
			pageStart = this.pageAxis - marginAdjust;
			final ExclusionSpace snapshot = this.snapshotExclusions();
			final ExclusionSpace.BoundAvoidance found = snapshot.findBoundAvoidance(pageStart, lineSize, lineStop,
					marginAdjust, clear);
			xMarginStart = found.xMarginStart();
			lineEnd = found.lineEnd();
			if (found.clearingExclusion() != null) {
				this.poLastMargin = this.neLastMargin = 0;
				this.pageAxis = found.clearPageEnd();
			}
		}
		final ExclusionSpace pageSpace = this.pageFloatExclusionsForLineLayout();
		if (!pageSpace.isEmpty()) {
			// Also avoid page-float bands (2026-10-05). Tables/replaced elements do not lay out their contents with page
			// exclusions, so starting in a bottom-figure band caused overlap (jigensha's vertical book). Place beside
			// the figure if it fits; otherwise, move to the band end (page edge) and carry over to the next page.
			final double marginAdjust = vertical ? amargin.right : amargin.top;
			final double pageStart = this.pageAxis - marginAdjust;
			final double bandEnd = pageSpace.bandEndAt(pageStart);
			if (!Double.isNaN(bandEnd)) {
				final AxisSpan room = pageSpace.narrowLineBandAt(pageStart,
						new AxisSpan(this.lineAxis + xMarginStart, lineEnd));
				if (LayoutUtils.compare(room.extent(), lineSize) >= 0) {
					xMarginStart = room.start() - this.lineAxis;
					lineEnd = room.end();
				} else {
					this.poLastMargin = this.neLastMargin = 0;
					this.pageAxis = bandEnd + marginAdjust;
				}
			}
		}
		xMarginEnd = lineStop - lineEnd;

		//
		// ■ Calculate normal-flow margins (pure calculation in resolveAutoMargins; 2026-07-30).
		// Do not resolve flex-item auto margins again: FlexBuilder has already resolved them
		// (see the description of FlowBlockBox.coordinatorOwnsAutoMargins).
		//
		if (align != null
				&& !(flowBox instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox fb
						&& fb.coordinatorOwnsAutoMargins())) {
			resolveAutoMargins(vertical, frame, margin, amargin, cLineSize, lineSize, xMarginStart, xMarginEnd,
					align);
		}
		if (amargin.top >= 0) {
			if (amargin.top > this.poLastMargin) {
				this.pageAxis -= this.poLastMargin;
				this.poLastMargin = amargin.top;
			} else {
				this.pageAxis -= amargin.top;
			}
		} else {
			if (amargin.top < this.neLastMargin) {
				this.pageAxis -= this.neLastMargin;
				this.neLastMargin = amargin.top;
			} else {
				this.pageAxis -= amargin.top;
			}
		}
		if (flowBox instanceof TableBox tableBox && tableBox.isIncomplete()) {
			this.poLastMargin = this.neLastMargin = 0;
		} else if (vertical) {
			this.poLastMargin = this.neLastMargin = amargin.left;
		} else {
			this.poLastMargin = this.neLastMargin = amargin.bottom;
		}
		flow.box.addFlow(flowBox, this.pageAxis - flow.pageAxis);

		if (flowBox instanceof TableBox tableBox && tableBox.isIncomplete()) {
			// getFrame() is the effective frame with its end deferred. Preserve the ordinary path's operation order.
			this.incompleteTablePlaced(tableBox, this.pageAxis);
			this.pageAxis += tableBox.getInnerPageExtent(params.flow) + frame.getFramePageExtent(params.flow);
		} else {
			this.pageAxis += flowBox.getPageExtent(params.flow);
		}
		flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
	}

	/** Receives a float. Mid-line, either places it on the current line or defers it until line end. */
	private void addFloatBound(final IBox box) {
		if (box.getType() == BoxType.REPLACED) {
			AbstractReplacedBox replacedBox = (AbstractReplacedBox) box;
			LayoutUtils.calculateReplacedSize(this, replacedBox);
		}

		// Float
		final IFloatBox floatBox = (IFloatBox) box;
		if (DebugFlags.FLOAT_TRACE) {
			final StringBuilder where = new StringBuilder();
			final StackTraceElement[] st = new Throwable().getStackTrace();
			for (int k = 1; k < Math.min(st.length, 7); ++k) {
				where.append(' ').append(st[k].getMethodName()).append(':').append(st[k].getLineNumber());
			}
			System.err.println("[float] 受理 side=" + floatBox.getFloatPos().floating + " box="
					+ System.identityHashCode(floatBox) + " 経路" + where);
		}
		if (this.textBuilder != null && this.textBuilder.getLineAxis() > 0) {
			// A float appearing mid-line. If on the line-end side and it fits in the current line's
			// remaining width, place it at the current line's top and narrow the line immediately
			// (CSS 2.1 §9.5, as in browsers; kabutan 2026-08-08).
			// If it does not fit, is on the line-start side, or has clear, defer to line end
			// and place in the next band as before.
			if (!this.tryFloatOnCurrentLine(floatBox)) {
				this.toAddFloating(floatBox);
			}
		} else {
			this.addFloating(floatBox);
		}
	}

	/** Registers an absolutely positioned box and its static position with the flow owner. */
	private void addAbsoluteBound(final IBox box) {
		// Ordinary absolute/fixed positioning
		final IAbsoluteBox absoluteBox = (IAbsoluteBox) box;
		final AbsolutePos pos = absoluteBox.getAbsolutePos();
		final Flow flow = this.getFlow();
		final AbstractContainerBox contextBox = flow.box;
		double staticX = this.lineAxis - flow.lineAxis;
		double staticY = this.pageAxis - flow.pageAxis;
		if (pos.usesStaticPageAxis(flow.box.getBlockParams().flow)) {
			assert pos.autoPosition == AutoPosition.BLOCK : box.getParams();
			if (this.textBuilder != null) {
				staticY += this.textBuilder.getVirtualClosedPageAxis(this.pendingText);
			} else {
				staticY += new TextBuilder(this, this.breakToken).getVirtualClosedPageAxis(this.pendingText);
			}
		}
		if (box.getType() == BoxType.REPLACED) {
			// Converting the static position in RL vertical writing to physical coordinates requires
			// the box's page-direction size, so finalize it before passing it to the absolute registry.
			((AbstractReplacedBox) box).calculateFrame(contextBox.getLineSize());
		}
		contextBox.addAbsolute(absoluteBox, staticX, staticY);
	}

	/**
	 * Adds a visual rescue split remainder fragment to the flow
	 * (added 2026-07-25, increment 5; design consultation §5).
	 *
	 * <p>
	 * Does <b>not</b> disguise the BoxType and send it through ordinary
	 * {@link #addBound(net.zamasoft.foliojet.layout.box.IBox)}. Fragments are short-lived drawing decorators
	 * with neither {@code ReplacedParams} nor {@code AbsoluteRectFrame}, so that path always fails on a cast.
	 * </p>
	 *
	 * <p>
	 * Since a fragment is a geometric slice of an already laid-out original box, this only adds its
	 * occupied extent at the current page-direction cursor. Performs <b>no</b> margin recalculation,
	 * alignment recalculation, or exclusion-area avoidance:
	 * </p>
	 *
	 * <ul>
	 * <li>The original box determines its line-direction position from its own margins, so it always
	 * matches the first fragment.</li>
	 * <li>The top margin is part of the original box's geometry within the first fragment and is absent
	 * from continuation fragments (cut surfaces receive no decorations). Adding a margin here would
	 * therefore count it twice.</li>
	 * <li>The bottom margin is likewise part of the final fragment's geometry. Margin collapsing
	 * immediately after the fragment resumes from that internal bottom margin.</li>
	 * </ul>
	 *
	 * @param box remainder fragment
	 */
	public void addRescueBound(final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox box) {
		if (this.textSession != null) {
			this.textSession.abortToLegacy();
		}
		this.requireNoOpenTextBuilder("(no context)");
		final Flow flow = this.getFlow();
		final BlockParams params = flow.box.getBlockParams();
		// Do not collapse margins before the fragment (the cut surface has no decorations).
		this.poLastMargin = this.neLastMargin = 0;
		flow.box.addFlow(box, this.pageAxis - flow.pageAxis);
		this.pageAxis += box.getPageExtent(params.flow);
		flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		// After the fragment, resume collapsing from the original box's bottom margin
		// (included in the final fragment's geometry).
		final double bottomMargin = box.isLastFragment() ? box.sourceCollapsibleEndMargin(params.flow) : 0;
		this.poLastMargin = this.neLastMargin = bottomMargin;
	}

	/**
	 * Sets the position of a left float.
	 *
	 * @param box
	 */
	protected void addStartFloat(IFloatBox box) {
		// 1. The reference left/right edge of a floating box does not extend outside the containing box.
		// 2. A floating box following another floating box is placed either beside or below it.
		// 3. Left and right floating boxes do not overlap.
		// 4. A floating box's top edge does not extend outside the containing box.
		// 5. A floating box's top edge is no higher than the top edge of any preceding block box.
		// 6. A floating box's top edge is no higher than the top edge of a line box containing a preceding box.
		// 7. A floating box must not extend beyond the containing box's left/right edges unless it is outermost.
		// 8. A floating box must be as high as possible first, and as close to the edge as possible second.
		this.commitFloatPlacement(this.tryFloatPlacement(box, this.snapshotExclusions(), FloatSide.START));
	}

	/**
	 * Sets the position of a right float.
	 *
	 * @param box
	 */
	protected void addEndFloat(IFloatBox box) {
		this.commitFloatPlacement(this.tryFloatPlacement(box, this.snapshotExclusions(), FloatSide.END));
	}

	/**
	 * Searches for a new float's placement without side effects and returns a placement plan
	 * (2026-07-23, exclusion areas P1 increment 3: unifies the search loops duplicated in
	 * addStartFloat/addEndFloat; behavior unchanged). All inputs are measured physical values
	 * (the {@code exclusions} snapshot and current cursor). Changes no builder state;
	 * discarding the plan rolls back the attempt.
	 *
	 * <p>
	 * The "float cannot be higher than this" constraint (the top of the last placed float) comes
	 * from the final {@code exclusions} entry (the same value as the old code's final
	 * {@code floatings} entry; the snapshot preserves order).
	 * </p>
	 */
	final FloatPlacementDelta tryFloatPlacement(final IFloatBox box, final ExclusionSpace exclusions,
			final FloatSide side) {
		final WritingMode progression = this.getRootBox().getBlockParams().flow;
		final double lineWidth = box.getLineExtent(progression);
		final double pageWidth = box.getPageExtent(progression);
		double pageStart = this.pageAxis;
		if (this.textBuilder != null) {
			pageStart += this.textBuilder.getActualPageAxis();
		}
		final double lineStart0 = this.lineAxis;
		final double lineEnd0 = this.lineAxis + this.getFlowBox().getLineSize();
		double lineStart = lineStart0;
		double lineEnd = lineEnd0;
		if (!exclusions.isEmpty()) {
			final List<FloatExclusion> ascending = exclusions.ascendingByPageEnd();
			pageStart = Math.max(pageStart, ascending.get(ascending.size() - 1).pageSpan().start());

			final FloatPos pos = box.getFloatPos();
			for (;;) {
				final ExclusionSpace.FloatPlacementScan found = exclusions.scanFloatPlacementBand(pageStart,
						lineStart0, lineEnd0, pos.clear);
				pageStart = found.pageStart();
				lineStart = found.lineStart();
				lineEnd = found.lineEnd();
				final double width = lineEnd - lineStart;
				if (LayoutUtils.compare(width, lineWidth) >= 0) {
					// Enough width is available.
					break;
				}
				// If there is not enough space, move down one step and search again.
				if (found.startExclusion() == null && found.endExclusion() == null) {
					break;
				}
				if (found.endExclusion() == null) {
					pageStart = found.startExclusion().pageSpan().end();
				} else if (found.startExclusion() == null) {
					pageStart = found.endExclusion().pageSpan().end();
				} else {
					final double startPageEnd = found.startExclusion().pageSpan().end();
					final double endPageEnd = found.endExclusion().pageSpan().end();
					if (startPageEnd > endPageEnd) {
						pageStart = endPageEnd;
					} else {
						pageStart = startPageEnd;
					}
				}
			}
		}
		// Align END-side floats to the line end, but **never place them before line start (= outside the page)**
		// (2026-08-21, sweep seed 615921). CSS 2.2 §9.5.1 requires START-side floats not to extend
		// before the containing block's line-start edge. Apply the same lower bound to END-side
		// floats wider than the band. Screen browsers allow overflow toward line start
		// (accessible by scrolling), but content placed outside the paper has nowhere else
		// to appear. This is the same print-first decision as the columnInflated clamp
		// (AbstractStaticBlockBox). Applies only to floats wider than the band.
		// Move overflow painting of orthogonal vertical-writing floats toward the paper (2026-08-22, sweep seed
		// 1353935): A vertical-writing float in a horizontal-writing containing block can paint beyond its box width
		// with overflow:visible along the inner page axis (= outer line axis). RL overflows physically left,
		// LR physically right. Shift the float inward only by the excess that would fall outside the paper
		// (the same print-first decision as the END-side clamp).
		final double paintedOverflow;
		final WritingMode innerFlow;
		if (progression == WritingMode.TB && box instanceof AbstractContainerBox innerBox
				&& innerBox.getBlockParams().flow.isVertical()) {
			innerFlow = innerBox.getBlockParams().flow;
			paintedOverflow = Math.max(0,
					box.paintedPageExtent(innerFlow) - box.getPageExtent(innerFlow));
		} else {
			innerFlow = progression;
			paintedOverflow = 0;
		}
		final double lineOffset;
		if (side == FloatSide.START) {
			if (innerFlow == WritingMode.RL && paintedOverflow > 0) {
				lineOffset = Math.min(lineStart + paintedOverflow,
						Math.max(lineStart, lineEnd - lineWidth));
			} else {
				lineOffset = lineStart;
			}
		} else if (innerFlow == WritingMode.LR && paintedOverflow > 0) {
			lineOffset = Math.max(lineStart, lineEnd - lineWidth - paintedOverflow);
		} else {
			lineOffset = Math.max(lineStart, lineEnd - lineWidth);
		}
		final FloatCommitKind kind = this.classifyFloatPlacement(box, pageStart);
		return new FloatPlacementDelta(box, side, new AxisSpan(lineOffset, lineOffset + lineWidth),
				new AxisSpan(pageStart, pageStart + pageWidth), kind);
	}

	/**
	 * Applies a placement plan to layout state (2026-07-23, exclusion areas P1 increment 3).
	 * Side effects retain the old code's order: record breakFloats → add to container (update serial) →
	 * exclusion registry (+hidden registry, stable FLOAT_COMP sort) → extend the parent box's page extent.
	 */
	final void commitFloatPlacement(final FloatPlacementDelta delta) {
		if (DebugFlags.BREAK_TRACE) {
			System.err.println("[float-commit] kind=" + delta.kind() + " el=" + delta.box().getParams().element
					+ " pageSpan=" + delta.pageSpan().start() + ".." + delta.pageSpan().end() + " limit="
					+ (this instanceof BreakableBuilder bb ? bb.getPageLimit() : Double.NaN));
		}
		if (delta.kind() != FloatCommitKind.PLACED) {
			this.recordBreakFloat(delta.side());
		}
		// Placement
		final double lineOffset = delta.lineSpan().start();
		final double pageStart = delta.pageSpan().start();
		final Flow flow = this.getFlow();
		final boolean forceMoveFromBottomBand = delta.kind() == FloatCommitKind.MOVE_TO_NEXT
				&& this instanceof RootBuilder root && root.hasTwoDimensionalBottomFloatLimit();
		if (forceMoveFromBottomBand
				&& flow.box.getContainer() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer container) {
			container.addFloating(delta.box(), lineOffset - flow.lineAxis, pageStart - flow.pageAxis, true);
		} else {
			flow.box.addFloating(delta.box(), lineOffset - flow.lineAxis, pageStart - flow.pageAxis);
		}
		if (delta.kind() == FloatCommitKind.MOVE_BY_CLEAR) {
			// Deferring clear preserves the current root-only extent rule (unlike ordinary extendParents,
			// it does not update parent extents while nested).
			// codex design: Do not silently normalize this asymmetry in P1.
			if (this.flowStack == null || this.flowStack.isEmpty()) {
				this.getRootBox().setPageAxis(delta.pageSpan().end());
			}
			return;
		}
		if (delta.kind() != FloatCommitKind.MOVE_TO_NEXT) {
			final WritingMode progression = this.getRootBox().getBlockParams().flow;
			this.addFloating(new LayoutContext.Floating(delta.box(), lineOffset, pageStart, progression));
		}
		// 2026-09-04: Report the atomic floor only from this single point where placement is finalized.
		if ((delta.kind() == FloatCommitKind.PLACED || delta.kind() == FloatCommitKind.SPLIT_AT_BREAK)
				&& this instanceof RootBuilder root) {
			final WritingMode ownerFlow = flow.box.getBlockParams().flow;
			final boolean first = root.isFloatAtFragmentStart(pageStart);
			final BoxType boxType = delta.box().getType();
			final net.zamasoft.foliojet.layout.box.params.PageBreakMode pageBreakInside = boxType == BoxType.BLOCK
					? ((AbstractContainerBox) delta.box()).getBlockParams().pageBreakInside
					: null;
			if (FloatMeasurement.isUnsplittable(boxType,
					FloatMeasurement.sameWritingAxis(ownerFlow, delta.box()), pageBreakInside, first)) {
				root.reportAtomicFloatPlacement(delta.box(), ownerFlow, pageStart);
			}
		}
		// Extend ancestor box widths.
		this.extendParents(pageStart, delta.pageSpan().extent());
	}

	private void extendParents(final double pageStart, final double pageWidth) {
		// Extend ancestor box widths for floating boxes.
		Flow contextFlow = this.getSubContextFlow();
		double pageAxis = pageStart + pageWidth;
		int i;
		if (this.flowStack != null) {
			i = this.flowStack.size() - 1;
			for (; i >= 0; --i) {
				contextFlow = (Flow) this.flowStack.get(i);
				final BlockParams params = contextFlow.box.getBlockParams();
				if (this.independentFloatScopeOwners != null
						&& this.independentFloatScopeOwners.contains(contextFlow.box)) {
					contextFlow.box.setPageAxis(pageAxis - contextFlow.pageAxis);
					// Propagate only the specified dimensions of overflow:hidden or the finalized inner
					// extent of a BFC created by a writing-mode change to ancestors.
					pageAxis = contextFlow.box.getInnerPageExtent(params.flow) + contextFlow.pageAxis;
				}
			}
		} else {
			i = -1;
		}
		if (i == -1) {
			AbstractContainerBox rootBox = this.getRootBox();
			rootBox.setPageAxis(pageAxis);
		}
	}

	/**
	 * Attempts to place a line-end float encountered mid-line on the current line (2026-08-08).
	 * Of CSS 2.1 §9.5's rule to place a mid-line float at the current line box's top and narrow the line
	 * if it fits, implements only the line-end side, which does not require moving already placed content.
	 * The line-start side requires relaying out existing content, so it remains deferred until line end.
	 *
	 * @return true if placed; if false, the caller uses the existing deferral path
	 */
	private boolean tryFloatOnCurrentLine(final IFloatBox box) {
		final FloatPos pos = box.getFloatPos();
		if (pos.floating != FloatSide.END) {
			return false;
		}
		// The exclusion-band scan (scanFloatPlacementBand) resolves clear as is.
		// If clear requires a position below the current line's top, the pageStart check
		// falls back to deferral (clear:left in 021-RIGHT_clear, matching Chrome observations).
		final WritingMode progression = this.getRootBox().getBlockParams().flow;
		final double lineWidth = box.getLineExtent(progression);
		final double pageWidth = box.getPageExtent(progression);
		// Top of the current line (excludes the open line's height).
		final double lineTop = this.pageAxis + this.textBuilder.getPageAxis();
		final double lineStart0 = this.lineAxis;
		final double lineEnd0 = this.lineAxis + this.getFlowBox().getLineSize();
		double lineEnd = lineEnd0;
		final ExclusionSpace exclusions = this.snapshotExclusions();
		if (!exclusions.isEmpty()) {
			// If the current line's top violates the constraint not to place above the top
			// of preceding floats, same-line placement is impossible.
			final List<FloatExclusion> ascending = exclusions.ascendingByPageEnd();
			if (LayoutUtils.compare(ascending.get(ascending.size() - 1).pageSpan().start(), lineTop) > 0) {
				return false;
			}
			final ExclusionSpace.FloatPlacementScan found = exclusions.scanFloatPlacementBand(lineTop, lineStart0,
					lineEnd0, pos.clear);
			if (LayoutUtils.compare(found.pageStart(), lineTop) != 0) {
				return false;
			}
			lineEnd = found.lineEnd();
		}
		final double lineOffset = lineEnd - lineWidth;
		// 2026-09-04: Classify first to avoid narrowing a MOVE_TO_NEXT line and leaving unused space behind.
		final FloatCommitKind kind = this.classifyFloatPlacement(box, lineTop);
		if (kind == FloatCommitKind.PLACED || kind == FloatCommitKind.SPLIT_AT_BREAK) {
			// Only when actually retaining it in the current fragment, narrow the line up to
			// its existing content (textIndent + finalized and pending advances).
			if (!this.textBuilder.narrowCurrentLine(lineOffset)) {
				return false;
			}
		}
		this.commitFloatPlacement(new FloatPlacementDelta(box, FloatSide.END,
				new AxisSpan(lineOffset, lineOffset + lineWidth), new AxisSpan(lineTop, lineTop + pageWidth),
				kind));
		if (this.floatings != null) {
			// Restore ascending bottom-edge order in the registry (as in addFloating(IFloatBox);
			// the current line's top can be above existing floats' bottom edges).
			Collections.sort(this.floatings, FLOAT_COMP);
		}
		return true;
	}

	/**
	 * Schedules a float for addition.
	 *
	 * @param box
	 */
	private void toAddFloating(IFloatBox box) {
		if (this.toAddFloatings == null) {
			this.toAddFloatings = new ArrayList<IFloatBox>();
		}
		this.toAddFloatings.add(box);
		if (this.textBuilder == null) {
			this.checkFloatings();
		}
	}

	/**
	 * Adds any scheduled floats.
	 */
	void checkFloatings() {
		if (this.toAddFloatings == null || this.toAddFloatings.isEmpty()) {
			return;
		}
		for (Iterator<IFloatBox> i = this.toAddFloatings.iterator(); i.hasNext();) {
			IFloatBox box = (IFloatBox) i.next();
			i.remove();
			this.addFloating(box);
		}
	}

	private void addFloating(IFloatBox box) {
		if (DebugFlags.FLOAT_TRACE) {
			System.err.println("[float] 配置 side=" + box.getFloatPos().floating + " box="
					+ System.identityHashCode(box) + " builder=" + System.identityHashCode(this));
		}
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("Add float: " + box.getParams().element + "/" + this.getFlow().box.getParams().element);
		}
		FloatPos pos = box.getFloatPos();
		switch (pos.floating) {
		case FloatSide.START:
			this.addStartFloat(box);
			break;
		case FloatSide.END:
			this.addEndFloat(box);
			break;
		default:
			throw new IllegalStateException();
		}
		if (this.floatings != null) {
			// Sort bottom edges starting from the bottom.
			// This sort must be stable.
			Collections.sort(this.floatings, FLOAT_COMP);
			this.noteFloatingsChanged();
		}
	}

	private void addFloating(LayoutContext.Floating floating) {
		if (this.floatings == null) {
			this.floatings = new ArrayList<Floating>();
		}
		this.floatings.add(floating);
		this.noteFloatingsChanged();
		if (this.noOverflowFloatings != null && !this.noOverflowFloatings.isEmpty()) {
			List<Floating> floatings = (List<Floating>) this.noOverflowFloatings
					.get(this.noOverflowFloatings.size() - 1);
			floatings.add(floating);
		}
	}

	/**
	 * Rebuilds the overflow:hidden float registries ({@link #noOverflowFloatings}) from active hidden
	 * flows on the current flowStack after crossing a fragment boundary (page/column break)
	 * (2026-07-23, exclusion areas P1 increment 1).
	 *
	 * <p>
	 * Previously, {@code resetFragmentCursor()} reset only {@code floatings} to null without touching
	 * these registries. Thus, on PAGE breaks (flowStack.clear() followed by resumed execution that
	 * pushes registries again for hidden flows), the old fragment's scope entries remained without
	 * being popped. This did not affect layout results (pop always took the newest last entry,
	 * and removeAll did nothing), but old Floating/IFloatBox references could not be garbage-collected
	 * until document end, at a cost of page count × hidden depth × float count (noted in codex review).
	 * </p>
	 *
	 * <p>
	 * For PAGE (called immediately after {@code flowStack.clear()}), the result is null: resumed hidden
	 * flows push new registries in {@code startFlowBlock()}. For COLUMN (called immediately after
	 * {@code pruneFlowStackTo()} + {@code resetFragmentCursor()}), pushes an empty registry for each
	 * retained hidden flow. Those flows do not reenter {@code startFlowBlock()}, so failing to repush
	 * would break the pop at completion. In both cases, {@code floatings} itself is already reset
	 * (checked by an assertion), so old floats need not be inherited; empty registries suffice
	 * (no owner assignment needed).
	 * </p>
	 */
	protected final void rebuildNoOverflowFloatingScopes() {
		assert this.floatings == null;
		this.noOverflowFloatings = null;
		this.independentFloatScopeOwners = null;
		if (this.flowStack == null) {
			return;
		}
		BlockParams parentParams = this.contextFlow.box.getBlockParams();
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			final FlowBlockBox flowBox = (FlowBlockBox) flow.box;
			if (establishesIndependentFloatScope(flowBox, parentParams)) {
				if (this.noOverflowFloatings == null) {
					this.noOverflowFloatings = new ArrayList<List<Floating>>();
					this.independentFloatScopeOwners = new ArrayList<AbstractContainerBox>();
				}
				this.noOverflowFloatings.add(new ArrayList<Floating>());
				this.independentFloatScopeOwners.add(flowBox);
			}
			parentParams = flowBox.getBlockParams();
		}
	}

	/**
	 * Margins of boxes establishing independent formatting contexts do not collapse with their content's
	 * margins (CSS 2.1 §8.3.1, css-flexbox-1 §3, css-grid-1 §3).
	 *
	 * <ul>
	 * <li>flex/grid (2026-10-04, TECH-20261003-004 ⑰): FlexBuilder/GridBuilder place their content directly.
	 * Leaving the top margin pending for collapse with children at opening caused it to be mistaken for
	 * the last child's margin at closing and collapsed with the bottom margin, reducing the bottom
	 * margin to "bottom − top" (0 if both were equal).</li>
	 * <li>Non-visible overflow, flow-root, and multi-column layout (2026-10-04, user decision):
	 * top margins previously collapsed with the first child's margin (Chrome does not collapse them).
	 * Bottom margins already did not collapse (except for flow-root).</li>
	 * </ul>
	 */
	private static boolean sealsMargins(final FlowBlockBox flowBox) {
		final BlockParams params = flowBox.getBlockParams();
		return flowBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.GridBox
				|| params.overflow != OverflowMode.VISIBLE || params.flowRoot || flowBox.getColumnCount() > 1;
	}

	/**
	 * Whether the box narrows to avoid floats (CSS 2.1 §9.5: the border box of a box establishing an
	 * independent formatting context does not overlap a float's margin box). Avoids both normal floats
	 * and page floats (flow-root and non-visible overflow boxes added 2026-10-05).
	 */
	private static boolean avoidsFloats(final FlowBlockBox flowBox) {
		final BlockParams params = flowBox.getBlockParams();
		return flowBox.getColumnCount() > 1 || flowBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.GridBox || params.flowRoot
				|| params.overflow != OverflowMode.VISIBLE;
	}

	/** Sizes the box in line-direction {@code band}, subtracting insets (the band start is the box's line-direction position). */
	private void calculateSizeIn(final FlowBlockBox flowBox, final AxisSpan band, final double insetStart,
			final double insetEnd) {
		this.calculateSizeIn(flowBox, band.start() - this.lineAxis, band.extent(), insetStart, insetEnd);
	}

	/** Sizes the box at line-direction position {@code xmargin} and length {@code lineSize}, subtracting insets. */
	private void calculateSizeIn(final FlowBlockBox flowBox, double xmargin, double lineSize, final double insetStart,
			final double insetEnd) {
		if (insetStart != 0 || insetEnd != 0) {
			final double inset = insetStart + insetEnd;
			if (inset < lineSize) {
				xmargin += insetStart;
				lineSize -= inset;
			}
		}
		flowBox.calculateSize(this, xmargin, lineSize);
	}

	/** Whether {@code band} is narrower than {@code full} (narrowed by floats). */
	private static boolean narrower(final AxisSpan band, final AxisSpan full) {
		return LayoutUtils.compare(band.start(), full.start()) != 0 || LayoutUtils.compare(band.end(), full.end()) != 0;
	}

	/**
	 * Whether a box placed in {@code band} fits beside floats. A box with a specified line-direction size
	 * fits if that size plus frame and margins fits the band. An auto-sized box fills the band, so assume
	 * it fits if its content width can accommodate one character (Chrome uses the content's min-content
	 * size, but this is unknown before content flows in).
	 */
	private boolean fitsBeside(final FlowBlockBox flowBox, final BlockParams cParams, final AxisSpan band) {
		if (flowBox.getBlockParams().size.getLineType(cParams.flow) != LengthType.AUTO) {
			return LayoutUtils.compare(this.placedLineExtent(flowBox, cParams, band), band.extent()) <= 0;
		}
		return LayoutUtils.compare(flowBox.getInnerLineExtent(cParams.flow),
				flowBox.getBlockParams().fontStyle.getSize()) >= 0;
	}

	/**
	 * Line-direction space needed by a box placed in {@code band} (border box plus positive line-direction
	 * margins). {@code calculateSize} has added the band's position to the start margin, so exclude it.
	 */
	private double placedLineExtent(final FlowBlockBox flowBox, final BlockParams cParams, final AxisSpan band) {
		final AbsoluteRectFrame frame = flowBox.getFrame();
		final boolean vertical = cParams.flow.isVertical();
		final double offset = band.start() - this.lineAxis;
		return flowBox.getInnerLineExtent(cParams.flow) + frame.getBorderLineExtent(cParams.flow)
				+ Math.max(0, (vertical ? frame.margin.top : frame.margin.left) - offset)
				+ Math.max(0, vertical ? frame.margin.bottom : frame.margin.right);
	}

	/** Frame start of the box about to open, after collapsing with the preceding margin (same calculation as below). */
	private double collapsedBorderStart(final AbsoluteRectFrame frame, final BlockParams cParams) {
		final double marginStart = cParams.flow.isVertical() ? frame.margin.right : frame.margin.top;
		double start = this.pageAxis;
		if (marginStart >= 0) {
			start -= marginStart > this.poLastMargin ? this.poLastMargin : marginStart;
		} else {
			start -= marginStart < this.neLastMargin ? this.neLastMargin : marginStart;
		}
		return start + marginStart;
	}

	/** Float boundary of an independent BFC under CSS Writing Modes 3 §3.2 and overflow. */
	private static boolean establishesIndependentFloatScope(final FlowBlockBox flowBox,
			final BlockParams parentParams) {
		final BlockParams params = flowBox.getBlockParams();
		// display:flow-root creates an independent BFC (2026-08-29). Treat like overflow:hidden.
		return params.overflow == OverflowMode.HIDDEN || params.flowRoot
				|| (params.flow.isVertical() == parentParams.flow.isVertical()
						&& params.flow != parentParams.flow);
	}

	public void addTable(final net.zamasoft.foliojet.layout.builder.RetainedTable tableBuilder) {
		tableBuilder.prepareLayout();
		tableBuilder.bind(this);
	}

	public void addGrid(final net.zamasoft.foliojet.layout.builder.RetainedGrid gridBuilder) {
		// Grid G3d1: Bind immediately in normal flow (track resolution → item bind → placement).
		gridBuilder.bind(this);
	}

	public void addFlex(final net.zamasoft.foliojet.layout.builder.RetainedFlex flexBuilder) {
		// Flex F1f: Bind immediately in normal flow (§9.7 resolution → item bind → row placement).
		flexBuilder.bind(this);
	}

	public Builder newBuilder(AbstractBlockBox blockBox) {
		final Builder builder;
		AbstractContainerBox containerBox;
		switch (blockBox.getPos().getType()) {
		case FLOW:
		case TABLE_CAPTION:
			if (blockBox.isFixedMulticolumn()) {
				final FlowBlockBox flowBox = (FlowBlockBox) blockBox;
				containerBox = this.getFlowBox();
				flowBox.calculateSize(this, 0, containerBox.getLineSize());
				final BlockBuilder columns = new ColumnBuilder(this, blockBox);
				if (retainsFlowContent(blockBox)) columns.beginRetainedContext(blockBox);
				return columns;
			}
			// Flow (when page progression directions differ)
		case FLOAT:
		case INLINE: {
			// Float
			// Inline placement
			final AbstractStaticBlockBox staticBlockBox = (AbstractStaticBlockBox) blockBox;
			containerBox = this.getFlowBox();
			if (!LayoutUtils.needsIntrinsicSizing(blockBox)) {
				// Fixed width
				staticBlockBox.shrinkToFit(this, IntrinsicSizes.ZERO, false);
				if (blockBox.isFixedMulticolumn()) {
					// Multi-column layout with a fixed page-direction size
					builder = new ColumnBuilder(this, blockBox);
				} else {
					builder = new BlockBuilder(this, blockBox);
				}
			} else {
				// STF
				blockBox.firstPassLayout(containerBox);
				builder = new TwoPassBlockBuilder(this, blockBox);
			}
		}
			break;

		case ABSOLUTE: {
			final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) blockBox;
			if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				// Fixed positioning
				containerBox = this.getPageContext().getRootBox();
			} else {
				// Absolute positioning
				containerBox = this.getContextBox();
			}
			if (!LayoutUtils.needsIntrinsicSizing(blockBox)) {
				// Fixed width
				absoluteBox.shrinkToFit(containerBox, IntrinsicSizes.ZERO);

				// Height is finalized at the end, so reflow even when multi-column height is explicitly specified.
				builder = new BlockBuilder(this, blockBox);
			} else {
				// STF
				absoluteBox.firstPassLayout(containerBox);
				builder = new TwoPassBlockBuilder(this, blockBox);
			}
		}
			break;

		default:
			throw new IllegalStateException();
		}
		if (builder instanceof BlockBuilder retained) {
			retained.beginRetainedContext(blockBox);
		}
		return builder;
	}

	/** Passes the start after margin collapse to the parent for initial incomplete-table placement and remainder placement. */
	protected void incompleteTablePlaced(final TableBox tableBox, final double pageStart) {
	}

	public void finish() {
		try (var retained = this.retainedRoot) {
			this.finishContent();
		} finally {
			this.retainedRoot = null;
		}
	}

	private void finishContent() {
		assert this.flowStack == null || this.flowStack.isEmpty();
		this.requireNoOpenTextBuilder("(no context)");

		final AbstractContainerBox flowBox = (AbstractContainerBox) this.contextFlow.box;
		final BlockParams params = flowBox.getBlockParams();
		if (flowBox.getColumnCount() > 1 && params.columns.fill == Columns.FILL_BALANCE) {
			// Column balancing
			this.pageAxis = this.contextFlow.pageAxis;
			flowBox.balance(this);
		}
	}

	public void close() {
		this.finish();
	}

	public void setBreakToken(BreakToken breakToken) {
		this.breakToken = this.breakToken.combine(breakToken);
	}

	protected void requireTextBlock() {
		// New text block
		this.requireNoOpenTextBuilder("(no context)");
		// textSession remains while page-break handling during replay creates a new TextBuilder
		// (to clamp the delivery boundary), so check only that it is not recording.
		assert this.textSession == null || !this.textSession.recording();
		final BreakToken breakToken = this.breakToken;
		this.textBuilder = new TextBuilder(this, breakToken);
		this.breakToken = BreakToken.MID_FLOW;

		final Flow flow = this.getFlow();
		double localPageAxis = this.pageAxis - flow.pageAxis;
		flow.box.addFlow(this.textBuilder.textBlockBox, localPageAxis);

		// M3c: Start a K-P accumulation session for an eligible paragraph
		// only when opted in (text-wrap-style: pretty).
		if (this.textSession == null && this.optimizedTextEnabled()) {
			this.textSession = TotalFitSession.tryBegin(this, this.textBuilder, breakToken);
		}
	}

	/**
	 * Returns the delivery boundary of source characters that have physically reached TextBuilder (M3c).
	 * When a K-P session is accumulating or replaying, clamps to the first undelivered event's source
	 * position to prevent duplicate delivery from split-paragraph tail replay and the session's remaining
	 * events. Returns unchanged if no session exists.
	 */
	final int clampDeliveredCharEnd(final int deliveredCharEnd) {
		final TotalFitSession session = this.textSession;
		if (session == null) {
			return deliveredCharEnd;
		}
		return session.clampDeliveredCharEnd(deliveredCharEnd);
	}

	/**
	 * Returns whether this context can start a Knuth-Plass line-breaking accumulation session (M3c).
	 * Opt-in itself depends on the paragraph's computed value ({@code text-wrap-style: pretty}) and is
	 * checked by {@link TotalFitSession#tryBegin} (2026-07-25, unified under CSS instead of the proprietary
	 * {@code text.line-breaker} property). This checks only context conditions: conservatively disables
	 * the session during break-remainder reconstruction (restyle) to avoid interference with resumption
	 * mechanisms such as split-paragraph tail replay.
	 */
	private boolean optimizedTextEnabled() {
		final RootBuilder root;
		if (this instanceof RootBuilder r) {
			root = r;
		} else if (this.layoutStack != null) {
			root = this.getPageContext();
		} else {
			// Relayout builder without page context
			return false;
		}
		if (root == null || root.isRestyling()) {
			return false;
		}
		if (this instanceof BreakableBuilder breakable && breakable.isRestyling()) {
			return false;
		}
		return true;
	}

	/**
	 * Returns the open text run's font ({@code null} if absent). Used to restore the font when a
	 * {@link TextBuilder} recreated mid-stream could not inherit it (2026-08-17; the same value
	 * {@link #glyph} uses for lazy creation).
	 */
	FontStyle getOpenRunFontStyle() {
		return this.openRunFontStyle;
	}

	/** @see #getOpenRunFontStyle() */
	FontMetrics getOpenRunFontMetrics() {
		return this.openRunFontMetrics;
	}

	public void startTextRun(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		this.openRunFontStyle = fontStyle;
		this.openRunFontMetrics = fontMetrics;
		if (this.textBuilder == null) {
			this.requireTextBlock();
		}
		if (this.textSession != null && this.textSession.recordRun(fontStyle, fontMetrics)) {
			return;
		}
		this.textBuilder.startTextRun(fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		if (this.textSession != null && this.textSession.recordGlyph(charOffset, ch, coff, clen, gid)) {
			return;
		}
		if (this.textBuilder == null) {
			// Fragmentation between lines during flush() may close only TextBuilder while
			// the shaper's text run continues. Create a continuation block only when
			// the next glyph actually arrives (do not synthesize an empty
			// TextBuilder at the end).
			if (this.openRunFontStyle == null || this.openRunFontMetrics == null) {
				throw new IllegalStateException("glyph outside a text run");
			}
			this.requireTextBlock();
			this.textBuilder.startTextRun(this.openRunFontStyle, this.openRunFontMetrics);
		}
		this.textBuilder.glyph(charOffset, ch, coff, clen, gid);
	}

	public void endTextRun() {
		try {
			if (this.textSession != null && this.textSession.recordRunEnd()) {
				return;
			}
			// If fragmentation occurs just before run end and no glyph follows,
			// there is no need to recreate TextBuilder.
			if (this.textBuilder != null) {
				this.textBuilder.endTextRun();
			}
		} finally {
			this.openRunFontStyle = null;
			this.openRunFontMetrics = null;
		}
	}

	public void control(final TextControl quad) {
		if (quad instanceof InlineQuad) {
			// Inline box
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				// Inline start
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				inlineStartQuad.box.fixLineAxis(this.getFlowBox());
			}
				break;

			case InlineQuad.INLINE_END:
			case InlineQuad.INLINE_BLOCK:
				break;

			case InlineQuad.INLINE_ABSOLUTE:
				final InlineAbsoluteQuad inlineAbsoluteQuad = (InlineAbsoluteQuad) inlineQuad;
				if (inlineAbsoluteQuad.box.getType() == BoxType.REPLACED) {
					LayoutUtils.calculateReplacedSize(this, (AbstractReplacedBox) inlineAbsoluteQuad.box);
				}
				break;

			case InlineQuad.INLINE_REPLACED: {
				// Replaced inline
				final InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
				LayoutUtils.calculateReplacedSize(this, inlineReplacedQuad.box);
			}
				break;

			default:
				throw new IllegalStateException();
			}
		}
		if (this.textBuilder == null) {
			this.requireTextBlock();
		}
		if (this.textSession != null && this.textSession.recordControl(quad)) {
			return;
		}
		this.textBuilder.control(quad);
	}

	public void flush() {
		if (this.textSession != null && this.textSession.recordFlush()) {
			return;
		}
		// flush does nothing for an empty text block (textBuilder was never created).
		// The same null guard as endTextBlock(); this actually occurs during
		// cell-range bind in E-6 increment 5a:
		// when a cell contains only a soft hyphen (U+00AD), StyledTextUnitizer creates
		// a textShaper, but WordHyphenator silently drops the Marker
		// (fontMetrics is unset with hyphens:manual). Neither glyph nor control reaches
		// the builder, and the shaper's close chain calls only flush()
		// (2026-07-24, NullPointerException in 040-8BITS_ASCII.html).
		// Live-path cells are recorded by TwoPassBlockBuilder (independent of textBuilder),
		// so this empty flush is reached only during range replay at bind time.
		if (this.textBuilder == null) {
			return;
		}
		while (this.textBuilder.flush())
			;
	}

	public void endTextBlock() {
		this.endTextBlock(false);
	}

	/**
	 * Closes the text block.
	 *
	 * @param fragmentBreak {@code true} when text continues into a subsequent fragment because the
	 *                      fragment's capacity was exceeded, rather than because body content ended
	 */
	protected final void endTextBlock(final boolean fragmentBreak) {
		// End the text block. If content is empty (control() was never called and
		// requireTextBlock() did not create textBuilder), it may still be null
		// when reaching here (2026-07-18, an actual NullPointerException
		// in an empty table cell). Handle this with a null guard
		// as in flush().
		if (this.textBuilder != null) {
			if (this.textSession != null) {
				// M3c: Choose breakpoints for the accumulated content and replay it (verbatim replay
				// identical to legacy if ineligible). Do nothing on reentry during replay.
				this.textSession.finishSession();
			}
			this.textBuilder.finish(fragmentBreak);
			this.pageAxis += this.textBuilder.getFlowPageAdvance();
			this.textBuilder = null;
		}
		final Flow flow = this.getFlow();
		flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
	}

	/**
	 * Classifies a new float's placement commit without side effects (2026-07-23, exclusion areas
	 * P1 increment 2: splits the former {@code transferFloatToNextPage} into pure classification and
	 * {@link #recordBreakFloat}). The base implementation always returns {@link FloatCommitKind#PLACED}
	 * (builders without page-break context do not defer floats).
	 */
	FloatCommitKind classifyFloatPlacement(IFloatBox box, double pageStart) {
		return FloatCommitKind.PLACED;
	}

	/**
	 * Hook for recording a float crossing a fragment boundary (2026-07-23, exclusion areas P1 increment 2).
	 * The base implementation does nothing. {@code BreakableBuilder} implements this by adding to
	 * {@code breakFloats}.
	 */
	void recordBreakFloat(FloatSide side) {
	}
}
