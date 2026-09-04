package net.zamasoft.foliojet.layout.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import net.zamasoft.foliojet.layout.util.FilterGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;

public abstract class AbstractDrawable implements Drawable {
	protected final Shape clip;
	protected final PageBox pageBox;
	protected final float opacity;
	protected final AffineTransform transform;
	/**
	 * {@code mix-blend-mode}(2026-08-29)。出力先が層ごとのブレンド
	 * ({@link GC.Capability#BLEND_GROUP})を持てばこの描画要素を1つの層に
	 * してブレンドし、持たなければ描画命令ごとに適用する近似
	 * (MixBlendMode参照)。paramsを受けない具象クラスは
	 * {@link #withBlendMode}で生成直後に設定する。
	 */
	protected net.zamasoft.pdfg2d.gc.paint.BlendMode blendMode = net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL;
	/**
	 * {@code filter}(2026-08-29)。要素全体の効果は{@link Drawer}が層に
	 * まとめる。層を持てない描画要素では、塗りと画像をすり替える
	 * {@link FilterGC}で描画命令ごとに掛ける近似へ戻す。
	 * {@link #withFilter}で設定する。
	 */
	protected FilterValue filter = FilterValue.NONE;

	public AbstractDrawable(final PageBox pageBox, final Shape clip, final float opacity,
			final AffineTransform transform) {
		this.pageBox = pageBox;
		this.clip = clip;
		this.opacity = opacity;
		this.transform = transform;
	}

	/** ブレンドモードを設定して自身を返します(生成直後に呼ぶ)。 */
	public final AbstractDrawable withBlendMode(final net.zamasoft.pdfg2d.gc.paint.BlendMode mode) {
		this.blendMode = mode == null ? net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL : mode;
		return this;
	}

	/** フィルタを設定して自身を返します(生成直後に呼ぶ)。 */
	public final AbstractDrawable withFilter(final FilterValue filter) {
		this.filter = filter == null ? FilterValue.NONE : filter;
		return this;
	}

	/** Whether this drawable's main pass is redirected to its own output stream. */
	final boolean createsOwnGroup(final GC gc, final java.util.Set<FilterValue> groupedFilters) {
		final FilterValue f = this.filter.excluding(groupedFilters);
		final boolean blendGroup = this.blendMode != gc.getBlendMode() && gc.supports(GC.Capability.BLEND_GROUP);
		return blendGroup || this.opacity * f.opacity != 1f;
	}

