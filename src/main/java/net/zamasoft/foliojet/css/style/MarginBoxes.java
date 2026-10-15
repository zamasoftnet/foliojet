package net.zamasoft.foliojet.css.style;

import java.awt.geom.AffineTransform;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.MarginBoxName;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.StringFunctionValue;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.ua.PageAssignmentState;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.impl.property.box.BoxSizing;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.box.VerticalAlign;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.LetterSpacing;
import net.zamasoft.foliojet.css.impl.property.font.LineHeight;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.impl.property.text.TextAlignLast;
import net.zamasoft.foliojet.css.impl.property.text.TextFillColor;
import net.zamasoft.foliojet.css.impl.property.text.UnicodeBidi;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.impl.property.text.WordSpacing;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.CellAlign;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.sizing.MeasuredIntrinsics;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.css.style.running.RunningRenderer;

/**
 * Layout and drawing of page margin boxes (css-page-3 §7).
 *
 * <p>
 * Called when a page is finalized (drawPage). Does not touch the live StyleBuilder /
 * DocumentBuilder / LayoutSource. Lays out each box in an isolated mini-layout with a
 * fresh DocumentBuilder (same isolation principle as SourceReplayer in M6b v3).
 * </p>
 *
 * <p>
 * Currently supports: content strings; counter()/counters() (page-level counters:
 * page/pages and those from @page counter-*); string() (GCPM, reads PageAssignmentState);
 * fonts, color, text-align, vertical-align (top/middle/bottom), margin/border/padding/background.
 * Vertical writing (writing-mode) since 2026-09-06: vertical-align controls top/bottom alignment,
 * the group of lines is centered in the band, and the background fills the region
 * (matching Vivliostyle observations). Unsupported (FINE log): url() images, quotes,
 * attr(), page-ref. Width distribution uses the basic css-page-3 §7.3 form
 * (center takes precedence; otherwise proportional to max-content).
 * Absolute {@code width}/{@code height} (2026-10-15, TECH-20261015-006 ③) size the box inside its region and put it
 * against the page area, as Vivliostyle does: center boxes stay centered, auto margins center the box.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class MarginBoxes {
	private static final Logger LOG = Logger.getLogger(MarginBoxes.class.getName());

	/** A sufficiently large dimension for max-content measurement. */
	private static final double INFINITE = 1e6;

	private MarginBoxes() {
		// pure functions
	}

	/**
	 * Lays out and draws the page's margin boxes.
	 *
	 * @param ua           user agent
	 * @param styleContext style context (obtains margin box declarations)
	 * @param pageElement  current page pseudo-element (:left/:right/:first)
	 * @param pageBox      finalized page
	 * @param drawer       drawing destination (coordinates originate at the page content area)
	 * @param visitor      visitor
	 * @param blank        whether this is an empty page caused by a forced page break ({@code @page :blank})
	 */
	static void draw(final UserAgent ua, final StyleContext styleContext, final CSSElement pageElement,
			final String pageName, final PageBox pageBox, final Drawer drawer, final Visitor visitor,
			final RunningRenderer running, final boolean blank) {
		final Map<MarginBoxName, Declaration> declarations = styleContext.pageMarginBoxes(pageElement, pageName,
				blank);
		if (declarations.isEmpty()) {
			return;
		}
		final Map<MarginBoxName, Box> boxes = new EnumMap<MarginBoxName, Box>(MarginBoxName.class);
		for (final Map.Entry<MarginBoxName, Declaration> e : declarations.entrySet()) {
			final Box box = Box.create(ua, e.getKey(), e.getValue(), running);
			if (box != null) {
				boxes.put(e.getKey(), box);
			}
		}
		if (boxes.isEmpty()) {
			return;
		}

		// Drawer coordinates originate at the page content area (paper origin is (-margin.left, -margin.top)).
		// PageBox width/height cover the whole sheet, so subtract margins.
		final AbsoluteInsets margin = pageBox.getFrame().margin;
		final double contentW = pageBox.getWidth() - margin.left - margin.right;
		final double contentH = pageBox.getHeight() - margin.top - margin.bottom;

		// Top/bottom bands (content area width between corners). A box of explicit height sits against the page area.
		band(ua, boxes, MarginBoxName.TOP_LEFT, MarginBoxName.TOP_CENTER, MarginBoxName.TOP_RIGHT, 0, -margin.top,
				contentW, margin.top, 1, drawer, visitor);
		band(ua, boxes, MarginBoxName.BOTTOM_LEFT, MarginBoxName.BOTTOM_CENTER, MarginBoxName.BOTTOM_RIGHT, 0,
				contentH, contentW, margin.bottom, 0, drawer, visitor);

		// Corners
		corner(ua, boxes.get(MarginBoxName.TOP_LEFT_CORNER), -margin.left, -margin.top, margin.left, margin.top, 1, 1,
				drawer, visitor);
		corner(ua, boxes.get(MarginBoxName.TOP_RIGHT_CORNER), contentW, -margin.top, margin.right, margin.top, 0, 1,
				drawer, visitor);
		corner(ua, boxes.get(MarginBoxName.BOTTOM_LEFT_CORNER), -margin.left, contentH, margin.left, margin.bottom, 1,
				0, drawer, visitor);
		corner(ua, boxes.get(MarginBoxName.BOTTOM_RIGHT_CORNER), contentW, contentH, margin.right, margin.bottom, 0, 0,
				drawer, visitor);

		// Side columns (content area height between corners)
		column(ua, boxes, MarginBoxName.LEFT_TOP, MarginBoxName.LEFT_MIDDLE, MarginBoxName.LEFT_BOTTOM, -margin.left,
				0, margin.left, contentH, 1, drawer, visitor);
		column(ua, boxes, MarginBoxName.RIGHT_TOP, MarginBoxName.RIGHT_MIDDLE, MarginBoxName.RIGHT_BOTTOM, contentW,
				0, margin.right, contentH, 0, drawer, visitor);
	}

	/**
	 * Distributes widths and places the three boxes in a top/bottom band (basic css-page-3 §7.3:
	 * if a center box exists, fix it at the center with equal sides; otherwise proportional to max-content).
	 */
	private static void band(final UserAgent ua, final Map<MarginBoxName, Box> boxes, final MarginBoxName leftName,
			final MarginBoxName centerName, final MarginBoxName rightName, final double x, final double y,
			final double w, final double h, final double ay, final Drawer drawer, final Visitor visitor) {
		final Box left = boxes.get(leftName);
		final Box center = boxes.get(centerName);
		final Box right = boxes.get(rightName);
		if (left == null && center == null && right == null) {
			return;
		}
		if (center != null) {
			final double cw = Math.min(center.preferredOuterWidth(ua), w);
			final double side = (w - cw) / 2;
			place(ua, left, x, y, side, h, 0, crossY(left, ay), drawer, visitor);
			place(ua, center, x + side, y, cw, h, 0.5, crossY(center, ay), drawer, visitor);
			place(ua, right, x + w - side, y, side, h, 1, crossY(right, ay), drawer, visitor);
		} else if (left != null && right != null) {
			final double pl = left.preferredOuterWidth(ua);
			final double pr = right.preferredOuterWidth(ua);
			final double lw = (pl + pr) <= 0 ? w / 2 : Math.min(w * pl / (pl + pr), w);
			place(ua, left, x, y, lw, h, 0, crossY(left, ay), drawer, visitor);
			place(ua, right, x + lw, y, w - lw, h, 1, crossY(right, ay), drawer, visitor);
		} else if (left != null) {
			place(ua, left, x, y, w, h, 0, crossY(left, ay), drawer, visitor);
		} else {
			place(ua, right, x, y, w, h, 1, crossY(right, ay), drawer, visitor);
		}
	}

	/**
	 * Distributes heights and places the three boxes in a side column (vertical version of a band).
	 */
	private static void column(final UserAgent ua, final Map<MarginBoxName, Box> boxes, final MarginBoxName topName,
			final MarginBoxName middleName, final MarginBoxName bottomName, final double x, final double y,
			final double w, final double h, final double ax, final Drawer drawer, final Visitor visitor) {
		final Box top = boxes.get(topName);
		final Box middle = boxes.get(middleName);
		final Box bottom = boxes.get(bottomName);
		if (top == null && middle == null && bottom == null) {
			return;
		}
		if (middle != null) {
			final double ch = Math.min(middle.preferredOuterHeight(ua, w, h), h);
			final double side = (h - ch) / 2;
			place(ua, top, x, y, w, side, crossX(top, ax), 0, drawer, visitor);
			place(ua, middle, x, y + side, w, ch, crossX(middle, ax), 0.5, drawer, visitor);
			place(ua, bottom, x, y + h - side, w, side, crossX(bottom, ax), 1, drawer, visitor);
		} else if (top != null && bottom != null) {
			final double pt = top.preferredOuterHeight(ua, w, h);
			final double pb = bottom.preferredOuterHeight(ua, w, h);
			final double th = (pt + pb) <= 0 ? h / 2 : Math.min(h * pt / (pt + pb), h);
			place(ua, top, x, y, w, th, crossX(top, ax), 0, drawer, visitor);
			place(ua, bottom, x, y + th, w, h - th, crossX(bottom, ax), 1, drawer, visitor);
		} else if (top != null) {
			place(ua, top, x, y, w, h, crossX(top, ax), 0, drawer, visitor);
		} else {
			place(ua, bottom, x, y, w, h, crossX(bottom, ax), 1, drawer, visitor);
		}
	}

	/** Places a corner box against the page area's corner; auto margins center it on that axis. */
	private static void corner(final UserAgent ua, final Box box, final double x, final double y, final double w,
			final double h, final double ax, final double ay, final Drawer drawer, final Visitor visitor) {
		place(ua, box, x, y, w, h, crossX(box, ax), crossY(box, ay), drawer, visitor);
	}

	/** Position on the cross axis of a top/bottom band: auto margin-top and margin-bottom center the box. */
	private static double crossY(final Box box, final double ay) {
		return box != null && box.autoMarginY ? 0.5 : ay;
	}

	/** Position on the cross axis of a side column: auto margin-left and margin-right center the box. */
	private static double crossX(final Box box, final double ax) {
		return box != null && box.autoMarginX ? 0.5 : ax;
	}

	/**
	 * Lays out and draws a box in a rectangle (vertical position follows vertical-align).
	 * A box of explicit width or height narrower than the rectangle takes that size and sits at {@code ax}/{@code ay}
	 * of the slack (0 start, 0.5 center, 1 end).
	 */
	private static void place(final UserAgent ua, final Box box, double x, double y, double w, double h,
			final double ax, final double ay, final Drawer drawer, final Visitor visitor) {
		if (box == null || w <= 0 || h <= 0) {
			return;
		}
		// A size larger than the region is kept too: the box reaches out of the region on the side away from the
		// page area (a 40pt badge in a 30pt margin stays round).
		if (Double.isFinite(box.outerWidth())) {
			x += (w - box.outerWidth()) * ax;
			w = box.outerWidth();
		}
		if (Double.isFinite(box.outerHeight())) {
			y += (h - box.outerHeight()) * ay;
			h = box.outerHeight();
		}
		final boolean text = box.running == null;
		final PageBox mini = box.layout(ua, w, h, text);
		if (mini == null) {
			return;
		}
		final boolean vertical = box.params.flow.isVertical();
		// Align on physical axes (2026-09-06, user handoff §4, Vivliostyle observations):
		// vertical-align controls top/bottom (y) alignment in both horizontal and vertical writing.
		// Vertical writing stacks lines along x, so center the group of lines in the band
		// (do not use text-align for inline-axis alignment in vertical writing; Box.create fixes it to start).
		// Horizontal padding/margins only narrow the content box and do not affect wrapping;
		// vertical padding/margins shorten the line length.
		// Lay out a vertical mini-page once with line length=region height (so percentage
		// padding uses the same basis as placement). Keep the frame (background/borders)
		// over the whole region and shift only the content by dy (as in Vivliostyle: the background fills the region).
		// If line start is the physical bottom (sideways-lr, vertical rtl), content is already
		// at the bottom, so shift dy toward the top by the remaining space. Running templates
		// align along the inline axis using their own text-align; do not add dy and double the alignment.
		// String boxes are fixed to the region, so determine alignment inside padding (the content box)
		// from the inner container's actual size. Running templates retain the previous behavior
		// (box matches content size; alignment uses the mini-page's outer dimensions).
		final RectFrame frame = box.params.frame;
		final Container inner = text ? innerContainer(mini) : null;
		final double innerW = text ? Math.max(0, w - frame.margin.getLeft() - frame.margin.getRight()
				- frame.border.getFrameWidth() - frame.padding.getLeft() - frame.padding.getRight()) : w;
		final double innerH = text ? Math.max(0, h - frame.margin.getTop() - frame.margin.getBottom()
				- frame.border.getFrameHeight() - frame.padding.getTop() - frame.padding.getBottom()) : h;
		final double blockSize = inner != null ? inner.getContentSize() : mini.getContainer().getContentSize();
		final double extent = !vertical ? blockSize
				: box.running != null ? h : MeasuredIntrinsics.usedLineExtent(inner, box.params.flow);
		final double slack = Math.max(0, innerH - extent);
		double dy;
		switch (box.verticalAlign) {
		case START:
			dy = 0;
			break;
		case END:
			dy = slack;
			break;
		default:
			// MIDDLE / BASELINE (treated as middle in margin boxes)
			dy = slack / 2;
			break;
		}
		if (vertical && inlineStartsAtBottom(box.params)) {
			dy -= slack;
		}
		final double dx = vertical ? Math.max(0, (innerW - blockSize) / 2) : 0;
		// frames is the background/border layer; draw is the content layer (same two layers as PageBox.drawFlow).
		// A vertical RL mini-page stacks lines from the right edge, so shift x to the left.
		final double drawX = x + (box.params.flow == WritingMode.RL ? -dx : dx);
		final double drawY = y + dy;
		if (box.running != null) {
			RunningRenderer.draw(mini, drawer, drawX, drawY);
		} else {
			// Keep the frame (background/borders) in the box fixed to the region; align only the drawn content.
			mini.frames(mini, drawer, null, new AffineTransform(), x, y);
			mini.draw(mini, drawer, visitor, null, new AffineTransform(), drawX, drawY, drawX, drawY);
		}
	}

	/** The inner container of the mini-page's first block box (the margin box itself). */
	private static Container innerContainer(final PageBox mini) {
		final AbstractContainerBox[] found = { null };
		mini.getContainer().eachFlowBox(b -> {
			if (found[0] == null && b instanceof AbstractContainerBox c) {
				found[0] = c;
			}
		});
		return found[0] == null ? mini.getContainer() : found[0].getContainer();
	}

	/**
	 * Whether line start is the physical bottom in a vertical mini-page (content already at the bottom).
	 * Use the same conditions as actual line-box reversal: for sideways,
	 * {@code LayoutUtils.inlineToPhysical} always reverses BOTTOM_TO_TOP progression;
	 * for ordinary vertical rtl, {@code AbstractLineBox} swaps start/end only when paragraph
	 * bidi is enabled (third codex review, 2026-09-06).
	 */
	private static boolean inlineStartsAtBottom(final BlockParams params) {
		if (TypesettingMode.usesSidewaysInlineAxis(params.flow, params.writingModeVariant)) {
			return TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
					params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		}
		return params.direction == net.zamasoft.foliojet.layout.box.params.AbstractTextParams.DIRECTION_RTL;
	}

	/**
	 * The composed result for one margin box (style + content text).
	 */
	private static final class Box {
		final BlockParams params;

		final String text;
		final RunningRenderer.Content running;

		final CellAlign verticalAlign;

		/** Explicit width and height including padding, border and margins; infinite when auto. */
		private double outerWidth = Double.POSITIVE_INFINITY, outerHeight = Double.POSITIVE_INFINITY;

		/** Whether both margins on the axis are auto (the box then centers in its region). */
		boolean autoMarginX, autoMarginY;

		private double preferredWidth = -1;

		private Box(BlockParams params, String text, CellAlign verticalAlign, RunningRenderer.Content running) {
			this.params = params;
			this.text = text;
			this.verticalAlign = verticalAlign;
			this.running = running;
		}

		double outerWidth() {
			return this.outerWidth;
		}

		double outerHeight() {
			return this.outerHeight;
		}

		/** The explicit outer width, or the max-content width when width is auto. */
		double preferredOuterWidth(final UserAgent ua) {
			return Double.isFinite(this.outerWidth) ? this.outerWidth : this.preferredWidth(ua);
		}

		/** The explicit outer height, or the content height when height is auto. */
		double preferredOuterHeight(final UserAgent ua, final double width, final double availableHeight) {
			return Double.isFinite(this.outerHeight) ? this.outerHeight
					: this.preferredHeight(ua, width, availableHeight);
		}

		/**
		 * Composes a box from declarations. Returns null if no content is generated.
		 */
		static Box create(final UserAgent ua, final MarginBoxName name, final Declaration declaration,
				final RunningRenderer renderer) {
			final CSSStyle style = CSSStyle.getCSSStyle(ua, null, CSSElement.BEFORE);
			// Allow variables declared on the root element to be referenced (var())
			style.setCustomPropertyFallback(ua.getDocumentContext().getRootStyle());
			// UA defaults for each box position (equivalent to css-page-3 Appendix A).
			// Set before applyProperties so declarations can override them.
			style.set(TextAlign.INFO, defaultTextAlign(name));
			style.set(VerticalAlign.INFO, defaultVerticalAlign(name));
			declaration.applyProperties(style);

			final Value[] contents = Content.get(style);
			if (contents == null) {
				return null;
			}
			final StringBuilder text = new StringBuilder();
			RunningRenderer.Content running = null;
			for (final Value v : contents) {
				switch (v) {
				case StringValue str -> text.append(str.getString());
				case CounterValue counter -> text
						.append(CounterStyles.of(ua).format(counterValue(ua, counter.getName()), counter.getStyle()));
				case CountersValue counters -> text.append(
						CounterStyles.of(ua).format(counterValue(ua, counters.getName()), counters.getStyle()));
				case StringFunctionValue sf -> {
					final PageAssignmentState.Resolution<String> result = ua.getPassContext().getStringState()
							.resolve(sf.getName(), sf.getMode());
					if (result.presence() == PageAssignmentState.Presence.VALUE) {
						text.append(result.value());
					}
				}
				case ElementFunctionValue element -> {
					running = renderer.prepare(element, style);
					if (running == null) {
						return null;
					}
				}
				default -> LOG.log(Level.FINE, "マージンボックスで未対応のcontent値: {0}", v);
				}
			}

			final BlockParams params = new BlockParams();
			params.element = style.getCSSElement();
			params.frame = RectFrame.create(
					BoxValueUtils.toInsets(Margin.get(style, Side.TOP), Margin.get(style, Side.RIGHT),
							Margin.get(style, Side.BOTTOM), Margin.get(style, Side.LEFT)),
					BoxStyleMapper.createRectBorder(style), BoxStyleMapper.createBackground(style),
					BoxValueUtils.toInsets(Padding.get(style, Side.TOP), Padding.get(style, Side.RIGHT),
							Padding.get(style, Side.BOTTOM), Padding.get(style, Side.LEFT)));
			params.color = TextFillColor.get(style);
			params.fontStyle = style.getFontStyle();
			params.fontManager = ua.getFontManager();
			params.lineBreakRules = LanguageProfileBundle.getLanguageProfile(style.getCSSElement().lang)
					.getTextBreakingRules(style);
			params.direction = Direction.get(style);
			params.unicodeBidi = UnicodeBidi.get(style);
			params.bidiSemanticAlias = UAProps.OUTPUT_PDF_BIDI_ACTUAL_TEXT.getBoolean(ua);
			params.flow = net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style);
			params.writingModeVariant = net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.get(style);
			if (params.flow.isVertical()) {
				// place() uses vertical-align for inline-axis (top/bottom) alignment in vertical writing.
				// The mini-page line length is exactly the band height, so setting center here
				// would move a vertical-align: top running header to the vertical center.
				params.textAlign = net.zamasoft.foliojet.layout.box.params.AbstractLineParams.TEXT_ALIGN_START;
				params.textAlignLast = net.zamasoft.foliojet.layout.box.params.AbstractLineParams.TEXT_ALIGN_START;
			} else {
				params.textAlign = TextAlign.get(style);
				params.textAlignLast = TextAlignLast.get(style);
			}
			params.lineHeight = LineHeight.get(style);
			params.whiteSpace = WhiteSpace.get(style);
			params.letterSpacing = LetterSpacing.get(style);
			params.wordSpacing = WordSpacing.get(style);
			final Box box = new Box(params, text.toString(), VerticalAlign.getForTableCell(style), running);
			// Absolute width/height only; percentages and keywords stay auto. So does a size whose padding or margin on
			// that axis is a percentage (it resolves against the box itself here) or whose outer size is not positive.
			final RectFrame frame = params.frame;
			final boolean contentBox = BoxSizing.get(style) != BoxSizingMode.BORDER_BOX;
			if (Width.get(style) instanceof AbsoluteLengthValue width
					&& !frame.margin.getLeftType().needsReference() && !frame.margin.getRightType().needsReference()
					&& !frame.padding.getLeftType().needsReference() && !frame.padding.getRightType().needsReference()) {
				final double outer = width.getLength() + frame.margin.getLeft() + frame.margin.getRight()
						+ (contentBox ? frame.border.getFrameWidth() + frame.padding.getLeft() + frame.padding.getRight()
								: 0);
				if (outer > 0) {
					box.outerWidth = outer;
				}
			}
			if (Height.get(style) instanceof AbsoluteLengthValue height
					&& !frame.margin.getTopType().needsReference() && !frame.margin.getBottomType().needsReference()
					&& !frame.padding.getTopType().needsReference() && !frame.padding.getBottomType().needsReference()) {
				final double outer = height.getLength() + frame.margin.getTop() + frame.margin.getBottom()
						+ (contentBox ? frame.border.getFrameHeight() + frame.padding.getTop()
								+ frame.padding.getBottom() : 0);
				if (outer > 0) {
					box.outerHeight = outer;
				}
			}
			box.autoMarginX = Margin.get(style, Side.LEFT) == KeywordValue.AUTO
					&& Margin.get(style, Side.RIGHT) == KeywordValue.AUTO;
			box.autoMarginY = Margin.get(style, Side.TOP) == KeywordValue.AUTO
					&& Margin.get(style, Side.BOTTOM) == KeywordValue.AUTO;
			return box;
		}

		/**
		 * Measures max-content width through actual layout (caches the result).
		 */
		double preferredWidth(final UserAgent ua) {
			if (this.preferredWidth < 0) {
				final PageBox wide = this.layout(ua, INFINITE, INFINITE);
				this.preferredWidth = wide == null ? 0 : this.params.flow.isVertical()
						? wide.getContainer().getContentSize()
						: MeasuredIntrinsics.usedLineExtent(wide.getContainer(), this.params.flow);
			}
			return this.preferredWidth;
		}

		/**
		 * Measures content height when laid out at the given width. For vertical writing,
		 * uses line length=available height (same percentage-padding basis as placement;
		 * infinite height would let padding alone fill the region: third codex review).
		 * For horizontal writing, uses infinite height to obtain the natural height.
		 */
		double preferredHeight(final UserAgent ua, final double width, final double availableHeight) {
			final PageBox mini = this.layout(ua, width, this.params.flow.isVertical() ? availableHeight : INFINITE);
			return mini == null ? 0 : this.params.flow.isVertical()
					? MeasuredIntrinsics.usedLineExtent(mini.getContainer(), this.params.flow)
					: mini.getContainer().getContentSize();
		}

		/**
		 * Lays out the content in an isolated mini-layout.
		 */
		PageBox layout(final UserAgent ua, final double width, final double height) {
			return this.layout(ua, width, height, false);
		}

		/**
		 * Lays out the content in an isolated mini-layout. With {@code fill}, fixes the box size
		 * to the region (width×height, excluding margin): background and borders cover the entire
		 * region, and {@link MarginBoxes#place} aligns the content (Vivliostyle observations,
		 * 2026-09-06: margin box backgrounds fill the allocated region in both writing modes).
		 * Measurement (preferredWidth/Height) needs natural dimensions, so does not fix the size.
		 */
		PageBox layout(final UserAgent ua, final double width, final double height, final boolean fill) {
			if (this.running != null) {
				return this.running.layout(this.params, width, height);
			}
			if (fill) {
				final Insets margin = this.params.frame.margin;
				this.params.boxSizing = BoxSizingMode.BORDER_BOX;
				this.params.size = Dimension.create(Math.max(0, width - margin.getLeft() - margin.getRight()),
						Math.max(0, height - margin.getTop() - margin.getBottom()), LengthType.ABSOLUTE,
						LengthType.ABSOLUTE);
			} else {
				this.params.boxSizing = BoxSizingMode.CONTENT_BOX;
				this.params.size = Dimension.AUTO_DIMENSION;
			}
			final MeasurePageGenerator pg = new MeasurePageGenerator(ua, this.params, width, height, null, false);
			final DocumentBuilder doc = new DocumentBuilder(pg);
			doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
			doc.startBox(new FlowBlockBox(this.params, new FlowPos()));
			if (!this.text.isEmpty()) {
				final char[] ch = this.text.toCharArray();
				doc.characters(-1, ch, 0, ch.length, true);
			}
			doc.endBox();
			doc.end();
			return pg.getLastPage();
		}

		private static int counterValue(final UserAgent ua, final String name) {
			// Margin boxes use page-level (level 0) counters
			// (page / pages / @page counter-*). Document-tree counters are unsupported
			// because they need a snapshot at the page break (no FINE log:
			// zero for an undefined counter is as specified).
			final CounterScope scope = ua.getPassContext().getCounterScope(0, false);
			if (scope != null && scope.defined(name)) {
				return scope.get(name);
			}
			return 0;
		}

		private static Value defaultTextAlign(final MarginBoxName name) {
			return switch (name) {
			case TOP_LEFT_CORNER, BOTTOM_LEFT_CORNER -> TextAlignValue.RIGHT_VALUE;
			case TOP_RIGHT_CORNER, BOTTOM_RIGHT_CORNER -> TextAlignValue.LEFT_VALUE;
			case TOP_LEFT, BOTTOM_LEFT -> TextAlignValue.LEFT_VALUE;
			case TOP_CENTER, BOTTOM_CENTER -> TextAlignValue.CENTER_VALUE;
			case TOP_RIGHT, BOTTOM_RIGHT -> TextAlignValue.RIGHT_VALUE;
			// Side boxes are centered
			default -> TextAlignValue.CENTER_VALUE;
			};
		}

		private static Value defaultVerticalAlign(final MarginBoxName name) {
			return switch (name) {
			case LEFT_TOP, RIGHT_TOP -> VerticalAlignValue.TOP_VALUE;
			case LEFT_BOTTOM, RIGHT_BOTTOM -> VerticalAlignValue.BOTTOM_VALUE;
			default -> VerticalAlignValue.MIDDLE_VALUE;
			};
		}
	}
}
