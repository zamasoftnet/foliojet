package net.zamasoft.foliojet.ua.impl;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import jp.cssj.cti2.helpers.CTIMessageCodes;
import jp.cssj.cti2.message.MessageHandler;
import net.zamasoft.pdfg2d.g2d.util.ImageTooLargeException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.ImageLoader;
import net.zamasoft.foliojet.ua.ImageMetricsIO;
import net.zamasoft.foliojet.ua.impl.image.RasterImageLoader;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.UAContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.plugin.PluginRegistry;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.PrepareMode;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;

/**
 * @author MIYABE Tatsuhiko
 */
public abstract class AbstractUserAgent implements UserAgent {
	private UAContext context = new UAContext();

	private PassContext passContext = new PassContext();

	private DocumentContext documentContext = new DocumentContext();

	private Map<String, String> props = null;

	/**
	 * Abort request (0 = none).
	 *
	 * <p>
	 * <b>volatile is required.</b> {@link net.zamasoft.foliojet.driver.DirectSession} runs layout
	 * on a dedicated thread ({@code foliojet-layout}), so the thread calling {@link #abort(byte)}
	 * differs from the thread reading in {@link #checkAbort(byte)}.
	 * Without volatile, writes may not be visible, making aborting <b>intermittently succeed or fail</b>
	 * (2026-07-27).
	 * </p>
	 */
	private volatile byte aborted = 0;

	private Locale locale;

	private String[] mediaTypes = null;

	private double normalLineHeight;

	private LengthValue defaultMarkerOffset;

	private AbsoluteLengthValue[] borderTable;

	private ColorValue defaultColor;

	private ColorValue matColor;

	private FontFamilyValue defaultFontFamily = null;

	private AbsoluteLengthValue mediumFontSize;

	private double fontScaleRatio;

	private LengthValue minSize;

	private Value maxSize = KeywordValue.NONE;

	private double pixelsPerInch = -1, fontMagnification = -1;

	private static final AffineTransform IDENTITY_AT = new AffineTransform();

	private AffineTransform pixelToUnit = null;

	private MessageHandler messageHandler = null;

	private SourceResolver resolver;

	private FontManager fontManager;

	private CSSJFontPolicyValue fontPolicy = null;

	private BoundSide boundSide = BoundSide.SINGLE;

	/**
	 * Root writing direction determining page progression (2026-09-02).
	 * {@code PageSequence} sets this from the root's {@code writing-mode}.
	 * Readers need "which way pages progress," not the binding side;
	 * even with {@code single} binding, vertical writing is read from the right (cti.li request).
	 */
	private net.zamasoft.foliojet.layout.box.params.WritingMode pageProgression = net.zamasoft.foliojet.layout.box.params.WritingMode.TB;

	protected double pageWidth, pageHeight;

	public AbstractUserAgent() {
		this.setDefaultLocale(Locale.getDefault());
		this.setNormalLineHeight(1.2);
		this.setDefaultMarkerOffset(RelativeLengthValue.ex(1));

		this.setMinSize(AbsoluteLengthValue.ZERO);
		// 14400 is the PDF size limit
		this.setMaxSize(AbsoluteLengthValue.create(this, 14400, Unit.PT));
		this.setBorderTable(new AbsoluteLengthValue[] { AbsoluteLengthValue.create(this, 1),
				AbsoluteLengthValue.create(this, 2), AbsoluteLengthValue.create(this, 3) });
		this.setFontScaleRatio(1.2);
		this.setMediumFontSize(AbsoluteLengthValue.create(this, 12));

		this.setDefaultColor(ColorValueUtils.BLACK);
		this.setMatColor(ColorValueUtils.WHITE);
	}

	public UAContext getUAContext() {
		return this.context;
	}

	public PassContext getPassContext() {
		return this.passContext;
	}

	private net.zamasoft.foliojet.layout.RetainedTextLimit retainedTextLimit;

	public net.zamasoft.foliojet.layout.RetainedTextLimit getRetainedTextLimit() {
		if (this.retainedTextLimit == null) {
			this.retainedTextLimit = new net.zamasoft.foliojet.layout.RetainedTextLimit(this);
		}
		return this.retainedTextLimit;
	}

