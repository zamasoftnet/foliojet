package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.util.StyleUtils;

/**
 * 画像ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteReplacedBox.java 1561 2018-07-04 11:44:21Z miyabe $
 */
public class AbsoluteReplacedBox extends AbstractReplacedBox implements IAbsoluteBox {
	protected final AbsolutePos pos;

	public AbsoluteReplacedBox(final ReplacedParams params, final AbsolutePos pos) {
		super(params);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbsolutePos getAbsolutePos() {
		return this.pos;
	}

	public final void finishLayout(IFramedBox containerBox) {
		//
		// ■ 幅と高さの計算
		//
		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();

		this.calculateSize(cWidth, cHeight, cWidth, cHeight);
		Insets margin = this.frame.frame.margin;
		AbsoluteInsets amargin = this.frame.margin;

		// 横書き
		double left = StyleUtils.computeInsetsLeft(this.pos.location, cWidth);
		double right = StyleUtils.computeInsetsRight(this.pos.location, cWidth);
		double marginLeft = margin.getLeftType() == LengthType.AUTO ? StyleUtils.NONE : amargin.left;
		double marginRight = margin.getRightType() == LengthType.AUTO ? StyleUtils.NONE : amargin.right;
		if (!StyleUtils.isNone(left) && !StyleUtils.isNone(right)) {
			if (StyleUtils.isNone(marginLeft) && StyleUtils.isNone(marginRight)) {
				marginLeft = marginRight = (cWidth - left - right - this.width - this.frame.getFrameWidth()) / 2.0;
			}
			if (StyleUtils.isNone(marginLeft) && !StyleUtils.isNone(marginRight)) {
				marginLeft = cWidth - left - right - this.width - this.frame.getFrameWidth();
			}
			if (!StyleUtils.isNone(marginLeft) && StyleUtils.isNone(marginRight)) {
				marginRight = cWidth - left - right - this.width - this.frame.getFrameWidth();
			} else {
				// 制限しすぎ
				right = 0;
				// right = lineWidth - left - width - marginLeft
				// - marginRight - aframe.getFrameWidth();
			}
		} else {
			if (StyleUtils.isNone(marginLeft)) {
				marginLeft = 0;
			}
			if (StyleUtils.isNone(marginRight)) {
				marginRight = 0;
			}
			if (StyleUtils.isNone(left) && StyleUtils.isNone(right)) {
				left = right = 0;
			} else if (StyleUtils.isNone(right)) {
				right = cWidth - left - this.width - this.frame.getFrameWidth();
			} else {
				left = cWidth - right - this.width - this.frame.getFrameWidth();
			}
		}
		this.offsetX = left;
		this.frame.margin.left = marginLeft;
		this.frame.margin.right = marginRight;
		assert !StyleUtils.isNone(marginRight);
		assert !StyleUtils.isNone(marginLeft);

		double top = StyleUtils.computeInsetsTop(this.pos.location, cHeight);
		double bottom = StyleUtils.computeInsetsBottom(this.pos.location, cHeight);
		double marginTop = margin.getTopType() == LengthType.AUTO ? StyleUtils.NONE : amargin.top;
		double marginBottom = margin.getBottomType() == LengthType.AUTO ? StyleUtils.NONE : amargin.bottom;
		if (!StyleUtils.isNone(top) && !StyleUtils.isNone(bottom)) {
			if (StyleUtils.isNone(marginTop) && StyleUtils.isNone(marginBottom)) {
				marginTop = marginBottom = (cHeight - top - bottom - this.height - this.frame.getFrameHeight()) / 2.0;
			}
			if (StyleUtils.isNone(marginTop) && !StyleUtils.isNone(marginBottom)) {
				marginTop = cHeight - top - bottom - this.height - this.frame.getFrameHeight();
			}
			if (!StyleUtils.isNone(marginTop) && StyleUtils.isNone(marginBottom)) {
				marginBottom = cHeight - top - bottom - this.height - this.frame.getFrameHeight();
			} else {
				// 制限しすぎ
				bottom = 0;
				// bottom = pageHeight - top - height - marginTop
				// - marginBottom - padding.getFrameHeight();
			}
		} else {
			if (StyleUtils.isNone(marginTop)) {
				marginTop = 0;
			}
			if (StyleUtils.isNone(marginBottom)) {
				marginBottom = 0;
			}
			if (StyleUtils.isNone(top) && StyleUtils.isNone(bottom)) {
				top = bottom = 0;
			} else if (StyleUtils.isNone(top)) {
				top = cHeight - bottom - this.height - marginTop - this.frame.getFrameHeight();
			} else {
				bottom = cHeight - top - this.height - marginTop - this.frame.getFrameHeight();
			}
		}
		this.offsetY = top;
		this.frame.margin.top = marginTop;
		this.frame.margin.bottom = marginBottom;
		assert !StyleUtils.isNone(marginTop);
		assert !StyleUtils.isNone(marginBottom);

		assert !StyleUtils.isNone(this.width);
		assert !StyleUtils.isNone(this.height);
		assert !StyleUtils.isNone(this.offsetX) : "Undefined offsetX";
		assert !StyleUtils.isNone(this.offsetY) : "Undefined offsetY";
	}
}
