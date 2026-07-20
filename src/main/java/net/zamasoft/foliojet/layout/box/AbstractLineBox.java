package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.content.JustificationState;
import net.zamasoft.foliojet.layout.box.impl.LineBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.LinePos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;

/**
 * 行ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractLineBox.java 1640 2023-10-04 03:06:26Z miyabe $
 */
public abstract class AbstractLineBox extends AbstractTextBox {
	private static final boolean DEBUG = false;

	/**
	 * 行方向アラインメントです。
	 */
	protected double lineAlign = 0;

	/**
	 * 行末またはブロックの末です。
	 */
	protected boolean last = false;

	public abstract AbstractLineParams getLineParams();

	public BoxType getType() {
		return BoxType.LINE;
	}

	public Pos getPos() {
		return LinePos.POS;
	}

	public boolean isLast() {
		return this.last;
	}

	public void addAscentDescent(double ascent, double descent) {
		// アセントディセントの拡大
		if (ascent > this.ascent) {
			this.ascent = ascent;
		}
		if (descent > this.descent) {
			this.descent = descent;
		}
		assert !LayoutUtils.isNone(this.ascent + this.descent);
	}

	/**
	 * 行方向アラインメントを適用します。
	 * 
	 * @param textIndent  インデント
	 * @param offset      浮動ボックス等によるずれ
	 * @param maxLineAxis 最大行幅
	 * @param last        ブロックの末尾または改行された行
	 */
	public void align(double textIndent, double offset, double maxLineAxis, boolean last) {
		// 行方向アラインメント
		assert this.contents != null && !this.contents.isEmpty();
		// Unicode 双方向テキスト(UAX #9)の視覚順並べ替え。純 LTR 行では
		// no-op となり既存の出力を変えない。
		this.reorderBidi();
		this.last = last;
		AbstractLineParams params = this.getLineParams();
		double lineWidth = this.lineSize + textIndent;
		textIndent += offset;
		final byte textAlign = last ? params.textAlignLast : params.textAlign;
		switch (textAlign) {
		case AbstractLineParams.TEXT_ALIGN_CENTER:
			// 中央合わせ
			this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_END:
			// 行末に合わせる
			this.lineAlign = maxLineAxis - lineWidth + textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_JUSTIFY: {
			// 両方合わせ
			double remainderAdvance = maxLineAxis - lineWidth;
			if (remainderAdvance > 0) {
				int count = this.countJustificationPoints(new JustificationState());
				if (count > 0) {
					double letterSpacing = remainderAdvance / count;
					if (letterSpacing != 0) {
						this.justify(letterSpacing, new JustificationState());
					}
				}
			}
			this.lineAlign = textIndent;
		}
			break;

		case AbstractLineParams.TEXT_ALIGN_START:
			// 行頭に合わせる
			this.lineAlign = textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_X_JUSTIFY_CENTER:
			// 中央-両合わせ
			double remainderAdvance = maxLineAxis - lineWidth;
			if (remainderAdvance <= 0) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}
			double fontSize = this.getTextParams().fontStyle.getSize();
			if (remainderAdvance <= fontSize) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}

			int count = this.countJustificationPoints(new JustificationState());
			if (count <= 0) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}
			double letterSpacing = (remainderAdvance - fontSize) / count;
			this.justify(letterSpacing, new JustificationState());
			this.lineAlign = textIndent + fontSize / 2.0;
			break;

		default:
			throw new IllegalStateException();
		}

		// ページ方向アラインメント
		super.verticalAlign(this, 0);
	}

	public LineBox splitLine(BlockParams params) {
		LineBox newLine = new LineBox(params);
		return newLine;
	}

	/**
	 * この行のトップレベルの内容を Unicode 双方向アルゴリズム(UAX #9)の
	 * 視覚順に並べ替え、右横書き(RTL)ランのグリフを反転します。行のテキストが
	 * すべて左横書き(LTR)なら何もしないため、既存の LTR 文書の出力は変わりません。
	 */
	private void reorderBidi() {
		if (this.contents == null || this.contents.isEmpty()) {
			return;
		}
		// 横書きのみを対象とする(縦書きの双方向は将来対応)。
		if (this.getLineParams().flow.isVertical()) {
			return;
		}
		final int n = this.contents.size();

		// 行の論理順テキストを構築(非テキストは中立オブジェクト U+FFFC)。
		final StringBuilder logical = new StringBuilder();
		final int[] itemStart = new int[n];
		for (int i = 0; i < n; ++i) {
			itemStart[i] = logical.length();
			if (this.contents.get(i) instanceof net.zamasoft.pdfg2d.gc.text.Text text) {
				logical.append(text.getChars(), 0, text.getCharCount());
			} else {
				logical.append('￼');
			}
		}

		final java.text.Bidi bidi = new java.text.Bidi(logical.toString(), java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);
		if (bidi.isLeftToRight()) {
			// 純 LTR: 並べ替え不要。既存出力を厳密に保持する。
			return;
		}

		final byte[] levels = new byte[n];
		for (int i = 0; i < n; ++i) {
			levels[i] = (byte) bidi.getLevelAt(itemStart[i]);
		}
		final int[] order = net.zamasoft.pdfg2d.gc.text.pipeline.Itemizer.reorderVisual(levels);

		final List<Object> newContents = new ArrayList<Object>(n);
		for (final int idx : order) {
			Object content = this.contents.get(idx);
			// RTL テキストランはグリフを視覚順に反転する。
			if ((levels[idx] & 1) != 0 && content instanceof net.zamasoft.pdfg2d.gc.text.TextImpl ti) {
				content = ti.reverse();
			}
			newContents.add(content);
		}
		this.contents = newContents;
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		switch (this.getLineParams().flow) {
		case WritingMode.TB:
			// 横書き
			x += this.lineAlign;
			break;

		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			y += this.lineAlign;
			break;

		default:
			throw new IllegalStateException();
		}

		visitor.visitBox(transform, this, drawer, x, y);
		if (DEBUG) {
			// super(子)の描画より後に見えるよう、先にpushして最後にpopされるようにする
			final double fx = x, fy = y;
			worklist.push(w -> {
				Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), GrayColor.create(.5f));
				drawer.visitDrawable(drawable, fx, fy);
			});
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			java.util.Deque<TextShapeStep> worklist) {
		switch (this.getLineParams().flow) {
		case WritingMode.TB:
			// 横書き
			x += this.lineAlign;
			break;

		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			y += this.lineAlign;
			break;

		default:
			throw new IllegalStateException();
		}
		super.pushTextShapeSteps(pageBox, path, transform, x, y, worklist);
	}
}
