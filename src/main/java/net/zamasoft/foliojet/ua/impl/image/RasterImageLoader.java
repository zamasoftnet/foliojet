package net.zamasoft.foliojet.ua.impl.image;

import java.awt.geom.AffineTransform;
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
			Iterator<ImageReader> iri = ImageIO.getImageReaders(imageIn);
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
				}
				else {
					throw new IOException("ImageIOがサポートしない画像形式です");
				}
			}
			else {
				if (cir != null) {
					cir.dispose();
				}
			}
			imageIn.seek(0);
			
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

			final Image image = new RasterImageImpl(G2DUtils.loadImage(ir, imageIn));
			if (orientation == 1) {
				return image;
			}
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
		} finally {
			imageIn.close();
		}
	}
}
