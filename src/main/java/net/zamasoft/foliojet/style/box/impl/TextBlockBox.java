package net.zamasoft.foliojet.style.box.impl;

import net.zamasoft.foliojet.style.box.content.BreakToken;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.impl.css.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractBox;
import net.zamasoft.foliojet.style.box.AbstractLineBox;
import net.zamasoft.foliojet.style.box.IFlowBox;
import net.zamasoft.foliojet.style.box.IFramedBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.TextBlockPos;
import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.builder.impl.BuilderGlyphHandler;
import net.zamasoft.foliojet.style.draw.DebugDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
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
		assert !StyleUtils.isNone(pageAxis);
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
			if (StyleUtils.compare(bottom, pageAxis) >= 0) {
				pageAxis = bottom;
				break;
			}
		}

		return pageAxis;
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		assert !StyleUtils.isNone(x);
		assert !StyleUtils.isNone(y);
		visitor.visitBox(transform, this, drawer, x, y);

		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(1, .5f, 1));
			drawer.visitDrawable(drawable, x, y);
		}
		for (int i = 0; i < this.lines.size(); ++i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			// 描画
			if (this.params.flow.isVertical()) {
				// 縦書き
				lineBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY,
						x + this.getPageSize() - line.getPageEnd(), y);
			} else {
				// 横書き
				lineBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y + line.pageAxis);
			}
		}
	}

	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y) {
		for (int i = 0; i < this.lines.size(); ++i) {
			Line line = (Line) this.lines.get(i);
			AbstractLineBox lineBox = line.box;
			// 描画
			if (this.params.flow.isVertical()) {
				// 縦書き
				lineBox.textShape(pageBox, path, transform, x + this.getPageSize() - line.getPageEnd(), y);
			} else {
				// 横書き
				lineBox.textShape(pageBox, path, transform, x, y + line.pageAxis);
			}
		}
	}

	public final IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, byte flags) {
		assert (!this.lines.isEmpty());
		// System.err.println("TBB A: " +flags + "/" + mode + "/" + pageLimit
		// + "/" + this.getHeight() + "/" + this.lines.size() + "/"
		// + this.params.augmentation);
		assert !(mode instanceof BreakMode.ForceBreakMode);
		// assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// FLAGS_LASTは実際の要素に対するもので、仮想的なテキストブロックには適用しない

		final double pageSize = this.getPageExtent(this.params.flow);
		if (StyleUtils.compare(pageLimit, pageSize) >= 0) {
			// 切断線が底辺以下にある場合は移動なし
			return null;
		}

		// 実質的に高さのある行をカウントする
		int nonZeroLines = 0;
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			if (line.pageAxis > 0 || line.box.getPageSize() > 0) {
				if (++nonZeroLines >= 2) {
					break;
				}
			}
		}
		if (nonZeroLines >= 2) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
				final Line line = (Line) this.lines.get(0);
				if (StyleUtils.compare(pageLimit, line.getPageEnd()) < 0) {
					// 切断線が最初の行の底辺より上にある場合は全部移動
					return this;
				}
			}
		} else {
			// １行だけの場合
			if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
				return this;
			}
			return null;
		}

		// 前ページに残すことができる最後の行を求める
		int lastOrphan;
		for (lastOrphan = this.lines.size() - 1; lastOrphan > 0; --lastOrphan) {
			final Line line = (Line) this.lines.get(lastOrphan);
			if (StyleUtils.compare(pageLimit, line.getPageEnd()) >= 0) {
				break;
			}
		}

		// widows, orphansは対象範囲の高さをline-heightで割った値(仮想行数)を基準に計算する
		// widows, orphansを満たすように改ページ位置を決める
		// 両方を満たすことができない場合、全体を次ページに送る、ただし
		// ページの先頭ではorphansを無視し、少なくとも１行を前ページに残す

		// 'widows'による制約
		while (lastOrphan >= 0) {
			final Line line = (Line) this.lines.get(lastOrphan);
			double virHeight = pageSize - line.getPageEnd();
			int virWidows = (int) Math.round(virHeight / this.params.lineHeight);
			if (virWidows >= this.params.widows) {
				break;
			}
			--lastOrphan;
		}
		if (lastOrphan == -1) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
				return this;
			}
			lastOrphan = 0;
		}
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
			// 'orphans'による制約
			final Line line = (Line) this.lines.get(lastOrphan);
			int virOrphans = (int) Math.round(line.getPageEnd() / this.params.lineHeight);
			if (virOrphans < this.params.orphans) {
				return this;
			}
		}

		// widowsを次ページに移動
		final int firstWidow = lastOrphan + 1;
		final double top = ((Line) this.lines.get(firstWidow)).pageAxis;
		final BreakToken token = ((Line) this.lines.get(lastOrphan)).box.isLast() ? BreakToken.MID_FLOW
				: BreakToken.MID_LINE;
		final TextBlockBox nextTextBlock = new TextBlockBox(this.params, token);
		for (int i = firstWidow; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			nextTextBlock.addLine(line.box, line.pageAxis - top);
		}
		while (this.lines.size() > firstWidow) {
			this.lines.remove(this.lines.size() - 1);
		}

		assert !this.lines.isEmpty() : mode;
		assert !nextTextBlock.lines.isEmpty();
		// System.err.println("TextBlockBox D: " + this.lines.size() + "/"
		// + nextTextBlock.lines.size());
		return nextTextBlock;
	}

	public final int getLineCount() {
		return this.lines.size();
	}

	public final void restyle(final BlockBuilder builder) {
		assert (!this.lines.isEmpty());
		builder.setBreakToken(this.breakToken);
		final GlyphHandler gh = new BuilderGlyphHandler(builder);
		final FilterGlyphHandler textUnitizer = new CSSJTextUnitizer(this.params.hyphenation);
		textUnitizer.setGlyphHandler(gh);
		// System.err.println("*** start");
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = (Line) this.lines.get(i);
			line.box.restyle(textUnitizer, i == 0);
		}
		// System.err.println("*** end");
		textUnitizer.close();
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
