package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.css.lang.LanguageProfile;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.ContentFunctionValue;
import net.zamasoft.foliojet.css.value.CounterSetValue;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.ListStylePositionValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.QuoteValue;
import net.zamasoft.foliojet.css.value.QuotesValue;
import net.zamasoft.foliojet.css.value.StringFunctionValue;
import net.zamasoft.foliojet.css.value.StringSetEntryValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TargetCounterValue;
import net.zamasoft.foliojet.css.value.TargetTextValue;
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.box.CSSFloat;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.box.Clear;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.content.CounterIncrement;
import net.zamasoft.foliojet.css.impl.property.content.CounterReset;
import net.zamasoft.foliojet.css.impl.property.content.CounterSet;
import net.zamasoft.foliojet.css.impl.property.content.StringSet;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.font.LineHeight;
import net.zamasoft.foliojet.css.impl.property.content.ListStyleImage;
import net.zamasoft.foliojet.css.impl.property.content.ListStylePosition;
import net.zamasoft.foliojet.css.impl.property.content.ListStyleType;
import net.zamasoft.foliojet.css.impl.property.page.PageBreakAfter;
import net.zamasoft.foliojet.css.impl.property.page.PageBreakBefore;
import net.zamasoft.foliojet.css.impl.property.content.Quotes;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.impl.property.text.TextIndent;
import net.zamasoft.foliojet.css.impl.property.box.VerticalAlign;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.box.BoxSizing;
import net.zamasoft.foliojet.css.impl.property.column.ColumnCount;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisColor;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisPosition;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisStyle;
import net.zamasoft.foliojet.css.impl.property.text.TextFillColor;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.foliojet.layout.util.TextUtils;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.PageAssignmentState;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.PageRef.Fragment;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.PendingStringSet;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Inset;

/**
 * State machine for style events (startStyle/characters/endStyle)
 * (StyleBuilder decomposition, increment 5, 2026-07-30; bodies moved verbatim from
 * StyleBuilder, with unchanged behavior). Owns state for counters, named strings,
 * target references, list markers, quotes, generated content, and ::first-letter.
 *
 * <p>
 * Synthetic ::before/::after/::first-letter events reenter this object's
 * {@code startStyle}/{@code endStyle} (depth bounded by pseudo-element nesting).
 * M6a Segment recording points remain at their original positions.
 * </p>
 */
final class StyleEventMachine {
	private static final Logger LOG = Logger.getLogger(StyleEventMachine.class.getName());

	private static final ValueListValue LF = new ValueListValue(new Value[] { new StringValue("\n") });

	private static final RelativeLengthValue EM_1_618 = RelativeLengthValue.em(1.618);
	private static final RelativeLengthValue EM_1_414 = RelativeLengthValue.em(1.414);
	private static final RelativeLengthValue EM_1_4 = RelativeLengthValue.em(1.4);

	private final StyleBuildContext context;
	private final Segment segment;
	private final RecordingLayoutSink sink;
	private final BoxStyleMapper mapper;
	private final StyleBoxEmitter emitter;
	private final PageSequence pageSequence;
	private final UserAgent ua;

	/** Generated-content reference resolution (string-set/target-* family, separated in increment 14). */
	private final GeneratedContentResolver generated;
	private final StyleContext styleContext;

	private boolean warnedReservedCounter = false;
	private int depth = 0;
	private int quoteLevel = 0;
	/** Counter for list items. Each entry is int[]{depth, value}. */
	private final List<int[]> listCounterStack = new ArrayList<int[]>();
	private Marker marker = null;
	private boolean firstLetter = false;
	private final net.zamasoft.foliojet.css.style.running.RunningCapture runningCapture;

	StyleEventMachine(final StyleBuildContext context, final Segment segment, final RecordingLayoutSink sink,
			final BoxStyleMapper mapper, final StyleBoxEmitter emitter, final PageSequence pageSequence,
			final UserAgent ua, final StyleContext styleContext) {
		this.context = context;
		this.segment = segment;
		this.sink = sink;
		this.mapper = mapper;
		this.emitter = emitter;
		this.pageSequence = pageSequence;
		this.ua = ua;
		this.generated = new GeneratedContentResolver(ua);
		this.styleContext = styleContext;
		this.runningCapture = new net.zamasoft.foliojet.css.style.running.RunningCapture(ua, styleContext,
				sink::assignment, this::warnElementFunction);
	}

	CSSStyle capturedStyle() {
		return this.runningCapture.currentStyle();
	}

	void startStyle(CSSStyle style) {
		final CSSElement ce = style.getCSSElement();

		short explDisplay = Display.get(style);
		if (this.runningCapture.start(style)) {
			return;
		}

		// @container G4 (2026-08-15 stage 4, development record §2):
		// For a container-type: inline-size element, record that it is a query container
		// and its name now (style finalized, before layout).
		// Write its measured inline-size separately after layout is finalized
		// (AbstractVisitor.visitBox). Exclude pseudo-elements: their elementKey is unstable (-1).
		if (!ce.isPseudoElement() && ce.elementKey >= 0
				&& net.zamasoft.foliojet.css.impl.property.container.ContainerType.get(style) //
						== net.zamasoft.foliojet.css.value.ContainerTypeValue.INLINE_SIZE) {
			this.ua.getUAContext().getContainerFacts().setInlineSizeContainer(ce.elementKey,
					net.zamasoft.foliojet.css.impl.property.container.ContainerName.get(style));
		}

		if (!ce.isPseudoElement()) {
			// Record the main-flow segment (M6a)
			this.segment.startStyle(style);
		}
		this.closeAnonymousStyles(style, explDisplay);

		this.emitBrClearance(style, ce);

		final boolean footnote = this.startFootnote(style, ce, explDisplay);

		this.settleMarkerBeforeTable(style, explDisplay);

		this.emitter._startStyle(style);

		this.firstLetter = true;
		if (!ce.isPseudoElement()) {
			++this.depth;
		}
		int depth = this.depth;

		this.applyCounterProperties(style, depth);

		this.applyStringSets(style, ce, depth);

		this.startListMarker(style, explDisplay, depth);

		this.emitGeneratedContent(style, ce, depth);

		// Footnotes F1: synthesize ::footnote-marker (number) at the start of the body text.
		// Order at body text start: list marker → footnote-marker → ::before.
		if (footnote) {
			this.footnotePseudo(style, CSSElement.FOOTNOTE_MARKER);
		}

		this.synthesizeBefore(style, ce);
	}

	/**
	 * Closes anonymous styles (anonymous table boxes) according to the starting element's display.
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 */
	private void closeAnonymousStyles(final CSSStyle style, final short explDisplay) {
		if (this.context.getCurrentStyle() != null) {
			WHILE: while (this.context.getCurrentStyle().isAnonStyle()) {
				// Close anonymous styles

				// Apply only to static elements
				final byte pos = CSSPosition.get(style);
				if (pos != PositionValue.STATIC && pos != PositionValue.RELATIVE && pos != PositionValue.STICKY) {
					break WHILE;
				}

				{
					// Table-related
					final short anonDisplay = Display.get(this.context.getCurrentStyle());
					switch (explDisplay) {
					case DisplayValue.TABLE_HEADER_GROUP:
					case DisplayValue.TABLE_FOOTER_GROUP:
					case DisplayValue.TABLE_ROW_GROUP:
						switch (anonDisplay) {
						case DisplayValue.TABLE_ROW:
						case DisplayValue.TABLE_ROW_GROUP:
							break;
						default:
							break WHILE;
						}
						break;
					case DisplayValue.TABLE_CELL:
						switch (anonDisplay) {
						case DisplayValue.TABLE_ROW_GROUP:
						case DisplayValue.TABLE:
						case DisplayValue.INLINE_TABLE:
							break;
						default:
							break WHILE;
						}
						break;
					case DisplayValue.INLINE:
					case DisplayValue.BLOCK:
					case DisplayValue.GRID:
					case DisplayValue.FLEX:
					case DisplayValue.LIST_ITEM:
					case DisplayValue.INLINE_BLOCK:
					case DisplayValue.TABLE:
					case DisplayValue.INLINE_TABLE:
						switch (anonDisplay) {
						case DisplayValue.TABLE_ROW:
							CSSStyle parent = this.context.getCurrentStyle().getParentStyle();
							if (!parent.isAnonStyle() || !parent.getParentStyle().isAnonStyle()) {
								break WHILE;
							}
						case DisplayValue.TABLE_ROW_GROUP:
						case DisplayValue.TABLE:
						case DisplayValue.INLINE_TABLE:
							break;
						default:
							break WHILE;
						}
						break;
					default:
						break WHILE;
					}
				}
				if (style.getParentStyle() == this.context.getCurrentStyle()) {
					style.removeAnonStyle();
				}
				this.emitter._endStyle();
			}
		}
	}

