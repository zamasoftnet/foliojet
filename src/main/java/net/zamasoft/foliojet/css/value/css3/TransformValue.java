package net.zamasoft.foliojet.css.value.css3;

import java.awt.geom.AffineTransform;
import net.zamasoft.foliojet.css.value.Value;

/**
 * transform です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class TransformValue implements Value {
	public static final TransformValue IDENTITY_TRANSFORM_VALUE = new TransformValue(new AffineTransform());

	private final AffineTransform transform;

	public static TransformValue create(AffineTransform transform) {
		if (transform.isIdentity()) {
			return IDENTITY_TRANSFORM_VALUE;
		}
		return new TransformValue(transform);
	}

	protected TransformValue(AffineTransform transform) {
		this.transform = transform;
	}

	public AffineTransform getTransform() {
		return this.transform;
	}

}