	/**
	 * 表示リストダンプ用に、非恒等のGC変換とフィルタを{@code describe}
	 * 文字列へ追記します(2026-08-08)。ダンプの座標はGC変換前の値のため、
	 * transformを含む回帰はこれが無いとgoldenに一切現れない
	 * (ParamsFieldsの%translate脱落を10日間素通りさせた穴)。
	 * 変換・フィルタを使わない既存goldenは不変。フィルタは宣言した
	 * 要素の描画要素にだけ出す(継承で届いた子孫には出さない)。
	 */
	protected final String describeTransform(final String base) {
		String s = base;
		if (!this.transform.isIdentity()) {
			final double[] m = new double[6];
			this.transform.getMatrix(m);
			s = s + String.format(java.util.Locale.ROOT, " tf=[%.2f %.2f %.2f %.2f %.2f %.2f]", m[0], m[1], m[2],
					m[3], m[4], m[5]);
		}
		if (this.filter.declared != null) {
			s = s + " filter=[" + this.filter.declared + "]";
		}
		return s;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * クリップ形状の外接矩形を出力します(2026-08-09)。クリップを使わない
	 * 既存goldenは不変。
	 * </p>
	 */
	@Override
	public final String describeClip() {
		if (this.clip == null) {
			return "";
		}
		final java.awt.geom.Rectangle2D b = this.clip.getBounds2D();
		return String.format(java.util.Locale.ROOT, " clip=[%.2f %.2f %.2f %.2f]", b.getX(), b.getY(), b.getWidth(),
				b.getHeight());
	}

	public final void draw(GC gc, double x, double y) throws GraphicsException {
		final FilterValue f = FilterScope.effective(gc, this.filter);
		GC.State state = null;
		if (this.clip != null || !this.transform.isIdentity()) {
			state = gc.begin();
			if (this.clip != null) {
				gc.clip(this.clip);
			}
			if (!this.transform.isIdentity()) {
				gc.transform(this.transform);
			}
		}
		// mix-blend-mode(2026-08-29)。出力先が層のブレンドを持てば、この
		// 描画要素を丸ごと1つの層(グループ画像)にしてからブレンドする
		// (厳密)。持たなければ透明化グループの外側で設定し、描画命令
		// ごとにブレンドする(近似。グループ画像のDoにもモードが効く)。
		// 終了時に元の値へ戻す
		final net.zamasoft.pdfg2d.gc.paint.BlendMode outerBlend = gc.getBlendMode();
		final boolean blends = this.blendMode != outerBlend;
		final boolean blendGroup = blends && gc.supports(GC.Capability.BLEND_GROUP);
		if (blends && !blendGroup) {
			ApproximationGC.report(gc, "mix-blend-mode", "2822.per-drawable");
			gc.setBlendMode(this.blendMode);
		}

		// filter: opacity()はグループ不透明度に掛ける(仕様の順序は
		// filter→opacityだが、どちらも同じグループへの掛け算なので同じ)
		final float opacity = this.opacity * f.opacity;
		if (f.needsGroup()) {
			ApproximationGC.report(gc, "filter", "2822.per-drawable");
		}

		/* NoAndroid begin */
		final GC xgc;
		final GroupImageGC ggc;
		float alpha = gc.getFillAlpha();
		if (blendGroup || opacity != 1f) {
			// 透明化開始(層にまとめる)
			xgc = gc;
			ggc = gc.createGroupImage(this.pageBox.getWidth(), this.pageBox.getHeight());
			gc = ggc;
		} else {
			xgc = ggc = null;
			gc.setFillAlpha(opacity);
		}
		/* NoAndroid end */

		this.drawFilterApproximation(gc, x, y, f);

		/* NoAndroid begin */
		if (ggc != null) {
			// 透明化終了
			Image gi = ggc.finish();
			if (blendGroup) {
				xgc.setBlendMode(this.blendMode);
			}
			xgc.setFillAlpha(opacity);
			xgc.drawImage(gi);
			xgc.setFillAlpha(alpha);
			gc = xgc;
		} else {
			gc.setFillAlpha(alpha);
		}
		/* NoAndroid end */
		if (blends) {
			gc.setBlendMode(outerBlend);
		}

		if (state != null) {
			state.close();
		}
	}

	/** filterを描画命令ごとに掛ける従来の近似経路。 */
	private void drawFilterApproximation(final GC gc, final double x, final double y, final FilterValue f)
			throws GraphicsException {
		// drop-shadow()は内容の下、色変換の外(影の色は指定どおり)
		if (f.shadow != null) {
			this.drawFilterShadow(gc, x, y, f.shadow);
		}
		if (f.hasColorOps()) {
			// 色行列・ぼかしは塗りと画像をすり替えるGCで内容全体に掛ける
			this.innerDraw(new FilterGC(gc, f), x, y);
		} else {
			this.innerDraw(gc, x, y);
		}
	}

	/**
	 * {@code filter: drop-shadow()}の影を描きます(内容の前に呼ばれる。
	 * 出力先が層への落とし影を持つときは呼ばれない)。
	 * 既定は何もしない——形を知る具象クラス(枠・置換要素)が上書きする。
	 * 文字列の描画要素には効かない({@code text-shadow}を使うこと、記録済み)。
	 */
	protected void drawFilterShadow(final GC gc, final double x, final double y,
			final FilterValue.DropShadow shadow) throws GraphicsException {
		// no-op
	}

	public abstract void innerDraw(GC gc, double x, double y) throws GraphicsException;
}
