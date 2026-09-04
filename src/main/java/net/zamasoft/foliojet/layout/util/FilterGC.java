package net.zamasoft.foliojet.layout.util;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * {@code filter}の色行列・ぼかしを、描画命令の途中で塗りと画像に
 * 掛ける{@link GC}の包み紙です(2026-08-29新設)。
 *
 * <p>
 * 描画要素(背景・境界・文字・画像)は塗りを{@link #setFillPaint}/
 * {@link #setStrokePaint}で設定し、画像を{@link #drawImage}で描く。
 * この2箇所で{@link FilterOps}を通せば、描画要素の実装を一切触らずに
 * 効果が掛かる。文字の色も塗りなので同じ経路で変わる。
 * {@link #fillBlurred}と{@link #tryFillBlurred}は設定済みの
 * (=変換済みの)塗りで塗るので素通し。
 * </p>
 *
 * <p>
 * {@link #createGroupImage}で作った子のGC(入れ子の不透明度)も包み、
 * 子の描画にも効果が届くようにする。それ以外は素通し
 * ({@link AbstractDelegatingGC})。出力先が{@code GROUP_FILTER}に対応する
 * ときはこの包み紙は使われず、{@code AbstractDrawable}が要素全体を
 * グループ画像にして{@link GroupEffects}で掛ける。
 * </p>
 */
public final class FilterGC extends AbstractDelegatingGC {
	private final FilterValue filter;

	public FilterGC(final GC gc, final FilterValue filter) {
		super(gc);
		this.filter = filter;
	}

	/** 現在の変換での1画素あたりのpt(ぼかしの換算用)。 */
	private double pixelScale() {
		final AffineTransform at = this.gc.getTransform();
		if (at == null) {
			return 1;
		}
		final double det = Math.abs(at.getDeterminant());
		return det > 0 ? Math.sqrt(det) : 1;
	}

	@Override
	public void setStrokePaint(final Paint paint) throws GraphicsException {
		this.gc.setStrokePaint(FilterOps.apply(this.filter, paint, this.pixelScale()));
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		this.gc.setFillPaint(FilterOps.apply(this.filter, paint, this.pixelScale()));
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		this.gc.drawImage(FilterOps.apply(this.filter, image, this.pixelScale()));
	}

	@Override
	public void drawImage(final Image image, final GroupEffects effects) throws GraphicsException {
		this.gc.drawImage(FilterOps.apply(this.filter, image, this.pixelScale()), effects);
	}

	@Override
	public GroupEffectsResult drawGroupEffects(final Image image, final GroupEffects effects)
			throws GraphicsException {
		return this.gc.drawGroupEffects(FilterOps.apply(this.filter, image, this.pixelScale()), effects);
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createGroupImage(width, height), this.filter);
	}

	@Override
	public GroupImageGC createFilterGroup(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createFilterGroup(width, height), this.filter);
	}

	/** 入れ子のグループにも効果を届ける包み紙。 */
	private static final class Group extends AbstractDelegatingGC implements GroupImageGC {
		private final GroupImageGC group;

		Group(final GroupImageGC group, final FilterValue filter) {
			super(new FilterGC(group, filter));
			this.group = group;
		}

		@Override
		public Image finish() throws GraphicsException {
			return this.group.finish();
		}
	}
}
