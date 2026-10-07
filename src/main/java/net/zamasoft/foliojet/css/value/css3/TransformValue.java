package net.zamasoft.foliojet.css.value.css3;

import java.awt.geom.AffineTransform;
import net.zamasoft.foliojet.css.value.Value;

/**
 * transform.
 *
 * @author MIYABE Tatsuhiko
 */
public class TransformValue implements Value {
	public static final TransformValue IDENTITY_TRANSFORM_VALUE = new TransformValue(new AffineTransform());

	private final AffineTransform transform;

	/**
	 * The <b>percentage components</b> of {@code translate()} (added on 2026-08-03).
	 *
	 * <p>
	 * Percentages in CSS Transforms' {@code translate()} refer to <b>the element's own border box</b>,
	 * so parsing cannot resolve them (the element's dimensions are still unknown). Retains only
	 * these components, which cannot be folded into the matrix, separately; at rendering time,
	 * multiplies them by the box dimensions and adds them ({@code AbstractBox.transform}).
	 * Percentages in {@code transform-origin} already use the same approach.
	 *
	 * <p>
	 * <b>Limited to specifications containing only translations.</b> When mixed with rotations or scales,
	 * order matters and folding is impossible, so the entire specification is invalidated as before.
	 * Most real-world uses are {@code translate(-50%,-50%)} (centering) and
	 * {@code translateX(-100%)} (off-screen menus), both of which contain only translations.
	 */
	private final double txRatio, tyRatio;

	/**
	 * Cross components when percentage translations appear <b>after</b> rotations, scales, or skews
	 * (2026-08-29).
	 *
	 * <p>
	 * When the kth function in {@code f1 … fk … fn} is a percentage translation T(v),
	 * the whole transform decomposes as A·T(v)·B = (A·B) + A_lin·v, where A is the composition
	 * of preceding functions and A_lin is its linear part. Since v=(px·W, py·H) is proportional
	 * to the box dimensions, A_lin·v = W·px·A_lin·e1 + H·py·A_lin·e2. Accumulating
	 * <b>the coefficient vectors for W and H</b> keeps the matrix folded and requires adding
	 * only one translation at rendering time, when dimensions are known. Adding
	 * {@code txRatioH} (H→x) and {@code tyRatioW} (W→y) to the existing
	 * {@code txRatio} (W→x) and {@code tyRatio} (H→y) gives four coefficients that represent
	 * any ordering. For translations alone, the cross components are zero, giving the same values as before.
	 * </p>
	 *
	 * <p>
	 * This resolved the problem where {@code translate(-50%,-50%) scale(1.1)}
	 * (a common centering idiom) was invalidated entirely. Previously, the whole declaration
	 * was discarded on the grounds that order mattered and folding was impossible.
	 * </p>
	 */
	private final double txRatioH, tyRatioW;

	public static TransformValue create(AffineTransform transform) {
		return create(transform, 0, 0);
	}

	public static TransformValue create(AffineTransform transform, double txRatio, double tyRatio) {
		return create(transform, txRatio, tyRatio, 0, 0);
	}

	public static TransformValue create(AffineTransform transform, double txRatio, double tyRatio,
			double txRatioH, double tyRatioW) {
		if (transform.isIdentity() && txRatio == 0 && tyRatio == 0 && txRatioH == 0 && tyRatioW == 0) {
			return IDENTITY_TRANSFORM_VALUE;
		}
		return new TransformValue(transform, txRatio, tyRatio, txRatioH, tyRatioW);
	}

	protected TransformValue(AffineTransform transform) {
		this(transform, 0, 0, 0, 0);
	}

	protected TransformValue(AffineTransform transform, double txRatio, double tyRatio) {
		this(transform, txRatio, tyRatio, 0, 0);
	}

	protected TransformValue(AffineTransform transform, double txRatio, double tyRatio, double txRatioH,
			double tyRatioW) {
		this.transform = transform;
		this.txRatio = txRatio;
		this.tyRatio = tyRatio;
		this.txRatioH = txRatioH;
		this.tyRatioW = tyRatioW;
	}

	/** The coefficient multiplied by the box height and added to x translation (cross component). */
	public double getTxRatioH() {
		return this.txRatioH;
	}

	/** The coefficient multiplied by the box width and added to y translation (cross component). */
	public double getTyRatioW() {
		return this.tyRatioW;
	}

	public AffineTransform getTransform() {
		return this.transform;
	}

	public double getTxRatio() {
		return this.txRatio;
	}

	public double getTyRatio() {
		return this.tyRatio;
	}

}