package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.PagePos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * A page.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: PageBox.java 1561 2018-07-04 11:44:21Z miyabe $
 */
public class PageBox extends AbstractBlockBox {
	protected final UserAgent ua;

	/**
	 * The bleed width (2026-09-02). Draw the {@code @page} background this far beyond the trim line
	 * instead of stopping there, to avoid white edges after trimming.
	 * Supplied by {@code PageSequence} when creating the page.
	 */
	private double bleed = 0;

	public void setBleed(final double bleed) {
		this.bleed = Math.max(0, bleed);
	}

	/** The {@code @page} background, drawn across the entire sheet separately from the normal frame (canvas) background. */
	private final Background pageBackground;

	/**
	 * A fixed-position block.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: PageBox.java 1561 2018-07-04 11:44:21Z miyabe $
	 */
	protected static class Fixed {
		public final IAbsoluteBox box;
		public final double x, y;

		public Fixed(IAbsoluteBox box, double x, double y) {
			this.box = box;
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * Fixed-position content.
	 */
	protected List<Fixed> fixeds = null;

	protected List<Fixed> toAddFixeds = null;

	/**
	 * The visual size.
	 */
	protected double visualWidth = 0, visualHeight = 0;

	/** The bottom footnote band. Preserves sheet margins and outer size, reducing only the body text's inner size. */
	private double footInset = 0;
	private double footAreaPageHeight, footAreaVisualHeight;

	/** Called once at page start, before determining child dimensions. */
	public void reserveFootArea(final double inset) {
		assert this.footInset == 0;
		if (inset == 0) {
			return;
		}
		assert inset > 0 && inset <= this.height;
		// Save the original outer size rather than adding back the subtracted amount, avoiding rounding errors too.
		this.footAreaPageHeight = super.getHeight();
		this.footAreaVisualHeight = this.getVisualHeight();
		this.footInset = inset;
		this.height -= inset;
		this.visualHeight -= inset;
	}

	public double getFootInset() {
		return this.footInset;
	}

	/**
	 * The top footnote band (headnotes, 2026-09-11).
	 *
	 * <p>
	 * A bottom band needs only to <b>reduce the type area's height</b>: body text still starts
	 * at the top but becomes shorter, and notes can sit below the reduced area.
	 * A top band must additionally <b>lower the body text start by the band size</b>.
	 * </p>
	 *
	 * <p>
	 * Do this by <b>increasing the type area's padding-top</b>. {@code getFrameTop()} is the content
	 * origin's position, so body text lines now start below the band. Reduce the inner size by
	 * the same amount; {@code getHeight()} ({@code = height + frame.getFrameHeight()})
	 * and {@code getVisualHeight()} therefore <b>remain unchanged</b>. Sheet outer size and margins
	 * are preserved, with no need to save the original size as for the bottom band.
	 * </p>
	 *
	 * <p>
	 * Place notes above the content origin, at <b>negative line-axis positions</b>
	 * ({@code RootBuilder} lays them out starting at {@code lineAxis = -inset}).
	 * This mirrors the bottom band's placement below the inner size, outside on the positive side.
	 * </p>
	 */
	public void reserveHeadArea(final double inset) {
		assert this.headInset == 0;
		if (inset == 0) {
			return;
		}
		assert inset > 0 && inset <= this.height;
		this.headInset = inset;
		this.height -= inset;
		this.visualHeight -= inset;
		this.frame.padding.top += inset;
	}

	/** The top footnote band. The body content origin has been lowered by this amount. */
	private double headInset = 0;

	public double getHeadInset() {
		return this.headInset;
	}

	@Override
	public final double getHeight() {
		if (this.footInset != 0) {
			return this.footAreaPageHeight;
		}
		return super.getHeight();
	}

	private boolean replayPage;
	private double replayX, replayY;

	/**
	 * The placement origin of an independent mini-page. Moves the fixed-position reference here too;
	 * does not clip by margins.
	 */
	public void setReplayOrigin(final double x, final double y) {
		this.replayPage = true;
		this.replayX = x;
		this.replayY = y;
	}

	public boolean isReplayPage() {
		return this.replayPage;
	}

	public PageBox(BlockParams params, UserAgent ua) {
		this(params, ua, Background.NULL_BACKGROUND);
	}

	public PageBox(BlockParams params, UserAgent ua, Container container) {
		this(params, ua, Background.NULL_BACKGROUND, container);
	}

	public PageBox(BlockParams params, UserAgent ua, Background pageBackground) {
		this(params, ua, pageBackground, new FlowContainer());
	}

	private PageBox(BlockParams params, UserAgent ua, Background pageBackground, Container container) {
		super(params, params.size, params.minSize, new AbsoluteRectFrame(params.frame), container);
		assert !this.size.getWidthType().needsReference();
		assert !this.size.getHeightType().needsReference();

		this.ua = ua;
		this.pageBackground = pageBackground == null ? Background.NULL_BACKGROUND : pageBackground;

		double lineWidth;
		switch (params.flow) {
		case WritingMode.TB:
			// Horizontal writing
			assert this.size.getWidthType() == LengthType.ABSOLUTE;
			lineWidth = this.size.getWidth();
			break;
		case WritingMode.LR:
		case WritingMode.RL:
			// Vertical writing
			assert this.size.getHeightType() == LengthType.ABSOLUTE;
			lineWidth = this.size.getHeight();
			break;
		default:
			throw new IllegalStateException();
		}

		RectFrame frame = this.frame.frame;
		{
			Insets insets = frame.margin;
			double top, right, bottom, left;
			switch (insets.getTopType()) {
			case ABSOLUTE:
				top = insets.getTop();
				break;
			case RELATIVE:
				top = insets.getTop() * lineWidth;
				break;
			case MIXED:
				top = insets.getTop() + insets.getTopRatio() * lineWidth;
				break;
			case AUTO:
				top = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getBottomType()) {
			case ABSOLUTE:
				bottom = insets.getBottom();
				break;
			case RELATIVE:
				bottom = insets.getBottom() * lineWidth;
				break;
			case MIXED:
				bottom = insets.getBottom() + insets.getBottomRatio() * lineWidth;
				break;
			case AUTO:
				bottom = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getLeftType()) {
			case ABSOLUTE:
				left = insets.getLeft();
				break;
			case RELATIVE:
				left = insets.getLeft() * lineWidth;
				break;
			case MIXED:
				left = insets.getLeft() + insets.getLeftRatio() * lineWidth;
				break;
			case AUTO:
				left = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (insets.getRightType()) {
			case ABSOLUTE:
				right = insets.getRight();
				break;
			case RELATIVE:
				right = insets.getRight() * lineWidth;
				break;
			case MIXED:
				right = insets.getRight() + insets.getRightRatio() * lineWidth;
				break;
			case AUTO:
				right = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			this.frame.margin.top = top;
			this.frame.margin.right = right;
			this.frame.margin.bottom = bottom;
			this.frame.margin.left = left;
			// Padding (`@page` padding, 2026-09-03). Resolve to absolute lengths by the same rules as margins
			// (previously left unresolved at 0, so only borders were drawn).
			final double[] padding = resolveInsets(frame.padding, lineWidth);
			this.frame.padding.top = padding[0];
			this.frame.padding.right = padding[1];
			this.frame.padding.bottom = padding[2];
			this.frame.padding.left = padding[3];
			if (this.size.getWidthType() == LengthType.ABSOLUTE) {
				this.visualWidth = this.width = this.size.getWidth() - this.frame.getFrameWidth();
			}
			if (this.size.getHeightType() == LengthType.ABSOLUTE) {
				this.visualHeight = this.height = this.size.getHeight() - this.frame.getFrameHeight();
			}
		}
	}

	public final BoxType getType() {
		return BoxType.PAGE;
	}

	public final Pos getPos() {
		return PagePos.POS;
	}

	public final UserAgent getUserAgent() {
		return this.ua;
	}

	/**
	 * Elements whose tagged-PDF structure element is currently open
	 * (identity-keyed: pseudo/anonymous singletons and non-StructureElement
	 * values).
	 */
	private final java.util.Set<Object> openStructElements = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());

	/**
	 * Real source elements (elementKey &gt;= 0) whose structure element is
	 * currently open, deduplicated by their logical identity (elementKey).
	 *
	 * <p>
	 * E-6 increment 4b (2026-07-24): In TwoPass range binding, live ancestor boxes
	 * (holding {@code CSSElement}) and replayed descendant boxes (holding {@code StructureToken})
	 * may refer to the same logical element: for example, a {@code <li>}'s principal box (live)
	 * and marker box (range replay). Previously, they were identified by sharing the same
	 * {@code CSSElement} instance. Reference identity cannot express sharing across replay boundaries,
	 * so real elements use {@code elementKey} (a document-order serial number = logical identity)
	 * to prevent duplicate opens. Live boxes for the same logical element always share the same
	 * instance (fragments share params), so this change does not affect live behavior.
	 * </p>
	 */
	private final java.util.Set<Long> openStructKeys = new java.util.HashSet<>();

	/**
	 * The declaration target for tagged-PDF structure (B-3, 2026-07-30).
	 * PageSequence.drawPage sets it before display-list construction.
	 * Remains null for untagged or non-PDF output (declare is not called and null references
	 * propagate, equivalent to the previous no-op).
	 */
	private net.zamasoft.pdfg2d.pdf.PDFPageOutput structOut = null;

	/**
	 * The cross-page structure-element registry (fix for defect ②, 2026-07-30).
	 * {@code PageSequence} holds it per document and passes it here for each page.
	 * Null for untagged output.
	 */
	private TaggedStructureContext structContext = null;

	/**
	 * The depth while drawing repeated content (repeated table headers/footers;
	 * fix for defect ②, 2026-07-30). While positive, {@link #beginStruct} bypasses the cross-page
	 * registry: repetition redisplays the same element rather than continuing it, so merging
	 * would duplicate the same content in one StructElem for every page.
	 * Keep independent per-page declarations as before (consider making repetitions artifacts
	 * in a separate increment).
	 */
	private int structRepetitionDepth = 0;

	/** Document-order structure nesting (a stack of declared refs). */
	private final java.util.ArrayDeque<net.zamasoft.pdfg2d.pdf.StructureRef> structStack = new java.util.ArrayDeque<>();

	public void setStructOutput(final net.zamasoft.pdfg2d.pdf.PDFPageOutput structOut,
			final TaggedStructureContext structContext) {
		this.structOut = structOut;
		this.structContext = structContext;
	}

	/** Begins a repeated-content section (repeated header/footer; from a worklist step). */
	public void pushStructRepetition() {
		++this.structRepetitionDepth;
	}

	/** The matching end for {@link #pushStructRepetition}. */
	public void popStructRepetition() {
		--this.structRepetitionDepth;
	}

	/** The current structure parent (stack top; empty/sentinel means null, directly under StructTreeRoot). */
	private net.zamasoft.pdfg2d.pdf.StructureRef structParent() {
		final var top = this.structStack.peek();
		return top == NULL_STRUCT ? null : top;
	}

	/** Wrap in a sentinel because structStack (ArrayDeque) cannot hold null. */
	private net.zamasoft.pdfg2d.pdf.StructureRef declareStruct(final String role, final String scope) {
		if (this.structOut == null) {
			return null;
		}
		return this.structOut.declareStructElement(this.structParent(), role, scope);
	}

	/** Sentinel because ArrayDeque disallows null entries (placeholder for untagged output). */
	private static final net.zamasoft.pdfg2d.pdf.StructureRef NULL_STRUCT = new net.zamasoft.pdfg2d.pdf.StructureRef() {
	};

	/** Marks the element's structure open; false when it already is. */
	private boolean openStruct(final Object element) {
		if (element instanceof net.zamasoft.foliojet.css.StructureElement se && se.elementKey() >= 0) {
			return this.openStructKeys.add(se.elementKey());
		}
		return this.openStructElements.add(element);
	}

	/** Clears the element's open mark ({@code openStruct}'s counterpart). */
	private void closeStruct(final Object element) {
		if (element instanceof net.zamasoft.foliojet.css.StructureElement se && se.elementKey() >= 0) {
			this.openStructKeys.remove(se.elementKey());
			return;
		}
		this.openStructElements.remove(element);
	}

	/**
	 * Inserts tagged-PDF structure begin markers for a box's element and
	 * returns how many were opened (0 when tagging is off, the element is not
	 * mappable, or the same element is already open — the outer box owns it).
	 * A list item additionally opens an {@code LBody} wrapper.
	 *
	 * @param drawer  the drawer to add markers to
	 * @param element the box's element
	 * @param x       the box x position
	 * @param y       the box y position
	 * @return the number of structure elements opened (to pass to
	 *         {@link #endStruct})
	 */
	public int beginStruct(final Drawer drawer, final Object element, final double x, final double y) {
		if (drawer.isArtifact()) {
			// 2026-07-25 (rescue splitting, increment 5, recommendation §3): An artifact drawer
			// (a continuation fragment of rescue splitting) is visually content but semantically belongs
			// to the head fragment. Only the head fragment opens structure elements once,
			// so bypass this here (elementKey dedup uses a separate set per page
			// and cannot suppress continuation fragments).
			return 0;
		}
		final String role = net.zamasoft.foliojet.ua.props.TaggedPdf.roleIfActive(this.ua, element);
		if (role == null || !this.openStruct(element)) {
			return 0;
		}
		// Fix for defect ② (2026-07-30): Continuation fragments reuse the StructureRef declared
		// at first occurrence, giving one StructElem per logical element (content MCIDs
		// span pages via pdfg2d's /Type /MCR /Pg). Repetitions
		// (structRepetitionDepth>0) and anonymous/pseudo-elements (elementKey<0) are excluded;
		// declare them per page as before.
		final long elementKey = this.structRepetitionDepth == 0 && this.structContext != null
				&& element instanceof net.zamasoft.foliojet.css.StructureElement se ? se.elementKey() : -1;
		final String scope = role.equals("TH") ? net.zamasoft.foliojet.ua.props.TaggedPdf.headerScope(element) : null;
		if (elementKey >= 0) {
			final TaggedStructureContext.Binding binding = this.structContext.lookup(elementKey);
			if (binding != null) {
				if (java.util.Objects.equals(binding.role(), role)
						&& java.util.Objects.equals(binding.scope(), scope)
						&& binding.parent() == this.structParent()) {
					// Continuation: Append content to the existing ref without redeclaring.
					for (final var ref : binding.refs()) {
						this.structStack.push(ref == null ? NULL_STRUCT : ref);
					}
					drawer.setCurrentStructRef(binding.contentRef());
					return binding.refs().length;
				}
				// A role/scope/parent mismatch: Redeclaring a changed structure repeats defect ②
				// and should be an invariant violation. However, the absolute no-crash requirement
				// takes precedence, so warn and fall back to a new declaration as before (old behavior).
				java.util.logging.Logger.getLogger(PageBox.class.getName())
						.warning("tagged-PDF continuation mismatch for elementKey=" + elementKey + ": declared=("
								+ binding.role() + "," + binding.scope() + ") now=(" + role + "," + scope
								+ "); falling back to a fresh StructElem (element split across pages)");
			}
		}
		// B-3 (2026-07-30): Declare structure immediately in this document-order traversal, and route drawing
		// to the reference held by PaintCommand. Even if z-index puts it in a different
		// stacking context, the logical /K order is already settled here.
		final var parent = this.structParent();
		final var ref = this.declareStruct(role, scope);
		this.structStack.push(ref == null ? NULL_STRUCT : ref);
		drawer.setCurrentStructRef(ref);
		if (role.equals("LI")) {
			// PDF/UA: an LI's content must sit in an LBody.
			final var lbody = this.declareStruct("LBody", null);
			this.structStack.push(lbody == null ? NULL_STRUCT : lbody);
			drawer.setCurrentStructRef(lbody);
			if (elementKey >= 0 && ref != null) {
				this.structContext.register(elementKey, new TaggedStructureContext.Binding(
						new net.zamasoft.pdfg2d.pdf.StructureRef[] { ref, lbody }, role, scope, parent));
			}
			return 2;
		}
		if (elementKey >= 0 && ref != null) {
			this.structContext.register(elementKey, new TaggedStructureContext.Binding(
					new net.zamasoft.pdfg2d.pdf.StructureRef[] { ref }, role, scope, parent));
		}
		return 1;
	}

	/**
	 * Closes the structure elements opened by a matching {@link #beginStruct}.
	 *
	 * @param drawer  the drawer to add markers to
	 * @param element the box's element
	 * @param count   the value returned by {@link #beginStruct}
	 * @param x       the box x position
	 * @param y       the box y position
	 */
	public void endStruct(final Drawer drawer, final Object element, final int count, final double x, final double y) {
		if (count == 0) {
			return;
		}
		for (int i = 0; i < count; ++i) {
			if (!this.structStack.isEmpty()) {
				this.structStack.pop();
			}
		}
		this.closeStruct(element);
		drawer.setCurrentStructRef(this.structParent());
	}

	public final boolean isSpecifiedPageSize() {
		return false;
	}

	/**
	 * Whether this page <b>started with a forced page break</b> (added 2026-07-28).
	 *
	 * <p>
	 * {@code page-break-before/after: always|left|right} explicitly requests a page even if blank.
	 * The rule omitting pages that paint nothing (css-break-3 §4.4, {@code StyleBuilder.drawPage})
	 * does not apply to pages with this marker.
	 * </p>
	 */
	private boolean forcedBreakOrigin = false;

	/**
	 * Records that this page started with a forced page break
	 * (for {@code RootBuilder.pageBreak} only).
	 */
	public final void markForcedBreakOrigin() {
		this.forcedBreakOrigin = true;
	}

	/**
	 * Returns true if this page started with a forced page break.
	 */
	public final boolean isForcedBreakOrigin() {
		return this.forcedBreakOrigin;
	}

	/**
	 * Marks closure due to a page-name transition at page start (named pages N2b).
	 * If marked and nothing is painted, omit the page regardless of running-header declarations
	 * or {@link #isForcedBreakOrigin()}. This is equivalent to replacing an unfinalized page:
	 * discard the page with the old name and recreate it with the new one.
	 */
	private boolean namedTransitionClosed = false;

	/** Records closure due to a page-name transition (for {@code RootBuilder.pageBreak} only). */
	public final void markNamedTransitionClosed() {
		this.namedTransitionClosed = true;
	}

	/** Returns true if this page was closed by a page-name transition. */
	public final boolean isNamedTransitionClosed() {
		return this.namedTransitionClosed;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Besides body text, a page holds <b>fixed-position content</b> ({@code position:fixed}).
	 * It is registered while drawing a preceding page and drawn on every subsequent page,
	 * so container traversal cannot find it.
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		if (this.pageBackground.isVisible()) {
			return true;
		}
		if (this.fixeds != null && !this.fixeds.isEmpty()) {
			return true;
		}
		if (this.toAddFixeds != null && !this.toAddFixeds.isEmpty()) {
			return true;
		}
		return super.paintsAnything();
	}

	@Override
	public double paintedPageExtent(final WritingMode flow) {
		return this.pageBackground.isVisible() ? this.getPageExtent(flow) : super.paintedPageExtent(flow);
	}

	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		throw new UnsupportedOperationException();
	}

	public final void setPageAxis(final double newSize) {
		assert !LayoutUtils.isNone(newSize);
		final BlockParams params = this.getBlockParams();
		switch (params.flow) {
		case WritingMode.TB: {
			// Horizontal writing
			this.visualHeight = Math.max(this.visualHeight, newSize);
			if (this.size.getHeightType() != LengthType.AUTO || newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
			break;
		case WritingMode.LR:
		case WritingMode.RL: {
			// Vertical writing
			this.visualWidth = Math.max(this.visualWidth, newSize);
			if (this.size.getWidthType() != LengthType.AUTO || newSize <= this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}
	
	public double getVisualWidth() {
		return this.visualWidth + this.frame.getFrameWidth();
	}

	public double getVisualHeight() {
		if (this.footInset != 0) {
			return this.footAreaVisualHeight;
		}
		return this.visualHeight + this.frame.getFrameHeight();
	}

	public final void addFixed(Drawer drawer, Visitor visitor, IAbsoluteBox box, double x, double y) {
		AbsolutePos pos = box.getAbsolutePos();
		if (pos.location.getLeftType() != LengthType.AUTO || pos.location.getRightType() != LengthType.AUTO) {
			x = this.replayX;
		}
		if (pos.location.getTopType() != LengthType.AUTO || pos.location.getBottomType() != LengthType.AUTO) {
			y = this.replayY;
		}
		box.finishLayout(this);
		Fixed fixed = new Fixed(box, x, y);
		if (this.toAddFixeds == null) {
			this.toAddFixeds = new ArrayList<Fixed>();
		}
		this.toAddFixeds.add(fixed);

		x = this.replayX + this.offsetX + this.frame.getFrameLeft() - this.frame.margin.left;
		y = this.replayY + this.offsetY + this.frame.getFrameTop() - this.frame.margin.top;
		fixed.box.draw(this, drawer, visitor, null, new AffineTransform(), x, y, fixed.x, fixed.y);
	}

	public final boolean isContextBox() {
		return true;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		throw new UnsupportedOperationException();
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final net.zamasoft.foliojet.ua.UserAgent ua = this.ua;
		final Background pageBackground = this.pageBackground;
		return (state, container) -> new PageBox(params, ua, pageBackground, container);
	}

	/** Resolves margin/padding values against line width to absolute lengths (top, right, bottom, left). AUTO is 0. */
	private static double[] resolveInsets(final Insets insets, final double lineWidth) {
		final double[] out = new double[4];
		final LengthType[] types = { insets.getTopType(), insets.getRightType(), insets.getBottomType(),
				insets.getLeftType() };
		final double[] values = { insets.getTop(), insets.getRight(), insets.getBottom(), insets.getLeft() };
		final double[] ratios = { insets.getTopRatio(), insets.getRightRatio(), insets.getBottomRatio(),
				insets.getLeftRatio() };
		for (int i = 0; i < 4; ++i) {
			out[i] = switch (types[i]) {
			case ABSOLUTE -> values[i];
			case RELATIVE -> values[i] * lineWidth;
			case MIXED -> values[i] + ratios[i] * lineWidth;
			case AUTO -> 0;
			default -> throw new IllegalStateException();
			};
		}
		return out;
	}

	public final void drawFlow(Drawer drawer, Visitor visitor) {
		double x = -this.frame.margin.left;
		double y = -this.frame.margin.top;
		if (this.pageBackground.isVisible()) {
			// PageSequence translates the GC by the margins, so these coordinates are the sheet origin.
			// Unlike frame.draw, draw the @page background across the entire sheet without subtracting margins.
			// If bleed is present, extend beyond the trim line by that width (2026-09-02;
			// previously stopped at the trim line, leaving a white band at the cut edge).
			final double b = this.bleed;
			drawer.visitDrawable(new BackgroundBorderDrawable(this, null, 1f, new AffineTransform(),
					this.pageBackground, null, null, this.getWidth() + b * 2, this.getHeight() + b * 2), x - b, y - b);
		}
		this.frames(this, drawer, null, new AffineTransform(), x, y);
		this.draw(this, drawer, visitor, null, new AffineTransform(), x, y, x, y);
	}

	/**
	 * The page-axis position of the footnote separator rule (footnote F6/F7 recommendation ①, 2026-07-31).
	 * Uses logical container coordinates originating at the type area's inner edge, the same coordinate
	 * system as (0,pageAxis) passed by RootBuilder to addFloating. -1 on pages without footnotes.
	 */
	private double footnoteSeparatorAxis = -1;
	private record ColumnFootnoteSeparator(Object owner, WritingMode flow, double lineOrigin, double pageOrigin,
			double lineSize, double pageAxis) { }
	private java.util.List<ColumnFootnoteSeparator> columnFootnoteSeparators;

	public void addColumnFootnoteSeparator(final Object owner, final WritingMode flow, final double lineOrigin,
			final double pageOrigin, final double lineSize, final double pageAxis) {
		if (this.columnFootnoteSeparators == null) this.columnFootnoteSeparators = new ArrayList<>();
		this.columnFootnoteSeparators.add(new ColumnFootnoteSeparator(owner, flow, lineOrigin, pageOrigin, lineSize, pageAxis));
	}

	/** Removes rules of multi-column layouts whose column footnotes were collected before balancing (increment 6). */
	public void removeColumnFootnoteSeparators(final Object owner) {
		if (this.columnFootnoteSeparators != null) this.columnFootnoteSeparators.removeIf(separator -> separator.owner() == owner);
	}

	public void setFootnoteSeparatorAxis(final double pageAxis) {
		this.footnoteSeparatorAxis = pageAxis;
	}

	/**
	 * The rule position of a sheet-edge band (along the line axis from the body text's inner-edge origin)
	 * and the area's orientation.
	 *
	 * <p>
	 * For a top band, this is above the content origin, hence <b>negative</b> (2026-09-11).
	 * Use {@code NaN} for unset: using a negative sentinel silently hid headnote rules.
	 * </p>
	 */
	private double footnoteSeparatorLineAxis = Double.NaN;
	private WritingMode footnoteSeparatorFlow;

	public void setFootnoteSeparatorLineAxis(final double lineAxis, final WritingMode flow) {
		this.footnoteSeparatorLineAxis = lineAxis;
		this.footnoteSeparatorFlow = flow;
	}

	/** Separator rule thickness and its length as a fraction of the type area's line-axis width (fixed by the UA). */
	private static final double FOOTNOTE_SEPARATOR_THICKNESS = 0.5;

	/**
	 * Draws footnote separator rules (after flow and before fixed in {@code PageSequence.drawPage}).
	 * They are decoration, so use artifacts (exclude them from tagged-PDF structure elements).
	 * The page frame contains only margins, so the body container origin is (0,0)
	 * (the recommendation's coordinate mapping).
	 */
	public void drawFootnoteSeparator(final Drawer drawer) {
		// @footnote border-top (2026-10-04): If specified, span the area's full width using its thickness
		// and color; draw nothing if thickness is 0. If unspecified, use the UA default rule.
		final net.zamasoft.foliojet.ua.FootnoteArea.Separator spec = this.getUserAgent().getUAContext()
				.getFootnoteArea().separator;
		final double thickness = spec == null ? FOOTNOTE_SEPARATOR_THICKNESS : spec.thickness();
		if (!(thickness > 0)) {
			return;
		}
		final double ratio = spec == null ? 1.0 / 3 : 1;
		final net.zamasoft.pdfg2d.gc.paint.Color color = spec == null || spec.color() == null
				? net.zamasoft.pdfg2d.gc.paint.GrayColor.BLACK
				: spec.color();
		if (this.columnFootnoteSeparators != null) {
			for (final ColumnFootnoteSeparator separator : this.columnFootnoteSeparators) {
				final double length = separator.lineSize() * ratio;
				final double axis = separator.pageOrigin() + separator.pageAxis() - thickness / 2;
				final java.awt.geom.Rectangle2D.Double rect;
				if (!separator.flow().isVertical()) {
					rect = new java.awt.geom.Rectangle2D.Double(separator.lineOrigin(), axis, length, thickness);
				} else {
					final double x = separator.flow() == WritingMode.RL
							? this.getInnerWidth() - axis - thickness : axis;
					rect = new java.awt.geom.Rectangle2D.Double(x, separator.lineOrigin(), thickness, length);
				}
				drawer.artifactView().visitDrawable(new FootnoteSeparatorDrawable(this, rect, color), rect.x, rect.y);
			}
		}
		if (!Double.isNaN(this.footnoteSeparatorLineAxis)) {
			final double length = this.getInnerWidth() * ratio;
			final double x = this.frame.getFrameLeft() - this.frame.margin.left
					+ (this.footnoteSeparatorFlow == WritingMode.RL ? this.getInnerWidth() - length : 0);
			final double y = this.frame.getFrameTop() - this.frame.margin.top
					+ this.footnoteSeparatorLineAxis - thickness / 2;
			final java.awt.geom.Rectangle2D.Double rect = new java.awt.geom.Rectangle2D.Double(
					x, y, length, thickness);
			drawer.artifactView().visitDrawable(new FootnoteSeparatorDrawable(this, rect, color), rect.x, rect.y);
			return;
		}
		if (this.footnoteSeparatorAxis < 0) {
			return;
		}
		final BlockParams params = this.getBlockParams();
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = params.flow;
		final double length = this.getInnerLineExtent(flow) * ratio;
		final double axis = this.footnoteSeparatorAxis - thickness / 2;
		final java.awt.geom.Rectangle2D.Double rect;
		if (!flow.isVertical()) {
			// TB: A horizontal line near the type area bottom (from the line-axis start; default 1/3).
			rect = new java.awt.geom.Rectangle2D.Double(0, axis, length, thickness);
		} else if (flow == net.zamasoft.foliojet.layout.box.params.WritingMode.RL) {
			// vertical-rl: A vertical line on the block-end = left side.
			rect = new java.awt.geom.Rectangle2D.Double(
					this.getInnerPageExtent(flow) - axis - thickness, 0,
					thickness, length);
		} else {
			// vertical-lr: A vertical line on the block-end = right side.
			rect = new java.awt.geom.Rectangle2D.Double(axis, 0, thickness, length);
		}
		drawer.artifactView().visitDrawable(new FootnoteSeparatorDrawable(this, rect, color), rect.x, rect.y);
	}

	/** The separator-rule drawable (decoration; excluded from structure elements). */
	private static final class FootnoteSeparatorDrawable
			extends net.zamasoft.foliojet.layout.draw.AbstractDrawable {
		private final java.awt.geom.Rectangle2D.Double rect;

		private final net.zamasoft.pdfg2d.gc.paint.Color color;

		FootnoteSeparatorDrawable(final PageBox pageBox, final java.awt.geom.Rectangle2D.Double rect,
				final net.zamasoft.pdfg2d.gc.paint.Color color) {
			super(pageBox, null, 1f, new AffineTransform());
			this.rect = rect;
			this.color = color;
		}

		@Override
		public void innerDraw(final net.zamasoft.pdfg2d.gc.GC gc, final double x, final double y)
				throws net.zamasoft.pdfg2d.gc.GraphicsException {
			try (final var state = gc.begin()) {
				gc.setFillPaint(this.color);
				gc.fill(new java.awt.geom.Rectangle2D.Double(x, y, this.rect.width, this.rect.height));
			}
		}

		@Override
		public String describe() {
			return String.format(java.util.Locale.ROOT, "FootnoteSeparator[w=%.2f h=%.2f]", this.rect.width,
					this.rect.height);
		}
	}

	public final void drawFixed(Drawer drawer, Visitor visitor) {
		double x = this.offsetX + this.frame.getFrameLeft() - this.frame.margin.left;
		double y = this.offsetY + this.frame.getFrameTop() - this.frame.margin.top;
		if (this.fixeds != null) {
			for (int i = 0; i < this.fixeds.size(); ++i) {
				Fixed c = (Fixed) this.fixeds.get(i);
				c.box.draw(this, drawer, visitor, null, new AffineTransform(), x, y, c.x, c.y);
			}
		}
		if (this.toAddFixeds != null && !this.toAddFixeds.isEmpty()) {
			if (this.fixeds == null) {
				this.fixeds = new ArrayList<Fixed>();
			}
			this.fixeds.addAll(this.toAddFixeds);
			this.toAddFixeds.clear();
		}
	}

	public final void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (this.fixeds == null) {
			return;
		}
		for (int i = 0; i < this.fixeds.size(); ++i) {
			Fixed fixed = (Fixed) this.fixeds.get(i);
			builder.addBound(fixed.box);
		}
	}
}
