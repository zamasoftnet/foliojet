package net.zamasoft.foliojet.ua.impl.image;

import net.zamasoft.pdfg2d.util.ImageInputStreamProxy;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReader;

import net.zamasoft.foliojet.ua.ImageLoader;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;

public class RasterImageLoader implements ImageLoader {
	public boolean match(Source key) {
		return true;
	}

	public int priority() {
		return -1000;
	}

	@SuppressWarnings("resource")
	public boolean available(Source source) throws IOException {
		ImageInputStream imageIn;
		if (source.isFile()) {
			imageIn = new FileImageInputStream(source.getFile());
		} else {
			imageIn = ImageIO.createImageInputStream(source.getInputStream());
		}
		try { // ImageIOによるラスタ画像の取得
			Iterator<ImageReader> iri = ImageIO.getImageReaders(imageIn);
			if (iri != null && iri.hasNext()) {
				return true;
			}
			return false;
		} finally {
			imageIn.close();
		}
	}

	/**
	 * 描画しないパス向けに、画素を展開せず固有寸法とEXIF方向だけを読みます。
	 */
	public Image loadImageForLayout(final Source source) throws IOException {
		final ImageInputStream imageIn;
		if (source.isFile()) {
			imageIn = new FileImageInputStream(source.getFile()) {
				public void flushBefore(long pos) {
					// EXIF走査のため先頭へ戻れる状態を保つ。
				}
			};
		} else {
			imageIn = new FileCacheImageInputStream(source.getInputStream(), null) {
				public void flushBefore(long pos) {
					// EXIF走査のため先頭へ戻れる状態を保つ。
				}
			};
		}
		if (imageIn == null) {
			throw new IOException("ImageIOがサポートしない画像形式です");
		}
		try {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageIn);
			if (readers == null || !readers.hasNext()) {
				throw new IOException("ImageIOがサポートしない画像形式です");
			}
			final ImageReader reader = readers.next();
			final int width;
			final int height;
			try {
				reader.setInput(imageIn);
				width = reader.getWidth(0);
				height = reader.getHeight(0);
			} finally {
				reader.dispose();
			}

			int orientation = 1;
			imageIn.seek(0);
			try {
				final Metadata metadata = ImageMetadataReader.readMetadata(new ImageInputStreamProxy(imageIn));
				final Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
				if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
					orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
				}
			} catch (ImageProcessingException | MetadataException e) {
				// 方向情報が無ければ通常方向として扱う。
			}

