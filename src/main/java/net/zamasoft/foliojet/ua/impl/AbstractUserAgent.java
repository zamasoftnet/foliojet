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
import net.zamasoft.foliojet.ua.ImageMetricsCache;
import net.zamasoft.foliojet.ua.ImageMetricsIO;
import net.zamasoft.foliojet.ua.impl.image.RasterImageLoader;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.UAContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPdfVersion;
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

	/**
	 * ページの進む向きを決める根の書字方向(2026-09-02)。{@code PageSequence} が
	 * 根の {@code writing-mode} から設定する。読み器が要るのは綴じ方向ではなく
	 * 「頁がどちらへ進むか」で、綴じが {@code single} でも縦組みなら右から読む
	 * (cti.li の要望)
	 */
	private net.zamasoft.foliojet.layout.box.params.WritingMode pageProgression = net.zamasoft.foliojet.layout.box.params.WritingMode.TB;

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

	/**
	 * 現在の入出力プロパティの写しです(2026-09-02)。EPUBの項目を組む子のUAへ
	 * 親と同じ設定を渡すために使う。
	 */
	public final Map<String, String> getProperties() {
		return this.props == null ? new HashMap<>() : new HashMap<>(this.props);
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
	 * 打ち切られてしまう。見るのは<b>「仕事が1単位も進まない状態が続いた
	 * 時間」</b>で、これなら文書の大きさに依存しない——長い文書は仕事を
	 * 進め続けるので当たらず、詰まったものは必ず当たる。
	 * </p>
	 *
	 * <p>
	 * <b>この値が超えるべきなのは「最も長い単一の仕事」</b>であって、
	 * 文書全体の処理時間ではない。{@link #noteProgress()}を細かく置いた
	 * ので、表の大きさには依存しなくなった。
	 * </p>
	 *
	 * <p>
	 * <b>値の根拠(実測、2026-07-27)</b>。進捗と進捗の最大間隔:
	 * </p>
	 *
	 * <table border="1">
	 * <tr><th>文書</th><th>最大間隔</th></tr>
	 * <tr><td><b>8000x8000 PNG(176MB)x 3</b></td><td><b>9.60秒</b></td></tr>
	 * <tr><td>60,000パスのSVG(5.4MB)x 3</td><td>2.33秒</td></tr>
	 * <tr><td>表 200,000行</td><td>2.23秒</td></tr>
	 * </table>
	 *
	 * <p>
	 * 支配項は<b>巨大画像のデコード1枚</b>。表は行ごとに進捗を刻むように
	 * したので、40万行でも2秒台に収まる(この変更の前は37.5秒だった)。
	 * </p>
	 *
	 * <p>
	 * <b>120秒の根拠</b>: 実測の最悪単位9.6秒の約12倍。内訳として
	 * 「4倍大きな素材(約700MBの画像)」×「3倍遅い/混雑したサーバ」を
	 * 見込む。<b>大きすぎる側の代償は限定的</b>(詰まった変換1件が
	 * スレッドとメモリをその時間だけ抱える)が、<b>小さすぎる側は正当な
	 * 文書が失敗する</b>ので、非対称を踏まえて余裕側へ倒した。
	 * </p>
	 *
	 * <p>
	 * <b>製品既定は無制限(オーナー裁定2026-08-01で反転)</b>。導入時
	 * (2026-07-27)は「オプトインの安全弁は事故に遭った人しか使わない」
	 * ([[LESSONS]] §6.9b)を根拠に既定有効としたが、この弁は
	 * <b>ユーザーの正当なジョブを殺しうる</b>点であの原則の適用対象では
	 * ない。上の非対称論理(小さすぎる側は正当な文書が失敗する)を徹底
	 * すると誤爆ゼロの値は無制限だけであり、実際に「120秒を超える正当な
	 * 待ち」のクラスが実在した(ストリーミング入力の間隙——遅いDB
	 * カーソルからの帳票逐次生成。入力待ちは進捗に数えられない)。
	 * クライアント側の遅延・切断の検出はネットワーク層の責務で、そちらには
	 * 設定可能なタイムアウトが既にある(CTIP {@code jp.cssj.cssjd.timeout}
	 * =既定180秒、RESTセッション=既定3分)。
	 * </p>
	 *
	 * <p>
	 * <b>ハングアップ検出は主にテストハーネスの用途</b>(掃過・CIで
	 * ライブロックを失敗として検出する——実績はseed 213026等)のため、
	 * foliojet4のtestタスク・copperpdf4/devのデーモン/CLI起動が
	 * {@code -Dfoliojet.noProgressSeconds=120}を明示設定する。本番でも
	 * SLA上必要ならこのプロパティで有効化できる(0以下=無制限)。
	 * 残存リスクとして「エンジンが真にハングし、かつクライアントが
	 * 無期限に待ち続ける」場合はワーカースレッドが再起動まで塞がるが、
	 * 既知のライブロッククラスは逃げ道実装(2026-07-29)で解消済み。
	 * </p>
	 *
	 * <h3>性能への影響(実測、2026-07-27)</h3>
	 *
	 * <p>
	 * 追加したのは{@link #checkAbort(byte)}のvolatile読み+
	 * {@code System.nanoTime()}と、{@link #noteProgress()}のvolatile書き。
	 * 呼び出し回数を数えたところ:
	 * </p>
	 *
	 * <table border="1">
	 * <tr><th>文書</th><th>checkAbort</th><th>noteProgress</th><th>追加コストの上限</th></tr>
	 * <tr><td>表 200,000行(変換65秒)</td><td>37,500</td><td>404,167</td><td>11.0 ms = <b>0.017%</b></td></tr>
	 * <tr><td>テキスト 20,000段落</td><td>24,377</td><td>434</td><td>0.6 ms</td></tr>
	 * <tr><td>フロート 8,000個</td><td>9,649</td><td>236</td><td>0.2 ms</td></tr>
	 * </table>
	 *
	 * <p>
	 * 端から端までの実測では基準実装との差が測定誤差(±6%)に埋もれた。
	 * <b>粗い粒度に置いている限り無視できる</b>——グリフ単位・文字単位へ
	 * 降ろすとこの前提は崩れる。
	 * </p>
	 */
	private static final long NO_PROGRESS_LIMIT_NANOS = Long.getLong("foliojet.noProgressSeconds", 0L)
			* 1_000_000_000L;

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
		if (NO_PROGRESS_LIMIT_NANOS > 0 && System.nanoTime() - this.lastProgressNanos > NO_PROGRESS_LIMIT_NANOS) {
			// 仕事が1単位も進まないまま設定時間を過ぎた。詰まっているとみなす
			// (0以下=無制限が製品既定。テストハーネスは120秒を明示設定)
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
		// 寸法しか要らないパスでは、記録済みの寸法を**資源に触れる前に**返す
		// (2026-08-16)。PluginRegistry.searchはローダを選ぶためにSourceの
		// MIME型を訊くので、ここより後ろで当てるとリモート資源の取得が
		// 走ってしまう。input.image-metricsで寸法を先に渡した場合も、
		// この判定に当たることで初めて取得そのものが要らなくなる。
		final ImageLoader loader = PluginRegistry.getInstance().search(ImageLoader.class, source);
		if (loader == null) {
			throw new IOException("Unsupported image source: " + source.getURI());
		}
		// 寸法しか要らないパスは画素を読まず、ヘッダだけを読む。
		// data:はURIそのものが中身で取得の往復が無いため、従来どおり
		// 通常の読み込みに任せる(切り替えると挙動が変わりうる)
		final URI uri = source.getURI();
		final boolean cacheable = uri != null && !"data".equalsIgnoreCase(uri.getScheme());
		if (cacheable && (this.isMeasurePass() || this.isStructureScanPass())
				&& loader instanceof RasterImageLoader rasterLoader) {
			return rasterLoader.loadImageForLayout(source);
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

	/**
	 * 記録済みの画像寸法を、<b>資源を解決する前に</b>返します(2026-08-16)。
	 *
	 * <p>
	 * 寸法しか要らないパスで、既に測った画像・{@code input.image-metrics}で
	 * 渡された画像なら、{@link #resolve(URI)}を呼ばずに済みます。これが要るのは
	 * <b>解決そのものが取得を伴う場合がある</b>ためです。ローカルファイルの
	 * 解決は遅延なので{@link #loadImage}側の判定で足りますが、CTIPで
	 * クライアントへ資源を要求する経路は<b>解決した時点で転送が起きます</b>。
	 * 呼び出し側が解決を済ませてから{@link #getImage}を呼ぶ形だと、
	 * 「転送してから使わない」ことになります。
	 * </p>
	 *
	 * @return 記録があればその寸法、無ければ{@code null}(呼び出し側は
	 *         これまでどおり解決して読み込む)。
	 */
	public Image getImageMetrics(final URI uri) {
		if (!this.isMetricsCacheable(uri)) {
			return null;
		}
		// 記録するのは getImage(Source) が返した値、つまり px→pt 変換の**適用後**。
		// ここで再度掛けると二重になるのでそのまま返す
		return this.getUAContext().getImageMetrics().get(uri.toString());
	}

	/**
	 * 画像を取得し、寸法しか要らないパスなら<b>要求時のURIで</b>寸法を記録します。
	 *
	 * <p>
	 * キーに解決後のURIではなく要求時のURIを使うのは、EPUBのように内部が
	 * 相対URIで参照し合う文書があるためです。相対URIのまま記録しておけば、
	 * 同じEPUBを別の基底(別のディレクトリ、別のサーバー)から与えても
	 * 寸法表がそのまま当たります。
	 * </p>
	 */
	public Image getImage(final URI uri, final Source source) throws IOException {
		// **必ず getImage(Source) を通すこと。** PDFUserAgentはこれを上書きして
		// PDFWriter側の読み込み経路(BMPやJPEG2000はここで扱われる)と
		// 非出力パス用の寸法専用画像を担っている。loadImage()を直に呼ぶと
		// その上書きを飛ばしてしまい、一部の画像形式が読めなくなる
		// (2026-08-16、基準画像テスト5件の回帰で判明)
		final Image image = this.getImage(source);
		if (this.isMetricsCacheable(uri)) {
			this.getUAContext().getImageMetrics().put(uri.toString(), image);
		}
		return image;
	}

	/**
	 * 記録の対象か。{@code data:}は取得の往復が無く、URIそのものが中身なので
	 * 記録しません(キーが画像本体と同じ大きさになり、書き出したXMLも膨れる)。
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

	/** 頁の進む向き({@code ltr} / {@code rtl})。{@code vertical-rl} だけ {@code rtl}。 */
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
		// *closePage
	}

	public void finish() throws BrokenResultException, IOException {
		// NOP
	}

	private PrepareMode currentMode = PrepareMode.DOCUMENT;

	public void prepare(PrepareMode mode) {
		// パスの切り替えは進捗である。前のパス(特にページを1枚も出さない
		// STRUCTURE_SCAN)の経過を持ち越すと、次のパスの最初の中断点が
		// 締切超過を誤検出する(2026-07-30)
		this.noteProgress();
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
			// ContainerFactsもSelectorFactsと同じ寿命(STRUCTURE_SCAN開始時
			// 1回だけリセット、以降の全パスで積み上げ・上書き)。
			// 設計はdocs/history/2026-08-15-container-queries-design.md §2
			this.getUAContext().getContainerFacts().reset();
		}
		if (mode == PrepareMode.MIDDLE_PASS || mode == PrepareMode.LAST_PASS) {
			// 段5(設計§3): このパスの書き込み前の値をスナップショットし、
			// パス終了後の不動点判定(DirectSession.format参照)に使う
			this.getUAContext().getContainerFacts().beginPass();
		}
		if (mode == PrepareMode.STRUCTURE_SCAN || mode == PrepareMode.DOCUMENT) {
			// パス持ち越しスタイルシートは変換(文書)の開始でクリアする
			// (UAContext.getCarriedStyleSheetのjavadoc参照)。中間・最終
			// パスは前のパスの収集を引き継ぐ
			this.getUAContext().setCarriedStyleSheet(null);
			// 画像寸法も同じ寿命。別の文書では同じURIが違う内容を指しうる
			this.getUAContext().getImageMetrics().reset();
			this.loadImageMetrics();
		}
		this.documentContext = new DocumentContext();
	}

	/**
	 * {@code input.image-metrics}で渡された寸法表を読み込みます。
	 * 読めなくても組版は続けられる(実測に戻るだけ)ので警告に留めます。
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

	public void dispose() {
		// ignore
	}
}
