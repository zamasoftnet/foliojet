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
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.PrepareMode;

/**
 * @author MIYABE Tatsuhiko
 */
public abstract class AbstractUserAgent implements UserAgent {
	private UAContext context = new UAContext();

	private PassContext passContext = new PassContext();

	private DocumentContext documentContext = new DocumentContext();

	private Map<String, String> props = null;

	/**
	 * 中断要求(0=なし)。
	 *
	 * <p>
	 * <b>volatile が要る。</b>{@link net.zamasoft.foliojet.driver.DirectSession}は
	 * レイアウトを専用スレッド({@code foliojet-layout})で走らせるので、
	 * {@link #abort(byte)}を呼ぶスレッドと{@link #checkAbort(byte)}を読む
	 * スレッドが別になる。volatileがないと書き込みが見えず、
	 * <b>止まったり止まらなかったりする</b>(2026-07-27)。
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

	// *Watermark

	private BoundSide boundSide = BoundSide.SINGLE;

	protected double pageWidth, pageHeight;

	public AbstractUserAgent() {
		this.setDefaultLocale(Locale.getDefault());
		this.setNormalLineHeight(1.2);
		this.setDefaultMarkerOffset(RelativeLengthValue.ex(1));

		this.setMinSize(AbsoluteLengthValue.ZERO);
		// 14400はPDFの限界サイズ
		this.setMaxSize(AbsoluteLengthValue.create(this, 14400, Unit.PT));
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

	/**
	 * <b>進捗が止まったら中断する締切</b>(2026-07-27新設)。
	 *
	 * <p>
	 * <b>壁時計の締切にしてはいけない。</b>1万ページの正当な帳票が
	 * 打ち切られてしまう。見るのは<b>「ページが1枚も出ない状態が続いた
	 * 時間」</b>で、これなら文書の大きさに依存しない——長い文書はページを
	 * 出し続けるので当たらず、詰まったものは必ず当たる。
	 * </p>
	 *
	 * <p>
	 * <b>値の根拠(実測、2026-07-27)</b>。自動表のページ間隔:
	 * </p>
	 *
	 * <table border="1">
	 * <tr><th>行数</th><th>ページ数</th><th>中央値</th><th>p99</th><th>最大</th></tr>
	 * <tr><td>10,000</td><td>209</td><td>2ms</td><td>17ms</td><td>1.3秒</td></tr>
	 * <tr><td>40,000</td><td>834</td><td>1ms</td><td>7ms</td><td>3.8秒</td></tr>
	 * <tr><td>100,000</td><td>2,084</td><td>2ms</td><td>11ms</td><td>9.4秒</td></tr>
	 * </table>
	 *
	 * <p>
	 * 最大間隔は測定パス由来で<b>文書の大きさに比例して伸びる</b>ので、
	 * 実測最大の13倍を取った。100万行規模の測定パスも通る見込み。
	 * </p>
	 *
	 * <p>
	 * <b>オプションにしない。</b>「オプトインの安全弁は事故に遭った人しか
	 * 使わない」([[LESSONS]] §6.9b)——既定で効かせる。
	 * </p>
	 */
	private static final long NO_PROGRESS_LIMIT_NANOS = Long.getLong("foliojet.noProgressSeconds", 120L) * 1_000_000_000L;

	/** 最後にページを出した時刻。{@link #checkAbort(byte)}が締切に使う。 */
	private volatile long lastProgressNanos = System.nanoTime();

	/**
	 * ページを1枚出したことを記録します。締切はこれを基準に測ります。
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
	 * <b>協調的な中断点</b>。中断要求が出ていれば{@link AbortException}を
	 * 投げます。長く走るループの先頭で呼んでください。
	 *
	 * <p>
	 * コストはvolatile 1個の読み取り。<b>行・表の行・ページといった粗い
	 * 粒度</b>に置くこと——グリフ単位に置いてはいけません。
	 * </p>
	 */
	public void checkAbort(byte mode) {
		if (this.aborted == mode || this.aborted == AbortException.ABORT_FORCE) {
			this.message(CTIMessageCodes.INFO_ABORT);
			throw new AbortException(this.aborted);
		}
		if (System.nanoTime() - this.lastProgressNanos > NO_PROGRESS_LIMIT_NANOS) {
			// 仕事が1単位も進まないまま既定時間を過ぎた。詰まっているとみなす
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

	public CSSJFontPolicyValue getDefaultFontPolicy() {
		if (this.fontPolicy == null) {
			String s = UAProps.OUTPUT_PDF_FONTS_POLICY.getString(this);
			// PDF/A・PDF/X・PDF/UA はいずれもフォント埋め込みが必須。
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
		this.messageHandler.message(code, args.length == 0 ? null : args, null);
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
		// 画像1枚の読み込みは**実際に進んだ仕事**。大きな画像・複雑なSVGが
		// 続く文書では、ページとページの間でここだけが進む(2026-07-27)
		this.noteProgress();
		AffineTransform pixelToUnit = this.getPixelToUnit();
		if (!pixelToUnit.isIdentity()) {
			image = new TransformedImage(image, this.pixelToUnit);
		}
		return image;
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
		// *closePage
	}

	public void finish() throws BrokenResultException, IOException {
		// NOP
	}

	private PrepareMode currentMode = PrepareMode.DOCUMENT;

	public void prepare(PrepareMode mode) {
		this.currentMode = mode;
		this.fontMagnification = -1;
		this.pixelsPerInch = -1;
		this.pixelToUnit = null;
		if (mode != PrepareMode.DOCUMENT) {
			int pages = this.getPassContext().getPageNumber();
			this.passContext = new PassContext();
			this.getUAContext().getPageRef().reset();
			// 総ページ数
			this.getPassContext().getCounterScope(0, true).reset("pages", pages);
		}
		if (mode == PrepareMode.STRUCTURE_SCAN) {
			// SelectorFactsはSTRUCTURE_SCANパス自身が新規に確定させるため、
			// 前回の走査結果(別文書、またはやり直し)を引きずらないよう
			// クリアする。PageRefと異なり複数のLAYOUTパスをまたいで
			// 段階的に確定させるものではないため、STRUCTURE_SCAN開始時
			// 1回だけリセットすれば足りる。
			this.getUAContext().getSelectorFacts().reset();
		}
		this.documentContext = new DocumentContext();
	}

	public boolean isMeasurePass() {
		return false;
	}

	public boolean isStructureScanPass() {
		return this.currentMode == PrepareMode.STRUCTURE_SCAN;
	}

	public boolean isLastPass() {
		return this.currentMode == PrepareMode.LAST_PASS;
	}

	public void dispose() {
		// ignore
	}
}