			final Image metrics = new Image() {
				public double getWidth() {
					return width;
				}

				public double getHeight() {
					return height;
				}

				public void drawTo(net.zamasoft.pdfg2d.gc.GC gc) {
					// 描画しないパスの寸法専用画像。
				}

				public String getAltString() {
					return null;
				}
			};
			return applyOrientation(metrics, orientation);
		} finally {
			imageIn.close();
		}
	}

	public Image loadImage(UserAgent ua, Source source) throws IOException {
		ImageInputStream imageIn;
		if (source.isFile()) {
			imageIn = new FileImageInputStream(source.getFile()) {
				public void flushBefore(long pos) throws IOException {
					// 再読み込み不可能になることを防止するため、flushを無視する
				}
			};
		} else {
			imageIn = new FileCacheImageInputStream(source.getInputStream(), null) {
				public void flushBefore(long pos) throws IOException {
					// 再読み込み不可能になることを防止するため、flushを無視する
				}
			};
		}
		try { // ImageIOによるラスタ画像の取得
			JPEGImageReader cir = null;
			ImageReader jdkJpeg = null;
			Iterator<ImageReader> iri = ImageIO.getImageReaders(imageIn);
			if (System.getProperty("foliojet.debug.imageTrace") != null) {
				System.err.println("[img] source=" + source.getURI() + " isFile=" + source.isFile());
			}
			ImageReader ir = null;
			while (iri != null && iri.hasNext()) {
				ir = iri.next();
				ir.setInput(imageIn);
				try {
					Iterator<ImageTypeSpecifier> iti = ir.getImageTypes(0);
					if (iti != null && iti.hasNext()) {
						imageIn.seek(0);
						if (ir instanceof JPEGImageReader) {
							cir = (JPEGImageReader)ir;
							ir = null;
							continue;
						}
						if (ir.getClass().getName().startsWith("com.sun.imageio.plugins.jpeg.")) {
							// JDK標準のJPEGリーダはCMYK(4成分)を読めないため
							// 後回しにする。ImageIOレジストリの登録順は環境で
							// 変わる(デーモンではJDKが先に並ぶことを実測)ので、
							// 順序に依存せずTwelveMonkeys優先を保証する(2026-08-10)
							jdkJpeg = ir;
							ir = null;
							continue;
						}
						break;
					}
				} catch (IOException e) {
					// ignore
				}
				ir.dispose();
				ir = null;
				imageIn.seek(0);
			}
			if (ir == null) {
				if (cir != null) {
					ir = cir;
				} else if (jdkJpeg != null) {
					ir = jdkJpeg;
				} else {
					throw new IOException("ImageIOがサポートしない画像形式です");
				}
			}
			else {
				if (cir != null) {
					cir.dispose();
				}
			}
			imageIn.seek(0);
			String formatName = null;
			try {
				formatName = ir.getFormatName();
			} catch (IOException e) {
				// 形式名が取れない場合は従来どおり復号画像を使う。
			}
			if (System.getProperty("foliojet.debug.imageTrace") != null) {
				System.err.println("[img] chosen=" + ir.getClass().getName());
			}
			
			int orientation = 1;
			try {
				Metadata metadata = ImageMetadataReader.readMetadata(new ImageInputStreamProxy(imageIn));
				Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
				if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
					// EXIFありかつ、画像方向ありの場合は取得する
					orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
				}
			} catch (ImageProcessingException e) {
				// ignore
			} catch (MetadataException e) {
				// ignore
			}

			final java.awt.image.BufferedImage decoded;
			try {
				decoded = G2DUtils.loadImage(ir, imageIn);
			} catch (final RuntimeException | IOException e) {
				if (System.getProperty("foliojet.debug.imageTrace") != null) {
					System.err.println("[img] load failed: " + e);
					e.printStackTrace();
				}
				throw e;
			}
			if (System.getProperty("foliojet.debug.imageTrace") != null) {
				System.err.println("[img] decoded " + decoded.getWidth() + "x" + decoded.getHeight() + " type="
						+ decoded.getType());
			}
			final boolean keepOriginalJpeg = orientation == 1 && formatName != null
					&& (formatName.equalsIgnoreCase("jpeg") || formatName.equalsIgnoreCase("jpg"))
					&& "application/vnd.copper.paged-svg".equals(UAProps.OUTPUT_TYPE.getString(ua));
			final Image image;
			if (keepOriginalJpeg) {
				image = new EncodedRasterImage(decoded, readAll(imageIn), "image/jpeg", "jpg");
			} else {
				image = new RasterImageImpl(decoded);
			}
			if (orientation == 1) {
				return image;
			}
			return applyOrientation(image, orientation);
		} finally {
			imageIn.close();
		}
	}

	private static byte[] readAll(final ImageInputStream imageIn) throws IOException {
		imageIn.seek(0);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final byte[] buffer = new byte[8192];
		for (int len; (len = imageIn.read(buffer)) != -1;) {
			bytes.write(buffer, 0, len);
		}
		return bytes.toByteArray();
	}

	private static Image applyOrientation(final Image image, final int orientation) {
		final AffineTransform at = new AffineTransform();
		final double width = image.getWidth();
		final double height = image.getHeight();
		switch (orientation) {
		case 2: // 左右反転
			at.scale(-1, 1);
			at.translate(-width, 0);
			break;
		case 3: // 180度回転
			at.rotate(Math.PI, width / 2.0, height / 2.0);
			break;
		case 4: // 上下反転
			at.scale(1, -1);
			at.translate(0, -height);
			break;
		case 5: // 左右反転して時計回り90度
			at.rotate(Math.PI / 2);
			at.scale(-1, 1);
			at.translate(0, -height);
			break;
		case 6: // 時計回り90度
			at.rotate(Math.PI / 2);
			at.translate(0, -height);
			break;
		case 7: // 左右反転して時計回り270度
			at.rotate(-Math.PI / 2);
			at.scale(-1, 1);
			at.translate(-width, 0);
			break;
		case 8: // 時計回り270度
			at.rotate(-Math.PI / 2);
			at.translate(-width, 0);
			break;
		default: // 通常
			return image;
		}
		return new TransformedImage(image, at);
	}
}
