package net.zamasoft.foliojet.impl.ua;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import jp.cssj.cti2.helpers.CTIMessageCodes;
import jp.cssj.cti2.message.MessageHandler;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.ExLengthValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.ImageLoader;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.UAContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPdfVersion;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.plugin.PluginRegistry;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractUserAgent.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public abstract class AbstractUserAgent implements UserAgent {
	private UAContext context = new UAContext();

	private PassContext passContext = new PassContext();

	private DocumentContext documentContext = new DocumentContext();

	private Map<String, String> props = null;

	private byte aborted = 0;

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

	private Value maxSize = NoneValue.NONE_VALUE;

	private double pixelsPerInch = -1, fontMagnification = -1;

	private static final AffineTransform IDENTITY_AT = new AffineTransform();

	private AffineTransform pixelToUnit = null;

	private MessageHandler messageHandler = null;

	private SourceResolver resolver;

	private FontManager fontManager;

	private CSSJFontPolicyValue fontPolicy = null;

	// *Watermark

	private byte boundSide = BOUND_SIDE_SINGLE;

	protected double pageWidth, pageHeight;

	public AbstractUserAgent() {
		this.setDefaultLocale(Locale.getDefault());
		this.setNormalLineHeight(1.2);
		this.setDefaultMarkerOffset(ExLengthValue.create(1));

		this.setMinSize(AbsoluteLengthValue.ZERO);
		// 14400はPDFの限界サイズ
		this.setMaxSize(AbsoluteLengthValue.create(this, 14400, LengthValue.UNIT_PT));
		this.setBorderTable(new AbsoluteLengthValue[] { AbsoluteLengthValue.create(this, 1),
				AbsoluteLengthValue.create(this, 2), AbsoluteLengthValue.create(this, 3) });
		this.setFontScaleRatio(1.2);
		this.setMediumFontSize(AbsoluteLengthValue.create(this, 12));

		this.setDefaultColor(ColorValueUtils.BLACK);
		this.setMatColor(ColorValueUtils.WHITE);

		// @AbstractUserAgent
	}

	public UAContext getUAContext() {
		return this.context;
	}

	public PassContext getPassContext() {
		return this.passContext;
	}

	public DocumentContext getDocumentContext() {
		return this.documentContext;
	}

	// @Limited

	public final String getProperty(String name) {
		if (this.props == null) {
			return null;
		}
		return this.props.get(name);
	}

	public final void setProperty(String name, String value) {
		// @setProperty
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

		// メタ情報
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

	public void abort(byte mode) {
		if (this.aborted != mode) {
			this.message(CTIMessageCodes.INFO_ABORT);
		}
		this.aborted = mode;
	}

	protected void checkAbort(byte mode) {
		if (this.aborted == mode || this.aborted == AbortException.ABORT_FORCE) {
			this.message(CTIMessageCodes.INFO_ABORT);
			throw new AbortException(this.aborted);
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
			// メディアタイプ
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

	public AbsoluteLengthValue getBorderWidth(byte type) {
		return this.borderTable[type - 1];
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

	public CSSJFontPolicyValue getDefaultFontPolicy() {
		if (this.fontPolicy == null) {
			String s = UAProps.OUTPUT_PDF_FONTS_POLICY.getString(this);
			int pdfVersion = UAProps.OUTPUT_PDF_VERSION.getCode(this);
			if (pdfVersion == OutputPdfVersion.V1_4A1 || pdfVersion == OutputPdfVersion.V1_4X1) {
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

	public final double getFontSize(byte absoluteFontSize) {
		double size = this.mediumFontSize.getLength();
		size = switch (absoluteFontSize) {
		case FONT_SIZE_XX_SMALL -> size * 3 / 5;
		case FONT_SIZE_X_SMALL -> size * 3 / 4;
		case FONT_SIZE_SMALL -> size * 8 / 9;
		case FONT_SIZE_MEDIUM -> size;
		case FONT_SIZE_LARGE -> size * 6 / 5;
		case FONT_SIZE_X_LARGE -> size * 3 / 2;
		case FONT_SIZE_XX_LARGE -> size * 2;
		default -> throw new IllegalArgumentException();
		};
		return size * this.getFontMagnification();
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
	 *            The borders to set. 配列のサイズは3です。
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
			double scale = LengthUtils.convert(this, 1.0, LengthValue.UNIT_PX, LengthValue.UNIT_PT);
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

	public final void message(short code, String[] args) {
		if (this.messageHandler == null) {
			return;
		}
		this.messageHandler.message(code, args, null);
	}

	public final void message(short code) {
		this.message(code, (String[]) null);
	}

	private String[] args1 = null;

	public final void message(short code, String arg0) {
		if (this.args1 == null) {
			this.args1 = new String[1];
		}
		this.args1[0] = arg0;
		this.message(code, this.args1);
	}

	private String[] args2 = null;

	public final void message(short code, String arg0, String arg1) {
		if (this.args2 == null) {
			this.args2 = new String[2];
		}
		this.args2[0] = arg0;
		this.args2[1] = arg1;
		this.message(code, this.args2);
	}

	private String[] args3 = null;

	public final void message(short code, String arg0, String arg1, String arg2) {
		if (this.args3 == null) {
			this.args3 = new String[3];
		}
		this.args3[0] = arg0;
		this.args3[1] = arg1;
		this.args3[2] = arg2;
		this.message(code, this.args3);
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

	public void setFontManager(FontManager fontManager) {
		this.fontManager = fontManager;
	}

	public FontManager getFontManager() {
		return this.fontManager;
	}

	protected Image loadImage(final Source source) throws IOException {
		final ImageLoader loader = PluginRegistry.getInstance().search(ImageLoader.class, source);
		if (loader == null) {
			throw new IOException("Unsupported image source: " + source.getURI());
		}
		return loader.loadImage(this, source);
	}

	public Image getImage(final Source source) throws IOException {
		Image image = this.loadImage(source);
		AffineTransform pixelToUnit = this.getPixelToUnit();
		if (!pixelToUnit.isIdentity()) {
			image = new TransformedImage(image, this.pixelToUnit);
		}
		return image;
	}

	public void setBoundSide(byte boundSide) {
		this.boundSide = boundSide;
	}

	public byte getBoundSide() {
		return this.boundSide;
	}

	public final GC nextPage(double pageWidth, double pageHeight) {
		this.pageWidth = pageWidth;
		this.pageHeight = pageHeight;
		return this.nextPage();
	}

	protected abstract GC nextPage();

	public void closePage(final GC gc) throws IOException {
		// *closePage
	}

	public void finish() throws BrokenResultException, IOException {
		// NOP
	}

	public void prepare(byte mode) {
		this.fontMagnification = -1;
		this.pixelsPerInch = -1;
		this.pixelToUnit = null;
		if (mode != PREPARE_DOCUMENT) {
			int pages = this.getPassContext().getPageNumber();
			this.passContext = new PassContext();
			this.getUAContext().getPageRef().reset();
			// 総ページ数
			this.getPassContext().getCounterScope(0, true).reset("pages", pages);
		}
		this.documentContext = new DocumentContext();
	}

	public boolean isMeasurePass() {
		return false;
	}

	public void dispose() {
		// ignore
	}
}
