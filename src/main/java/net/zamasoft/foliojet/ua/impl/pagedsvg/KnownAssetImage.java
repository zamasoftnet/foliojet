package net.zamasoft.foliojet.ua.impl.pagedsvg;

import net.zamasoft.foliojet.ua.ImageMetricsCache;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * 前回の出力で書かれた資源を指すだけの画像です(2026-08-28)。
 *
 * <p>
 * Paged SVGのページは画像を{@code assets/images/<sha256>.<ext>}という
 * 内容ハッシュの名前で参照します。そのため
 * {@code output.paged-svg.resources=omit}の再変換でも、名前を決めるために
 * 画像のバイト列を読み直す必要がありました。前回の{@code metrics.json}に
 * 記録した同一性({@link ImageMetricsCache.Asset})を渡せば、この画像を
 * 立てるだけで済み、<b>資源を一度も開きません</b>(遠隔資源では取得の
 * 往復がまるごと無くなります)。
 * </p>
 *
 * <p>
 * 描けるのは{@link DirectPagedSVGGC}だけです。ほかのGCへ渡っても
 * 何も描かないので、{@link PagedSVGUserAgent}は
 * <b>直接書き出し+omitのときだけ</b>この画像を返します。
 * </p>
 */
final class KnownAssetImage implements Image {
	/** 組版に使う論理寸法(pt)。 */
	private final double width, height;

	/** 前回の出力で書かれた資源の同一性。 */
	final ImageMetricsCache.Asset asset;

	KnownAssetImage(final double width, final double height, final ImageMetricsCache.Asset asset) {
		this.width = width;
		this.height = height;
		this.asset = asset;
	}

	@Override
	public double getWidth() {
		return this.width;
	}

	@Override
	public double getHeight() {
		return this.height;
	}

	@Override
	public void drawTo(final GC gc) {
		// 参照だけの画像なので、自分では描かない。
		// DirectPagedSVGGC.drawImage がこの型を見て参照を書く
	}

	@Override
	public String getAltString() {
		return null;
	}
}
