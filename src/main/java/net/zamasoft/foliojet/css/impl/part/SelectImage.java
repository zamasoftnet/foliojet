package net.zamasoft.foliojet.css.impl.part;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * @author MIYABE Tatsuhiko
 */
public class SelectImage implements Image {
	private final boolean disabled;
	private final double height;

	public SelectImage(boolean disabled, double height) {
		this.disabled = disabled;
		this.height = height;
	}

	public double getWidth() {
		return 16.0;
	}

	public double getHeight() {
		return this.height;
	}

	public String getAltString() {
		return "○";
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {

			gc.setLineWidth(1.0);
			gc.setLinePattern(GC.STROKE_SOLID);
			Shape frame = new Rectangle2D.Double(0, 0, 16, this.height);
			Shape upFrame = new Rectangle2D.Double(0, 0, 16, 16);
			Shape up, upShadow;
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(8, 4);
				path.lineTo(3, 10);
				path.lineTo(13, 10);
				up = path;
				path = new GeneralPath();
				path.moveTo(16, 0);
				path.lineTo(16, 16);
				path.lineTo(0, 16);
				upShadow = path;
			}
			Shape downFrame = new Rectangle2D.Double(0, this.height - 16, 16, 16);
			Shape down, downShadow;
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(8, (float) (this.height - 4));
				path.lineTo(3, (float) (this.height - 10));
				path.lineTo(13, (float) (this.height - 10));
				down = path;
				path = new GeneralPath();
				path.moveTo(16, (float) (this.height - 16));
				path.lineTo(16, (float) this.height);
				path.lineTo(0, (float) this.height);
				downShadow = path;
			}

			gc.setFillPaint(ColorValueUtils.LIGHTGRAY.getColor());
			gc.fill(frame);

			gc.fill(upFrame);
			gc.setFillPaint((this.disabled ? ColorValueUtils.DIMGRAY : ColorValueUtils.BLACK).getColor());
			gc.fill(up);
			gc.draw(upShadow);

			gc.setFillPaint(ColorValueUtils.LIGHTGRAY.getColor());
			gc.fill(downFrame);
			gc.setFillPaint((this.disabled ? ColorValueUtils.DIMGRAY : ColorValueUtils.BLACK).getColor());
			gc.fill(down);
			gc.draw(downShadow);

		}
	}
}
