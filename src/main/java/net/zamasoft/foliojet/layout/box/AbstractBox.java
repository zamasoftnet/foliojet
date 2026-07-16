package net.zamasoft.foliojet.layout.box;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.params.Offset;

public abstract class AbstractBox implements IBox {
	public BoxSubtype getSubtype() {
		return BoxSubtype.NONE;
	}

	protected final AffineTransform transform(AffineTransform transform, double x, double y) {
		AffineTransform ct = this.getParams().transform;
		if (ct.isIdentity()) {
			return transform;
		}
		transform = new AffineTransform(transform);
		double ax = x;
		double ay = y;
		Offset offset = this.getParams().transformOrigin;
		switch (offset.getXType()) {
		case ABSOLUTE:
			ax += offset.getX();
			break;
		case RELATIVE:
			ax += this.getWidth() * offset.getX();
			break;
		default:
			throw new IllegalStateException();
		}
		switch (offset.getXType()) {
		case ABSOLUTE:
			ay += offset.getY();
			break;
		case RELATIVE:
			ay += this.getHeight() * offset.getY();
			break;
		default:
			throw new IllegalStateException();
		}

		transform.translate(ax, ay);
		transform.concatenate(ct);
		transform.translate(-ax, -ay);
		return transform;
	}

	public String toString() {
		return super.toString() + "[width=" + this.getWidth() + ",height=" + this.getHeight() + ",params="
				+ this.getParams() + ",pos=" + this.getPos() + "]";
	}
}
