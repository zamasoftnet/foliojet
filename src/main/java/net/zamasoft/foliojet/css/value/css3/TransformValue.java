package net.zamasoft.foliojet.css.value.css3;

import java.awt.geom.AffineTransform;

/**
 * transform です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TransformValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TransformValue implements CSS3Value {
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

	public short getValueType() {
		return TYPE_TRANSFORM;
	}
}