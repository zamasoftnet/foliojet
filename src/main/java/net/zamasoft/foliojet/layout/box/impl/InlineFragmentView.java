package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.text.LeaderQuad;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;

/**
 * An inline fragment used only for bidi visual order. Never inserted into logical {@code contents}.
 */
public final class InlineFragmentView extends InlineBox {
	private final InlineBox source;
	private final String semanticText;
	private final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission logicalLineEmission;
	private final java.util.function.Supplier<String> logicalLineVisualText;
	private final java.util.Map<Object, net.zamasoft.foliojet.layout.text.bidi.BidiSlice> bidiSlices;
	private boolean finished;
	private boolean keepsStartEdge, keepsEndEdge;

	public InlineFragmentView(final InlineBox source, final String semanticText,
			final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission logicalLineEmission,
			final java.util.function.Supplier<String> logicalLineVisualText,
			final java.util.Map<Object, net.zamasoft.foliojet.layout.text.bidi.BidiSlice> bidiSlices) {
		super(source.getInlineParams(), source.getInlinePos());
		this.source = source;
		this.semanticText = semanticText;
		this.logicalLineEmission = logicalLineEmission;
		this.logicalLineVisualText = logicalLineVisualText;
		this.bidiSlices = bidiSlices;
		source.copyDecorationTo(this);
		this.ascent = source.getAscent();
		this.descent = source.getDescent();
	}

	@Override
	protected net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission getLogicalLineEmission() {
		return this.logicalLineEmission;
	}

	@Override
	protected String getLogicalLineVisualText() {
		return this.logicalLineVisualText.get();
	}

	@Override
	protected net.zamasoft.foliojet.layout.text.bidi.BidiSlice getBidiSlice(final Object visualContent) {
		return this.bidiSlices.get(visualContent);
	}

	/** Adds content to the visual fragment. */
	public void append(final Object content) {
		if (content instanceof Text text) {
			this.addText(text);
			this.addAdvance(text.getAdvance());
		} else if (content instanceof Control control) {
			this.addControl(control);
			this.addAdvance(control.getAdvance());
		} else if (content instanceof AbstractTextBox.Inline inline) {
			// Place the copy made by flatten() as is (it must be the same reference as the BidiSlice key;
			// copying again here would prevent lookup of atomic slices such as ruby/warichu).
			this.add(inline);
			this.addAdvance(inline.box.getLineExtent(this.getTextParams().flow));
		} else if (content instanceof IAbsoluteBox absolute) {
			this.addAbsolute(absolute);
		} else if (content instanceof LeaderQuad leader) {
			this.addLeader(leader);
			this.addAdvance(leader.getAdvance());
		} else {
			throw new IllegalArgumentException(String.valueOf(content));
		}
	}

	/** Registers only the child fragment order first. Adds its width after the child edges are finalized. */
	public void appendFragment(final AbstractTextBox.Inline inline) {
		this.add(inline);
	}

	/**
	 * Retains each edge only in the fragment containing the original inline's logical start or end.
	 */
	public void finishEdges(final boolean keepStart, final boolean keepEnd, final double lineSize) {
		if (this.finished) {
			throw new IllegalStateException("inline fragment already finished");
		}
		this.finished = true;
		this.keepsStartEdge = keepStart;
		this.keepsEndEdge = keepEnd;
		this.setFragmentCutHead(!keepStart);
		final WritingMode flow = this.getTextParams().flow;
		// Logical line splitting assumes LTR and retains only left=start values. Recompute margin/padding
		// from the original inline's unsplit frame (params.frame), then cut the appropriate side for the direction.
		this.frame.frame = this.getInlineParams().frame;
		this.fixLineAxis(flow.isVertical(), lineSize);
		// Convert logical start/end edges to physical ones from actual inline progression. Using direction alone
		// reverses top/bottom for SIDEWAYS_CCW in LTR/RTL.
		final TypesettingMode.InlineProgression progression = TypesettingMode.inlineProgression(flow,
				this.getTextParams().writingModeVariant, this.getTextParams().direction);
		final boolean reversed = progression == TypesettingMode.InlineProgression.RIGHT_TO_LEFT
				|| progression == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		final boolean lineStart = reversed ? keepEnd : keepStart;
		final boolean lineEnd = reversed ? keepStart : keepEnd;
		final boolean top = flow.isVertical() ? lineStart : true;
		final boolean right = flow.isVertical() ? true : lineEnd;
		final boolean bottom = flow.isVertical() ? lineEnd : true;
		final boolean left = flow.isVertical() ? true : lineStart;
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame cut = this.frame.cut(top, right, bottom, left);
		this.frame.frame = cut.frame;
		this.frame.margin = cut.margin;
		this.frame.padding = cut.padding;
		this.addAdvance(this.frame.getFrameLineExtent(flow));
	}

	public InlineBox source() {
		return this.source;
	}

	public boolean keepsStartEdge() {
		return this.keepsStartEdge;
	}

	public boolean keepsEndEdge() {
		return this.keepsEndEdge;
	}

	/** The entire logical inline text, without visual reordering, for visitor semantics. */
	public void appendSemanticText(final StringBuilder text) {
		if (this.semanticText == null) {
			this.source.getText(text);
		} else {
			text.append(this.semanticText);
		}
	}

	@Override
	public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
			final AffineTransform transform, final double contextX, final double contextY, final double x,
			final double y, final java.util.Deque<DrawStep> worklist) {
		// The visual tree is built at paragraph resolution, but position:relative used offsets are determined later
		// by finishLayoutSelf, so synchronize from the logical source immediately before drawing.
		this.source.copyResolvedOffsetTo(this);
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	/** Lets the parent fragment account for the child fragment's finalized width. */
	public void addFragmentAdvance(final IInlineBox child) {
		this.addAdvance(child.getLineExtent(this.getTextParams().flow));
	}
}