	public DocumentContext getDocumentContext() {
		return this.documentContext;
	}

	@Override
	public void beginDocument(final java.net.URI documentURI) {
		this.documentContext = new DocumentContext();
		this.documentContext.setBaseURI(documentURI);
		this.documentContext.setDocumentURI(documentURI);
		// The footnote area comes from the document's own @footnote rule (parsed again with its style sheets)
		this.getUAContext().setFootnoteArea(null);
	}

	public final String getProperty(String name) {
		final String value = this.props == null ? null : this.props.get(name);
		// Apply operator limits on every read, regardless of where the value was set
		// (client, profile, or processing instruction in the document; 2026-10-03)
		return this.operatorLimits.clamp(name, value);
	}

	/** Operator-defined limits that users cannot loosen (none by default). */
	private net.zamasoft.foliojet.driver.OperatorLimits operatorLimits = net.zamasoft.foliojet.driver.OperatorLimits.NONE;

	/** Sets operator limits. Also pass them to child UAs created from this UA. */
	public final void setOperatorLimits(final net.zamasoft.foliojet.driver.OperatorLimits limits) {
		this.operatorLimits = limits == null ? net.zamasoft.foliojet.driver.OperatorLimits.NONE : limits;
	}

	/** Operator limits. */
	public final net.zamasoft.foliojet.driver.OperatorLimits getOperatorLimits() {
		return this.operatorLimits;
	}

	/**
	 * Snapshot of current I/O properties (2026-09-02).
	 * Used to give child UAs laying out EPUB items the same settings as their parent.
	 */
	public final Map<String, String> getProperties() {
		return this.props == null ? new HashMap<>() : new HashMap<>(this.props);
	}

	public final void setProperty(String name, String value) {
		if (this.props == null) {
			if (value == null || value.length() == 0) {
				return;
			}
			this.props = new HashMap<>();
		}
		if (value == null || value.length() == 0) {
			this.props.remove(name);
		} else {
			this.props.put(name, value);
		}
	}

	public final void setProperties(Map<String, String> props) {
		this.props = null;
		for (Entry<String, String> e : props.entrySet()) {
			this.setProperty(e.getKey(), e.getValue());
		}

		// Metadata
		if (this.props != null) {
			for (int i = 0;; ++i) {
				String prefix = UAProps.OUTPUT_META + i + ".";
				String name = this.props.get(prefix + "name");
				if (name == null) {
					break;
				}
				String value = this.props.get(prefix + "value");
				this.meta(name, value);
			}
		}

	}

