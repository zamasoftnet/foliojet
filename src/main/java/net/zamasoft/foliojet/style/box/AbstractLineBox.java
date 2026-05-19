package net.zamasoft.foliojet.style.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.style.box.content.JustificationState;
import net.zamasoft.foliojet.style.box.impl.LineBox;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.AbstractLineParams;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.LinePos;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.draw.DebugDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
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

	public byte getType() {
		return TYPE_LINE;
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
		assert !StyleUtils.isNone(this.ascent + this.descent);
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
		assert this.types != null && !this.types.isEmpty();
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

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		switch (this.getLineParams().flow) {
		case AbstractTextParams.FLOW_TB:
			// 横書き
			x += this.lineAlign;
			break;

		case AbstractTextParams.FLOW_LR:
		case AbstractTextParams.FLOW_RL:
			// 縦書き
			y += this.lineAlign;
			break;

		default:
			throw new IllegalStateException();
		}

		visitor.visitBox(transform, this, x, y);
		super.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), GrayColor.create(.5f));
			drawer.visitDrawable(drawable, x, y);
		}
	}

	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y) {
		switch (this.getLineParams().flow) {
		case AbstractTextParams.FLOW_TB:
			// 横書き
			x += this.lineAlign;
			break;

		case AbstractTextParams.FLOW_LR:
		case AbstractTextParams.FLOW_RL:
			// 縦書き
			y += this.lineAlign;
			break;

		default:
			throw new IllegalStateException();
		}
		super.textShape(pageBox, path, transform, x, y);
	}
}
