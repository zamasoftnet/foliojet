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
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.g2d.util.G2DUtils;
import net.zamasoft.pdfg2d.g2d.util.ImageTooLargeException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;

public class RasterImageLoader implements ImageLoader {
	/** {@code -Dfoliojet.debug.imageTrace}: image loading diagnostics (determined at startup). */
	private static final boolean IMAGE_TRACE = System.getProperty("foliojet.debug.imageTrace") != null;

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
			imageIn = openFileImageInputStream(source, false);
		} else {
			imageIn = ImageIO.createImageInputStream(source.getInputStream());
		}
		try { // Load a raster image through ImageIO.
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
	 * Reads only intrinsic dimensions and EXIF orientation without decoding pixels, for passes that do not draw.
	 */
	public Image loadImageForLayout(final Source source) throws IOException {
		return this.loadImageForLayout(source, -1L);
	}

	/**
	 * Reads only intrinsic dimensions and EXIF orientation without decoding pixels, for passes that do not draw.
	 * Reject images above the pixel limit ({@code input.image-pixel-limit}) with
	 * {@link ImageTooLargeException}, as in the output pass, so measurement and output agree
	 * on whether an image is present (2026-10-03).
	 *
	 * @param pixelLimit maximum pixel count; a negative value means unlimited
	 */
	public Image loadImageForLayout(final Source source, final long pixelLimit) throws IOException {
		final ImageInputStream imageIn;
		if (source.isFile()) {
			imageIn = openFileImageInputStream(source, true);
		} else {
			imageIn = new FileCacheImageInputStream(source.getInputStream(), null) {
				public void flushBefore(long pos) {
					// Keep the stream rewindable for EXIF scanning.
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
				G2DUtils.checkPixelLimit(reader, pixelLimit);
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
				// Treat missing orientation information as normal orientation.
			}

			final Image metrics = new Image() {
				public double getWidth() {
					return width;
				}

				public double getHeight() {
					return height;
				}

				public void drawTo(net.zamasoft.pdfg2d.gc.GC gc) {
					// Dimensions-only image for passes that do not draw.
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
			imageIn = openFileImageInputStream(source, true);
		} else {
			imageIn = new FileCacheImageInputStream(source.getInputStream(), null) {
				public void flushBefore(long pos) throws IOException {
					// Ignore flush to keep the image rereadable.
				}
			};
		}
		// ua may be null (tests and paths that only inspect dimensions).
		final long pixelLimit = ua == null ? -1L : UAProps.INPUT_IMAGE_PIXEL_LIMIT.getLong(ua);
		try { // Load a raster image through ImageIO.
			JPEGImageReader cir = null;
			ImageReader jdkJpeg = null;
			Iterator<ImageReader> iri = ImageIO.getImageReaders(imageIn);
			if (IMAGE_TRACE) {
				System.err.println("[img] source=" + source.getURI() + " isFile=" + source.isFile());
			}
			ImageReader ir = null;
			while (iri != null && iri.hasNext()) {
				ir = iri.next();
				ir.setInput(imageIn);
				// Check the pixel limit against header dimensions before inspecting ICC or image type
				// (2026-10-03). Once the image is known to be too large, do not try other readers.
				// If a reader cannot read dimensions, try the next as before, then check again
				// below with the selected reader.
				try {
					G2DUtils.checkPixelLimit(ir, pixelLimit);
				} catch (final ImageTooLargeException e) {
					if (e.getWidth() >= 0) {
						ir.dispose();
						if (cir != null) {
							cir.dispose();
						}
						if (jdkJpeg != null) {
							jdkJpeg.dispose();
						}
						throw e;
					}
				}
				imageIn.seek(0);
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
							// Defer the JDK's standard JPEG reader because it cannot read CMYK (4 components).
							// ImageIO registry order varies by environment
							// (the JDK reader came first in the daemon in actual measurements), so ensure
							// TwelveMonkeys takes priority regardless of registration order (2026-08-10).
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
			// Check with the selected reader. If it cannot read dimensions and a limit is set, reject the image.
			try {
				G2DUtils.checkPixelLimit(ir, pixelLimit);
			} catch (final ImageTooLargeException e) {
				ir.dispose();
				throw e;
			}
			imageIn.seek(0);
			String formatName = null;
			try {
				formatName = ir.getFormatName();
			} catch (IOException e) {
				// If the format name is unavailable, use the decoded image as before.
			}
			if (IMAGE_TRACE) {
				System.err.println("[img] chosen=" + ir.getClass().getName());
			}
			
			int orientation = 1;
			try {
				Metadata metadata = ImageMetadataReader.readMetadata(new ImageInputStreamProxy(imageIn));
				Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
				if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
					// Read image orientation if EXIF and orientation information are present.
					orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
				}
			} catch (ImageProcessingException e) {
				// ignore
			} catch (MetadataException e) {
				// ignore
			}

			final java.awt.image.BufferedImage decoded;
			try {
				decoded = G2DUtils.loadImage(ir, imageIn, pixelLimit);
			} catch (final RuntimeException | IOException e) {
				if (IMAGE_TRACE) {
					System.err.println("[img] load failed: " + e);
					e.printStackTrace();
				}
				throw e;
			}
			if (IMAGE_TRACE) {
				System.err.println("[img] decoded " + decoded.getWidth() + "x" + decoded.getHeight() + " type="
						+ decoded.getType());
			}
				// **Do not re-encode if the original bytes can be emitted unchanged**
			// (2026-08-28). Limit this to formats browsers can read. Check UA capabilities:
			// comparing output.type strings never matched, because it returned
			// application/pdf when images were loaded (observed).
			// ua may be null (tests and paths that only inspect dimensions).
			final boolean keepEncoded = ua != null && ua.keepsEncodedImages();
			String[] passThrough = keepEncoded && orientation == 1 ? browserFormat(formatName) : null;
			byte[] encoded = null;
			if (passThrough != null) {
				encoded = readAll(imageIn);
				if ("jpg".equals(passThrough[1]) && jpegComponents(encoded) > 3) {
					// CMYK JPEG: use pixels because major browsers cannot render it correctly.
					passThrough = null;
					encoded = null;
				}
			}
			if (IMAGE_TRACE) {
				System.err.println("[img] format=" + formatName + " orientation=" + orientation + " keepEncoded="
						+ keepEncoded + " passThrough="
						+ (passThrough == null ? "no" : passThrough[0]));
			}
			final Image image;
			if (passThrough != null) {
				image = new EncodedRasterImage(decoded, encoded, passThrough[0], passThrough[1]);
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

	private static ImageInputStream openFileImageInputStream(final Source source, final boolean preserveHead)
			throws IOException {
		try {
			return new FileImageInputStream(source.getFile()) {
				@Override
				public int read() throws IOException {
					try {
						return super.read();
					} catch (final IOException e) {
						ImageLoadDiagnostics.recordFetchFailure(source, e);
						throw e;
					}
				}

				@Override
				public int read(final byte[] bytes, final int off, final int len) throws IOException {
					try {
						return super.read(bytes, off, len);
					} catch (final IOException e) {
						ImageLoadDiagnostics.recordFetchFailure(source, e);
						throw e;
					}
				}

				@Override
				public void flushBefore(final long pos) throws IOException {
					if (!preserveHead) {
						super.flushBefore(pos);
					}
				}
			};
		} catch (final IOException e) {
			ImageLoadDiagnostics.recordFetchFailure(source, e);
			throw e;
		}
	}

	/** Returns {@code {MIME type, extension}} for formats browsers can display directly. */
	private static String[] browserFormat(final String formatName) {
		if (formatName == null) {
			return null;
		}
		return switch (formatName.toLowerCase(java.util.Locale.ROOT)) {
		case "jpeg", "jpg" -> new String[] { "image/jpeg", "jpg" };
		case "png" -> new String[] { "image/png", "png" };
		case "gif" -> new String[] { "image/gif", "gif" };
		case "webp" -> new String[] { "image/webp", "webp" };
		default -> null;
		};
	}

	/**
	 * Reads the number of JPEG components from the SOF marker (1 = gray, 3 = YCbCr, 4 = CMYK).
	 *
	 * <p>
	 * ImageIO's {@code getRawImageType} is another option, but some readers throw exceptions
	 * or return {@code null}, making <b>detection reader-dependent</b>
	 * (observed: TwelveMonkeys' JPEG reader failed to detect even normal YCbCr,
	 * disabling all passthrough). Reading the bytes directly avoids reader dependencies.
	 * </p>
	 */
	private static int jpegComponents(final byte[] jpeg) {
		for (int i = 2; i + 9 < jpeg.length;) {
			if ((jpeg[i] & 0xFF) != 0xFF) {
				++i;
				continue;
			}
			final int marker = jpeg[i + 1] & 0xFF;
			if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
				i += 2;
				continue;
			}
			final int length = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
			// SOF0–SOF15 (except DHT=0xC4, JPG=0xC8, and DAC=0xCC) contain the component count.
			if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
				return jpeg[i + 9] & 0xFF;
			}
			if (marker == 0xDA) {
				break; // Image payload. If no count was found by this point, detection is impossible.
			}
			i += 2 + Math.max(length, 2);
		}
		return 3; // Treat undetectable images as normal color images.
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
		case 2: // Flip horizontally.
			at.scale(-1, 1);
			at.translate(-width, 0);
			break;
		case 3: // Rotate 180 degrees.
			at.rotate(Math.PI, width / 2.0, height / 2.0);
			break;
		case 4: // Flip vertically.
			at.scale(1, -1);
			at.translate(0, -height);
			break;
		case 5: // Flip horizontally, then rotate 90 degrees clockwise.
			at.rotate(Math.PI / 2);
			at.scale(-1, 1);
			at.translate(0, -height);
			break;
		case 6: // Rotate 90 degrees clockwise.
			at.rotate(Math.PI / 2);
			at.translate(0, -height);
			break;
		case 7: // Flip horizontally, then rotate 270 degrees clockwise.
			at.rotate(-Math.PI / 2);
			at.scale(-1, 1);
			at.translate(-width, 0);
			break;
		case 8: // Rotate 270 degrees clockwise.
			at.rotate(-Math.PI / 2);
			at.translate(-width, 0);
			break;
		default: // Normal
			return image;
		}
		return new OrientedImage(image, at);
	}

	/**
	 * Number of bytes to read ahead for EXIF orientation.
	 *
	 * <p>
	 * JPEG EXIF is in an APP1 segment (up to 64 KB), always near the start.
	 * Reading up to 256 KB provides enough margin to avoid missing it in practice.
	 */
	private static final int ORIENTATION_HEADER = 256 * 1024;

	/**
	 * Reads EXIF orientation from the leading bytes. Returns 1 (normal) if unavailable.
	 *
	 * <p>
	 * Assumes the byte sequence may be truncated and does not throw on parsing failure:
	 * failure to read orientation does not mean the image is unreadable.
	 */
	public static int readOrientation(final byte[] header) {
		try {
			final Metadata metadata = ImageMetadataReader
					.readMetadata(new java.io.ByteArrayInputStream(header));
			final Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
			if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
				return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
			}
		} catch (final Exception e) {
			// Treat unreadable orientation as normal.
		}
		return 1;
	}

	/**
	 * Peeks at the start of a resource to read EXIF orientation, and returns it with
	 * <b>a resource that returns the same stream rewound to its start</b> (2026-08-30).
	 *
	 * <p>
	 * {@code Source#getInputStream} promises to restart reading on each call, but for HTTP resources
	 * that means <b>fetching again</b>. Here, peek into a single stream using
	 * {@code mark}/{@code reset} to avoid fetching twice.
	 *
	 * @return {@code {orientation, resource}}; orientation 1 and the original resource if peeking fails
	 */
	public static Object[] peekOrientation(final Source source) {
		try {
			final java.io.BufferedInputStream in = new java.io.BufferedInputStream(source.getInputStream(),
					ORIENTATION_HEADER);
			in.mark(ORIENTATION_HEADER);
			final byte[] header = in.readNBytes(ORIENTATION_HEADER);
			in.reset();
			final int orientation = readOrientation(header);
			if (IMAGE_TRACE) {
				System.err.println("[img] peek orientation=" + orientation + " header=" + header.length
						+ " source=" + source.getURI());
			}
			// **Return the resource holding the peeked stream even without orientation** (2026-09-02).
			// Previously, orientation 1 returned the original resource, but a main-document resource
			// streamed over CTIP is a StreamSource, whose getInputStream() contract
			// resets to an 8 KiB mark on subsequent calls. After reading 256 KiB here,
			// rereading the original resource failed with "Resetting to invalid mark", so **images
			// larger than 8 KiB could not be converted as main documents** (cti.li report,
			// 2026-09-01: 8,022 B succeeded, 9,108 B failed). The peeked bytes remain in the local
			// buffer; rewind and pass it on to avoid rereading.
			return new Object[] { Integer.valueOf(orientation),
					new net.zamasoft.zstream.resolver.util.SourceWrapper(source) {
						private boolean taken = false;

						@Override
						public java.io.InputStream getInputStream() throws IOException {
							if (this.taken) {
								return super.getInputStream();
							}
							this.taken = true;
							return in;
						}
					} };
		} catch (final IOException e) {
			return new Object[] { Integer.valueOf(1), source };
		}
	}

	/**
	 * Applies EXIF orientation to a loaded image. For orientation 1 (normal),
	 * returns the same instance unchanged.
	 */
	public static Image orient(final Image image, final int orientation) {
		return orientation == 1 ? image : applyOrientation(image, orientation);
	}

	/**
	 * An image with EXIF orientation applied (2026-08-30).
	 *
	 * <p>
	 * A plain {@link TransformedImage} is indistinguishable from the same type of wrapper
	 * used for px-to-pt conversion. Mark it with a dedicated type so
	 * {@code image-orientation: none} can <b>remove only the orientation</b>.
	 */
	public static final class OrientedImage extends TransformedImage {
		OrientedImage(final Image image, final AffineTransform at) {
			super(image, at);
		}
	}

	/**
	 * Returns an image with <b>only EXIF orientation</b> removed from its wrapper chain
	 * ({@code image-orientation: none}). If no orientation is applied, returns the same instance
	 * unchanged, so callers can use this unconditionally.
	 *
	 * <p>
	 * The orientation wrapper sits inside the px-to-pt conversion wrapper, so rebuild and reapply
	 * the outer wrappers. This also restores intrinsic dimensions (a 90-degree rotation swaps width and height).
	 */
	public static Image withoutOrientation(final Image image) {
		if (image instanceof OrientedImage oriented) {
			return oriented.getImage();
		}
		if (image instanceof TransformedImage transformed) {
			final Image inner = withoutOrientation(transformed.getImage());
			return inner == transformed.getImage() ? transformed
					: new TransformedImage(inner, transformed.getTransform());
		}
		return image;
	}
}