	/**
	 * <b>Deadline for aborting when progress stalls</b> (added 2026-07-27).
	 *
	 * <p>
	 * <b>Do not use an overall wall-clock deadline.</b> That would terminate a legitimate
	 * 10,000-page business form. Measure <b>"time spent without completing even one unit of work"</b>.
	 * This is independent of document size: long documents keep working and do not trigger it,
	 * while stalled ones always do.
	 * </p>
	 *
	 * <p>
	 * <b>This value must exceed "the longest single unit of work"</b>,
	 * not the entire document's processing time. Fine-grained placement of {@link #noteProgress()}
	 * removed dependence on table size.
	 * </p>
	 *
	 * <p>
	 * <b>Basis for the value (measurements, 2026-07-27)</b>. Maximum intervals between progress updates:
	 * </p>
	 *
	 * <table border="1">
	 * <tr><th>Document</th><th>Maximum interval</th></tr>
	 * <tr><td><b>8000x8000 PNG (176 MB) x 3</b></td><td><b>9.60 seconds</b></td></tr>
	 * <tr><td>SVG with 60,000 paths (5.4 MB) x 3</td><td>2.33 seconds</td></tr>
	 * <tr><td>Table with 200,000 rows</td><td>2.23 seconds</td></tr>
	 * </table>
	 *
	 * <p>
	 * The dominant cost is <b>decoding one huge image</b>. Tables now record progress per row,
	 * so even 400,000 rows stay in the two-second range (before this change it was 37.5 seconds).
	 * </p>
	 *
	 * <p>
	 * <b>Basis for 120 seconds</b>: about 12 times the measured worst unit, 9.6 seconds.
	 * Allows for "4× larger material (an image around 700 MB)" × "a server 3× slower or busier."
	 * <b>The cost of setting it too high is limited</b> (one stalled conversion holds a thread
	 * and memory for that duration), whereas <b>too low a value fails legitimate documents</b>.
	 * This asymmetry favors a generous margin.
	 * </p>
	 *
	 * <p>
	 * <b>The product default is unlimited (reversed by the owner's decision on 2026-08-01)</b>.
	 * At introduction (2026-07-27), it was enabled by default based on "only those who have suffered
	 * an incident use opt-in safety valves" ([[LESSONS]] §6.9b). However, this valve does not fall
	 * under that principle because it <b>can kill legitimate user jobs</b>.
	 * Following the asymmetry above (too low a value fails legitimate documents) to its conclusion,
	 * only unlimited guarantees no false positives. A class of "legitimate waits exceeding 120 seconds"
	 * actually existed: gaps in streaming input while progressively generating business forms from
	 * a slow DB cursor (waiting for input cannot count as progress).
	 * Detecting client-side delays/disconnections belongs to the network layer, which already has
	 * configurable timeouts (CTIP {@code jp.cssj.cssjd.timeout} = 180 seconds by default,
	 * REST sessions = 3 minutes by default).
	 * </p>
	 *
	 * <p>
	 * <b>Hang detection is mainly for test harnesses</b> (detecting livelocks as failures in sweeps/CI;
	 * observed with seed 213026 and others), so unit tests and server product daemon/CLI startup
	 * explicitly set {@code -Dfoliojet.noProgressSeconds=120}.
	 * Production can also enable it through this property if required by an SLA
	 * (0 or less = unlimited). The remaining risk is that "the engine truly hangs and the client
	 * waits indefinitely," blocking a worker thread until restart, but the known livelock classes
	 * were resolved by escape paths implemented on 2026-07-29.
	 * </p>
	 *
	 * <h3>Performance impact (measurements, 2026-07-27)</h3>
	 *
	 * <p>
	 * Added a volatile read + {@code System.nanoTime()} in {@link #checkAbort(byte)} and
	 * a volatile write in {@link #noteProgress()}. Measured call counts:
	 * </p>
	 *
	 * <table border="1">
	 * <tr><th>Document</th><th>checkAbort</th><th>noteProgress</th><th>Upper bound on added cost</th></tr>
	 * <tr><td>Table with 200,000 rows (65-second conversion)</td><td>37,500</td><td>404,167</td>
	 * <td>11.0 ms = <b>0.017%</b></td></tr>
	 * <tr><td>Text with 20,000 paragraphs</td><td>24,377</td><td>434</td><td>0.6 ms</td></tr>
	 * <tr><td>8,000 floats</td><td>9,649</td><td>236</td><td>0.2 ms</td></tr>
	 * </table>
	 *
	 * <p>
	 * End-to-end measurements put the difference from the baseline within measurement noise (±6%).
	 * <b>Negligible as long as granularity stays coarse</b>; moving to glyph/character granularity
	 * invalidates this assumption.
	 * </p>
	 */
	private static final long NO_PROGRESS_LIMIT_NANOS = Long.getLong("foliojet.noProgressSeconds", 0L)
			* 1_000_000_000L;

	/** Time the last page was emitted. {@link #checkAbort(byte)} uses it for the deadline. */
	private volatile long lastProgressNanos = System.nanoTime();

	/**
	 * Records emission of one page. The deadline is measured from this.
	 */
	public final void noteProgress() {
		this.lastProgressNanos = System.nanoTime();
	}

	public void abort(byte mode) {
		if (this.aborted != mode) {
			this.message(CTIMessageCodes.INFO_ABORT);
		}
		this.aborted = mode;
	}

