package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;
import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;
import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJRubyValue;
import net.zamasoft.foliojet.css.impl.property.box.CSSFloat;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJRuby;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.PageFloatPos;
import net.zamasoft.foliojet.layout.box.params.PageMarginNotePos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPrintMode;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.ua.BoundSide;

/**
 * Dispatches boxes by display value and completes anonymous table structure
 * (StyleBuilder decomposition, increment 4a, 2026-07-30. Method bodies moved verbatim
 * from StyleBuilder; behavior is unchanged. State is accessed through {@link StyleBuildContext}).
 *
 * <p>
 * Anonymous table completion became iterative with an explicit stack (an {@code OpenStep}
 * chain) in increment 4b (2026-07-30). Preserves the old recursion's order: corrections
 * and insertions inside out, emission outside in (see the comment on {@link #_startStyle}).
 * </p>
 */
public final class StyleBoxEmitter {
	/**
	 * Entry point for repeated content: generates only layout events from computed styles.
	 * Does not enter cascade, counter, string-set, or main-source recording processing.
	 */
	public static final class Replay implements StyleBuildContext {
		private CSSStyle current;
		private final StyleBoxEmitter emitter;
		private final java.util.function.Consumer<net.zamasoft.foliojet.layout.segment.SegmentEvent> events;
		private boolean inTextBlock;
		private boolean rightSide;

		public Replay(final UserAgent ua, final CSSStyle root, final boolean rightSide,
				final java.util.function.Consumer<net.zamasoft.foliojet.layout.segment.SegmentEvent> events) {
			this.current = root;
			this.rightSide = rightSide;
			this.events = events;
			final StyleContext empty = new StyleContext(new net.zamasoft.foliojet.css.CSSStyleSheet(), null, null);
			this.emitter = new StyleBoxEmitter(this, new RecordingLayoutSink(events),
					new BoxStyleMapper(ua, empty), null, ua, null);
		}

		public void start(final CSSStyle style) {
			this.emitter._startStyle(style);
		}

		/** Closes the explicit element and its added anonymous boxes back to the original parent. */
		public void end(final CSSStyle parent) {
			while (this.current != parent) {
				this.emitter._endStyle();
			}
		}

		public void text(final String text, final boolean fixed) {
			if (text.isEmpty()) {
				return;
			}
			final byte display = Display.get(this.current);
			final boolean table = display == DisplayValue.TABLE || display == DisplayValue.INLINE_TABLE
					|| display == DisplayValue.TABLE_ROW || display == DisplayValue.TABLE_ROW_GROUP
					|| display == DisplayValue.TABLE_HEADER_GROUP || display == DisplayValue.TABLE_FOOTER_GROUP;
			if (table && text.isBlank()) {
				return;
			}
			final CSSStyle parent = this.current;
			if (table || display == DisplayValue.CONTENTS) {
				final CSSStyle inline = parent.inheritAnonStyle(CSSElement.ANON);
				inline.set(Display.INFO, DisplayValue.INLINE_VALUE);
				this.start(inline);
			}
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Text(-1, text, fixed));
			this.end(parent);
		}

