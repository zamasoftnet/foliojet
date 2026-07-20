package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.Insets;

import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

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
		assert !LayoutUtils.isNone(staticX) : "Undefined x";
		assert !LayoutUtils.isNone(staticY) : "Undefined y";
		AbsolutePos pos = box.getAbsolutePos();
		if (pos.location.getLeftType() != LengthType.AUTO || pos.location.getRightType() != LengthType.AUTO) {
			staticX = LayoutUtils.NONE;
		}
		if (pos.location.getTopType() != LengthType.AUTO || pos.location.getBottomType() != LengthType.AUTO) {
			staticY = LayoutUtils.NONE;
		}
		Absolute absolute = new Absolute(box, staticX, staticY);
		if (this.absolutes == null) {
			this.absolutes = new ArrayList<Absolute>();
		}
		this.absolutes.add(absolute);
	}

	/**
	 * drawの反復化(2026-07-20、IBox.drawと同じ理由)。固定配置ボックスの
	 * 登録(pageBox.addFixed)はこの場のDrawer列に描画を加えず、別ページ
	 * サイクルで扱われるため、走査順に関係なく即座に行ってよい。context
	 * 配置のボックスの描画手順だけを、元の走査順のまま**逆順**で
	 * {@code worklist}へ積む(逆順走査により、削除時のインデックス補正が
	 * 不要になる)。
	 */
	public void pushDraw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x) : "Undefined x";
		assert !LayoutUtils.isNone(y) : "Undefined y";
		if (this.absolutes == null) {
			return;
		}
		for (int i = this.absolutes.size() - 1; i >= 0; --i) {
			final Absolute c = (Absolute) this.absolutes.get(i);
			final double xx = LayoutUtils.isNone(c.x) ? contextX : x + c.x;
			final double yy = LayoutUtils.isNone(c.y) ? contextY : y + c.y;
			if (c.box.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				// 固定配置。登録・初回描画(pageBox.addFixed)は他の項目との
				// 相対順序を保つため、リストからの除去だけ即座に行い、
				// 実際の登録・描画はworklistへ積んで遅延させる(2026-07-20、
				// FixedOrderTest回帰で発見: 即座に描画すると、混在する
				// 非固定配置の項目より先に描画されてしまい元の走査順が崩れる)
				this.absolutes.remove(i);
				worklist.push(w -> pageBox.addFixed(drawer, visitor, c.box, xx, yy));
			} else {
				worklist.push(IBox.drawStep(c.box, pageBox, drawer, visitor, clip, transform, contextX, contextY, xx,
						yy));
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
