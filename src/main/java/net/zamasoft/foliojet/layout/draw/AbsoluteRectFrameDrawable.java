package net.zamasoft.foliojet.layout.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class AbsoluteRectFrameDrawable extends AbstractDrawable {
	protected final AbsoluteRectFrame frame;
	protected final double width, height;
	protected final Shape textClip;

	public AbsoluteRectFrameDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
			AbsoluteRectFrame frame, double width, double height, Shape textClip) {
		super(pageBox, clip, opacity, transform);
		// 確定寸法に番兵の算術結果やNaNが漏れると、内容が紙面の
		// どこにも現れないまま静かに欠落する(LayoutUtils.isDrawable参照)
		assert net.zamasoft.foliojet.layout.util.LayoutUtils.isDrawable(width) : "描画幅が異常: " + width;
		assert net.zamasoft.foliojet.layout.util.LayoutUtils.isDrawable(height) : "描画高が異常: " + height;
		this.frame = frame;
		this.width = width;
		this.height = height;
		this.textClip = textClip;
	}

	public void innerDraw(GC gc, double x, double y) throws GraphicsException {
		this.frame.draw(gc, x, y, this.width, this.height, this.textClip);
	}

	@Override
	public String describe() {
		return String.format(java.util.Locale.ROOT, "AbsoluteRectFrame[w=%.2f h=%.2f]", this.width, this.height);
	}
}
