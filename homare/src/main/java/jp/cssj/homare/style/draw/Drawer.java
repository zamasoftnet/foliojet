package jp.cssj.homare.style.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.cssj.homare.style.util.StyleUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * 描画可能なオブジェクトを描画します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Drawer.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Drawer implements Comparable<Drawer> {
	/**
	 * 位置が決められた描画可能ボックスです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: Drawer.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	protected static class ArrangedDrawable {
		private final Drawable drawable;
		private final double x, y;

		public ArrangedDrawable(Drawable drawable, double x, double y) {
			assert !StyleUtils.isNone(x) : "Undefined x";
			assert !StyleUtils.isNone(y) : "Undefined y";
			this.drawable = drawable;
			this.x = x;
			this.y = y;
		}

		public void draw(GC gc) throws GraphicsException {
			this.drawable.draw(gc, this.x, this.y);
		}
	}

	protected final int z;
	protected List<ArrangedDrawable> drawables = null;
	protected List<Drawer> drawers = null;

	public Drawer(int z) {
		this.z = z;
	}

	public void visitDrawable(Drawable drawable, double x, double y) {
		assert !StyleUtils.isNone(x) : "Undefined x";
		assert !StyleUtils.isNone(y) : "Undefined y";
		if (this.drawables == null) {
			this.drawables = new ArrayList<ArrangedDrawable>();
		}
		this.drawables.add(new ArrangedDrawable(drawable, x, y));
	}

	public void visitDrawer(Drawer drawer) {
		if (this.drawers == null) {
			this.drawers = new ArrayList<Drawer>();
		}
		this.drawers.add(drawer);
	}

	public void draw(GC gc) throws GraphicsException {
		if (this.drawables != null) {
			for (int i = 0; i < this.drawables.size(); ++i) {
				ArrangedDrawable drawable = (ArrangedDrawable) this.drawables.get(i);
				drawable.draw(gc);
			}
		}

		if (this.drawers != null) {
			// z-indexによるソート
			// 原則ドキュメントの開始順に整列するため、順序が安定したソートアルゴリズムを使用すること
			Collections.sort(this.drawers);
			for (int i = 0; i < this.drawers.size(); ++i) {
				Drawer drawer = (Drawer) this.drawers.get(i);
				drawer.draw(gc);
			}
		}
	}

	public int compareTo(Drawer o) {
		Drawer a1 = this;
		Drawer a2 = (Drawer) o;
		long s1 = a1.z;
		long s2 = a2.z;
		return s1 < s2 ? -1 : s1 > s2 ? 1 : 0;
	}
}
