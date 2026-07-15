package net.zamasoft.foliojet.style.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.Background;
import net.zamasoft.foliojet.style.box.params.Insets;
import net.zamasoft.foliojet.style.box.params.RectBorder;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class BackgroundBorderDrawable extends AbstractDrawable {
	protected final Background background;
	protected final RectBorder border;
	protected final Insets padding;
	protected final double width, height;

	public BackgroundBorderDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
			Background background, RectBorder border, Insets padding, double width, double height) {
		super(pageBox, clip, opacity, transform);
		this.background = background;
		this.border = border;
		this.width = width;
		this.height = height;
		this.padding = padding;
	}

	@Override
	public String describe() {
		return String.format(java.util.Locale.ROOT, "BackgroundBorder[w=%.2f h=%.2f]", this.width, this.height);
	}

	public void innerDraw(GC gc, double x, double y) throws GraphicsException {
		this.background.draw(gc, x, y, this.width, this.height, this.border, this.padding, null); // TODO text clip
	}
}
