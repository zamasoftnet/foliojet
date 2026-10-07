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
 * Manages out-of-flow boxes together.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: Absolutes.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Absolutes {
	/**
	 * An absolutely positioned box.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: Absolutes.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class Absolute {
		public final IAbsoluteBox box;
		public final double x, y;
		/**
		 * Whether {@code x} in vertical RL is a logical page position from the owner box's right edge,
		 * rather than a physical X coordinate. Neither the owner's width nor the absolutely positioned
		 * box's width is definite at registration time, so convert to physical X at drawing time using
		 * {@code ownerPageExtent - x - box.getWidth()}.
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
	 * Absolutely positioned boxes.
	 */
	private List<Absolute> absolutes = null;

	public Absolutes() {
		// ignore
	}

	/**
	 * Adds an absolutely positioned box.
	 *
	 * @param box
	 * @param staticX
	 * @param staticY
	 */
	public void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.addAbsolute(box, staticX, staticY, false);
	}

	/**
	 * @param blockStartAnchored true if {@code staticX} points to the box's right (block-start) edge in vertical RL
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
	 * Iterative drawing (2026-07-20, for the same reason as IBox.draw). Registering fixed-position
	 * boxes (pageBox.addFixed) adds no drawing to the current Drawer sequence; a separate page
	 * cycle handles it, so registration may run immediately regardless of traversal order.
	 * Push only the drawing steps of context-positioned boxes onto {@code worklist} in
	 * **reverse order** to preserve the original traversal order (reverse traversal eliminates
	 * the need to adjust indices on removal).
	 *
	 * @param ownerPageExtent the owner's physical width (used to convert {@link Absolute#blockStartAnchored} in vertical RL)
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
			// For a block-start-anchored static position (vertical RL), subtract the final box width to get the origin.
			// In vertical RL, static position is logical page position from the right edge; use final width for physical X.
			final double xx = LayoutUtils.isNone(c.x) ? contextX
					: x + (c.blockStartAnchored ? ownerPageExtent - c.x - c.box.getWidth() : c.x);
			final double yy = LayoutUtils.isNone(c.y) ? contextY : y + c.y;
			if (c.box.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				// Fixed positioning. To preserve the order of registration and initial drawing (pageBox.addFixed)
				// relative to other items, only remove the item from the list immediately;
				// defer actual registration and drawing by pushing it onto the worklist (2026-07-20:
				// discovered in the FixedOrderTest regression. Immediate drawing preceded interspersed
				// non-fixed items, breaking the original traversal order).
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
	 * Translates static positions along the page axis and rebuilds the ledger in the original order.
	 * For static positions stored as physical coordinates, use {@code y + dy} in horizontal writing,
	 * {@code x + dy} in vertical LR, and {@code x - dy} in vertical RL.
	 * If the page-axis value is {@link LayoutUtils#NONE}, that axis is determined by explicit
	 * {@code top/bottom} or {@code left/right}, not a static position, so leave it unchanged.
	 * Fixed-position boxes other than {@link Fiducial#CONTEXT} and boxes in {@code keep}
	 * also remain fixed on the page without moving.
	 *
	 * @param dy   the translation along the page axis
	 * @param flow the writing direction of the page container holding this ledger
	 * @param keep the set of boxes to keep at their current positions without moving
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
				// Use +dy for a logical page position relative to the right edge, or -dy for physical X.
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
