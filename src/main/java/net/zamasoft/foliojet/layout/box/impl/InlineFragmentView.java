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
 * bidi の視覚順だけに使う inline 断片。論理 {@code contents} には挿入しない。
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

	/** 視覚断片の内容を追加する。 */
	public void append(final Object content) {
		if (content instanceof Text text) {
			this.addText(text);
			this.addAdvance(text.getAdvance());
		} else if (content instanceof Control control) {
			this.addControl(control);
			this.addAdvance(control.getAdvance());
		} else if (content instanceof AbstractTextBox.Inline inline) {
			// flatten() が作った複製をそのまま置く(BidiSlice の鍵と同一の参照でなければ
			// ならない。ここで再複製すると ruby/warichu 等の atomic の slice が引けない)
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

	/** 子 fragment を順序だけ先に登録する。幅は子の edge 確定後に加える。 */
	public void appendFragment(final AbstractTextBox.Inline inline) {
		this.add(inline);
	}

	/**
	 * 元 inline の論理的な始端・終端を含む断片にだけ該当 edge を残す。
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
		// 論理側の行分割は LTR 前提で左=start の値だけを残しているので、元 inline の
		// 未切断 frame(params.frame)から margin/padding を再計算してから、方向に応じた側を切る
		this.frame.frame = this.getInlineParams().frame;
		this.fixLineAxis(flow.isVertical(), lineSize);
		// 論理 start/end 辺は実際の行内進行から物理化する。direction だけで決めると
		// SIDEWAYS_CCW の LTR/RTL で top/bottom が逆になる。
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

	/** visitor の意味処理で使う、視覚順へ変えていない論理 inline 全体の文字列。 */
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
		// visual tree は段落解決時、position:relative の used offset はその後の
		// finishLayoutSelf で確定するため、描画直前に論理 source から同期する。
		this.source.copyResolvedOffsetTo(this);
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	/** 親 fragment が子 fragment の確定幅を会計する。 */
	public void addFragmentAdvance(final IInlineBox child) {
		this.addAdvance(child.getLineExtent(this.getTextParams().flow));
	}
}
