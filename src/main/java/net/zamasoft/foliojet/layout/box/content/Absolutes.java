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
import net.zamasoft.foliojet.layout.box.params.WritingMode;

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
		/**
		 * 縦組みRLで、{@code x}が物理Xでなく「所有箱の右端からの論理page位置」か。
		 * 登録時には所有箱の幅も絶対配置箱の幅も未確定なので、描画時に
		 * {@code ownerPageExtent - x - box.getWidth()} で物理Xへ写す。
		 */
		public final boolean blockStartAnchored;

		public Absolute(IAbsoluteBox box, double x, double y) {
			this(box, x, y, false);
		}

		public Absolute(IAbsoluteBox box, double x, double y, boolean blockStartAnchored) {
			this.box = box;
			this.x = x;
			this.y = y;
			this.blockStartAnchored = blockStartAnchored;
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
		this.addAbsolute(box, staticX, staticY, false);
	}

	/**
	 * @param blockStartAnchored 縦組みRLで{@code staticX}が箱の右端(block-start辺)を指すならtrue
	 */
	public void addAbsolute(IAbsoluteBox box, double staticX, double staticY, boolean blockStartAnchored) {
		assert !LayoutUtils.isNone(staticX) : "Undefined x";
		assert !LayoutUtils.isNone(staticY) : "Undefined y";
		AbsolutePos pos = box.getAbsolutePos();
		if (pos.location.getLeftType() != LengthType.AUTO || pos.location.getRightType() != LengthType.AUTO) {
			staticX = LayoutUtils.NONE;
		}
		if (pos.location.getTopType() != LengthType.AUTO || pos.location.getBottomType() != LengthType.AUTO) {
			staticY = LayoutUtils.NONE;
		}
		Absolute absolute = new Absolute(box, staticX, staticY, blockStartAnchored && !LayoutUtils.isNone(staticX));
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
	/**
	 * @param ownerPageExtent 所有箱の物理幅(縦組みRLの{@link Absolute#blockStartAnchored}の変換に使う)
	 */
	public void pushDraw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, double ownerPageExtent, Deque<DrawStep> worklist) {
		assert !LayoutUtils.isNone(x) : "Undefined x";
		assert !LayoutUtils.isNone(y) : "Undefined y";
		if (this.absolutes == null) {
			return;
		}
		for (int i = this.absolutes.size() - 1; i >= 0; --i) {
			final Absolute c = (Absolute) this.absolutes.get(i);
			// block-start辺基準(縦組みRL)の静的位置は、確定した箱の幅を引いて原点へ
			// 縦組みRLの静的位置は右端からの論理page位置。確定した幅で物理Xへ
			final double xx = LayoutUtils.isNone(c.x) ? contextX
					: x + (c.blockStartAnchored ? ownerPageExtent - c.x - c.box.getWidth() : c.x);
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

	/**
	 * 静的位置をページ軸方向へ平行移動し、元の順序で台帳を作り直します。
	 * 物理座標に格納された静的位置は、横書きでは{@code y + dy}、
	 * 縦書きLRでは{@code x + dy}、縦書きRLでは{@code x - dy}とします。
	 * ページ方向の値が{@link LayoutUtils#NONE}なら、その軸は明示した
	 * {@code top/bottom}または{@code left/right}で決まり静的位置ではないため
	 * 動かしません。{@link Fiducial#CONTEXT}以外の固定配置と、{@code keep}に
	 * 含まれるボックスもページに固定されたまま動かしません。
	 *
	 * @param dy   ページ軸方向の移動量
	 * @param flow この台帳を持つページコンテナの書字方向
	 * @param keep 移動せず現在位置に留めるボックスの集合
	 */
	public void shiftPageAxis(final double dy, final WritingMode flow, final java.util.Set<IBox> keep) {
		if (this.absolutes == null) {
			return;
		}
		for (int i = 0; i < this.absolutes.size(); ++i) {
			final Absolute absolute = this.absolutes.get(i);
			if (keep.contains(absolute.box)
					|| absolute.box.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				continue;
			}
			final Absolute shifted;
			switch (flow) {
			case TB:
				if (LayoutUtils.isNone(absolute.y)) {
					continue;
				}
				shifted = new Absolute(absolute.box, absolute.x, absolute.y + dy, absolute.blockStartAnchored);
				break;
			case LR:
				if (LayoutUtils.isNone(absolute.x)) {
					continue;
				}
				shifted = new Absolute(absolute.box, absolute.x + dy, absolute.y, absolute.blockStartAnchored);
				break;
			case RL:
				if (LayoutUtils.isNone(absolute.x)) {
					continue;
				}
				// 右端基準の論理page位置は+dy、物理Xなら-dy
				shifted = new Absolute(absolute.box, absolute.blockStartAnchored ? absolute.x + dy : absolute.x - dy,
						absolute.y, absolute.blockStartAnchored);
				break;
			default:
				throw new IllegalStateException();
			}
			this.absolutes.set(i, shifted);
		}
	}
}
