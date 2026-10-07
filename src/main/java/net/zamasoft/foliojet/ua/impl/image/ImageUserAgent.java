package net.zamasoft.foliojet.ua.impl.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.SequentialOutput;
import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.zstream.io.util.SequentialOutputAdapter;
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.foliojet.ua.PrepareMode;

public class ImageUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	/**
	 * The default font policy for raster image output is <b>embedding</b> (2026-09-03,
	 * user decision; the same as single SVG and page-split SVG).
	 *
	 * <p>
	 * The shared default, {@code cid-keyed}, assumes PDF's externally referenced CID-keyed fonts,
	 * a mechanism that images do not have. CID-keyed fonts without glyph data fell back to
	 * AWT system fonts ({@code SystemCIDFont}) and used hinted outlines from a different face
	 * (observed in single SVG: "日" was 6% wider than the actual glyph, with thicker vertical strokes).
	 * The embedding policy uses pdfg2d's own outlines, producing the same glyphs as PDF.
	 * Honor {@code output.pdf.fonts.policy} if the user sets it explicitly.
	 * </p>
	 */
	@Override
	protected boolean embedsFontsByDefault() {
		return true;
	}

	private Results results, xresults;
	private boolean middleStateSaved = false;

	protected BufferedImage image;

	protected int page = 0;

	// ---- Page numbers for one-pass target-counter() (2026-10-04, docs/design/one-pass-target-counter-design.md §8)

	/**
	 * Recorder for the current page's drawing operations. For documents with slots
	 * ({@code TargetCounterSlotImage}), record pages first, then draw and emit images once slot values are available.
	 */
	private RecorderGC recorder;

	/** A recorded page and its dimensions (pt). */
	private record HeldPage(RecorderGC.Page recording, List<TargetCounterSlotImage> slots, double width,
			double height) {
		boolean resolved() {
			for (final var slot : this.slots) {
				if (!slot.isResolved()) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Pages not yet emitted. Result numbers ({@code #1}, {@code #2}, ...) follow emission order,
	 * so pages after an unresolved page must wait even if resolved, and are emitted in page order.
	 * (Page-split SVG uses page numbers in file names and can be emitted out of order,
	 * but image consumers treat the sequence as page numbers.)
	 */
	private final ArrayDeque<HeldPage> heldPages = new ArrayDeque<>();

	/**
	 * Maximum number of pending pages (the same as page-split SVG). Recordings live in memory,
	 * so a reference to a distant page must not cause unbounded accumulation. Above the limit, emit
	 * the oldest pages with the numbers known so far (unresolved slots remain empty).
	 */
	private static final int MAX_HELD_PAGES = 64;

	/**
	 * Delay output of pages with unresolved slots until their values are available (2026-10-04, §8).
	 */
	@Override
	public boolean paintsPageNumbersLater() {
		return true;
	}

	public void setResults(Results results) {
		this.results = results;
	}

	public void prepare(PrepareMode mode) {
		super.prepare(mode);
		switch (mode) {
		case MIDDLE_PASS:
			if (!this.middleStateSaved) {
				this.xresults = this.results;
				this.middleStateSaved = true;
			}
			this.results = NopResults.SHARED_INSTANCE;
			this.reset();
			break;
		case LAST_PASS:
			if (this.middleStateSaved) {
				this.results = this.xresults;
				this.xresults = null;
				this.middleStateSaved = false;
			}
			this.reset();
			break;
		}
	}

	private void reset() {
		this.image = null;
		this.closeOwnedFontManager();
		this.page = 0;
		this.recorder = null;
		this.heldPages.clear();
	}

	public FontManager getFontManager() {
		// Use core fonts without glyph outlines only as a last resort; Java2D substitution distorts them (2026-10-04).
		return this.ownedFontManager(true);
	}

	public void meta(String name, String content) {
		// ignore
	}

	public GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		if (this.isMeasurePass() || this.isStructureScanPass()) {
			this.noteProgress();
			return null;
		}
		if (!this.heldPages.isEmpty()
				|| this.getUAContext().hasTargetCounterSlots() && TargetCounterSlotImage.available(this)) {
			// Documents with slots: record the page first. Check the pixel limit before recording. The recorder's
			// supports() returns the same answer as Java2D (everything is supported). Otherwise, recording takes
			// an approximate drawing path, which replay cannot undo.
			this.pixelSize(this.pageWidth, this.pageHeight);
			this.recorder = new RecorderGC(this.getFontManager(), capability -> capability != null);
			return this.recorder;
		}
		return this.openImage(this.pageWidth, this.pageHeight);
	}

	/** The page's pixel count. Abort conversion if it exceeds the type area's pixel limit. */
	private int[] pixelSize(final double pageWidth, final double pageHeight) {
		final Point2D size = new Point2D.Double(pageWidth, pageHeight);
		final double ppi = UAProps.OUTPUT_IMAGE_RESOLUTION.getDouble(this);
		final double pxPerPt = ppi / 72;
		final AffineTransform at = AffineTransform.getScaleInstance(pxPerPt, pxPerPt);
		at.transform(size, size);
		// Round to nearest (2026-10-04). Truncation made 50 mm × 350 dpi = 688.98 into 688 pixels, misleading the printer.
		// At least 1 pixel (until 2026-10-05, tiny pages became 0, preventing image creation and failing the conversion).
		final long w = Math.max(1, Math.round(size.getX()));
		final long h = Math.max(1, Math.round(size.getY()));
		// Pixel limit for the type area (2026-10-03). Page size × resolution can be arbitrarily
		// large, so reject before allocating. Even without a limit, reject pixel counts beyond Java images' int range.
		final long outputPixelLimit = UAProps.OUTPUT_IMAGE_PIXEL_LIMIT.getLong(this);
		final long pixelLimit = outputPixelLimit >= 0 ? Math.min(outputPixelLimit, Integer.MAX_VALUE)
				: Integer.MAX_VALUE;
		if (w > pixelLimit / h) {
			this.message(MessageCodes.ERROR_OUTPUT_IMAGE_TOO_LARGE, String.valueOf(w), String.valueOf(h),
					String.valueOf(pixelLimit));
			throw new AbortException(CTISession.ABORT_FORCE);
		}
		return new int[] { (int) w, (int) h };
	}

	/** Creates the page image and returns its drawing GC. */
	private G2DGC openImage(final double pageWidth, final double pageHeight) {
		final int[] size = this.pixelSize(pageWidth, pageHeight);
		final int w = size[0];
		final int h = size[1];
		final double pxPerPt = UAProps.OUTPUT_IMAGE_RESOLUTION.getDouble(this) / 72;
		final AffineTransform at = AffineTransform.getScaleInstance(pxPerPt, pxPerPt);
		final boolean transparent = this.transparentBackground();
		this.image = new BufferedImage(w, h,
				transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		final Graphics2D g2d = (Graphics2D) this.image.getGraphics();

		if (!transparent) {
			// Clear the background. For transparency, **do not paint**, so untouched areas
			// retain alpha 0.
			g2d.setColor(Color.WHITE);
			g2d.fillRect(0, 0, w, h);
		}
		g2d.setColor(Color.BLACK);
		g2d.setTransform(at);

		// Antialiasing for objects and text
		if (UAProps.OUTPUT_IMAGE_ANTIALIAS.getBoolean(this)) {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		} else {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		}
		return new G2DGC(g2d, this.getFontManager());
	}

	/** Whether transparency has been decided. Check once per document and issue at most one warning. */
	private Boolean transparent = null;

	/**
	 * Whether to make the background transparent.
	 *
	 * <p>
	 * Even if requested, <b>keep it white if the output format cannot store alpha</b>,
	 * and report {@code 2824}. Query {@link ImageWriter} rather than using a fixed format table,
	 * because available writers vary by runtime environment
	 * (adding Java Image I/O writers adds formats).
	 * </p>
	 */
	private boolean transparentBackground() {
		if (this.transparent != null) {
			return this.transparent.booleanValue();
		}
		boolean value = false;
		if (UAProps.OUTPUT_IMAGE_TRANSPARENT.getBoolean(this)) {
			final String mimeType = UAProps.OUTPUT_TYPE.getString(this);
			if (canStoreAlpha(mimeType)) {
				value = true;
			} else {
				this.message(MessageCodes.WARN_NO_ALPHA_IN_IMAGE_FORMAT, mimeType);
			}
		}
		this.transparent = Boolean.valueOf(value);
		return value;
	}

	/** Asks the writer whether the format can preserve alpha. */
	private static boolean canStoreAlpha(final String mimeType) {
		final Iterator<ImageWriter> i = ImageIO.getImageWritersByMIMEType(mimeType);
		if (!i.hasNext()) {
			return false;
		}
		final ImageWriter writer = i.next();
		try {
			return writer.getOriginatingProvider().canEncodeImage(
					ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB));
		} catch (final RuntimeException e) {
			return false;
		} finally {
			writer.dispose();
		}
	}

	public void closePage(GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
		}
		if (this.recorder != null) {
			final RecorderGC.Page recording = this.recorder.getPage();
			this.recorder = null;
			this.heldPages.add(
					new HeldPage(recording, TargetCounterSlotImage.slots(recording), this.pageWidth, this.pageHeight));
			if (this.heldPages.size() > MAX_HELD_PAGES) {
				final var context = this.getUAContext();
				if (context.getReportedApproximations().add("target-counter() 2822.target-counter-held")) {
					this.message(MessageCodes.WARN_APPROXIMATED_RENDERING, "target-counter()",
							UAProps.OUTPUT_TYPE.getString(this),
							net.zamasoft.foliojet.message.MessageCodeUtils.detail("2822.target-counter-held"));
				}
				this.writeHeldPage(true);
			}
			while (!this.heldPages.isEmpty() && this.heldPages.peekFirst().resolved()) {
				this.writeHeldPage(false);
			}
		} else {
			this.writeImage();
		}
		this.checkAbort(CTISession.ABORT_NORMAL);
	}

	/**
	 * Draws the first pending page into an image and emits it. With {@code force}, silently draw unresolved
	 * slots as empty (when the limit is exceeded or the document ends). Abort conversion if the consumer
	 * no longer accepts results.
	 */
	private void writeHeldPage(final boolean force) throws IOException {
		final HeldPage held = this.heldPages.removeFirst();
		final var context = this.getUAContext();
		context.setDrawingHeldPages(force);
		try {
			held.recording().drawTo(this.openImage(held.width(), held.height()));
		} finally {
			context.setDrawingHeldPages(false);
		}
		this.writeImage();
	}

	/** Writes the rendered page image to the next result. Abort conversion if the consumer no longer accepts results. */
	private void writeImage() throws IOException {
		String mimeType = UAProps.OUTPUT_TYPE.getString(this);
		SourceMetadata metaSource = new SimpleSourceMetadata(URI.create("#" + (++this.page)), mimeType, null, -1);
		FragmentedOutput builder = this.results.nextBuilder(metaSource);
		try {
			OutputStream out;
			if (builder instanceof SequentialOutput) {
				out = new SequentialOutputAdapter((SequentialOutput) builder);
			} else {
				builder.addFragment();
				out = new FragmentOutputAdapter(builder, 0);
			}
			try (FileCacheImageOutputStream iout = new FileCacheImageOutputStream(out, null)) {
				Iterator<ImageWriter> i = ImageIO.getImageWritersByMIMEType(mimeType);
				ImageWriter writer = (ImageWriter) i.next();
				try {
					writer.setOutput(iout);
					writer.write(null, new javax.imageio.IIOImage(this.image, null, resolutionMetadata(writer,
							this.image, UAProps.OUTPUT_IMAGE_RESOLUTION.getDouble(this))), null);
				} finally {
					writer.dispose();
				}
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			builder.close();
			this.image = null;
		}
		if (!this.results.hasNext()) {
			throw new AbortException(CTISession.ABORT_NORMAL);
		}
	}

	/**
	 * Image metadata with resolution (dpi) (2026-10-04). Uses pHYs for PNG, JFIF (unit 1 = dpi) for JPEG,
	 * and pixel size in the standard format for others. Returns null if unsupported (output works as before).
	 *
	 * <p>
	 * Without resolution, consumers (such as print submission services) cannot derive physical dimensions
	 * from pixel counts. Seihon Chokuso's cover creation service accepts images at 300–350 dpi, so the reader
	 * and publishing tools had to add it.
	 * </p>
	 */
	private static javax.imageio.metadata.IIOMetadata resolutionMetadata(final ImageWriter writer,
			final BufferedImage image, final double dpi) {
		try {
			final javax.imageio.metadata.IIOMetadata metadata = writer.getDefaultImageMetadata(
					ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
			if (metadata == null || metadata.isReadOnly() || !(dpi > 0)) {
				return null;
			}
			final String nativeFormat = metadata.getNativeMetadataFormatName();
			if ("javax_imageio_png_1.0".equals(nativeFormat)) {
				final javax.imageio.metadata.IIOMetadataNode root = new javax.imageio.metadata.IIOMetadataNode(
						nativeFormat);
				final javax.imageio.metadata.IIOMetadataNode phys = new javax.imageio.metadata.IIOMetadataNode("pHYs");
				final String perMeter = Long.toString(Math.round(dpi / 0.0254));
				phys.setAttribute("pixelsPerUnitXAxis", perMeter);
				phys.setAttribute("pixelsPerUnitYAxis", perMeter);
				phys.setAttribute("unitSpecifier", "meter");
				root.appendChild(phys);
				metadata.mergeTree(nativeFormat, root);
				return metadata;
			}
			if ("javax_imageio_jpeg_image_1.0".equals(nativeFormat)) {
				final org.w3c.dom.Node tree = metadata.getAsTree(nativeFormat);
				final org.w3c.dom.NodeList jfif = ((org.w3c.dom.Element) tree).getElementsByTagName("app0JFIF");
				if (jfif.getLength() == 0) {
					return null;
				}
				final org.w3c.dom.Element app0 = (org.w3c.dom.Element) jfif.item(0);
				final String density = Long.toString(Math.min(65535, Math.round(dpi)));
				app0.setAttribute("resUnits", "1");
				app0.setAttribute("Xdensity", density);
				app0.setAttribute("Ydensity", density);
				metadata.setFromTree(nativeFormat, tree);
				return metadata;
			}
			if (metadata.isStandardMetadataFormatSupported()) {
				final javax.imageio.metadata.IIOMetadataNode root = new javax.imageio.metadata.IIOMetadataNode(
						javax.imageio.metadata.IIOMetadataFormatImpl.standardMetadataFormatName);
				final javax.imageio.metadata.IIOMetadataNode dimension = new javax.imageio.metadata.IIOMetadataNode(
						"Dimension");
				final String mmPerPixel = Double.toString(25.4 / dpi);
				for (final String name : new String[] { "HorizontalPixelSize", "VerticalPixelSize" }) {
					final javax.imageio.metadata.IIOMetadataNode node = new javax.imageio.metadata.IIOMetadataNode(name);
					node.setAttribute("value", mmPerPixel);
					dimension.appendChild(node);
				}
				root.appendChild(dimension);
				metadata.mergeTree(javax.imageio.metadata.IIOMetadataFormatImpl.standardMetadataFormatName, root);
				return metadata;
			}
		} catch (final javax.imageio.metadata.IIOInvalidTreeException | RuntimeException e) {
			// Write formats that do not support it without resolution.
		}
		return null;
	}

	public void finish() throws BrokenResultException, IOException {
		super.finish();
		// Emit pending pages; slots without targets stay empty (stop if the consumer refuses further results).
		try {
			while (!this.heldPages.isEmpty() && this.results.hasNext()) {
				this.writeHeldPage(true);
			}
		} catch (final AbortException e) {
			// The consumer no longer accepts results.
		}
		this.heldPages.clear();
		this.results.end();
	}

	public Visitor getVisitor(GC gc) {
		return new NopVisitor(this);
	}
}
