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

	/**
	 * {@code translate()}の<b>割合成分</b>(2026-08-03新設)。
	 *
	 * <p>
	 * CSS Transformsの{@code translate()}の割合は<b>その要素自身の境界箱</b>を
	 * 基準にするので、解析時には解けない(要素の寸法がまだ無い)。行列へ畳めない
	 * この分だけを別に持ち、描画時に箱の寸法を掛けて足す
	 * ({@code AbstractBox.transform})。{@code transform-origin}の割合が既に
	 * 同じ扱いになっている。
	 *
	 * <p>
	 * <b>平行移動だけで組まれた指定に限る。</b>回転や拡大と混ざると順序が効いて
	 * 畳めないため、その場合は従来どおり指定全体を無効にする。実地で使われるのは
	 * {@code translate(-50%,-50%)}(中央寄せ)や{@code translateX(-100%)}
	 * (画面外へ逃がすメニュー)がほとんどで、これらは平行移動だけである。
	 */
	private final double txRatio, tyRatio;

	public static TransformValue create(AffineTransform transform) {
		return create(transform, 0, 0);
	}

	public static TransformValue create(AffineTransform transform, double txRatio, double tyRatio) {
		if (transform.isIdentity() && txRatio == 0 && tyRatio == 0) {
			return IDENTITY_TRANSFORM_VALUE;
		}
		return new TransformValue(transform, txRatio, tyRatio);
	}

	protected TransformValue(AffineTransform transform) {
		this(transform, 0, 0);
	}

	protected TransformValue(AffineTransform transform, double txRatio, double tyRatio) {
		this.transform = transform;
		this.txRatio = txRatio;
		this.tyRatio = tyRatio;
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