	/**
	 * Performs {@code <br>} clearance and forced page breaks with an empty block.
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 */
	private void emitBrClearance(final CSSStyle style, final CSSElement ce) {
		// BR
		if (XHTML.BR_ELEM.equalsElement(ce)) {
			// Clearance and forced page breaks generate a block afterward
			ClearMode clear = Clear.get(style);
			PageBreakMode pageBreakBefore = this.mapper.toPageBreak(PageBreakBefore.get(style), this.context.isRightSide());
			PageBreakMode pageBreakAfter = this.mapper.toPageBreak(PageBreakAfter.get(style), this.context.isRightSide());
			if (clear != ClearMode.NONE || pageBreakBefore != PageBreakMode.AUTO
					|| pageBreakAfter != PageBreakMode.AUTO) {
				// Perform clearance, etc.
				final FlowPos pos = new FlowPos();
				pos.clear = clear;
				pos.pageBreakBefore = pageBreakBefore;
				pos.pageBreakAfter = pageBreakAfter;
				BlockParams params = new BlockParams();
				params.fontStyle = style.getFontStyle();
				params.fontManager = this.ua.getFontManager();
				params.lineBreakRules = LanguageProfileBundle
						.getLanguageProfile(style.getLang()).getTextBreakingRules(style);
				params.direction = Direction.get(style);
				params.flow = BlockFlow.get(style);
				params.writingModeVariant = net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.get(style);
				params.element = ce;
				final Insets margin = Insets.create(0, 0, -LineHeight.get(style), 0, LengthType.ABSOLUTE,
						LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
				params.frame = RectFrame.create(margin, RectBorder.NONE_RECT_BORDER,
						Background.NULL_BACKGROUND, Insets.NULL_INSETS);
				// Insert after anonymous-box processing to avoid problems inside tables
				FlowBlockBox flowBox = new FlowBlockBox(params, pos);
				this.sink.start(flowBox);
				this.sink.end();
			}
		}
	}

	/**
	 * For a {@code float: footnote} element, advances the footnote number and synthesizes a call
	 * (::footnote-call) into the parent's inline flow.
	 *
	 * @return {@code true} if treated as a footnote
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 */
	private boolean startFootnote(final CSSStyle style, final CSSElement ce, final short explDisplay) {
		// Footnotes F1 (2026-07-31, consult-codex-2026-07-31-footnote.txt §3):
		// At the start of a float:footnote element, advance the footnote number
		// (engine-owned document sequence, global "footnote" counter) and synthesize
		// ::footnote-call at the call site in the parent's inline flow. Per-page reset
		// is deferred until the page-local replay increment (F5). Moving body text to the
		// page bottom is wired in F3; until then, it is drawn in place.
		// display:contents creates no box, so float does not apply either (CSS Display 3)
		boolean footnote = !ce.isPseudoElement() && explDisplay != DisplayValue.NONE
				&& explDisplay != DisplayValue.CONTENTS
				&& CSSFloat.get(style) == CSSFloatValue.FOOTNOTE;
		if (footnote && inTableStructure(style)) {
			// **Do not create footnotes inside table structure (rows, row groups, columns)**
			// (discovered in a sweep, 2026-08-02). The call (::footnote-call) is synthesized
			// as an inline in the parent, but inlines cannot sit directly under table structure.
			// This fell into construction of a box requiring TableBuilder,
			// failing conversion. Inside cells, footnotes still work as before.
			// The proper solution is to use the mechanism that wraps inlines directly under table
			// structure in anonymous cells (remaining footnote work in PLAN); until then, fall back to normal floats.
			if (!this.warnedFootnoteInTableStructure) {
				this.warnedFootnoteInTableStructure = true;
				LOG.warning("float: footnote inside a table structure (row/row-group/column)"
						+ " is not supported; treated as a normal float");
			}
			footnote = false;
		}
		if (footnote) {
			final net.zamasoft.foliojet.ua.FootnoteArea area = this.ua.getUAContext().getFootnoteArea();
			final boolean bottomBand = area.isPageBand()
					&& this.pageSequence.getProgression().isVertical();
			// Reserve the bottom band once at page start to avoid different capacities across columns.
			for (CSSStyle ancestor = style.getParentStyle(); !bottomBand && ancestor != null; ancestor = ancestor
					.getParentStyle()) {
				if (ColumnCount.get(ancestor) > 1) {
					if (!area.isHeightFixed() && this.sink.isEligibleFootnoteColumnOwner()) {
						LOG.info("footnotes inside a multi-column element are placed at the end of the column containing the call");
					} else {
						LOG.warning("footnote inside a multi-column ancestor: column heights may become uneven");
					}
					break;
				}
			}
			// F4: attach a logical ID (independent of the displayed number) to both the original element and ::footnote-call.
			// Used for set membership checks at page finalization: did the call remain on this page?
			style.footnoteId = this.nextFootnoteId++;
			this.ua.getPassContext().getCounterScope(0, true).increment("footnote", 1);
			this.footnotePseudo(style, CSSElement.FOOTNOTE_CALL);
			// **Lay out footnote body text in the page footnote area's writing direction**
			// (2026-09-03, cti.li report). A note in a horizontal figure's caption (orthogonal flow)
			// inside a vertical book inherited the figure's horizontal direction; its width
			// became page-axis occupancy, reserving most of the type area (232pt/202pt in a real document).
			// The figure and note could not fit on the same page, so the figure kept being deferred.
			// The stagnation safeguard placed the note on a page without its call, and numbering
			// fell back to document sequence. The footnote area belongs to the page, so the note
			// follows the page's direction, not its original position (the call was already synthesized
			// above and keeps the original direction). Ignore even an author's writing-mode on the note.
			// Only when @footnote specifies the area's direction does that direction replace the page's (F-1).
			final WritingMode page = area.flow == null ? this.pageSequence.getProgression() : area.flow;
			if (BlockFlow.get(style) != page) {
				style.set(BlockFlow.INFO, switch (page) {
				case RL -> net.zamasoft.foliojet.css.value.BlockFlowValue.RL_VALUE;
				case LR -> net.zamasoft.foliojet.css.value.BlockFlowValue.LR_VALUE;
				default -> net.zamasoft.foliojet.css.value.BlockFlowValue.TB_VALUE;
				}, CSSStyle.MODE_IMPORTANT);
			}
			if (area.isPageBand()
					&& this.pageSequence.getProgression().isVertical() && page == WritingMode.TB) {
				// Before the box and replay recipe capture dimensions, set horizontal line length to the type area width.
				// Do not use vertical body text line length or the original host's width (figure, table, etc.).
				// The band's line length is the sheet's horizontal inner dimension (physical). Choosing
				// the axis from page box flow would select the vertical inner dimension on the first
				// page box (TB), created before progression direction is finalized.
				// Supply width as the note's border-box and set left/right margins to 0 (the band spans
				// the sheet width, so adding author padding/borders must not overflow it:
				// codex F-1 review). Even if the carryover page has a different width
				// (named pages), retain the width of the call's page.
				// For named pages with input ahead, use B's current width; do not freeze delayed C's width.
				final PageBox pageBox = this.pageSequence.getCurrentPage();
				if (pageBox != null) {
					style.set(Width.INFO, AbsoluteLengthValue.create(this.ua, this.sink.footnoteLineWidth(pageBox)),
							CSSStyle.MODE_IMPORTANT);
					style.set(BoxSizing.INFO, net.zamasoft.foliojet.css.value.css3.BoxSizingValue.BORDER_BOX_VALUE,
							CSSStyle.MODE_IMPORTANT);
					style.set(Margin.LEFT, AbsoluteLengthValue.ZERO, CSSStyle.MODE_IMPORTANT);
					style.set(Margin.RIGHT, AbsoluteLengthValue.ZERO, CSSStyle.MODE_IMPORTANT);
				}
			}
			if (area.isPageBand()
					&& this.pageSequence.getProgression().isVertical() && page.isVertical()
					&& area.isHeightFixed()) {
				// Bottom band in vertical writing (2026-09-11). When the band itself uses vertical
				// writing, the note's **line length is the band's inner dimension along sheet height**:
				// the inline axis is physical height, so height corresponds to width for a horizontal band.
				// Without this, line length comes from the host or type area, and the note extends
				// below the type area and is clipped (observed: y=86→230 on 220pt paper).
				//
				// Band dimension descriptors include the gap, so subtract it for line length.
				final double band = area.height.doubleValue() - FOOTNOTE_BAND_GAP;
				if (band > 0) {
					style.set(Height.INFO, AbsoluteLengthValue.create(this.ua, band), CSSStyle.MODE_IMPORTANT);
					style.set(BoxSizing.INFO, net.zamasoft.foliojet.css.value.css3.BoxSizingValue.BORDER_BOX_VALUE,
							CSSStyle.MODE_IMPORTANT);
					style.set(Margin.TOP, AbsoluteLengthValue.ZERO, CSSStyle.MODE_IMPORTANT);
					style.set(Margin.BOTTOM, AbsoluteLengthValue.ZERO, CSSStyle.MODE_IMPORTANT);
				}
			}
		}
		return footnote;
	}

	/** Gap between the bottom band and body text (equal to {@code RootBuilder.FOOTNOTE_GAP}). */
	private static final double FOOTNOTE_BAND_GAP = 6;

	/**
	 * Finalizes an outside list marker before opening a table (prevents it from entering cell content), or a box
	 * whose writing mode is orthogonal to the list item's.
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 *
	 * <p>
	 * An orthogonal first child has no line box of the list item's, so the marker went into the child's own first
	 * line, where it took that line's start in the other axis: the first characters moved down by the marker
	 * (a vertical-lr child of a horizontal li), a block in an inline moved over by a line, and a floated li grew past
	 * its shrink-to-fit width off the paper (fit sweep seed 12297742, 2026-10-08). Chrome gives the marker no
	 * room there. Settle it before the child, overlaying, as for a table.
	 * </p>
	 */
	private void settleMarkerBeforeTable(final CSSStyle style, final short explDisplay) {
		// Outside list markers are normally deferred to the line created by the first character.
		// If the first child is a table, however, that character first appears in its first cell.
		// Deferring that far mixes the marker into cell content; splitting a row can then leave
		// only the marker in the earlier fragment and the cell body in the later fragment,
		// moving the body to a later page than neighboring cells (seed 455). Finalize the marker
		// before opening the table, while still directly under list-item.
		if (this.marker != null && (explDisplay == DisplayValue.TABLE || explDisplay == DisplayValue.INLINE_TABLE
				|| BlockFlow.get(style).isVertical() != this.marker.ownerVertical)) {
			if (this.marker.box instanceof OutsideMarkerBox outsideMarker) {
				outsideMarker.setOverlaysFollowingBlock(true);
			}
			this.checkMarker();
		}
	}

	/**
	 * Applies {@code counter-reset} / {@code counter-set} / {@code counter-increment}.
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 */
	private void applyCounterProperties(final CSSStyle style, final int depth) {
		// Reset counters
		Value[] resets = CounterReset.get(style);
		if (resets != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < resets.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) resets[i];
				String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.warnReservedCounter(name);
					continue;
				}
				int value = counterSet.getValue();
				CounterScope scope = pc.getCounterScope(0, false);
				if (scope != null && scope.defined(name)) {
					scope.reset(name, value);
					continue;
				}
				pc.getCounterScope(depth, true).reset(name, value);
			}
		}

		// Set counters (counter-set, CSS Lists 3; 2026-08-02).
		// Assign to the innermost existing counter without creating a new nesting level
		// (same lookup as counter-increment; create on this element if absent).
		final Value[] sets = CounterSet.get(style);
		if (sets != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < sets.length; ++i) {
				final CounterSetValue counterSet = (CounterSetValue) sets[i];
				final String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.warnReservedCounter(name);
					continue;
				}
				int level = depth;
				for (; level > 0; --level) {
					final CounterScope scope = pc.getCounterScope(level, false);
					if (scope != null && scope.defined(name)) {
						break;
					}
				}
				if (level == 0) {
					final CounterScope root = pc.getCounterScope(0, false);
					if (root == null || !root.defined(name)) {
						// Not found anywhere: create on this element
						level = depth;
					}
				}
				pc.getCounterScope(level, true).reset(name, counterSet.getValue());
			}
		}

		// Increment counters
		final Value[] increments = CounterIncrement.get(style);
		if (increments != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < increments.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) increments[i];
				String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.warnReservedCounter(name);
					continue;
				}
				int delta = counterSet.getValue();
				int level = depth;
				for (; level > 0; --level) {
					CounterScope scope = pc.getCounterScope(level, false);
					if (scope != null && scope.defined(name)) {
						break;
					}
				}
				pc.getCounterScope(level, true).increment(name, delta);
			}
		}
	}

	/**
	 * Resolves counter/attr, etc. at input time and passes every assignment to a placement anchor.
	 * Handles forward references from generated content in body text with buildStringState,
	 * separate from finalized page state.
	 */
	private void applyStringSets(final CSSStyle style, final CSSElement ce, final int depth) {
		final Value[] stringSets = StringSet.get(style);
		if (stringSets != null) {
			final long order = this.ua.getPassContext().getRunningRegistry().nextOrder();
			final List<PendingStringSet> assignments = new ArrayList<PendingStringSet>();
			// Later values replace same-name assignments on the same element; do not register the same (name, order) twice
			final java.util.LinkedHashMap<String, List<Object>> byName = new java.util.LinkedHashMap<String, List<Object>>();
			for (int i = 0; i < stringSets.length; ++i) {
				final StringSetEntryValue entry = (StringSetEntryValue) stringSets[i];
				final Value[] parts = entry.getParts();
				final List<Object> resolvedParts = new ArrayList<Object>(parts.length);
				for (int j = 0; j < parts.length; ++j) {
					final Value part = parts[j];
					if (part instanceof ContentFunctionValue) {
						resolvedParts.add(PendingStringSet.CONTENT);
					} else {
						resolvedParts.add(this.generated.stringSetPart(part, ce, depth));
					}
				}
				byName.put(entry.getName(), resolvedParts);
			}
			for (final Map.Entry<String, List<Object>> e : byName.entrySet()) {
				final String name = e.getKey();
				final List<Object> resolvedParts = e.getValue();
				assignments.add(new PendingStringSet(name, resolvedParts, order));
				this.ua.getPassContext().getBuildStringState().begin(name, order);
				if (!resolvedParts.contains(PendingStringSet.CONTENT)) {
					// Register immediately at build time too, so body-side string() on the same page can read it
					final StringBuilder buff = new StringBuilder();
					for (final Object part : resolvedParts) {
						buff.append((String) part);
					}
					this.ua.getPassContext().getBuildStringState().assign(name, buff.toString(), order, false);
				}
			}
			if (!assignments.isEmpty()) {
				this.sink.stringAssignments(assignments, style,
						Display.get(style) == DisplayValue.CONTENTS ? null : this.sink.sourceBox());
			}
		}
	}

	/**
	 * Creates the marker (::marker) for {@code display: list-item}.
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 */
	private void startListMarker(final CSSStyle style, final short explDisplay, final int depth) {
		// Marker
		if (explDisplay == DisplayValue.LIST_ITEM) {
			int[] counter = null;
			if (!this.listCounterStack.isEmpty()) {
				counter = (int[]) this.listCounterStack.get(this.listCounterStack.size() - 1);
				if (counter[0] == depth) {
					++counter[1];
				} else {
					counter = null;
				}
			}
			if (counter == null) {
				int start = 1;
				CSSStyle parentStyle = style;
				for (parentStyle = parentStyle
						.getParentStyle(); parentStyle != null; parentStyle = parentStyle
								.getParentStyle()) {
					CSSElement parentCe = parentStyle.getCSSElement();
					if (parentCe == null) {
						continue;
					}
					if (XHTML.UL_ELEM.equalsElement(parentCe)) {
						break;
					}
					if (XHTML.OL_ELEM.equalsElement(parentCe)) {
						String str = parentCe.atts.getValue("start");
						if (str != null) {
							try {
								start = Integer.parseInt(str);
							} catch (NumberFormatException e) {
								ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "OL", "start" + str);
							}
						}
						break;
					}
				}
				counter = new int[] { depth, start };
				this.listCounterStack.add(counter);
			}
			if (style.getCSSElement() != null && XHTML.LI_ELEM.equalsElement(style.getCSSElement())) {
				String value = style.getCSSElement().atts.getValue("value");
				if (value != null) {
					try {
						counter[1] = Integer.parseInt(value);
					} catch (NumberFormatException e) {
						ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "LI", "value" + value);
					}
				}
			}

			int number = counter[1];
			InlinePos pos = new InlinePos();
			// Added 2026-07-21: ::marker (CSS Lists). Resolve the cascade for CSSElement.MARKER
			// using the same mechanism as BEFORE/AFTER, then override only limited properties
			// (color/font-*, etc.) in li's actual style.
			// list-style-type/list-style-position, etc. do not apply to ::marker,
			// so always read them from li's actual style (style),
			// as specified.
			// The marker is always a child style of li, even without ::marker rules (2026-10-08): using li's own
			// style handed li's non-inherited properties to the marker box, so `li { height: 96pt }` made the
			// marker, and with it the first line, 96pt tall and pushed the second line to the bottom of the li
			// (sweep fit seed 12475813; Chrome keeps the lines together).
			this.styleContext.startElement(CSSElement.MARKER);
			final Declaration markerDeclaration = this.styleContext.merge(null);
			final CSSStyle markerStyle = CSSStyle.getCSSStyle(this.ua, style, CSSElement.MARKER);
			if (markerDeclaration != null) {
				markerDeclaration.applyProperties(markerStyle);
			}
			this.styleContext.endElement();
			BlockParams params = new BlockParams();
			this.mapper.setupBlockParams(params, markerStyle, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			this.mapper.setupInlinePos(pos, markerStyle);
			params.frame = RectFrame.NULL_FRAME;
			short listStyleType = ListStyleType.get(style);
			Image image = ListStyleImage.get(style);
			if (image == null) {
				image = GeneratedValueUtils.format(listStyleType, params.color, params.fontStyle);
				if (image != null) {
					// A bullet image stands for the glyph browsers draw, so its line gets the strut like a text
					// line, in quirks mode too (2026-10-08; TextBuilder.addStrutIfTextless). A list-style-image
					// keeps the document's mode, as an image line does in browsers.
					params.strictLineBox = true;
				}
			}
			this.marker = null;
			Marker marker = null;
			if (image == null) {
				final CounterStyles counterStyles = CounterStyles.of(this.ua);
				String str = counterStyles.format(number, listStyleType);
				if (str != null) {
					marker = new Marker();
					// Use the previous period for built-in types; for author-defined types,
					// use prefix/suffix descriptors (default ".").
					marker.text = (counterStyles.prefix(listStyleType) + str
							+ counterStyles.suffix(listStyleType) + ' ').toCharArray();
				}
			} else {
				marker = new Marker();
				ReplacedParams rparams = new ReplacedParams();
				this.mapper.setupParams(rparams, markerStyle);
				rparams.image = image;
				marker.imageBox = new InlineReplacedBox(rparams, pos);
			}
			if (marker != null) {
				switch (ListStylePosition.get(style)) {
				case ListStylePositionValue.INSIDE:
					// Inside marker
					marker.box = new net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox(params, pos);
					this.marker(marker);
					break;
				case ListStylePositionValue.OUTSIDE:
					// Outside marker
					marker.box = new OutsideMarkerBox(params, pos);
					marker.ownerVertical = BlockFlow.get(style).isVertical();
					this.marker = marker;
					break;
				default:
					throw new IllegalStateException();
				}
			}
		}
	}

	/**
	 * Emits ::before / ::after {@code content}.
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 */
	private void emitGeneratedContent(final CSSStyle style, final CSSElement ce, final int depth) {
		// element() is for margin boxes only. Warn and discard declarations on normal elements/pseudo-elements.
		final Value[] contents = Content.get(style);
		if (contents != null) {
			for (final Value value : contents) {
				if (value instanceof ElementFunctionValue) {
					this.warnElementFunction();
					return;
				}
			}
		}
		// Generate content (footnote call/marker moved to footnotePseudo label compilation
		// in F5 to avoid baking numbers into text).
		if (ce == CSSElement.AFTER || ce == CSSElement.BEFORE) {
			if (contents != null) {
				for (int i = 0; i < contents.length; ++i) {
					final Value v = contents[i];
					switch (v) {
					case StringValue stringValue: {
						// String
						String str = stringValue.getString();
						if (str.length() > 0) {
							char[] ch = str.toCharArray();
							this.checkMarker();
							this.sink.characters(-1, ch, 0, ch.length, true);
						}
					}
						break;
					case URIValue uriValue: {
						// Image
						URI uri = uriValue.getURI();
						Image image = ImageLoadDiagnostics.load(this.ua, uri,
								(resolvedUri, source) -> this.ua.getImage(source));
						if (image != null) {
							ReplacedParams rparams = new ReplacedParams();
							this.mapper.setupParams(rparams, style);
							rparams.image = image;
							AbstractReplacedBox replaced = new InlineReplacedBox(rparams,
									new InlinePos());
							this.checkMarker();
							this.sink.replaced(replaced);
						}
					}
						break;

					case CounterValue counter: {
						// Counter
						final String name = counter.getName();
						final short counterStyle = counter.getStyle();
						int number = 0;
						final PassContext pc = this.ua.getPassContext();
						for (int level = depth; level >= 0; --level) {
							CounterScope scope = pc.getCounterScope(level, false);
							if (scope != null && scope.defined(name)) {
								number = scope.get(name);
								break;
							}
						}
						this.counter(number, counterStyle, style);
					}
						break;

					case CountersValue counters: {
						// Counter
						final String name = counters.getName();
						final String delim = counters.getDelimiter();
						final short counterStyle = counters.getStyle();
						boolean first = true;
						final PassContext pc = this.ua.getPassContext();
						for (int level = 0; level <= depth; ++level) {
							CounterScope scope = pc.getCounterScope(level, false);
							if (scope != null && scope.defined(name)) {
								if (!first && delim != null && delim.length() > 0) {
									char[] ch = delim.toCharArray();
									this.checkMarker();
									this.sink.characters(-1, ch, 0, ch.length, true);
								}
								first = false;
								final int number = scope.get(name);
								this.counter(number, counterStyle, style);
							}
						}
					}
						break;

					case QuoteValue quote: {
						// Quotes
						Value[] quotesList = Quotes.get(style);

						switch (quote.getQuote()) {
						case QuoteValue.OPEN_QUOTE: {
							if (quotesList != null) {
								String str = ((QuotesValue) quotesList[Math.min(this.quoteLevel,
										quotesList.length - 1)]).getOpen();
								if (str.length() > 0) {
									char[] ch = str.toCharArray();
									this.checkMarker();
									this.sink.characters(-1, ch, 0, ch.length, true);
								}
							}
							++this.quoteLevel;
						}
							break;

						case QuoteValue.CLOSE_QUOTE: {
							if (this.quoteLevel > 0) {
								--this.quoteLevel;
								if (quotesList != null) {
									String str = ((QuotesValue) quotesList[Math.min(this.quoteLevel,
											quotesList.length - 1)]).getClose();
									if (str.length() > 0) {
										char[] ch = str.toCharArray();
										this.checkMarker();
										this.sink.characters(-1, ch, 0, ch.length, true);
									}
								}
							}
						}
							break;

						case QuoteValue.NO_OPEN_QUOTE: {
							++this.quoteLevel;
						}
							break;

						case QuoteValue.NO_CLOSE_QUOTE: {
							if (this.quoteLevel > 0) {
								--this.quoteLevel;
							}
						}
							break;

						default:
							throw new IllegalStateException();
						}
					}
						break;
					case AttrValue attr: {
						// Attribute
						CSSElement parentCe = style.getParentStyle().getCSSElement();
						if (parentCe.atts != null) {
							String str = parentCe.atts.getValue(attr.getName());
							if (str != null && str.length() > 0) {
								char[] ch = str.toCharArray();
								this.checkMarker();
								this.sink.characters(-1, ch, 0, ch.length, true);
							}
						}
					}
						break;
					case StringFunctionValue sf: {
						// string()(GCPM)
						final PageAssignmentState.Resolution<String> result = this.ua.getPassContext().getBuildStringState()
								.resolve(sf.getName(), sf.getMode());
						final String str = result.presence() == PageAssignmentState.Presence.VALUE ? result.value() : null;
						if (str != null && str.length() > 0) {
							char[] ch = str.toCharArray();
							this.checkMarker();
							this.sink.characters(-1, ch, 0, ch.length, true);
						}
					}
						break;
					case TargetCounterValue pageRefFunc: {
						// Page number
						String ref = GeneratedContentResolver.targetRef(pageRefFunc.getType(), pageRefFunc.getRef(), style);
						if (ref != null) {
							this.pageRef(pageRefFunc, ref, style);
						}
					}
						break;
					case TargetTextValue targetText: {
						// Target text
						String ref = GeneratedContentResolver.targetRef(targetText.getType(), targetText.getRef(), style);
						if (ref != null) {
							this.targetText(targetText, ref);
						}
					}
						break;
					case net.zamasoft.foliojet.css.value.LeaderValue leader: {
						// leader() L1: carry the normalized pattern unchanged
						// (shaping and width allocation belong to layout).
						this.checkMarker();
						this.sink.leader(leader.getPattern());
					}
						break;
					default:
						throw new IllegalStateException(String.valueOf(v));
					}
				}
			}
		}
	}

	/** Warn about {@code content: element()} once per document. */
	private boolean elementFunctionWarned = false;

	/**
	 * Returns whether a pseudo-element's {@code content} can be generated. {@code element()}
	 * is for margin boxes only, so warn and omit the entire pseudo-element when a declaration
	 * contains it (leave no box/counter/string-set side effects; codex review 2026-09-05 R1a #4).
	 */
	private boolean usableGeneratedContent(final CSSStyle pseudoStyle) {
		final Value[] contents = Content.get(pseudoStyle);
		if (contents == null) {
			return false;
		}
		for (final Value value : contents) {
			if (value instanceof ElementFunctionValue) {
				this.warnElementFunction();
				return false;
			}
		}
		return true;
	}

	private void warnElementFunction() {
		if (!this.elementFunctionWarned) {
			this.elementFunctionWarned = true;
			this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX,
					String.valueOf(this.ua.getDocumentContext().getBaseURI()),
					"content: element() はマージンボックスでのみ使用できます");
		}
	}

	/**
	 * Synthesizes the element's ::before (not for a synthetic pseudo-element itself).
	 * (Extracted from startStyle on 2026-09-02; body moved unchanged.)
	 */
	private void synthesizeBefore(final CSSStyle style, final CSSElement ce) {
		// before (do not create ::before/::after for synthetic pseudo-elements themselves)
		if (!ce.isPseudoElement()
				&& CSSJInternalImage.getImage(style) == null) {
			// :before
			CSSElement beforeCe = CSSElement.BEFORE;
			this.styleContext.startElement(beforeCe);
			final Declaration beforeDeclaration = this.styleContext.merge(null);
			if (beforeDeclaration != null || HTMLStyle.hasBeforeContent(ce)) {
				CSSStyle beforeStyle = CSSStyle.getCSSStyle(this.ua, style, beforeCe);
				HTMLStyle.applyBeforeStyle(beforeStyle);
				if (beforeDeclaration != null) {
					beforeDeclaration.applyProperties(beforeStyle);
				}
				if (this.usableGeneratedContent(beforeStyle) && Display.get(beforeStyle) != DisplayValue.NONE) {
					this.startStyle(beforeStyle);
					this.endStyle();
				}
			}
			this.styleContext.endElement();
		}
	}

	/**
	 * Resolves one entry in the {@code string-set} value list to a string (entries that
	 * can be finalized at build time; the caller handles {@link ContentFunctionValue}
	 * separately). Treats image-based {@code list-style-type} as an empty string because
	 * it has no meaning as text.
	 */

	private void counter(int number, short counterStyle, CSSStyle style) {
		final String str = CounterStyles.of(this.ua).format(number, counterStyle);
		if (str != null) {
			char[] ch = str.toCharArray();
			this.checkMarker();
			// Counter
			this.sink.characters(-1, ch, 0, ch.length, true);
		} else {
			final ReplacedParams rparams = new ReplacedParams();
			this.mapper.setupParams(rparams, style);
			rparams.image = GeneratedValueUtils.format(counterStyle, CSSColor.get(style), style.getFontStyle());
			if (rparams.image != null) {
				final AbstractReplacedBox replaced = new InlineReplacedBox(rparams, new InlinePos());
				this.checkMarker();
				this.sink.replaced(replaced);
			}
		}
	}

	/**
	 * Warns when {@code counter-reset}/{@code counter-increment} specifies a reserved
	 * counter name ({@code pages}); only once per document. css-page-3 §6.1 reserves
	 * {@code pages} for the UA, so ignore explicit author declarations and continue
	 * (policy: warning + fallback, not an exception).
	 */
	void warnReservedCounter(String name) {
		if (!this.warnedReservedCounter) {
			this.warnedReservedCounter = true;
			LOG.warning("counter '" + name + "' is reserved by the UA (total page count) and cannot be "
					+ "reset/incremented by author style; ignoring.");
		}
	}

	/** Warn about disabled page references once per document. */
	private boolean warnedPageReferencesDisabled = false;

	/**
	 * Reports use of {@code target-counter()}, etc. while page references
	 * ({@code processing.page-references}) are disabled (2026-10-04,
	 * TECH-20261003-004 item ⑩). Previously this silently became empty, leaving page
	 * numbers absent from the output table of contents.
	 */
	private void warnPageReferencesDisabled() {
		if (!this.warnedPageReferencesDisabled) {
			this.warnedPageReferencesDisabled = true;
			this.ua.message(MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION, "target-counter()",
					net.zamasoft.foliojet.message.MessageCodeUtils.detail("2823.page-references"));
		}
	}

	private void targetText(TargetTextValue targetText, String ref) {
		if (!net.zamasoft.foliojet.ua.props.UAProps.PROCESSING_PAGE_REFERENCES.getBoolean(this.ua)) {
			this.warnPageReferencesDisabled();
		}
		PageRef pageRef = this.ua.getUAContext().getPageRef();
		if (pageRef == null) {
			return;
		}
		try {
			URI uri = PageRef.targetURI(this.ua.getDocumentContext().getEncoding(),
					this.ua.getDocumentContext().getBaseURI(), ref);
			Fragment frag = pageRef.getFragment(uri);
			if (frag == null) {
				return;
			}
			this.generated.checkConverged(pageRef, frag, null);
			if (frag.text == null || frag.text.length() == 0) {
				return;
			}
			char[] ch = frag.text.toCharArray();
			this.checkMarker();
			// Target text
			this.sink.characters(-1, ch, 0, ch.length, true);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
		}
	}

	/**
	 * Whether {@code target-counter()} can be laid out as a fixed-width field in a
	 * one-pass PDF (2026-10-04, docs/design/one-pass-target-counter-design.md).
	 * The initial version supports only decimal numbers, horizontal writing, and use
	 * outside running header capture.
	 */
	private boolean targetCounterSlot(final TargetCounterValue pageRefFunc, final CSSStyle style) {
		if (pageRefFunc.getSeparator() != null || this.runningCapture.isCapturing()) {
			return false;
		}
		final short type = pageRefFunc.getNumberStyleType();
		if (type != net.zamasoft.foliojet.css.value.ListStyleTypeValue.DECIMAL
				&& type != net.zamasoft.foliojet.css.value.ListStyleTypeValue.DECIMAL_LEADING_ZERO) {
			return false;
		}
		if (!net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage.available(this.ua)) {
			return false;
		}
		// Exclude vertical writing. Tate-chu-yoko (text-combine-upright) elements have a
		// horizontal direction themselves, so check ancestors too.
		for (CSSStyle s = style; s != null; s = s.getParentStyle()) {
			if (BlockFlow.get(s).isVertical()) {
				return false;
			}
		}
		return true;
	}

	private void pageRef(TargetCounterValue pageRefFunc, String ref, CSSStyle style) {
		final boolean slot = this.targetCounterSlot(pageRefFunc, style);
		if (!slot && !net.zamasoft.foliojet.ua.props.UAProps.PROCESSING_PAGE_REFERENCES.getBoolean(this.ua)) {
			this.warnPageReferencesDisabled();
		}
		PageRef pageRef = this.ua.getUAContext().getPageRef();
		if (pageRef == null) {
			return;
		}

		try {
			URI uri = PageRef.targetURI(this.ua.getDocumentContext().getEncoding(),
					this.ua.getDocumentContext().getBaseURI(), ref);
			if (slot) {
				// Lay out a field independent of the number value, and insert the value at drawing
				// time (at document close for references to later pages).
				final ReplacedParams rparams = new ReplacedParams();
				this.mapper.setupParams(rparams, style);
				rparams.image = new net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage(this.ua, uri,
						pageRefFunc.getCounter(), pageRefFunc.getNumberStyleType(), style.getFontStyle(),
						net.zamasoft.foliojet.css.impl.property.text.TextFillColor.get(style));
				this.checkMarker();
				this.sink.replaced(new InlineReplacedBox(rparams, new InlinePos()));
				return;
			}
			String sep = pageRefFunc.getSeparator();
			String counter = pageRefFunc.getCounter();
			char[] ch;
			if (sep == null) {
				Fragment frag = pageRef.getFragment(uri);
				if (frag == null) {
					return;
				}
				this.generated.checkConverged(pageRef, frag, counter);
				int count = frag.getCounterValue(counter);
				String str = CounterStyles.of(this.ua).format(count, pageRefFunc.getNumberStyleType());
				if (str == null) {
					return;
				}
				ch = str.toCharArray();
			} else {
				Collection<?> frags = pageRef.getFragments(uri);
				if (frags == null || frags.isEmpty()) {
					return;
				}
				IntList counts = new IntList();
				for (Iterator<?> j = frags.iterator(); j.hasNext();) {
					Fragment fragment = (Fragment) j.next();
					this.generated.checkConverged(pageRef, fragment, counter);
					int count = fragment.getCounterValue(counter);
					if (!counts.contains(count)) {
						counts.add(count);
					}
				}
				StringBuilder buff = new StringBuilder();
				for (int j = 0; j < counts.size(); ++j) {
					if (buff.length() > 0) {
						buff.append(sep);
					}
					String str = CounterStyles.of(this.ua).format(counts.get(j), pageRefFunc.getNumberStyleType());
					if (str != null) {
						buff.append(str);
					}
				}
				if (buff.length() <= 0) {
					return;
				}
				ch = buff.toString().toCharArray();
			}
			this.checkMarker();
			// Page reference
			this.sink.characters(-1, ch, 0, ch.length, true);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
		}
	}



	void characters(int charOffset, char[] ch, int off, int len) {
		assert len > 0;
		if (this.runningCapture.isCapturing()) {
			this.runningCapture.characters(ch, off, len);
			return;
		}
		if (this.context.getHtmlRootBlock() == null && this.context.getCurrentStyle() != null) {
			// Inside body text
			this.segment.characters(charOffset, ch, off, len); // Record the main-flow segment (M6a)
			if (!this.context.isInTextBlock()) {
				// Check text block start for block completion
				// StyledTextUnitizer performs the same processing for direct use
				// of the net.zamasoft.foliojet.layout package.
				final CSSStyle style = this.context.getCurrentStyle();
				TEXTBLOCK: switch (WhiteSpace.get(style)) {
				case AbstractTextParams.WHITE_SPACE_NORMAL:
				case AbstractTextParams.WHITE_SPACE_NOWRAP:
					// Require a character other than whitespace or a control code
					for (int i = 0; i < len; ++i) {
						char c = ch[i + off];
						if (!TextUtils.isWhiteSpace(c)) {
							break TEXTBLOCK;
						}
					}
					return;

				case AbstractTextParams.WHITE_SPACE_PRE_LINE:
					// Require a newline or a character other than whitespace or a control code
					for (int i = 0; i < len; ++i) {
						char c = ch[i + off];
						if (!TextUtils.isWhiteSpace(c) || c == '\n') {
							break TEXTBLOCK;
						}
					}
					return;
				case AbstractTextParams.WHITE_SPACE_PRE:
				case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
					break;
				default:
					throw new IllegalStateException();
				}
				this.context.setInTextBlock(true);
			}

			if (this.firstLetter) {
				this.firstLetter = false;

				// :first-letter
				this.styleContext.startElement(CSSElement.FIRST_LETTER);
				final Declaration declaration = this.styleContext.merge(null);
				this.styleContext.endElement();
				if (declaration != null) {
					final CSSStyle firstLetterStyle = CSSStyle.getCSSStyle(this.ua, this.context.getCurrentStyle(),
							CSSElement.FIRST_LETTER);
					declaration.applyProperties(firstLetterStyle);
					// Desugar initial-letter (css-inline-3) here into float + character dimensions
					// and use the existing mechanism (2026-08-20).
					net.zamasoft.foliojet.css.impl.property.text.InitialLetter.desugar(firstLetterStyle,
							this.context.getCurrentStyle());
					if (Display.get(firstLetterStyle) != DisplayValue.NONE) {
						this.startStyle(firstLetterStyle);
						final LanguageProfile lang = LanguageProfileBundle
								.getLanguageProfile(this.context.getCurrentStyle().getLang());
						int first = lang.countFirstLetter(ch, off, len);
						if (this.runningCapture.isCapturing()) {
							this.runningCapture.characters(ch, off, first);
						} else {
							this.checkMarker();
							this.sink.characters(charOffset, ch, off, first, false);
						}
						len -= first;
						off += first;
						charOffset += first;
						this.endStyle();
					}
					if (len == 0) {
						return;
					}
				}
			}
			this.checkMarker();

			if (this.context.getCurrentStyle() != null) {
				WHILE: while (this.context.getCurrentStyle().isAnonStyle()) {
					// Close anonymous styles
					final short anonDisplay = Display.get(this.context.getCurrentStyle());
					switch (anonDisplay) {
					case DisplayValue.TABLE_ROW:
						CSSStyle parent = this.context.getCurrentStyle().getParentStyle();
						if (!parent.isAnonStyle() || !parent.getParentStyle().isAnonStyle()) {
							break WHILE;
						}
					case DisplayValue.TABLE_ROW_GROUP:
					case DisplayValue.TABLE:
					case DisplayValue.INLINE_TABLE:
						break;
					default:
						break WHILE;
					}
					this.emitter._endStyle();
				}
			}

			// Wrap text directly under display:contents in an anonymous inline inheriting
			// the contents element's style (2026-08-07). contents creates no box,
			// so passing text through unchanged would lay it out with the outer box's parameters
			// (the style of an ancestor above contents), losing inherited color/font, etc.
			// specified on the contents element.
			final boolean inContents = Display.get(this.context.getCurrentStyle()) == DisplayValue.CONTENTS;
			if (inContents) {
				final CSSStyle contentsInline = this.context.getCurrentStyle().inheritAnonStyle(CSSElement.ANON);
				contentsInline.set(Display.INFO, DisplayValue.INLINE_VALUE);
				this.emitter._startStyle(contentsInline);
			}
			String em = TextEmphasisStyle.get(this.context.getCurrentStyle());
			if (em == null || em.length() == 0) {
				this.sink.characters(charOffset, ch, off, len, false);
			} else {
				// Emphasis marks
				final char[] emc = em.toCharArray();
				final net.zamasoft.foliojet.layout.box.params.WritingMode flow =
						BlockFlow.get(this.context.getCurrentStyle());
				final net.zamasoft.foliojet.layout.box.params.WritingModeVariant variant =
						net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant
								.get(this.context.getCurrentStyle());
				final boolean vert = net.zamasoft.foliojet.layout.box.params.TypesettingMode.isVertical(flow, variant);
				final boolean sideways = variant
						!= net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL;
				final var emPosition = TextEmphasisPosition.get(this.context.getCurrentStyle());
				Value color = this.context.getCurrentStyle().get(TextEmphasisColor.INFO);
				if (color == KeywordValue.DEFAULT) {
					color = this.context.getCurrentStyle().get(CSSColor.INFO);
				}
				for (int i = 0; i < len; ++i) {
					final CSSStyle eb = this.context.getCurrentStyle().inheritAnonStyle(CSSElement.ANON);
					eb.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
					eb.set(CSSPosition.INFO, PositionValue.RELATIVE_VALUE);
					eb.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
					if (vert) {
						eb.set(LineHeight.INFO, EM_1_618);
					} else {
						eb.set(LineHeight.INFO, EM_1_414);
					}
					this.emitter._startStyle(eb);
					final CSSStyle et = eb.inheritAnonStyle(CSSElement.ANON);
					et.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
					et.set(CSSPosition.INFO, PositionValue.ABSOLUTE_VALUE);
					et.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
					et.set(CSSColor.INFO, color);
					et.set(FontSize.INFO, PercentageValue.HALF);
					if (vert || sideways) {
						et.set(Height.INFO, PercentageValue.FULL);
						if (variant
								== net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW) {
							et.set(emPosition.isUnder() ? Inset.LEFT : Inset.RIGHT, EM_1_4);
						} else {
							et.set(emPosition.isLeft() ? Inset.RIGHT : Inset.LEFT, EM_1_4);
						}
					} else {
						et.set(Width.INFO, PercentageValue.FULL);
						et.set(emPosition.isUnder() ? Inset.TOP : Inset.BOTTOM, EM_1_4);
					}
					et.set(TextAlign.INFO, TextAlignValue.CENTER_VALUE);
					this.emitter._startStyle(et);
					this.sink.characters(-1, emc, 0, 1, false);
					this.emitter._endStyle();
					this.sink.characters(charOffset, ch, i + off, 1, false);
					this.emitter._endStyle();
				}
			}
			if (inContents) {
				// Close the anonymous inline for text directly under contents
				this.emitter._endStyle();
			}
		}
	}


	void checkMarker() {
		if (this.marker == null) {
			return;
		}
		// Outside marker
		Marker marker = this.marker;
		this.marker = null;
		this.marker(marker);
	}

	private void marker(Marker marker) {
		this.sink.start(marker.box);
		if (marker.text != null) {
			// Marker text
			this.sink.characters(-1, marker.text, 0, marker.text.length, false);
		} else if (marker.imageBox != null) {
			this.sink.replaced(marker.imageBox);
		}
		this.sink.end();
	}

	/** Assigns logical footnote IDs (F4; independent of the display-number counter "footnote"). */
	private long nextFootnoteId = 0;

	/**
	 * Whether inside table structure that cannot directly contain inlines (table, row,
	 * row group, column). {@code display:inline} can intervene, so walk up until reaching
	 * <b>an ancestor that can contain inlines</b> (block, cell, flex, etc.).
	 */
	private static boolean inTableStructure(final CSSStyle style) {
		for (CSSStyle parent = style.getParentStyle(); parent != null; parent = parent.getParentStyle()) {
			switch (Display.get(parent)) {
			case DisplayValue.INLINE:
			case DisplayValue.INLINE_BLOCK:
			case DisplayValue.CONTENTS:
				continue;
			default:
				return isTableStructure(Display.get(parent));
			}
		}
		return false;
	}

	/** Whether this is table structure that cannot directly contain inlines. */
	private static boolean isTableStructure(final byte display) {
		switch (display) {
		case DisplayValue.TABLE:
		case DisplayValue.INLINE_TABLE:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_COLUMN_GROUP:
			return true;
		default:
			return false;
		}
	}

	/** Warn about footnotes in table structure once per document. */
	private boolean warnedFootnoteInTableStructure = false;

	/** Warn about unsupported footnote label content once per document. */
	private boolean warnedFootnoteLabelContent = false;

	/**
	 * Synthesizes {@code ::footnote-call}/{@code ::footnote-marker} (footnotes F1,
	 * 2026-07-31; consult-codex-2026-07-31-footnote.txt §3). Cascades user pseudo-element
	 * rules with these names. If {@code content} is specified, emits it using
	 * {@link #startStyle}'s generation mechanism; otherwise emits the UA default:
	 * the footnote number (global "footnote" counter, with delimiters for marker).
	 * The UA default for call is a small superscript number (user rules override it later).
	 */
	private void footnotePseudo(final CSSStyle style, final CSSElement pseudoCe) {
		this.styleContext.startElement(pseudoCe);
		final Declaration declaration = this.styleContext.merge(null);
		final CSSStyle pseudoStyle = CSSStyle.getCSSStyle(this.ua, style, pseudoCe);
		pseudoStyle.footnoteId = style.footnoteId;
		if (pseudoCe == CSSElement.FOOTNOTE_CALL) {
			pseudoStyle.set(VerticalAlign.INFO, VerticalAlignValue.SUPER_VALUE);
			pseudoStyle.set(FontSize.INFO, PercentageValue.create(83));
		}
		if (declaration != null) {
			declaration.applyProperties(pseudoStyle);
		}
		if (pseudoCe == CSSElement.FOOTNOTE_CALL) {
			// F4: force ::footnote-call to **always be inline** (deliberate specification deviation).
			// The call's inline box is the sole fact used to determine page ownership at finalization;
			// removing it makes the footnote's placement destination indeterminate
			// (consult-codex-2026-07-31-footnote-f4.txt).
			// **Expanded on 2026-08-02 from only when display:none**:
			// computed display depends on the parent (anonymous table formatting).
			// With float:footnote on a `display:table` element, this pseudo-element computed
			// to table-cell, required TableBuilder, and failed conversion.
			// A call is an inline atom placed in the parent's flow, so it must not inherit
			// table formatting from the footnote element.
			pseudoStyle.set(Display.INFO, DisplayValue.INLINE_VALUE, CSSStyle.MODE_IMPORTANT);
		} else if (Display.get(pseudoStyle) == DisplayValue.NONE) {
			// The marker may be hidden
			this.styleContext.endElement();
			return;
		}
		this.startStyle(pseudoStyle);
		// F5 (2026-07-31, consult-codex-2026-07-31-footnote-f5.txt): do not bake the number
		// into text; emit an unresolved label atom with footnoteId
		// (an InlineReplacedBox holding FootnoteLabelImage).
		// At page finalization, RootBuilder resolves numbering from 1 on each page where
		// calls remain. Field width is fixed regardless of digit count (deliberate specification deviation).
		final boolean isMarker = pseudoCe == CSSElement.FOOTNOTE_MARKER;
		String prefix = "";
		String suffix = isMarker ? ". " : "";
		final Value[] labelContents = Content.get(pseudoStyle);
		if (labelContents != null) {
			// Accept only literal* counter(footnote,decimal) literal*.
			// Everything else is typed unsupported; do not silently bake in document-wide numbering.
			final StringBuilder pre = new StringBuilder();
			final StringBuilder post = new StringBuilder();
			boolean seenCounter = false;
			for (final Value v : labelContents) {
				if (v instanceof StringValue sv) {
					(seenCounter ? post : pre).append(sv.getString());
				} else if (v instanceof CounterValue cv && !seenCounter && cv.getName().equals("footnote")
						&& cv.getStyle() == net.zamasoft.foliojet.css.value.ListStyleTypeValue.DECIMAL) {
					seenCounter = true;
				} else if (!this.warnedFootnoteLabelContent) {
					// Treat specification limitations as warnings, not conversion failures (design review 2026-09-02
					// §1-6). Ignore unsupported content and lay out only numbers and strings.
					this.warnedFootnoteLabelContent = true;
					this.ua.message(MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION, "::footnote-call content",
							net.zamasoft.foliojet.message.MessageCodeUtils.detail("2823.footnote-label"));
				}
			}
			if (!seenCounter) {
				// Emit literal-only labels without numbers as ordinary generated content
				// (exclude from page numbering; e.g. symbolic footnotes).
				final String text = pre.toString();
				if (!text.isEmpty()) {
					final char[] chars = text.toCharArray();
					this.checkMarker();
					this.sink.characters(-1, chars, 0, chars.length, true);
				}
				this.endStyle();
				this.styleContext.endElement();
				return;
			}
			prefix = pre.toString();
			suffix = post.toString();
		}
		final ReplacedParams rparams = new ReplacedParams();
		this.mapper.setupParams(rparams, pseudoStyle);
		rparams.image = new net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage(pseudoStyle.footnoteId,
				isMarker, prefix, suffix, pseudoStyle.getFontStyle(), this.ua.getFontManager());
		final InlinePos labelPos = new InlinePos();
		this.mapper.setupInlinePos(labelPos, pseudoStyle);
		final AbstractReplacedBox labelBox = new InlineReplacedBox(rparams, labelPos);
		this.checkMarker();
		this.sink.replaced(labelBox);
		this.endStyle();
		this.styleContext.endElement();
	}


	void endStyle() {
		if (this.runningCapture.isCapturing()) {
			this.runningCapture.end();
			return;
		}
		CSSStyle style = this.context.getCurrentStyle();

		final CSSElement ce = style.getCSSElement();
		if (!ce.isPseudoElement()
				&& CSSJInternalImage.getImage(style) == null) {
			// :after (do not create for synthetic pseudo-elements themselves: footnotes F1
			// generalized the ce check from separate AFTER/BEFORE checks to isPseudoElement)
			boolean br = XHTML.BR_ELEM.equalsElement(ce);
			CSSElement afterCe = CSSElement.AFTER;
			this.styleContext.startElement(afterCe);
			final Declaration afterDeclaration = this.styleContext.merge(null);
			if (afterDeclaration != null || br || HTMLStyle.hasAfterContent(ce)) {
				CSSStyle afterStyle = CSSStyle.getCSSStyle(this.ua, style, afterCe);
				HTMLStyle.applyAfterStyle(afterStyle);
				if (br) {
					afterStyle.set(Content.INFO, LF);
					afterStyle.set(Clear.INFO, KeywordValue.INHERIT);
				}
				if (afterDeclaration != null) {
					afterDeclaration.applyProperties(afterStyle);
				}
				if (br && Display.get(afterStyle) == DisplayValue.INLINE) {
					PageBreakMode pageBreakBefore = this.mapper.toPageBreak(PageBreakBefore.get(afterStyle), this.context.isRightSide());
					PageBreakMode pageBreakAfter = this.mapper.toPageBreak(PageBreakAfter.get(afterStyle), this.context.isRightSide());
					if ((pageBreakBefore != PageBreakMode.AUTO
							&& pageBreakBefore != PageBreakMode.AVOID)
							|| (pageBreakAfter != PageBreakMode.AUTO
									&& pageBreakAfter != PageBreakMode.AVOID)) {
						afterStyle.set(Display.INFO, DisplayValue.BLOCK_VALUE);
					}
				}
				if (this.usableGeneratedContent(afterStyle) && Display.get(afterStyle) != DisplayValue.NONE) {
					this.startStyle(afterStyle);
					this.endStyle();
				}
			}
			this.styleContext.endElement();
		}

		// Close anonymous styles
		while (this.context.getCurrentStyle().isAnonStyle()) {
			this.emitter._endStyle();
		}

		// Close the explicit style
		style = this.context.getCurrentStyle();
		if (!style.getCSSElement().isPseudoElement()) {
			// Record the main-flow segment (M6a)
			this.segment.endStyle(style);
		}
		this.emitter._endStyle();
		if (this.context.getCurrentStyle() != null) {
			short explDisplay = Display.get(style);
			WHILE: while (this.context.getCurrentStyle().isInsertedAnonStyle()) {
				// Close anonymous styles
				final short anonDisplay = Display.get(this.context.getCurrentStyle());
				switch (explDisplay) {
				case DisplayValue.TABLE_CELL:
					switch (anonDisplay) {
					case DisplayValue.TABLE_ROW:
						// Stop at the row when closing a cell
						break WHILE;
					}
					break;
				case DisplayValue.TABLE_ROW:
					switch (anonDisplay) {
					// Stop at the row group when closing a row
					case DisplayValue.TABLE_ROW_GROUP:
						break WHILE;
					}
					break;
				case DisplayValue.INLINE:
				case DisplayValue.BLOCK:
				case DisplayValue.LIST_ITEM:
				case DisplayValue.INLINE_BLOCK:
				case DisplayValue.TABLE:
				case DisplayValue.INLINE_TABLE:
					switch (anonDisplay) {
					// Stop at the row if an anonymous cell was generated
					case DisplayValue.TABLE_ROW:
						break WHILE;
					}
					break;
				}
				if (style.getParentStyle() == this.context.getCurrentStyle()) {
					style.removeAnonStyle();
				}
				this.emitter._endStyle();
			}
		}

		if (!style.getCSSElement().isPseudoElement()) {
			// Clear list counters
			if (!this.listCounterStack.isEmpty()) {
				int[] counter = (int[]) this.listCounterStack.get(this.listCounterStack.size() - 1);
				if (counter[0] > this.depth) {
					this.listCounterStack.remove(this.listCounterStack.size() - 1);
				}
			}
			--this.depth;
		}
		this.firstLetter = false;
	}

}
