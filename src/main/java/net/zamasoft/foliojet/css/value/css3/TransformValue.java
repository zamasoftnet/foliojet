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

	/**
	 * 割合の平行移動が回転・拡大・傾斜の<b>後ろ</b>に来たときの交差成分
	 * (2026-08-29)。
	 *
	 * <p>
	 * 関数列 {@code f1 … fk … fn} のk番目が割合つき平行移動 T(v) のとき、
	 * 全体は A·T(v)·B = (A·B) + A_lin·v と分解できる(Aは前半の合成、
	 * A_linはその線形部)。v=(px·W, py·H) は箱の寸法に比例するので、
	 * A_lin·v = W·px·A_lin·e1 + H·py·A_lin·e2 ——つまり<b>Wの係数ベクトルと
	 * Hの係数ベクトル</b>を足し込んでおけば、行列は畳んだまま、寸法が
	 * 決まる描画時に平行移動を1回足すだけで済む。従来の
	 * {@code txRatio}(W→x)・{@code tyRatio}(H→y)に、{@code txRatioH}(H→x)・
	 * {@code tyRatioW}(W→y)を加えた4係数で任意の並びを表せる。平行移動だけ
	 * なら交差成分は0で、従来と同じ値になる。
	 * </p>
	 *
	 * <p>
	 * これで {@code translate(-50%,-50%) scale(1.1)}(中央寄せの定番書法)が
	 * 丸ごと無効になる問題が解けた。以前は「順序が効くので畳めない」として
	 * 宣言全体を捨てていた。
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

	/** 箱の高さに掛けてxの平行移動へ足す係数(交差成分)。 */
	public double getTxRatioH() {
		return this.txRatioH;
	}

	/** 箱の幅に掛けてyの平行移動へ足す係数(交差成分)。 */
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