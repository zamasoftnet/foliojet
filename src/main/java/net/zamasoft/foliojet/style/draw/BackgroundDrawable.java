package net.zamasoft.foliojet.style.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.Background;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class BackgroundDrawable extends AbstractDrawable {
	protected final Background background;
	protected final double width, height;

	public BackgroundDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
			Background background, double width, double height) {
		super(pageBox, clip, opacity, transform);
		this.background = background;
		this.width = width;
		this.height = height;
	}

	public void innerDraw(GC gc, double x, double y) throws GraphicsException {
		this.background.draw(gc, x, y, this.width, this.height, null, null, null);// TODO text clip
	}

	@Override
	public String describe() {
		return String.format(java.util.Locale.ROOT, "Background[w=%.2f h=%.2f]", this.width, this.height);
	}
}
