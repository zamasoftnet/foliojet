package net.zamasoft.foliojet.ua.impl.image;

import java.awt.image.BufferedImage;

import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;

/**
 * Paged SVGで再圧縮せず再利用できる元画像を持つラスタ画像。
 * 通常出力では生成せず、元の符号化を共有資産へ出す場合だけ使う。
 */
public final class EncodedRasterImage extends RasterImageImpl {
	private final byte[] encoded;
	private final String mediaType;
	private final String extension;

	public EncodedRasterImage(final BufferedImage image, final byte[] encoded, final String mediaType,
			final String extension) {
		super(image);
		this.encoded = encoded;
		this.mediaType = mediaType;
		this.extension = extension;
	}

	public byte[] getEncoded() {
		return this.encoded;
	}

	public String getMediaType() {
		return this.mediaType;
	}

	public String getExtension() {
		return this.extension;
	}
}
