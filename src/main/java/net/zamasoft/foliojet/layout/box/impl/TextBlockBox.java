package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.fragment.LineCutter;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

import net.zamasoft.foliojet.layout.box.content.BreakToken;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.impl.css.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TextBlockPos;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.BuilderGlyphHandler;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;

/**
 * テキストだけを含むことができるボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TextBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TextBlockBox extends AbstractBox implements IPageBreakableBox, IFlowBox {
	/**
	 * ボックスの外辺を薄紫色の枠で囲みます。
	 */
	private static final boolean DEBUG = false;

	/**
	 * 配置された行です。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: TextBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
	 */
	protected static class Line {
		public final AbstractLineBox box;
		public final double pageAxis;

		public Line(AbstractLineBox line, double pageAxis) {
			this.box = line;
			this.pageAxis = pageAxis;
		}

		public double getPageEnd() {
			return this.pageAxis + this.box.getAscent() + this.box.getDescent();
		}

		public String toString() {
			return this.box.toString();
		}
	}

	protected final BlockParams params;

	/**
	 * テキストブロックに含まれる行のリスト。
	 */
	protected final List<Line> lines = new ArrayList<Line>();

	protected double lineSize = 0;

	/**
	 * このテキストブロックの継続状態です。
	 */
	protected final BreakToken breakToken;

	public TextBlockBox(final BlockParams params, final BreakToken breakToken) {
		this.params = params;
		this.breakToken = breakToken;
	}

	public final BoxType getType() {
		return BoxType.TEXT_BLOCK;
	}

	/**
	 * このテキストブロックの継続トークンを返します(M6b)。
	 */
	public final BreakToken getBreakToken() {
		return this.breakToken;
	}

	public final Params getParams() {
		return this.params;
	}

	public final BlockParams getBlockParams() {
		return this.params;
	}

	public final Pos getPos() {
		return TextBlockPos.POS;
	}

	public final double getFirstAscent() {
		double ascent = 0;
		if (this.lines != null && !this.lines.isEmpty()) {
			Line line = (Line) this.lines.get(0);
			ascent += line.box.getAscent();
		}
		return ascent;
	}

	public final double getLastDescent() {
		double descent = 0;
		if (this.lines != null && !this.lines.isEmpty()) {
			final Line line = (Line) this.lines.get(this.lines.size() - 1);
			descent += line.box.getDescent();
		}
		return descent;
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		Line line = (Line) this.lines.get(this.lines.size() - 1);
		return line.getPageEnd();
	}

	public final double getWidth() {
		if (this.params.flow.isVertical()) {
			// 縦書き
			return this.getPageSize();
		} else {
			// 横書き
			return this.lineSize;
		}
	}

	public final double getHeight() {
		if (this.params.flow.isVertical()) {
			// 縦書き
			return this.lineSize;
		} else {
			// 横書き
			return this.getPageSize();
		}
	}

	public final double getInnerWidth() {
		return this.getWidth();
	}

	public final double getInnerHeight() {
		return this.getHeight();
	}

	public final void addLine(AbstractLineBox lineBox, double pageAxis) {
		assert !LayoutUtils.isNone(pageAxis);
		this.lines.add(new Line(lineBox, pageAxis));
		// この拡張はIE互換モードでなければ、あまり意味はない
		this.lineSize = Math.max(lineBox.getLineSize(), this.lineSize);
	}

	public final void finishLayout(IFramedBox containerBox) {
		for (int i = 0; i < this.lines.size(); ++i) {
			Line line = (Line) this.lines.get(i);
			line.box.finishLayout(containerBox);
		}
	}

	public final void getText(StringBuilder textBuff) {
		for (int i = 0; i < this.lines.size(); ++i) {
			Line line = (Line) this.lines.get(i);
			line.box.getText(textBuff);
		}
	}

	public final double getCutPoint(double pageAxis) {
		if (this.lines.isEmpty()) {
			return pageAxis;
		}
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			final double bottom = line.pageAxis + line.box.getPageExtent(this.getBlockParams().flow);
			if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
				pageAxis = bottom;
				break;
			}
		}

		return pageAxis;
	}

	/**
	 * 提案位置の直前の行境界を返します(M5-B)。getCutPoint の切り上げに
	 * 対する切り下げで、提案位置より前に行境界がなければ 0 を返します。
	 *
	 * @param pageAxis 提案位置
	 * @return 直前の行境界(なければ 0)
	 */
	public final double getCutPointBelow(final double pageAxis) {
		double result = 0;
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			final double bottom = line.pageAxis + line.box.getPageExtent(this.getBlockParams().flow);
			if (LayoutUtils.compare(bottom, pageAxis) > 0) {
				break;
			}
			result = bottom;
		}
		return result;
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);
		visitor.visitBox(transform, this, drawer, x, y);

		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(1, .5f, 1));
			drawer.visitDrawable(drawable, x, y);
		}
		for (int i = 0; i < this.lines.size(); ++i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			// 描画(論理→物理変換は LayoutUtils.drawX/drawY に集約)
			lineBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(this.params.flow, x, this.getPageSize(), line.getPageEnd(), 0),
					LayoutUtils.drawY(this.params.flow, y, line.pageAxis, 0));
		}
	}

	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y) {
		for (int i = 0; i < this.lines.size(); ++i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			lineBox.textShape(pageBox, path, transform,
					LayoutUtils.drawX(this.params.flow, x, this.getPageSize(), line.getPageEnd(), 0),
					LayoutUtils.drawY(this.params.flow, y, line.pageAxis, 0));
		}
	}

	/**
	 * 行境界でページ方向に切断します(柱2cの型付きプロトコル)。
	 * 切断判定は {@link LineCutter} が行い、Split の場合このボックスは
	 * 前ページ分の行のみを保持するよう変異します。
	 *
	 * @param pageLimit ボックスの外辺から切断線までの距離
	 * @param flags     IPageBreakableBox.FLAGS_* のビット和
	 * @return 切断結果
	 */
	public final SplitResult split(final double pageLimit, final byte flags) {
		assert (!this.lines.isEmpty());
		// FLAGS_LASTは実際の要素に対するもので、仮想的なテキストブロックには適用しない

		final double pageSize = this.getPageExtent(this.params.flow);
		final double[] lineStarts = new double[this.lines.size()];
		final double[] lineEnds = new double[this.lines.size()];
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			lineStarts[i] = line.pageAxis;
			lineEnds[i] = line.getPageEnd();
		}
		final LineCutter.Decision decision = LineCutter.decide(pageLimit, pageSize, this.params.lineHeight,
				this.params.orphans, this.params.widows, (flags & IPageBreakableBox.FLAGS_FIRST) != 0, lineStarts,
				lineEnds);
		switch (decision) {
		case LineCutter.Decision.Keep keep:
			return SplitResult.KEEP;
		case LineCutter.Decision.Move move:
			return SplitResult.MOVE;
		case LineCutter.Decision.CutAfter(final int lastLine): {
			// 切断行以降(widows)を次ページのフラグメントに移す
			final int firstWidow = lastLine + 1;
			final double top = ((Line) this.lines.get(firstWidow)).pageAxis;
			// 再開位置 = 前断片(切断行まで)の末尾文字終端(M6b v3)。
			// 残余先頭の firstCharOffset は行分割時の Text 分割の丸めで
			// ずれることがあるため、残った側の終端から導出する
			final int resumeOffset = ((Line) this.lines.get(lastLine)).box.lastCharEnd();
			final BreakToken token = ((Line) this.lines.get(lastLine)).box.isLast()
					? new BreakToken.MidFlow(resumeOffset)
					: new BreakToken.MidLine(resumeOffset);
			final TextBlockBox nextTextBlock = new TextBlockBox(this.params, token);
			for (int i = firstWidow; i < this.lines.size(); ++i) {
				final Line line = (Line) this.lines.get(i);
				nextTextBlock.addLine(line.box, line.pageAxis - top);
			}
			while (this.lines.size() > firstWidow) {
				this.lines.remove(this.lines.size() - 1);
			}
			assert !this.lines.isEmpty();
			assert !nextTextBlock.lines.isEmpty();
			// M3b Phase 2: handoff 内容は破断時点で確定する — 残余の
			// 正規化イベント列をここで捕捉し、box は運搬体に落とす
			// (Phase 3 で TextTail 型付き item へ置換し運搬体を除去)
			nextTextBlock.slice = nextTextBlock.recordSlice();
			return new SplitResult.Split(nextTextBlock);
		}
		}
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert !(mode instanceof BreakMode.ForceBreakMode);
		return this.split(pageLimit, flags);
	}

	public final int getLineCount() {
		return this.lines.size();
	}

	/**
	 * 破断時に捕捉した残余の正規化イベント列です(M3b Phase 2)。
	 * 分割断片(運搬体)のみ非 null。
	 */
	private net.zamasoft.foliojet.layout.fragment.TextReplaySlice slice;

	public final void restyle(final BlockBuilder builder) {
		assert (!this.lines.isEmpty());
		builder.setBreakToken(this.breakToken);
		// M3b Phase 1/2: 運搬体はスライス。分割断片は破断時に捕捉済み、
		// それ以外(全 restyle 経路)はここで捕捉する。捕捉→再生は
		// 構成的に同一の呼び出し列なので挙動不変
		final net.zamasoft.foliojet.layout.fragment.TextReplaySlice slice = this.slice != null ? this.slice
				: this.recordSlice();
		slice.replay(new BuilderGlyphHandler(builder));
	}

	/**
	 * 残余行の正規化イベント列を捕捉します(M3b Phase 1 / C3)。
	 * WordHyphenator 相当(unitizer)の出口で捕捉した、restyle が
	 * BuilderGlyphHandler へ配達するのと同一の列。
	 */
	private net.zamasoft.foliojet.layout.fragment.TextReplaySlice recordSlice() {
		return net.zamasoft.foliojet.layout.fragment.TextReplaySlice.record(gh -> {
			final FilterGlyphHandler textUnitizer = new CSSJTextUnitizer(this.params);
			textUnitizer.setGlyphHandler(gh);
			for (int i = 0; i < this.lines.size(); ++i) {
				final Line line = (Line) this.lines.get(i);
				line.box.restyle(textUnitizer, i == 0);
			}
			textUnitizer.close();
		});
	}

	public final boolean avoidBreakAfter() {
		return false;
	}

	public final boolean avoidBreakBefore() {
		return false;
	}

	public String toString() {
		return super.toString() + "/lineCount=" + this.lines.size();
	}
}