	/**
	 * <b>Cooperative abort point.</b> Throws {@link AbortException} if an abort was requested.
	 * Call at the start of long-running loops.
	 *
	 * <p>
	 * Costs one volatile read. Place at <b>coarse granularity, such as lines, table rows, or pages</b>;
	 * never at glyph granularity.
	 * </p>
	 */
	public void checkAbort(byte mode) {
		if (this.aborted == mode || this.aborted == AbortException.ABORT_FORCE) {
			this.message(CTIMessageCodes.INFO_ABORT);
			throw new AbortException(this.aborted);
		}
		if (NO_PROGRESS_LIMIT_NANOS > 0 && System.nanoTime() - this.lastProgressNanos > NO_PROGRESS_LIMIT_NANOS) {
			// The configured interval elapsed without one unit of work completing. Treat as stalled
			// (0 or less = unlimited is the product default; test harnesses explicitly set 120 seconds)
			this.message(CTIMessageCodes.INFO_ABORT);
			this.aborted = AbortException.ABORT_FORCE;
			throw new AbortException(AbortException.ABORT_FORCE);
		}
	}

	public Locale getDefaultLocale() {
		return this.locale;
	}

	public boolean is(String mediaTypes) {
		if (mediaTypes == null || mediaTypes.length() == 0) {
			return true;
		}
		if (this.mediaTypes == null) {
			// Media type
			String media = UAProps.OUTPUT_MEDIA_TYPES.getString(this);
			this.mediaTypes = media.split("[\\s]+");
		}
		for (int i = 0; i < this.mediaTypes.length; ++i) {
			if (mediaTypes.indexOf(this.mediaTypes[i]) != -1) {
				return true;
			}
		}
		return false;
	}

	public double getNormalLineHeight() {
		return this.normalLineHeight;
	}

	public LengthValue getDefaultMarkerOffset() {
		return this.defaultMarkerOffset;
	}

	public AbsoluteLengthValue getBorderWidth(BorderWidthKeyword keyword) {
		return this.borderTable[keyword.ordinal()];
	}

	public ColorValue getDefaultColor() {
		return this.defaultColor;
	}

	public ColorValue getMatColor() {
		return this.matColor;
	}

	public FontFamilyValue getDefaultFontFamily() {
		if (this.defaultFontFamily == null) {
			String str = UAProps.OUTPUT_DEFAULT_FONT_FAMILY.getString(this);
			this.defaultFontFamily = FontValueUtils.toFontFamily(str);
		}
		return this.defaultFontFamily;
	}

	/**
	 * Returns whether this output defaults to font embedding (core embedded) when
	 * {@code output.pdf.fonts.policy} is unspecified. The shared cid-keyed default references external
	 * PDF CID-keyed fonts, which image/SVG output does not have (glyphs fall back to AWT substitutes
	 * or outlines). Image, SVG, and page-split SVG return true
	 * (until 2026-10-04, these three outputs had copies of the same {@code getDefaultFontPolicy} override).
	 */
	protected boolean embedsFontsByDefault() {
		return false;
	}

