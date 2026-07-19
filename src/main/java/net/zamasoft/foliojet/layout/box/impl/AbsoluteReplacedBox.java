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
import net.zamasoft.foliojet.layout.util.LayoutUtils;

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

	public final void finishLayoutSelf(IFramedBox containerBox) {
		//
		// ■ 幅と高さの計算
		//
		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();

		this.calculateSize(cWidth, cHeight, cWidth, cHeight);
		Insets margin = this.frame.frame.margin;
		AbsoluteInsets amargin = this.frame.margin;

		// 横書き
		double left = LayoutUtils.computeInsetsLeft(this.pos.location, cWidth);
		double right = LayoutUtils.computeInsetsRight(this.pos.location, cWidth);
		double marginLeft = margin.getLeftType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.left;
		double marginRight = margin.getRightType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.right;
		if (!LayoutUtils.isNone(left) && !LayoutUtils.isNone(right)) {
			if (LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
				marginLeft = marginRight = (cWidth - left - right - this.width - this.frame.getFrameWidth()) / 2.0;
			}
			if (LayoutUtils.isNone(marginLeft) && !LayoutUtils.isNone(marginRight)) {
				marginLeft = cWidth - left - right - this.width - this.frame.getFrameWidth();
			}
			if (!LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
				marginRight = cWidth - left - right - this.width - this.frame.getFrameWidth();
			} else {
				// 制限しすぎ
				right = 0;
				// right = lineWidth - left - width - marginLeft
				// - marginRight - aframe.getFrameWidth();
			}
		} else {
			if (LayoutUtils.isNone(marginLeft)) {
				marginLeft = 0;
			}
			if (LayoutUtils.isNone(marginRight)) {
				marginRight = 0;
			}
			if (LayoutUtils.isNone(left) && LayoutUtils.isNone(right)) {
				left = right = 0;
			} else if (LayoutUtils.isNone(right)) {
				right = cWidth - left - this.width - this.frame.getFrameWidth();
			} else {
				left = cWidth - right - this.width - this.frame.getFrameWidth();
			}
		}
		this.offsetX = left;
		this.frame.margin.left = marginLeft;
		this.frame.margin.right = marginRight;
		assert !LayoutUtils.isNone(marginRight);
		assert !LayoutUtils.isNone(marginLeft);

		double top = LayoutUtils.computeInsetsTop(this.pos.location, cHeight);
		double bottom = LayoutUtils.computeInsetsBottom(this.pos.location, cHeight);
		double marginTop = margin.getTopType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.top;
		double marginBottom = margin.getBottomType() == LengthType.AUTO ? LayoutUtils.NONE : amargin.bottom;
		if (!LayoutUtils.isNone(top) && !LayoutUtils.isNone(bottom)) {
			if (LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
				marginTop = marginBottom = (cHeight - top - bottom - this.height - this.frame.getFrameHeight()) / 2.0;
			}
			if (LayoutUtils.isNone(marginTop) && !LayoutUtils.isNone(marginBottom)) {
				marginTop = cHeight - top - bottom - this.height - this.frame.getFrameHeight();
			}
			if (!LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
				marginBottom = cHeight - top - bottom - this.height - this.frame.getFrameHeight();
			} else {
				// 制限しすぎ
				bottom = 0;
				// bottom = pageHeight - top - height - marginTop
				// - marginBottom - padding.getFrameHeight();
			}
		} else {
			if (LayoutUtils.isNone(marginTop)) {
				marginTop = 0;
			}
			if (LayoutUtils.isNone(marginBottom)) {
				marginBottom = 0;
			}
			if (LayoutUtils.isNone(top) && LayoutUtils.isNone(bottom)) {
				top = bottom = 0;
			} else if (LayoutUtils.isNone(top)) {
				top = cHeight - bottom - this.height - marginTop - this.frame.getFrameHeight();
			} else {
				bottom = cHeight - top - this.height - marginTop - this.frame.getFrameHeight();
			}
		}
		this.offsetY = top;
		this.frame.margin.top = marginTop;
		this.frame.margin.bottom = marginBottom;
		assert !LayoutUtils.isNone(marginTop);
		assert !LayoutUtils.isNone(marginBottom);

		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
		assert !LayoutUtils.isNone(this.offsetX) : "Undefined offsetX";
		assert !LayoutUtils.isNone(this.offsetY) : "Undefined offsetY";
	}

	public AbsoluteReplacedBox newReplayInstance() {
		return new AbsoluteReplacedBox(this.params, this.pos);
	}
}
