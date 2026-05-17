package jp.cssj.homare.style.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.homare.style.box.IAbsoluteBox;
import jp.cssj.homare.style.box.impl.PageBox;
import jp.cssj.homare.style.box.params.AbsolutePos;
import jp.cssj.homare.style.box.params.Insets;
import jp.cssj.homare.style.box.params.Types;
import jp.cssj.homare.style.draw.Drawer;
import jp.cssj.homare.style.util.StyleUtils;
import jp.cssj.homare.style.visitor.Visitor;

/**
 * 通常のフロー以外のボックスを一括管理します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Absolutes.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Absolutes {
	/**
	 * 絶対位置指定されたボックスです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: Absolutes.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class Absolute {
		public final IAbsoluteBox box;
		public final double x, y;

		public Absolute(IAbsoluteBox box, double x, double y) {
			this.box = box;
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * 絶対位置指定されたボックス。
	 */
	private List<Absolute> absolutes = null;

	public Absolutes() {
		// ignore
	}

	/**
	 * 絶対位置指定されたボックスを追加します。
	 * 
	 * @param box
	 * @param staticX
	 * @param staticY
	 */
	public void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		assert !StyleUtils.isNone(staticX) : "Undefined x";
		assert !StyleUtils.isNone(staticY) : "Undefined y";
		AbsolutePos pos = box.getAbsolutePos();
		if (pos.location.getLeftType() != Insets.TYPE_AUTO || pos.location.getRightType() != Insets.TYPE_AUTO) {
			staticX = StyleUtils.NONE;
		}
		if (pos.location.getTopType() != Insets.TYPE_AUTO || pos.location.getBottomType() != Insets.TYPE_AUTO) {
			staticY = StyleUtils.NONE;
		}
		Absolute absolute = new Absolute(box, staticX, staticY);
		if (this.absolutes == null) {
			this.absolutes = new ArrayList<Absolute>();
		}
		this.absolutes.add(absolute);
	}

	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		assert !StyleUtils.isNone(x) : "Undefined x";
		assert !StyleUtils.isNone(y) : "Undefined y";
		if (this.absolutes == null) {
			return;
		}
		for (int i = 0; i < this.absolutes.size(); ++i) {
			Absolute c = (Absolute) this.absolutes.get(i);
			double xx = StyleUtils.isNone(c.x) ? contextX : x + c.x;
			double yy = StyleUtils.isNone(c.y) ? contextY : y + c.y;
			if (c.box.getAbsolutePos().fiducial != Types.FODUCIAL_CONTEXT) {
				// 固定配置
				pageBox.addFixed(drawer, visitor, c.box, xx, yy);
				this.absolutes.remove(i);
				--i;
			} else {
				c.box.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yy);
			}
		}
	}

	public int getCount() {
		if (this.absolutes == null) {
			return 0;
		}
		return this.absolutes.size();
	}

	public Absolute getAbsolute(int i) {
		return (Absolute) this.absolutes.get(i);
	}
}
