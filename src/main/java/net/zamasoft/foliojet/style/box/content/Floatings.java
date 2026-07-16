package net.zamasoft.foliojet.style.box.content;

import net.zamasoft.foliojet.style.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.AbstractReplacedBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IFloatBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.BlockParams;

import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;

/**
 * 通常のフロー以外のボックスを一括管理します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Floatings.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class Floatings {
	/**
	 * 配置された浮動ボックスです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: Floatings.java 1554 2018-04-26 03:34:02Z miyabe $
	 */
	public static class Floating extends BoxHolder {
		public final IFloatBox box;
		public final double lineAxis, pageAxis;

		public Floating(int serial, IFloatBox box, double lineAxis, double pageAxis) {
			super(serial);
			this.box = box;
			this.lineAxis = lineAxis;
			this.pageAxis = pageAxis;
		}

		public IBox getBox() {
			return this.box;
		}

		public void restyle(BlockBuilder builder) {
			switch (this.box.getType()) {
			case BLOCK: {
				// ブロックボックス
				// 匿名ボックス
				AbstractContainerBox floatBox = (AbstractContainerBox) this.box;
				BlockBuilder floatBindBuilder = new BlockBuilder(builder, floatBox);
				floatBox.restyle(floatBindBuilder, 0);
				floatBindBuilder.close();
				builder.addBound(floatBox);
			}
				break;
			case REPLACED: {
				// 置換されたボックス
				AbstractReplacedBox floatBox = (AbstractReplacedBox) this.box;
				builder.addBound(floatBox);
			}
				break;
			default:
				throw new IllegalStateException(this.box.toString());
			}
		}
	}

	/**
	 * 浮動ボックス。
	 */
	private final List<Floating> floatings = new ArrayList<Floating>();

	/**
	 * 浮動ボックスを追加します。
	 * 
	 * @param floating
	 */
	public void addFloating(Floating floating) {
		assert !StyleUtils.isNone(floating.pageAxis) : "Undefined pageAxis";
		assert !StyleUtils.isNone(floating.lineAxis) : "Undefined lineAxis";
		this.floatings.add(floating);
	}

	public void draw(AbstractContainerBox box, PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y) {
		assert !StyleUtils.isNone(x) : "Undefined x";
		assert !StyleUtils.isNone(y) : "Undefined y";
		// 浮動体
		boolean vertical = box.getBlockParams().flow.isVertical();
		if (vertical) {
			x += box.getInnerWidth();
		}
		for (int i = 0; i < this.floatings.size(); ++i) {
			Floating floating = (Floating) this.floatings.get(i);
			double xx;
			double yy;
			if (vertical) {
				// 縦書き
				xx = x - floating.pageAxis - floating.box.getWidth();
				yy = y + floating.lineAxis;
			} else {
				// 横書き
				xx = x + floating.lineAxis;
				yy = y + floating.pageAxis;
			}
			floating.box.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yy);
		}
	}

	public int getCount() {
		return this.floatings.size();
	}

	public Floating getFloating(int i) {
		return (Floating) this.floatings.get(i);
	}

	/**
	 * 浮動ボックスをページ分割します。全て前ページに残す場合はnull, 全て送る場合は自分自身を返します。
	 */
	public Floatings splitPageAxis(final AbstractContainerBox box, final double pageLimit, final byte flags) {
		assert !this.floatings.isEmpty();
		final boolean vertical = box.getBlockParams().flow.isVertical();
		Floatings nextFloatings = this;
		// 浮動体
		for (int i = 0; i < this.floatings.size(); ++i) {
			Floating floating = (Floating) this.floatings.get(i);
			double pageEnd = floating.pageAxis;
			if (vertical) {
				pageEnd += floating.box.getWidth();
			} else {
				pageEnd += floating.box.getHeight();
			}
			final boolean first = (flags & IPageBreakableBox.FLAGS_FIRST) != 0
					&& StyleUtils.compare(floating.pageAxis, 0) <= 0;
			final IFloatBox nextBox;
			if (StyleUtils.compare(pageEnd, pageLimit) <= 0) {
				// 移動なし
				nextBox = null;
			} else if (!first && StyleUtils.compare(pageLimit, floating.pageAxis) < 0) {
				nextBox = floating.box;
			} else {
				switch (floating.box.getType()) {
				case BLOCK: {
					// ブロックボックス
					// 匿名ボックス
					final AbstractContainerBox containerBox = (AbstractContainerBox) floating.box;
					final BlockParams params = containerBox.getBlockParams();
					if (params.pageBreakInside != PageBreakMode.AVOID
							&& vertical == params.flow.isVertical()) {
						byte xflags = first ? IPageBreakableBox.FLAGS_FIRST : IPageBreakableBox.FLAGS_SPLIT;
						double pageAxis = pageLimit - floating.pageAxis;
						nextBox = (IFloatBox) containerBox.splitPageAxis(pageAxis, BreakMode.DEFAULT_BREAK_MODE,
								xflags);
						break;
					}
					// 改ページ禁止されていた場合、ページ進行方向が違う場合は置換されたボックスと同じ処理
				}
				case REPLACED: {
					// 置換されたボックス
					nextBox = first ? null : floating.box;
				}
					break;
				default:
					throw new IllegalStateException(floating.box.toString());
				}
			}
			if (nextFloatings == this) {
				if (nextBox == floating.box) {
					continue;
				}
				if (i > 0 || nextBox != null) {
					nextFloatings = new Floatings();
					for (int j = 0; j < i; ++j) {
						nextFloatings.floatings.add(this.floatings.remove(j));
						--j;
						--i;
					}
				} else {
					nextFloatings = null;
				}
			}
			if (nextBox == null) {
				continue;
			}
			if (nextFloatings == null) {
				nextFloatings = new Floatings();
			}
			if (nextBox == floating.box) {
				this.floatings.remove(i);
				--i;
			} else {
				floating = new Floating(floating.serial, nextBox, 0, 0);
			}
			nextFloatings.floatings.add(floating);
		}
		assert !(nextFloatings != null && nextFloatings.floatings.isEmpty());
		return nextFloatings;
	}

	public String toString() {
		return super.toString() + ": floatings.size=" + this.floatings.size();
	}
}
