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
	/**
	 * 行末の詰め/ぶら下げ分です(和文詰めT2/H1——
	 * consult-codex-2026-07-31-text-spacing.txt)。行の配置・均等割りは
	 * この分を除いた実効行幅を基準にし、glyph自体は通常どおり描画される
	 * (ぶら下げ句読点・半角化された行末約物のはみ出しはink扱い)。
	 */
	private double endHangAdvance;

	/**
	 * {@code text-overflow: ellipsis}の省略記号(なければnull。2026-08-29、
	 * TextBuilder.applyTextOverflow)。行の内容とは別に持ち、描画時に
	 * 行末をクリップして追加描画する。
	 */
	private net.zamasoft.pdfg2d.gc.text.Text ellipsis;

	/** 行原点(lineAlign適用前)から測った、内容を描く行方向の長さ。 */
	private double ellipsisClipExtent;

	public void setEllipsis(final net.zamasoft.pdfg2d.gc.text.Text ellipsis, final double clipExtent) {
		this.ellipsis = ellipsis;
		this.ellipsisClipExtent = clipExtent;
	}

	public net.zamasoft.pdfg2d.gc.text.Text getEllipsis() {
		return this.ellipsis;
	}

	public void setEndHangAdvance(final double endHangAdvance) {
		this.endHangAdvance = endHangAdvance;
	}

	public double getEndHangAdvance() {
		return this.endHangAdvance;
	}

	public void align(double textIndent, double offset, double maxLineAxis, boolean last) {
		// 行方向アラインメント
		assert this.contents != null && !this.contents.isEmpty();
		// Unicode 双方向テキスト(UAX #9)の視覚順並べ替え。純 LTR 行では
		// no-op となり既存の出力を変えない。
		this.reorderBidi();
		this.last = last;
		AbstractLineParams params = this.getLineParams();
		// T2/H1: 実効行幅(行末の詰め/ぶら下げ分を除く)
		double lineWidth = this.lineSize - this.endHangAdvance + textIndent;
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
				this.justifyByWritingSystem(remainderAdvance);
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

			final boolean japanese = this.containsJapaneseComposition();
			final double capacity = japanese
					? this.justificationCapacity(JUSTIFY_FALLBACK, new JustificationState())
					: this.countGeneralJustificationPoints(new JustificationState());
			if (capacity <= 0) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}
			this.justifyByWritingSystem(remainderAdvance - fontSize);
			this.lineAlign = textIndent + fontSize / 2.0;
			break;

		default:
			throw new IllegalStateException();
		}

		// ページ方向アラインメント
		super.verticalAlign(this, 0);
	}

	/** 和文行はJLREQ、純欧文・一般文字列は従来の分離可能境界で両端揃えする。 */
	private void justifyByWritingSystem(final double remainder) {
		if (remainder <= 0) {
			return;
		}
		if (this.containsJapaneseComposition()) {
			this.justifyByJlreqPriorities(remainder);
			return;
		}
		final int count = this.countGeneralJustificationPoints(new JustificationState());
		if (count > 0) {
			this.justifyGeneral(remainder / count, new JustificationState());
		}
	}

	/** JLREQ 3.8.4の4段階で行の余りを配分する。 */
	private void justifyByJlreqPriorities(double remainder) {
		if (remainder <= 0) {
			return;
		}
		for (int priority = JUSTIFY_WORD_SPACE; priority <= JUSTIFY_GENERAL && remainder > 0.0001;
				++priority) {
			final double capacity = this.justificationCapacity(priority, new JustificationState());
			if (capacity <= 0) {
				continue;
			}
			final double used = Math.min(remainder, capacity);
			this.justify(priority, used / capacity, new JustificationState());
			remainder -= used;
		}
		if (remainder > 0.0001) {
			final double weight = this.justificationCapacity(JUSTIFY_FALLBACK, new JustificationState());
			if (weight > 0) {
				this.justify(JUSTIFY_FALLBACK, remainder / weight, new JustificationState());
			}
		}
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
		if (this.ellipsis != null) {
			// text-overflow: ellipsis(2026-08-29)。内容は行末側を
			// ellipsisClipExtentで切り、省略記号を元のクリップで最後に描く
			// (worklistはLIFOなので先にpushすると子の後で実行される)
			final boolean vertical = this.getLineParams().flow.isVertical();
			final double pw = pageBox.getWidth(), ph = pageBox.getHeight();
			final java.awt.geom.Rectangle2D.Double keep = vertical
					? new java.awt.geom.Rectangle2D.Double(x - pw, y - ph, pw * 3, ph + this.ellipsisClipExtent)
					: new java.awt.geom.Rectangle2D.Double(x - pw, y - ph, pw + this.ellipsisClipExtent, ph * 3);
			final Shape outerClip = clip;
			final double ex = vertical ? x : x + this.ellipsisClipExtent;
			final double ey = vertical ? y + this.ellipsisClipExtent : y;
			final List<Object> run = java.util.Collections.singletonList(this.ellipsis);
			worklist.push(w -> drawer.visitDrawable(new TextSequenceDrawable(pageBox, outerClip, transform, run, 0, 1,
					this.getTextParams(), this.ascent, this.descent), ex, ey));
			if (clip == null) {
				clip = keep;
			} else if (clip instanceof java.awt.geom.Rectangle2D rc) {
				clip = rc.createIntersection(keep);
			} else {
				final java.awt.geom.Area area = new java.awt.geom.Area(clip);
				area.intersect(new java.awt.geom.Area(keep));
				clip = area;
			}
		}
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