		public CSSStyle getCurrentStyle() { return this.current; }
		public void setCurrentStyle(final CSSStyle style) { this.current = style; }
		public FlowBlockBox getHtmlRootBlock() { return null; }
		public void setHtmlRootBlock(final FlowBlockBox box) { throw new IllegalStateException(); }
		public boolean isInBody() { return true; }
		public void setInBody(final boolean inBody) { throw new IllegalStateException(); }
		public boolean isInTextBlock() { return this.inTextBlock; }
		public void setInTextBlock(final boolean inTextBlock) { this.inTextBlock = inTextBlock; }
		public boolean isRightSide() { return this.rightSide; }
		public void setRightSide(final boolean rightSide) { this.rightSide = rightSide; }
		public void checkMarker() { /* Do not change body list numbering from repeated content. */ }
	}
	private final StyleBuildContext context;
	private final RecordingLayoutSink sink;
	private final BoxStyleMapper mapper;
	private final PageSequence pageSequence;
	private final UserAgent ua;
	private final Imposition imposition;
	/**
	 * Whether a notice (2823) for an ineffective combination has been issued (2026-08-29).
	 * Since documents commonly repeat the same pattern, report only once per kind.
	 */
	private boolean flexFallbackReported = false;
	private boolean gridFallbackReported = false;
	/** Grid column occupancy for each open table (innermost first). {@link TableSlotTracker}. */
	private final java.util.ArrayDeque<TableSlotTracker> tableSlots = new java.util.ArrayDeque<>();

	StyleBoxEmitter(final StyleBuildContext context, final RecordingLayoutSink sink, final BoxStyleMapper mapper,
			final PageSequence pageSequence, final UserAgent ua, final Imposition imposition) {
		this.context = context;
		this.sink = sink;
		this.mapper = mapper;
		this.pageSequence = pageSequence;
		this.ua = ua;
		this.imposition = imposition;
	}

	void requireRoot(byte direction, WritingMode progression, WritingModeVariant writingModeVariant) {
		// Output the pending HTML root
		if (!this.context.isInBody()) {
			this.context.setInBody(true);
			if (this.context.getHtmlRootBlock() != null) {
				// Page drawing method
				final BlockParams params = this.context.getHtmlRootBlock().getBlockParams();
				params.direction = direction;
				params.flow = progression;
				params.writingModeVariant = writingModeVariant;
			}
			this.pageSequence.setProgression(progression);
			// Named pages N1b: apply root (html)'s page used value to all pages
			// (mid-document transitions and names under body are N2:
			// consult-codex-2026-07-31-named-pages.txt)
			if (this.context.getHtmlRootBlock() != null && this.context.getHtmlRootBlock()
					.getPos() instanceof net.zamasoft.foliojet.layout.box.params.AbstractBlockLevelPos blockLevel) {
				this.pageSequence.setPageName(blockLevel.pageName);
			}
			// Right binding
			boolean right;
			OutputPrintMode printMode = UAProps.OUTPUT_PRINT_MODE.get(this.ua);
			if (printMode == OutputPrintMode.LEFT_SIDE) {
				right = false;
			} else if (printMode == OutputPrintMode.RIGHT_SIDE) {
				right = true;
			} else {
				right = progression == WritingMode.RL
						|| (progression == WritingMode.TB && direction == AbstractTextParams.DIRECTION_RTL);
			}
			if (right) {
				this.imposition.setBoundSide(BoundSide.RIGHT);
				this.context.setRightSide(true);
			}
			if (this.context.getHtmlRootBlock() != null) {
				this.sink.start(this.context.getHtmlRootBlock());
				this.context.setHtmlRootBlock(null);
			}
		}
	}

	/**
	 * Returns whether this is a <b>full-area absolutely positioned wrapper</b> directly
	 * under the root (2026-08-17; see the caller's comment for the reasoning).
	 *
	 * <p>
	 * Deliberately narrow conditions: (1) the parent is html/body (deeper full-area positioning
	 * may intentionally overlay content, e.g. modal backgrounds); (2) dimensions are
	 * {@code width:100%} and {@code height:100%}, or all four insets are zero.
	 * Both match only standard patterns intended to fill the viewport.
	 * </p>
	 */
	private static boolean isFullViewportWrapper(final CSSStyle style) {
		final CSSStyle parentStyle = style.getParentStyle();
		if (parentStyle == null) {
			return false;
		}
		final Object pe = parentStyle.getCSSElement();
		if (!(pe instanceof CSSElement pce)) {
			return false;
		}
		final String pName = pce.lName();
		if (!"body".equalsIgnoreCase(pName) && !"html".equalsIgnoreCase(pName)) {
			return false;
		}
		if (isFullRatio(Width.get(style)) && isFullRatio(Height.get(style))) {
			return true;
		}
		return isZeroLength(Inset.get(style, net.zamasoft.foliojet.css.impl.property.box.Side.TOP))
				&& isZeroLength(Inset.get(style, net.zamasoft.foliojet.css.impl.property.box.Side.RIGHT))
				&& isZeroLength(Inset.get(style, net.zamasoft.foliojet.css.impl.property.box.Side.BOTTOM))
				&& isZeroLength(Inset.get(style, net.zamasoft.foliojet.css.impl.property.box.Side.LEFT));
	}

	private static boolean isFullRatio(final Value value) {
		return value instanceof net.zamasoft.foliojet.css.value.PercentageValue percentage
				&& percentage.getRatio() >= 1.0;
	}

	private static boolean isZeroLength(final Value value) {
		return value instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue length
				&& length.getLength() == 0;
	}

	/**
	 * Params of the block that replaces a floated flex or grid container (2026-10-10): the ones display: block gets, so
	 * the element, the text settings and everything else are those of a block.
	 */
	private BlockParams fallbackBlockParams(final CSSStyle style) {
		final BlockParams params = new BlockParams();
		this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(),
				this.pageSequence);
		return params;
	}

	AbstractBlockBox createBlockBox(CSSStyle style, BlockParams params, byte position, byte display,
			byte floating) {
		final AbstractBlockBox blockBox;
		if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
			final AbsolutePos pos = new AbsolutePos();
			this.mapper.setupAbsolutePos(pos, style);
			blockBox = new AbsoluteBlockBox(params, pos);
		} else if (display == DisplayValue.INLINE_BLOCK || display == DisplayValue.INLINE_TABLE) {
			final InlinePos pos = new InlinePos();
			this.mapper.setupInlinePos(pos, style);
			blockBox = new InlineBlockBox(params, pos);
		} else if (CSSFloatValue.isPageFloat(floating)) {
			// Page floats (2026-08-02): as with footnotes, keep PosType=FLOAT and use
			// the separate builder lifecycle, handing off to the page ledger (RootBuilder)
			// at the end. After placement, top becomes an exclusion region for Root line scanning.
			final PageFloatPos pos = new PageFloatPos(
					floating == CSSFloatValue.PAGE_TOP || floating == CSSFloatValue.PAGE_BLOCK_START,
					floating == CSSFloatValue.PAGE_TOP || floating == CSSFloatValue.PAGE_BOTTOM);
			this.mapper.setupStaticPos(pos, style);
			blockBox = new FloatBlockBox(params, pos);
		} else if (floating == CSSFloatValue.PAGE_NOTE_START || floating == CSSFloatValue.PAGE_NOTE_END) {
			// JLREQ parallel notes. Place separately in the page's logical inline-axis margins.
			final PageMarginNotePos pos = new PageMarginNotePos(floating == CSSFloatValue.PAGE_NOTE_START);
			this.mapper.setupStaticPos(pos, style);
			blockBox = new FloatBlockBox(params, pos);
		} else if (floating == CSSFloatValue.FOOTNOTE) {
			// Footnotes F2 (2026-07-31): use the separate builder lifecycle with
			// FootnotePos (PosType=FLOAT). Unlike left/right floats, do not use FloatSide/clear,
			// and hand off to the page footnote ledger at the end instead of addBound to the parent.
			// Vertical writing enabled in F7 (axis-neutral occupancy, area at type-area block-end,
			// upright number labels using limited tate-chu-yoko: deliberate specification deviation).
			final FootnotePos pos = new FootnotePos();
			this.mapper.setupStaticPos(pos, style);
			blockBox = new FloatBlockBox(params, pos);
		} else if (floating != CSSFloatValue.NONE && !CSSFloatValue.isPageLevel(floating)) {
			final FloatPos pos = new FloatPos();
			this.mapper.setupFloatPos(pos, style, this.context.isRightSide());
			blockBox = new FloatBlockBox(params, pos);
		} else {
			final FlowPos pos = new FlowPos();
			this.mapper.setupFlowPos(pos, style, this.context.isRightSide());
			final CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null) {
				pos.align = CSSJHtmlAlign.get(parentStyle);
			}
			blockBox = new FlowBlockBox(params, pos);
		}
		return blockBox;
	}

	CSSStyle startColumns(CSSStyle style, AbstractContainerBox box) {
		int c = LayoutUtils.getColumnCount(box);
		// While C is delayed, the box is not built yet. Do not fix the used column count
		// for column-width to 1 here; emit a wrapper and determine it from containing dimensions after reservation.
		if (c > 1 || (this.sink.isFootnoteInputDelayed()
				&& !LayoutUtils.isNone(box.getBlockParams().columns.width))) {
			final BlockParams params = box.getBlockParams();
			final BlockParams mcParams = new BlockParams();
			final FlowPos mcPos = new FlowPos();
			final CSSStyle mc = style.inheritAnonStyle(CSSElement.ANON);
			this.mapper.setupBlockParams(mcParams, mc, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			this.mapper.setupFlowPos(mcPos, mc, this.context.isRightSide());
			mcParams.columns = params.columns;
			if (params.size.getWidthType() != LengthType.AUTO) {
				if (params.size.getHeightType() != LengthType.AUTO) {
					mcParams.size = Dimension.create(1, 1, LengthType.RELATIVE, LengthType.RELATIVE);
				} else {
					mcParams.size = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.AUTO);
				}
			} else if (params.size.getHeightType() != LengthType.AUTO) {
				mcParams.size = Dimension.create(0, 1, LengthType.AUTO, LengthType.RELATIVE);
			}
			final MulticolumnBlockBox mcBox = new MulticolumnBlockBox(mcParams, mcPos);
			this.sink.start(mcBox);
			style = mc;
		}
		return style;
	}

	void _startStyle(final CSSStyle startStyle) {
		// Replace self-recursion for anonymous table completion with an explicit stack (increment 4b, 2026-07-30).
		// Invariant: head corrections and anonymous-parent insertion run inside out (discovery order),
		// box emission outside in: the same order as the old recursion
		// (fix(style)→fix(anon)→…→emit(anonN)→…→emit(style)). Capture display/position/htmlRoot
		// at correction time (e.g. a caption still dispatches as TABLE_CAPTION after conversion
		// to BLOCK, as with the old local variable).
		final java.util.ArrayDeque<OpenStep> chain = new java.util.ArrayDeque<>();
		CSSStyle style = startStyle;
		while (true) {
			CSSStyle inserted = null;

			if (CSSJRuby.get(style) != CSSJRubyValue.NONE) {
				// Ruby-related elements (ruby/rb/rt) create no boxes; pass through as ordinary INLINE
				// (annotated-text approach, specification decision on 2026-07-25). The text processing
				// layer (StyledTextUnitizer) assembles units using the rubyRole marker
				// as its guide.
				style.set(Display.INFO, DisplayValue.INLINE_VALUE, CSSStyle.MODE_IMPORTANT);
			}
			// Fix the root HTML tag to block
			boolean htmlRoot = false;
			if (!this.context.isInBody() && this.context.getHtmlRootBlock() == null) {
				final CSSElement ce = style.getCSSElement();
				if (ce.isPseudoClass(CSSElement.PC_ROOT)) {
					htmlRoot = true;
				}
				style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
				style.set(CSSFloat.INFO, CSSFloatValue.NONE_VALUE, CSSStyle.MODE_IMPORTANT);
				final byte position = CSSPosition.get(style);
				if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
					style.set(CSSPosition.INFO, PositionValue.STATIC_VALUE, CSSStyle.MODE_IMPORTANT);
				}
			}

			// SPEC CSS 2.1 9.7 computation is already implemented in Display
			final byte display = Display.get(style);
			byte position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE && isFullViewportWrapper(style)) {
				// **Convert full-area absolute positioning directly under the root to normal flow** (2026-08-17).
				//
				// The Read the Docs theme's
				// {@code .wy-grid-for-nav{position:absolute;width:100%;height:100%}}
				// is an example of a **full-area screen layout wrapper** around the entire body text.
				// This pattern is common on documentation sites (two documents in the real-world corpus:
				// mathjax-docs and rtd-theme; their theme's print CSS also fails to undo it).
				// Absolute positioning is intentionally not fragmented
				// (ARCHITECTURE §5.10: arbitrarily splitting intended overflow for watermarks or decorations
				// would be wrong), so leaving it unchanged stacks hundreds of pages of body text
				// **onto one page, losing all content**.
				//
				// Filling the viewport has no meaning in print, and Chrome printing also flows the content
				// across pages. Restrict this to **direct children of body/html with width:100% and
				// height:100% (or the equivalent of inset:0)**:
				// localized absolute positioning for watermarks, bleed, etc. is unaffected.
				// Validated the decision with zero differences across 592 imageTest documents and
				// observations of the real-world corpus (user approval, 2026-08-17).
				position = PositionValue.STATIC;
			}
			if (position == PositionValue.STATIC || position == PositionValue.RELATIVE
					|| position == PositionValue.STICKY) {
				// Complete tags
				final CSSStyle parentStyle = style.getParentStyle();
				if (parentStyle != null) {
					// display:contents ancestors have no boxes, so anonymous-box completion treats
					// the nearest non-contents ancestor as the parent (2026-08-07).
					final short parentDisplay = Display.getFlattenedParentDisplay(style);
					// **float has no effect on flex or grid items**
					// (CSS Flexbox §3: "float and clear do not create floating or
					// clearance for flex items"; CSS Grid §3 has the same wording).
					//
					// Until 2026-08-03 it was not ignored, so {@code float:left} on a flex container's
					// child made it a <b>float that could not span pages</b>.
					// Content exceeding the sheet was all stacked on the first page, with the rest
					// outside the paper: <b>content loss</b>.
					//
					// Sphinx's classic theme uses exactly this pattern
					// ({@code div.document{display:flex}} +
					// {@code div.documentwrapper{float:left;width:100%}});
					// importing Python's official documentation collapsed 23 pages of body text
					// onto one page. Many technical documents use Sphinx, so the impact is broad.
					// Regression: files/unittest/0510-flex/float-item.html.
					// (inline-flex/inline-grid are not implemented and do not reach here.)
					if (parentDisplay == DisplayValue.FLEX || parentDisplay == DisplayValue.GRID) {
						style.set(CSSFloat.INFO, CSSFloatValue.NONE_VALUE, CSSStyle.MODE_IMPORTANT);
					}
					switch (display) {
					case DisplayValue.TABLE_CELL: {
						// CSS 2.1 17.2.1 #1
						// Insert a table row above a table cell
						if (parentDisplay != DisplayValue.TABLE_ROW) {
							final CSSStyle row = style.insertAnonStyle(CSSElement.ANON_TR);
							row.set(Display.INFO, DisplayValue.TABLE_ROW_VALUE);
							inserted = row;
						}
					}
						break;

					case DisplayValue.TABLE_ROW: {
						// CSS 2.1 17.2.1 #2
						// Insert a table row group above a table row
						if (parentDisplay != DisplayValue.TABLE_ROW_GROUP
								&& parentDisplay != DisplayValue.TABLE_HEADER_GROUP
								&& parentDisplay != DisplayValue.TABLE_FOOTER_GROUP) {
							CSSStyle rowGroup = style.insertAnonStyle(CSSElement.ANON_TBODY);
							rowGroup.set(Display.INFO, DisplayValue.TABLE_ROW_GROUP_VALUE);
							inserted = rowGroup;
						}
					}
						break;

					case DisplayValue.TABLE_COLUMN_GROUP:
						if (parentDisplay == DisplayValue.TABLE_COLUMN_GROUP
								|| parentDisplay == DisplayValue.TABLE_COLUMN) {
							break;
						}
					case DisplayValue.TABLE_ROW_GROUP:
					case DisplayValue.TABLE_HEADER_GROUP:
					case DisplayValue.TABLE_FOOTER_GROUP: {
						// CSS 2.1 17.2.1 #2
						// Insert a table above a table column group or row group
						if (parentDisplay != DisplayValue.TABLE && parentDisplay != DisplayValue.INLINE_TABLE) {
							CSSStyle table = style.insertAnonStyle(CSSElement.ANON_TBODY);
							if (parentDisplay == DisplayValue.INLINE) {
								table.set(Display.INFO, DisplayValue.INLINE_TABLE_VALUE);
							} else {
								table.set(Display.INFO, DisplayValue.TABLE_VALUE);
							}
							inserted = table;
						}
					}
						break;

					case DisplayValue.TABLE_COLUMN: {
						// Insert a table above a table column
						if (parentDisplay != DisplayValue.TABLE && parentDisplay != DisplayValue.INLINE_TABLE
								&& parentDisplay != DisplayValue.TABLE_COLUMN_GROUP) {
							CSSStyle table = style.insertAnonStyle(CSSElement.ANON_TABLE);
							if (parentDisplay == DisplayValue.INLINE) {
								table.set(Display.INFO, DisplayValue.INLINE_TABLE_VALUE);
							} else {
								table.set(Display.INFO, DisplayValue.TABLE_VALUE);
							}
							inserted = table;
						}
					}
						break;

					case DisplayValue.TABLE_CAPTION:
						switch (parentDisplay) {
						case DisplayValue.INLINE_TABLE:
						case DisplayValue.TABLE:
						case DisplayValue.TABLE_ROW_GROUP:
						case DisplayValue.TABLE_HEADER_GROUP:
						case DisplayValue.TABLE_FOOTER_GROUP:
						case DisplayValue.TABLE_ROW:
							break;
						default:
							// Convert a table caption to a block
							style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
							break;
						}
						break;

					case DisplayValue.TABLE:
					case DisplayValue.BLOCK:
					case DisplayValue.GRID:
					case DisplayValue.FLEX:
					case DisplayValue.LIST_ITEM:
					case DisplayValue.INLINE_TABLE:
					case DisplayValue.INLINE:
					case DisplayValue.INLINE_BLOCK:
						// Insert a cell above tables, blocks, and inlines inside a table
						switch (parentDisplay) {
						case DisplayValue.INLINE_TABLE:
						case DisplayValue.TABLE:
						case DisplayValue.TABLE_ROW_GROUP:
						case DisplayValue.TABLE_HEADER_GROUP:
						case DisplayValue.TABLE_FOOTER_GROUP:
						case DisplayValue.TABLE_ROW:
							CSSStyle anon = style.insertAnonStyle(CSSElement.ANON_TD);
							anon.set(Display.INFO, DisplayValue.TABLE_CELL_VALUE);
							inserted = anon;
						}
						break;

					case DisplayValue.CONTENTS:
						// A contents element itself creates no box, so needs no completion
						break;

					default:
						throw new IllegalStateException();
					}
				}
			}


			chain.push(new OpenStep(style, htmlRoot, display, position));
			if (inserted == null) {
				break;
			}
			style = inserted;
		}
		while (!chain.isEmpty()) {
			this.openBox(chain.pop());
		}
	}

	/**
	 * Style of an absolutely positioned Grid/Flex element that opens two boxes:
	 * an outer absolutely positioned box and an inner anonymous container
	 * (2026-09-02, E-3). Close both when the element ends.
	 */
	private final java.util.Set<CSSStyle> wrappedContainers = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());

	/**
	 * Removes the frame, background, and dimensions handled by the outer box from the
	 * wrapped inner anonymous container's box parameters. Leaves container-specific
	 * values, such as tracks and item alignment, unchanged.
	 */
	private static void anonymizeWrappedParams(final BlockParams inner) {
		inner.frame = net.zamasoft.foliojet.layout.box.params.RectFrame.NULL_FRAME;
		inner.size = net.zamasoft.foliojet.layout.box.params.Dimension.AUTO_DIMENSION;
		inner.minSize = net.zamasoft.foliojet.layout.box.params.Dimension.ZERO_DIMENSION;
		inner.maxSize = net.zamasoft.foliojet.layout.box.params.Dimension.AUTO_DIMENSION;
		inner.boxSizing = net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX;
	}

	/** Captured data for one opening level (display/position/htmlRoot at correction time). */
	private record OpenStep(CSSStyle style, boolean htmlRoot, byte display, byte position) {
	}

	/** Emits boxes for one level (dispatch part of the old _startStyle, moved verbatim). */
	private void openBox(final OpenStep step) {
		this.sink.beginSource(step.style().getCSSElement());
		// Non-final because startColumns (multi-column wrapper) replaces style (same as the old code)
		CSSStyle style = step.style();
		final boolean htmlRoot = step.htmlRoot();
		final byte display = step.display();
		final byte position = step.position();
		// Set up positioning
		byte floating = CSSFloat.get(style);

		// Process each box type
		switch (display) {
		case DisplayValue.BLOCK:
		case DisplayValue.INLINE_BLOCK: {
			// Block
			final Image image = CSSJInternalImage.getImage(style);
			if (image != null) {
				// Image
				final AbstractReplacedBox replacedBox;
				boolean inline = false;
				ReplacedParams params;
				if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
					final AbsolutePos pos = new AbsolutePos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupAbsolutePos(pos, style);
					replacedBox = new AbsoluteReplacedBox(params, pos);
				} else if (display == DisplayValue.INLINE_BLOCK) {
					final InlinePos pos = new InlinePos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupInlinePos(pos, style);
					inline = true;
					replacedBox = new InlineReplacedBox(params, pos);
				} else if (floating != CSSFloatValue.NONE && !CSSFloatValue.isPageLevel(floating)) {
					// Page-level floats (footnotes/page floats) on replaced elements fall back to
					// normal flow (wrapping in a block element still makes them work as before).
					final FloatPos pos = new FloatPos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupFloatPos(pos, style, this.context.isRightSide());
					replacedBox = new FloatReplacedBox(params, pos);
				} else {
					final FlowPos pos = new FlowPos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupFlowPos(pos, style, this.context.isRightSide());
					final CSSStyle parentStyle = style.getParentStyle();
					if (parentStyle != null) {
						pos.align = CSSJHtmlAlign.get(parentStyle);
					}
					replacedBox = new FlowReplacedBox(params, pos);
				}
				this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB, WritingModeVariant.NORMAL);
				if (inline) {
					this.context.checkMarker();
				}
				this.sink.replaced(replacedBox);
			} else {
				// Block box
				final BlockParams params = new BlockParams();
				this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
				final AbstractBlockBox blockBox = this.createBlockBox(style, params, position, display, floating);
				// Defer output of the HTML root
				if (blockBox.getPos().getType() == PosType.FLOW && htmlRoot) {
					this.context.setHtmlRootBlock((FlowBlockBox) blockBox);
					break;
				}
				this.requireRoot(params.direction, params.flow, params.writingModeVariant);
				if (blockBox.getPos().getType() == PosType.INLINE) {
					this.context.checkMarker();
				}
				this.sink.start(blockBox);

				// Start multi-column layout
				style = this.startColumns(style, blockBox);
			}
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.INLINE: {
			Image image = CSSJInternalImage.getImage(style);
			InlinePos pos = new InlinePos();
			if (image != null) {
				// Inline image
				ReplacedParams params = new ReplacedParams();
				this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
				this.mapper.setupInlinePos(pos, style);
				AbstractReplacedBox replaced = new InlineReplacedBox(params, pos);
				this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB, WritingModeVariant.NORMAL);
				this.context.checkMarker();
				this.sink.replaced(replaced);
			} else {
				// Inline box
				InlineParams params = new InlineParams();
				this.mapper.setupInlineParams(params, style, this.context.isInBody(), this.pageSequence);
				this.mapper.setupInlinePos(pos, style);
				InlineBox inline = new InlineBox(params, pos);
				this.requireRoot(params.direction, params.flow, params.writingModeVariant);
				this.sink.start(inline);
			}
		}
			break;
		case DisplayValue.LIST_ITEM: {
			// List item
			final BlockParams params = new BlockParams();
			this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			final AbstractBlockBox listItem = this.createBlockBox(style, params, position, display, floating);
			this.requireRoot(params.direction, params.flow, params.writingModeVariant);
			this.sink.start(listItem);
		}
			break;

		case DisplayValue.FLEX: {
			// Flex F0b (consult-codex-2026-08-02-flexbox.txt): FlexBox only in normal flow
			// contexts (PageAtomicBox=always unbreakable). Floated/absolute/fixed Flex containers
			// are outside the initial subset and fall back to ordinary blocks
			// (no content loss). Content uses single-column flow until F1.
			final net.zamasoft.foliojet.layout.box.params.FlexParams params = new net.zamasoft.foliojet.layout.box.params.FlexParams();
			this.mapper.setupFlexParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(),
					this.pageSequence);
			final AbstractBlockBox blockBox;
			if ((position == PositionValue.STATIC || position == PositionValue.RELATIVE
					|| position == PositionValue.STICKY)
					&& floating == CSSFloatValue.NONE) {
				final FlowPos pos = new FlowPos();
				this.mapper.setupFlowPos(pos, style, this.context.isRightSide());
				final CSSStyle parentStyle = style.getParentStyle();
				if (parentStyle != null) {
					pos.align = CSSJHtmlAlign.get(parentStyle);
				}
				blockBox = new net.zamasoft.foliojet.layout.box.impl.FlexBox(params, pos);
			} else if ((position == PositionValue.ABSOLUTE || position == PositionValue.FIXED)
					&& floating == CSSFloatValue.NONE) {
				// Wrap absolutely positioned Flex containers the same way as Grid (2026-09-02)
				final net.zamasoft.foliojet.layout.box.params.FlexParams inner = new net.zamasoft.foliojet.layout.box.params.FlexParams();
				this.mapper.setupFlexParams(inner, style, this.context.getCurrentStyle(), this.context.isInBody(),
						this.pageSequence);
				anonymizeWrappedParams(inner);
				this.sink.start(this.createBlockBox(style, params, position, DisplayValue.BLOCK, floating));
				this.wrappedContainers.add(style);
				blockBox = new net.zamasoft.foliojet.layout.box.impl.FlexBox(inner, new FlowPos());
			} else {
				// Do not silently discard (user report, 2026-08-29): report with 2823 that the
				// declaration was parsed but its context prevents it from taking effect.
				if (!this.flexFallbackReported) {
					this.flexFallbackReported = true;
					this.ua.message(net.zamasoft.foliojet.message.MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION,
							"display: flex",
							net.zamasoft.foliojet.message.MessageCodeUtils.detail("2823.flex-not-in-flow"));
				}
				// The block that replaces the container is set up as display: block sets it up (2026-10-10). With the
				// flex params kept, the shrink-to-fit measurement of a float replayed its content in a flex container
				// (the measure wrapper follows the params), where text directly inside opened an anonymous item that
				// the recording never had, and the conversion failed (TwoPass NO_RANGE, A/B ff-e).
				blockBox = this.createBlockBox(style, this.fallbackBlockParams(style), position, DisplayValue.BLOCK,
						floating);
			}
			this.requireRoot(params.direction, params.flow, params.writingModeVariant);
			this.sink.start(blockBox);
		}
			break;

		case DisplayValue.GRID: {
			// Grid G0 (consult-codex-2026-07-31-grid.txt §1.1): GridBox only in normal flow
			// contexts (PageAtomicBox=always unbreakable). Floated/absolute/fixed Grid containers
			// are outside the initial subset and fall back to ordinary blocks
			// (no content loss).
			final net.zamasoft.foliojet.layout.box.params.GridParams params = new net.zamasoft.foliojet.layout.box.params.GridParams();
			this.mapper.setupGridParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(),
					this.pageSequence);
			final AbstractBlockBox blockBox;
			if ((position == PositionValue.STATIC || position == PositionValue.RELATIVE
					|| position == PositionValue.STICKY)
					&& floating == CSSFloatValue.NONE) {
				final FlowPos pos = new FlowPos();
				this.mapper.setupFlowPos(pos, style, this.context.isRightSide());
				final CSSStyle parentStyle = style.getParentStyle();
				if (parentStyle != null) {
					pos.align = CSSJHtmlAlign.get(parentStyle);
				}
				blockBox = new net.zamasoft.foliojet.layout.box.impl.GridBox(params, pos);
			} else if ((position == PositionValue.ABSOLUTE || position == PositionValue.FIXED)
					&& floating == CSSFloatValue.NONE) {
				// E-3 (2026-09-02): absolutely positioned Grid containers. Absolute positioning of a
				// type area within a sheet is a standard print pattern, so cannot be excluded.
				// Wrap one **anonymous static Grid box** inside an absolutely positioned box
				// (which handles frame/background/dimensions). Insets determine the dimensions,
				// so the containing block is definite and layout is straightforward. Close both at element end.
				final net.zamasoft.foliojet.layout.box.params.GridParams inner = new net.zamasoft.foliojet.layout.box.params.GridParams();
				this.mapper.setupGridParams(inner, style, this.context.getCurrentStyle(), this.context.isInBody(),
						this.pageSequence);
				anonymizeWrappedParams(inner);
				this.sink.start(this.createBlockBox(style, params, position, DisplayValue.BLOCK, floating));
				this.wrappedContainers.add(style);
				blockBox = new net.zamasoft.foliojet.layout.box.impl.GridBox(inner, new FlowPos());
			} else {
				if (!this.gridFallbackReported) {
					this.gridFallbackReported = true;
					this.ua.message(net.zamasoft.foliojet.message.MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION,
							"display: grid",
							net.zamasoft.foliojet.message.MessageCodeUtils.detail("2823.grid-not-in-flow"));
				}
				// Block params, as for a flex container that falls back (2026-10-10).
				blockBox = this.createBlockBox(style, this.fallbackBlockParams(style), position, DisplayValue.BLOCK,
						floating);
			}
			this.requireRoot(params.direction, params.flow, params.writingModeVariant);
			this.sink.start(blockBox);
		}
			break;

		case DisplayValue.TABLE:
		case DisplayValue.INLINE_TABLE: {
			// Table
			final TableParams params = new TableParams();
			this.mapper.setupTableParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			final AbstractBlockBox blockBox = this.createBlockBox(style, params, position, display, floating);
			if (blockBox.getPos().getType() == PosType.FLOW) {
				if (CSSJHtmlAlign.get(style) == Align.CENTER) {
					((FlowPos) blockBox.getPos()).align = Align.CENTER;
				}
			}
			TableBox table = new TableBox(params, blockBox);
			this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB, WritingModeVariant.NORMAL);
			this.sink.start(table);
			this.context.setInTextBlock(false);
			this.tableSlots.push(new TableSlotTracker(style));
		}
			break;

		case DisplayValue.TABLE_CAPTION: {
			// Table caption
			final TableCaptionPos pos = new TableCaptionPos();
			final BlockParams params = new BlockParams();
			this.mapper.setupTableCaptionPos(pos, style, this.context.isRightSide());
			this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			params.pageBreakInside = PageBreakMode.AVOID;
			switch (pos.captionSide) {
			case CaptionSideMode.BEFORE:
				pos.pageBreakAfter = PageBreakMode.AVOID;
				break;
			case CaptionSideMode.AFTER:
				pos.pageBreakBefore = PageBreakMode.AVOID;
				break;
			default:
				throw new IllegalStateException();
			}
			final FlowBlockBox caption = new FlowBlockBox(params, pos);
			this.requireRoot(params.direction, params.flow, params.writingModeVariant);
			this.sink.start(caption);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_COLUMN_GROUP: {
			// Table column group
			final TableColumnPos pos = new TableColumnPos();
			final InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableColumn(params, pos, style);
			final TableColumnGroupBox columnGroup = new TableColumnGroupBox(params, pos);
			this.sink.start(columnGroup);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_COLUMN: {
			// Table column
			TableColumnPos pos = new TableColumnPos();
			InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableColumn(params, pos, style);
			TableColumnBox column = new TableColumnBox(params, pos);
			this.sink.start(column);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_HEADER_GROUP: {
			// Table header group
			final TableRowGroupPos pos = new TableRowGroupPos();
			final InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRowGroup(params, pos, style, RowGroupType.HEADER, this.context.isRightSide());
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.sink.start(rowGroup);
			this.context.setInTextBlock(false);
			this.beginSlotRowGroup();
		}
			break;

		case DisplayValue.TABLE_ROW_GROUP: {
			// Table row group
			final TableRowGroupPos pos = new TableRowGroupPos();
			final InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRowGroup(params, pos, style, RowGroupType.BODY, this.context.isRightSide());
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.sink.start(rowGroup);
			this.context.setInTextBlock(false);
			this.beginSlotRowGroup();
		}
			break;

		case DisplayValue.TABLE_FOOTER_GROUP: {
			// Table footer group
			TableRowGroupPos pos = new TableRowGroupPos();
			InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRowGroup(params, pos, style, RowGroupType.FOOTER, this.context.isRightSide());
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.sink.start(rowGroup);
			this.context.setInTextBlock(false);
			this.beginSlotRowGroup();
		}
			break;

		case DisplayValue.TABLE_ROW: {
			// Table row
			TableRowPos pos = new TableRowPos();
			InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRow(params, pos, style, this.context.isRightSide());
			TableRowBox row = new TableRowBox(params, pos);
			this.sink.start(row);
			this.context.setInTextBlock(false);
			final TableSlotTracker slots = this.tableSlots.peek();
			if (slots != null) {
				slots.beginRow();
			}
		}
			break;

		case DisplayValue.TABLE_CELL: {
			// Table cell
			final TableCellPos pos = new TableCellPos();
			final BlockParams params = new BlockParams();
			this.mapper.setupTableCellPos(pos, style, this.context.isRightSide());
			this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			final TableCellBox cell = new TableCellBox(params, pos, new FlowContainer());
			this.sink.start(cell);
			this.context.setInTextBlock(false);
			final TableSlotTracker slots = this.tableSlots.peek();
			if (slots != null) {
				slots.placeCell(pos.colspan, pos.rowspan);
			}

			// Start multi-column layout
			style = this.startColumns(style, cell);
		}
			break;

		case DisplayValue.CONTENTS:
			// display:contents (CSS Display 3 §2.5, 2026-08-07). Creates no box for the element
			// itself; children flow directly into the currently open box (the nearest non-contents
			// ancestor's box). Push the style onto the stack: children inherit from it during
			// style resolution. StyleEventMachine.characters wraps direct text
			// in an anonymous inline.
			break;

		default:
			throw new IllegalStateException();
		}

		this.context.setCurrentStyle(style);
	}


	void _endStyle() {
		final CSSStyle style = this.context.getCurrentStyle();
		final byte closing = Display.get(style);
		if (closing == DisplayValue.TABLE_ROW) {
			this.fillSlotGaps(style);
		} else if ((closing == DisplayValue.TABLE || closing == DisplayValue.INLINE_TABLE)
				&& CSSJInternalImage.getImage(style) == null) {
			this.tableSlots.poll();
		}
		this.sink.endContentsSource(style);
		if (!this.context.isInBody()) {
			this.context.setInBody(true);
			this._startStyle(style);
		}
		final byte endDisplay = Display.get(style);
		// contents created no box when opened (CONTENTS branch in openBox),
		// so there is no box to close.
		if (CSSJInternalImage.getImage(style) == null && endDisplay != DisplayValue.CONTENTS) {
			this.sink.end();
			if (this.wrappedContainers.remove(style)) {
				// Absolutely positioned Grid/Flex: close the inner anonymous box, then the outer absolutely positioned box
				this.sink.end();
			}
		}
		switch (endDisplay) {
		case DisplayValue.TABLE:
		case DisplayValue.INLINE_TABLE:
		case DisplayValue.BLOCK:
		case DisplayValue.GRID:
		case DisplayValue.FLEX:
		case DisplayValue.LIST_ITEM:
		case DisplayValue.TABLE_CAPTION:
		case DisplayValue.TABLE_COLUMN_GROUP:
		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_CELL:
			this.context.setInTextBlock(false);
			break;

		case DisplayValue.INLINE_BLOCK:
			this.context.setInTextBlock(true);
			break;

		case DisplayValue.INLINE:
		case DisplayValue.CONTENTS:
			break;

		default:
			throw new IllegalStateException();
		}

		this.context.setCurrentStyle(style.getParentStyle());
	}

	private void beginSlotRowGroup() {
		final TableSlotTracker slots = this.tableSlots.peek();
		if (slots != null) {
			slots.beginRowGroup();
		}
	}

	/**
	 * Before closing a row, fills empty columns after this row's cells and before continuing
	 * rowspans with empty anonymous cells (2026-09-29; rationale in {@link TableSlotTracker}).
	 */
	private void fillSlotGaps(final CSSStyle row) {
		final TableSlotTracker slots = this.tableSlots.peek();
		if (slots == null) {
			return;
		}
		while (slots.hasGapBeforeCarried()) {
			final CSSStyle cell = row.inheritAnonStyle(CSSElement.ANON_TD);
			cell.set(Display.INFO, DisplayValue.TABLE_CELL_VALUE);
			// Inheriting a row direction orthogonal to the table makes cells orthogonal too, changing row splitting.
			cell.set(BlockFlow.INFO, slots.table.get(BlockFlow.INFO));
			this._startStyle(cell);
			this._endStyle();
		}
		slots.endRow();
	}

}
