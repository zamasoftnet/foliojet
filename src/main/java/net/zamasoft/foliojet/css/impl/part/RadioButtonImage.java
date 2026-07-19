package net.zamasoft.foliojet.css.impl.part;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;

import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * @author MIYABE Tatsuhiko
 */
public class RadioButtonImage implements Image {
	private final boolean checked, disabled;

	public RadioButtonImage(boolean checked, boolean disabled) {
		this.checked = checked;
		this.disabled = disabled;
	}

	public double getWidth() {
		return 12.0;
	}

	public double getHeight() {
		return 12.0;
	}

	public String getAltString() {
		return "○";
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {

			Shape frame = new Ellipse2D.Double(2, 2, 8, 8);
			Shape check = new Ellipse2D.Double(4, 4, 4, 4);

			gc.setFillPaint((this.disabled ? ColorValueUtils.LIGHTGRAY : ColorValueUtils.WHITE).getColor());
			gc.setStrokePaint((this.disabled ? ColorValueUtils.DIMGRAY : ColorValueUtils.BLACK).getColor());
			gc.setLineWidth(1.0);
			gc.setLinePattern(GC.STROKE_SOLID);
			gc.fillDraw(frame);

			if (this.checked) {
				gc.setFillPaint((this.disabled ? ColorValueUtils.DIMGRAY : ColorValueUtils.BLACK).getColor());
				gc.fill(check);
			}

		}
	}
}