	public CSSJFontPolicyValue getDefaultFontPolicy() {
		if (this.embedsFontsByDefault() && this.getProperty(UAProps.OUTPUT_PDF_FONTS_POLICY.name) == null) {
			return CSSJFontPolicyValue.CORE_EMBEDDED_VALUE;
		}
		if (this.fontPolicy == null) {
			String s = UAProps.OUTPUT_PDF_FONTS_POLICY.getString(this);
			// PDF/A, PDF/X, and PDF/UA all require font embedding.
			if (UAProps.OUTPUT_PDF_VERSION.get(this).requiresFontEmbedding()) {
				this.fontPolicy = FontValueUtils.toFontPolicyA1(s);
				if (this.fontPolicy == null) {
					this.fontPolicy = CSSJFontPolicyValue.PDFA1_VALUE;
				}
			} else {
				this.fontPolicy = FontValueUtils.toFontPolicy(s);
				if (this.fontPolicy == null) {
					this.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PDF_FONTS_POLICY.name, s);
					this.fontPolicy = CSSJFontPolicyValue.CORE_CID_KEYED_VALUE;
				}
			}
		}
		return this.fontPolicy;
	}

	public final double getFontSize(AbsoluteFontSize absoluteFontSize) {
		return this.mediumFontSize.getLength() * absoluteFontSize.ratio() * this.getFontMagnification();
	}

	public double getFontMagnification() {
		if (this.fontMagnification == -1) {
			this.fontMagnification = UAProps.OUTPUT_TEXT_SIZE.getDouble(this);
		}
		return this.fontMagnification;
	}

	public double getLargerFontSize(double fontSize) {
		return fontSize * this.fontScaleRatio;
	}

	public double getSmallerFontSize(double fontSize) {
		return fontSize / this.fontScaleRatio;
	}

	public LengthValue getMinSize() {
		return this.minSize;
	}

	public Value getMaxSize() {
		return this.maxSize;
	}

	public double getPixelsPerInch() {
		if (this.pixelsPerInch == -1) {
			this.pixelsPerInch = UAProps.OUTPUT_RESOLUTION.getDouble(this);
		}
		return this.pixelsPerInch;
	}

	/**
	 * @param defaultMarkerOffset
	 *            The defaultMarkerOffset to set.
	 */
	public void setDefaultMarkerOffset(LengthValue defaultMarkerOffset) {
		this.defaultMarkerOffset = defaultMarkerOffset;
	}

	/**
	 * @param locale
	 *            The languageSupport to set.
	 */
	public void setDefaultLocale(Locale locale) {
		this.locale = locale;
	}

	/**
	 * @param normalLineHeight
	 *            The normalLineHeight to set.
	 */
	public void setNormalLineHeight(double normalLineHeight) {
		this.normalLineHeight = normalLineHeight;
	}

	/**
	 * @param borderTable
	 *            The borders to set. The array size is 3.
	 */
	public void setBorderTable(AbsoluteLengthValue[] borderTable) {
		if (borderTable.length != 3) {
			throw new IllegalArgumentException();
		}
		this.borderTable = borderTable;
	}

	/**
	 * @param defaultColor
	 *            The defaultColor to set.
	 */
	public void setDefaultColor(ColorValue defaultColor) {
		this.defaultColor = defaultColor;
	}

	public void setMatColor(ColorValue matColor) {
		this.matColor = matColor;
	}

	/**
	 * @param fontScaleRatio
	 *            The fontScaleRatio to set.
	 */
	public void setFontScaleRatio(double fontScaleRatio) {
		this.fontScaleRatio = fontScaleRatio;
	}

	/**
	 * @param mediumFontSize
	 *            The fontSizeTable to set.
	 */
	public void setMediumFontSize(AbsoluteLengthValue mediumFontSize) {
		this.mediumFontSize = mediumFontSize;
	}

	public void setMinSize(LengthValue minSize) {
		this.minSize = minSize;
	}

	public void setMaxSize(Value maxSize) {
		this.maxSize = maxSize;
	}

	protected AffineTransform getPixelToUnit() {
		if (this.pixelToUnit == null) {
			double scale = LengthUtils.convert(this, 1.0, Unit.PX, Unit.PT);
			if (scale == 0) {
				this.pixelToUnit = IDENTITY_AT;
			} else {
				this.pixelToUnit = AffineTransform.getScaleInstance(scale, scale);
			}
		}
		return this.pixelToUnit;
	}

	public void setMessageHandler(MessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public final void message(short code, String... args) {
		if (this.messageHandler == null) {
			return;
		}
		if (isDeclarationWarning(code)
				&& !this.getUAContext().firstStyleWarning(code + "\u0000" + String.join("\u0000", args))) {
			return;
		}
		this.messageHandler.message(code, args.length == 0 ? null : args, null);
	}

	/**
	 * The warnings about one CSS declaration (unsupported or ignored property, invalid value) and about a style sheet
	 * that cannot be loaded come once per conversion (2026-10-08). An EPUB parses its shared style sheet again for every
	 * item, and every pass parses the style sheets again, so the same warning came once per item and pass (4950 lines
	 * for one book).
	 */
	private static boolean isDeclarationWarning(final short code) {
		return code == MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY || code == MessageCodes.WARN_IGNORED_CSS_PROPERTY
				|| code == MessageCodes.WARN_BAD_CSS_ARGMENTS || code == MessageCodes.WARN_MISSING_CSS_STYLESHEET;
	}

	public void setSourceResolver(SourceResolver resolver) {
		this.resolver = resolver;
	}

	public SourceResolver getSourceResolver() {
		return this.resolver;
	}

	public Source resolve(URI uri) throws IOException {
		try {
			return this.resolver.resolve(uri);
		} catch (SecurityException e) {
			this.message(MessageCodes.WARN_BLOCKED_RESOURCE, uri.toString());
			IOException ioe = new IOException(e.getMessage());
			ioe.initCause(e);
			throw ioe;
		}
	}

	public void release(Source source) {
		this.resolver.release(source);
	}

	public FontManager getFontManager() {
		return this.fontManager;
	}

	protected Image loadImage(final Source source) throws IOException {
		final URI uri = source.getURI();
		this.throwIfRefusedImage(uri);
		// In dimension-only passes, return recorded dimensions **before touching the resource**
		// (2026-08-16). PluginRegistry.search asks Source for its MIME type
		// to select a loader, so checking later triggers retrieval
		// of remote resources. Even when input.image-metrics supplies dimensions in advance,
		// only this check makes retrieval itself unnecessary.
		final ImageLoader loader = PluginRegistry.getInstance().search(ImageLoader.class, source);
		if (loader == null) {
			throw new IOException("Unsupported image source: " + source.getURI());
		}
		// Dimension-only passes read headers, not pixels.
		// For data:, the URI itself is the content, with no retrieval round trip, so keep
		// normal loading as before (switching could change behavior)
		final boolean cacheable = uri != null && !"data".equalsIgnoreCase(uri.getScheme());
		try {
			if (cacheable && (this.isMeasurePass() || this.isStructureScanPass())
					&& loader instanceof RasterImageLoader rasterLoader) {
				return rasterLoader.loadImageForLayout(source, UAProps.INPUT_IMAGE_PIXEL_LIMIT.getLong(this));
			}
			return loader.loadImage(this, source);
		} catch (final ImageTooLargeException e) {
			this.noteRefusedImage(uri, e);
			throw e;
		}
	}

	/**
	 * Images rejected by the pixel-count limit ({@code input.image-pixel-limit}), 2026-10-03.
	 * Avoids reopening resources and rereading headers across repeated references and passes.
	 * Lifetime matches recorded image dimensions (cleared at document start).
	 * Records the limit value as well; a changed limit causes a reread.
	 */
	private Map<URI, ImageTooLargeException> refusedImages;

	/** Throws the same exception if the image was rejected by the limit. */
	protected final void throwIfRefusedImage(final URI uri) throws ImageTooLargeException {
		if (uri == null || this.refusedImages == null) {
			return;
		}
		final ImageTooLargeException refused = this.refusedImages.get(uri);
		if (refused != null && refused.getLimit() == UAProps.INPUT_IMAGE_PIXEL_LIMIT.getLong(this)) {
			throw refused;
		}
	}

	/** Remembers an image rejected by the limit. */
	protected final void noteRefusedImage(final URI uri, final ImageTooLargeException e) {
		if (uri == null) {
			return;
		}
		if (this.refusedImages == null) {
			this.refusedImages = new HashMap<URI, ImageTooLargeException>();
		}
		this.refusedImages.put(uri, e);
	}

	public Image getImage(final Source source) throws IOException {
		Image image = this.loadImage(source);
		// Loading one image is **actual completed work**. In documents with successive large images or complex SVGs,
		// this is the only progress between pages (2026-07-27)
		this.noteProgress();
		AffineTransform pixelToUnit = this.getPixelToUnit();
		if (!pixelToUnit.isIdentity()) {
			image = new TransformedImage(image, this.pixelToUnit);
		}
		return image;
	}

	/**
	 * Returns recorded image dimensions <b>before resolving the resource</b> (2026-08-16).
	 *
	 * <p>
	 * In dimension-only passes, images already measured or supplied via {@code input.image-metrics}
	 * avoid {@link #resolve(URI)}. This is needed because <b>resolution itself may fetch the resource</b>.
	 * Local file resolution is lazy, so checking in {@link #loadImage} suffices, but requesting a
	 * resource from a client over CTIP <b>transfers it at resolution time</b>.
	 * If the caller resolves first and then calls {@link #getImage}, it "transfers and then does not use it."
	 * </p>
	 *
	 * @return recorded dimensions if available; otherwise {@code null}
	 *         (the caller resolves and loads as before).
	 */
	public Image getImageMetrics(final URI uri) {
		if (!this.isMetricsCacheable(uri)) {
			return null;
		}
		// Recorded values are those returned by getImage(Source), **after** px → pt conversion.
		// Applying it again would double it, so return unchanged
		return this.getUAContext().getImageMetrics().get(uri.toString());
	}

	/**
	 * Retrieves an image and, in dimension-only passes, records dimensions <b>under the requested URI</b>.
	 *
	 * <p>
	 * Uses the requested URI as the key rather than the resolved URI because documents such as EPUB
	 * reference internal resources by relative URIs. Recording relative URIs lets the metrics table
	 * match unchanged when the same EPUB is supplied from another base
	 * (another directory or server).
	 * </p>
	 */
	public Image getImage(final URI uri, final Source source) throws IOException {
		// **Always go through getImage(Source).** PDFUserAgent overrides it to provide
		// the PDFWriter loading path (which handles BMP and JPEG2000) and
		// dimension-only images for non-output passes. Calling loadImage() directly
		// bypasses that override, making some image formats unreadable
		// (found 2026-08-16 through five baseline image test regressions)
		final Image image = this.getImage(source);
		if (this.isMetricsCacheable(uri)) {
			this.getUAContext().getImageMetrics().put(uri.toString(), image);
		}
		return image;
	}

	/**
	 * Whether to record this resource. Does not record {@code data:}, which has no retrieval round trip
	 * and carries the content in the URI itself (the key would be as large as the image
	 * and inflate the exported XML).
	 */
	private boolean isMetricsCacheable(final URI uri) {
		return uri != null && (this.isMeasurePass() || this.isStructureScanPass())
				&& !"data".equalsIgnoreCase(uri.getScheme());
	}

	public void setPageProgression(final net.zamasoft.foliojet.layout.box.params.WritingMode progression) {
		this.pageProgression = progression;
	}

	public net.zamasoft.foliojet.layout.box.params.WritingMode getPageProgression() {
		return this.pageProgression;
	}

	/** Page progression direction ({@code ltr} / {@code rtl}). Only {@code vertical-rl} uses {@code rtl}. */
	public String getPageProgressionDirection() {
		return this.pageProgression == net.zamasoft.foliojet.layout.box.params.WritingMode.RL ? "rtl" : "ltr";
	}

	public void setBoundSide(BoundSide boundSide) {
		this.boundSide = boundSide;
	}

	public BoundSide getBoundSide() {
		return this.boundSide;
	}

	public final GC nextPage(double pageWidth, double pageHeight) {
		this.pageWidth = pageWidth;
		this.pageHeight = pageHeight;
		return this.nextPage();
	}

	protected abstract GC nextPage();

	public void closePage(final GC gc) throws IOException {
	}

	public void finish() throws BrokenResultException, IOException {
		// NOP
	}

	private PrepareMode currentMode = PrepareMode.DOCUMENT;

	public void prepare(PrepareMode mode) {
		// Switching passes is progress. Carrying over elapsed time from the preceding pass
		// (especially STRUCTURE_SCAN, which emits no pages) makes the first abort point in the next pass
		// falsely detect an exceeded deadline (2026-07-30)
		this.noteProgress();
		this.currentMode = mode;
		this.fontMagnification = -1;
		this.pixelsPerInch = -1;
		this.pixelToUnit = null;
		if (mode != PrepareMode.DOCUMENT) {
			int pages = this.getPassContext().getPageNumber();
			this.passContext = new PassContext();
			this.getUAContext().getPageRef().reset();
			// Total page count
			this.getPassContext().getCounterScope(0, true).reset("pages", pages);
		}
		if (mode == PrepareMode.STRUCTURE_SCAN) {
			// STRUCTURE_SCAN itself resolves SelectorFacts anew, so clear them
			// to avoid carrying over results of a preceding scan (another document or a restart).
			// Unlike PageRef, they are not resolved progressively
			// across multiple LAYOUT passes, so resetting once at STRUCTURE_SCAN start
			// is sufficient.
			this.getUAContext().getSelectorFacts().reset();
			// ContainerFacts has the same lifetime as SelectorFacts (reset once at STRUCTURE_SCAN start,
			// then accumulate/overwrite in all later passes).
			// See the development records §2 for the design
			this.getUAContext().getContainerFacts().reset();
		}
		if (mode == PrepareMode.MIDDLE_PASS || mode == PrepareMode.LAST_PASS) {
			// Stage 5 (design §3): snapshot values before this pass's writes and
			// use them for the fixed-point check after the pass (see DirectSession.format)
			this.getUAContext().getContainerFacts().beginPass();
		}
		if (mode == PrepareMode.STRUCTURE_SCAN || mode == PrepareMode.DOCUMENT) {
			// Clear the carried stylesheet at conversion (document) start
			// (see UAContext.getCarriedStyleSheet Javadoc). Intermediate and final
			// passes inherit the preceding pass's collection
			this.getUAContext().clearCarriedStyleSheets();
			this.getUAContext().clearReportedStyleWarnings();
			// Reset the footnote area per document as well. CSS parsing in each pass sets the rules again.
			this.getUAContext().setFootnoteArea(null);
			// Image dimensions have the same lifetime. The same URI can refer to different content in another document
			this.getUAContext().getImageMetrics().reset();
			this.refusedImages = null;
			this.loadImageMetrics();
		}
		this.documentContext = new DocumentContext();
	}

	/**
	 * Loads the metrics table supplied through {@code input.image-metrics}.
	 * Failure only warns because layout can continue (it simply falls back to measurement).
	 */
	private void loadImageMetrics() {
		final String location = UAProps.INPUT_IMAGE_METRICS.getString(this);
		if (location == null || location.isEmpty()) {
			return;
		}
		try {
			final Source source = this.resolve(URIHelper.create("UTF-8", location));
			try (java.io.InputStream in = source.getInputStream()) {
				ImageMetricsIO.read(in, this.getUAContext().getImageMetrics(),
						UAProps.OUTPUT_RESOLUTION.getDouble(this));
			} finally {
				this.release(source);
			}
		} catch (final Exception e) {
			this.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.INPUT_IMAGE_METRICS.name, location);
		}
	}

	public boolean isMeasurePass() {
		return this.currentMode == PrepareMode.MIDDLE_PASS;
	}

	public boolean isStructureScanPass() {
		return this.currentMode == PrepareMode.STRUCTURE_SCAN;
	}

	public boolean isLastPass() {
		return this.currentMode == PrepareMode.LAST_PASS;
	}

	/**
	 * Font manager created by image/SVG output. Closed on pass changes and {@link #dispose};
	 * this {@code close()} deletes temporary font files retrieved through {@code @font-face}
	 * (until 2026-10-04, references were merely dropped without closing, leaving files across conversions).
	 */
	private FontManagerImpl ownedFontManager;

	protected final FontManagerImpl ownedFontManager(final boolean coreFontsLast) {
		if (this.ownedFontManager == null) {
			this.ownedFontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
			this.ownedFontManager.setCoreFontsLast(coreFontsLast);
		}
		return this.ownedFontManager;
	}

	protected final void closeOwnedFontManager() {
		if (this.ownedFontManager != null) {
			this.ownedFontManager.close();
			this.ownedFontManager = null;
		}
	}

	public void dispose() {
		this.closeOwnedFontManager();
		if (this.retainedTextLimit != null) this.retainedTextLimit.close();
	}
}
