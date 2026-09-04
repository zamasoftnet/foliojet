package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.net.URI;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;

/**
 * 取得元のURIを添えた画像です(2026-08-28、Paged SVGの再変換用)。
 *
 * <p>
 * 描画時に決まる資源の同一性(内容ハッシュ)を、<b>どのURIの画像だったか</b>と
 * 結び付けて{@code metrics.json}へ書くために使います。GCは
 * {@link WrappedImage}の連鎖を辿って中身へ届くので、ここに挟んでも
 * 描画の振る舞いは変わりません。
 * </p>
 */
final class SourcedImage extends WrappedImage {
	final URI uri;

	/**
	 * 随伴の PDF が同じ取得元から作った絵(2026-09-03、PDF の同時出力)。PDF は
	 * 取得元 URI で画像を重複排除し、JPEG は元のバイト列のまま埋めるので、
	 * 従へはこちらを渡す。無ければ主の絵をそのまま。
	 */
	Image companion;

	SourcedImage(final Image image, final URI uri) {
		super(image);
		this.uri = uri;
	}

	@Override
	public double getWidth() {
		return this.image.getWidth();
	}

	@Override
	public double getHeight() {
		return this.image.getHeight();
	}

	@Override
	public void drawTo(final GC gc) {
		this.image.drawTo(gc);
	}

	@Override
	public String getAltString() {
		return this.image.getAltString();
	}
}
