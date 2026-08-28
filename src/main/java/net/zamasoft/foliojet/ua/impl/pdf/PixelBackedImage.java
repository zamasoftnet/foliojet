package net.zamasoft.foliojet.ua.impl.pdf;

import java.util.function.Supplier;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;

/**
 * PDFへ直接登録した画像({@code PDFImage}、画素を持たない)に、必要に
 * なったときだけ復号した画素を添える包み紙です(2026-08-29新設)。
 *
 * <p>
 * {@code PDFUserAgent}は画像をPDFWriterへ直接読ませて({@code loadImage})
 * 復号を省くため、描画時の{@code Image}は画素を持たない。ところが
 * {@code filter}のグレースケール等はラスタの画素を変換して別の画像に
 * する必要がある。そこで画素は{@link #getPixels}で遅延して読み、フィルタが
 * 無い普通の文書では元の経路のまま(復号もメモリも増えない)にする。
 * </p>
 */
public final class PixelBackedImage extends WrappedImage {
	private final Supplier<Image> pixels;
	private Image loaded;
	private boolean tried;

	public PixelBackedImage(final Image image, final Supplier<Image> pixels) {
		super(image);
		this.pixels = pixels;
	}

	/** 復号した画素つきの画像({@code RasterImage})。読めなければnull。 */
	public synchronized Image getPixels() {
		if (!this.tried) {
			this.tried = true;
			this.loaded = this.pixels.get();
		}
		return this.loaded;
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
	public void drawTo(final GC gc) throws GraphicsException {
		this.image.drawTo(gc);
	}

	@Override
	public String getAltString() {
		return this.image.getAltString();
	}
}
