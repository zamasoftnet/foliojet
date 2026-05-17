package jp.cssj.homare.style.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import jp.cssj.homare.style.box.impl.PageBox;
import jp.cssj.homare.style.box.params.Background;
import jp.cssj.homare.style.box.params.Insets;
import jp.cssj.homare.style.box.params.RectBorder;
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

	public void innerDraw(GC gc, double x, double y) throws GraphicsException {
		this.background.draw(gc, x, y, this.width, this.height, this.border, this.padding, null); // TODO text clip
	}
}
