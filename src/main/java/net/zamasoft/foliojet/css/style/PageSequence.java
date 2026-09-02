package net.zamasoft.foliojet.css.style;

import java.awt.geom.AffineTransform;
import java.util.function.Consumer;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.page.PageBleed;
import net.zamasoft.foliojet.css.impl.property.page.PageMarks;
import net.zamasoft.foliojet.css.impl.property.page.PageSize;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.content.CounterIncrement;
import net.zamasoft.foliojet.css.impl.property.content.CounterReset;
import net.zamasoft.foliojet.css.lang.LanguageProfile;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CounterSetValue;
import net.zamasoft.foliojet.css.value.PageSizeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.OverflowMode;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.ua.impl.Impositions;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPageLimitAbort;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * ページのライフサイクル(ページの作成・{@code @page}スタイルとカウンタ・
 * 白紙判定と巻き戻し・描画・面付けの終了)です(StyleBuilder解体・増分2で
 * 抽出、2026-07-30。各メソッドの本体はStyleBuilderから逐語移動——挙動不変)。
 *
 * <p>
 * <b>順序が契約である</b>: {@code nextPage}は
 * segment.trimToOpenElements → imposition.nextPageSide →
 * {@code @page}宣言とカウンタ適用 の順、{@code drawPage}は
 * 白紙判定(巻き戻し) → imposition.nextPage → flow → fixed →
 * margin boxes → DisplayListDumper → drawer.draw → closePage の順。
 * </p>
 */
final class PageSequence {
	private final UserAgent ua;
	private final StyleContext styleContext;
	private final Imposition imposition;
	private final DocumentBuilder doc;
	private final Segment segment;
	private final AbsoluteLengthValue[] margins;

	/**
	 * 予約カウンタ({@code pages})の警告。author側のカウンタ処理
	 * (StyleBuilder)と「1文書につき1回」のフラグを共有するため、
	 * 判定・警告ともStyleBuilderへ委ねる。
	 */
	private final Consumer<String> reservedCounterWarner;

	private CSSElement pageElement = null;
	private int pageNumber = 0;
	private int maxPageNumber = Integer.MAX_VALUE;

	/**
	 * 実際に出力したページ数(2026-07-28、css-break-3 §4.4)。
	 */
	private int emittedPages = 0;

	/**
	 * タグ付きPDF構造要素のページ横断レジストリです(欠陥②の修正、
	 * 2026-07-30)。文書(このPageSequence)単位で1つ持ち、各ページの
	 * PageBoxへ配線する。untagged/非PDF出力ではlookupが起きないだけで
	 * 無害。
	 */
	private final net.zamasoft.foliojet.layout.box.impl.TaggedStructureContext structContext = new net.zamasoft.foliojet.layout.box.impl.TaggedStructureContext();

	/**
	 * 直前のページ面。落としたページの面を返すために覚えます
	 * ({@link #nextPage()}が {@code imposition.nextPageSide()} で進める)。
	 */
	private CSSElement previousPageSide = null;

	/** そのページで {@code @page} の counter-increment が加えた量。 */
	private Value[] appliedPageIncrements = null;

	/** そのページで page カウンタを自動加算したか(css-page-3 §6.1)。 */
	private boolean appliedAutoPageIncrement = false;

	/** ルートページの背景(HTML/BODYから昇格)。 */
	private Background background = null;

	/** 組版方向(HTML/BODYのwriting-modeから確定)。 */
	private WritingMode progression = WritingMode.TB;

	PageSequence(final UserAgent ua, final StyleContext styleContext, final Imposition imposition,
			final DocumentBuilder doc, final Segment segment, final Consumer<String> reservedCounterWarner) {
		this.ua = ua;
		this.styleContext = styleContext;
		this.imposition = imposition;
		this.doc = doc;
		this.segment = segment;
		this.reservedCounterWarner = reservedCounterWarner;
		this.pageNumber = ua.getPassContext().getPageNumber();

		// ページ幅
		{
			String s = UAProps.OUTPUT_PAGE_WIDTH.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false, s);
			if (length != null) {
				double l = length.getLength();
				this.imposition.setPageWidth(l);
				if (this.imposition.getNote() != null) {
					this.imposition.setNote(this.imposition.getNote() + " / width " + s);
				}
			} else {
				this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_WIDTH.name, s);
			}
		}

		// ページ高さ
		{
			String s = UAProps.OUTPUT_PAGE_HEIGHT.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false, s);
			if (length != null) {
				double l = length.getLength();
				this.imposition.setPageHeight(l);
				if (this.imposition.getNote() != null) {
					this.imposition.setNote(this.imposition.getNote() + " / height " + s);
				}
			} else {
				this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_HEIGHT.name, s);
			}
		}
		Impositions.setupImposition(this.ua, this.imposition);

		// マージン
		{
			AbsoluteLengthValue[] margins;
			String s = UAProps.OUTPUT_PAGE_MARGINS.getString(ua);
			if (s != null) {
				String[] values = s.split("[\\s]+");
				if (values.length <= 0 || values.length > 4) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_MARGINS.name, s);
					margins = null;
				} else {
					margins = new AbsoluteLengthValue[values.length];
					for (int i = 0; i < values.length; ++i) {
						AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, values[i]);
						if (length != null) {
							margins[i] = length;
						} else {
							ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_MARGINS.name, s);
							margins = null;
							break;
						}
					}
				}
			} else {
				margins = null;
			}
			this.margins = margins;
		}

		// 最大ページ数
		this.maxPageNumber = UAProps.OUTPUT_PAGE_LIMIT.getInteger(ua);
	}

	CSSElement getPageElement() {
		return this.pageElement;
	}

	WritingMode getProgression() {
		return this.progression;
	}

	void setProgression(final WritingMode progression) {
		this.progression = progression;
	}

	/**
	 * HTML/BODYの背景をルートページの背景へ昇格します。
	 *
	 * @return 昇格した(呼び出し側は要素側の背景をNULLにする)ならtrue
	 */
	boolean promoteRootBackground(final Background background) {
		if (this.background == null && background != Background.NULL_BACKGROUND) {
			this.background = background;
			return true;
		}
		return false;
	}

	PageBreakMode getPageSide() {
		if (this.pageElement.isPseudoClass(CSSElement.PC_EVEN)) {
			return PageBreakMode.VERSO;
		}
		if (this.pageElement.isPseudoClass(CSSElement.PC_ODD)) {
			return PageBreakMode.RECTO;
		}
		return PageBreakMode.AUTO;
	}

	/**
	 * 次に生成されるページからのページ名です(名前付きページN2a。
	 * null=無名。境界裁定(BreakableBuilder)が改ページに先立って設定
	 * する——生成済みページの解決を汚染しないよう、確定値は
	 * {@link #pageName}へnextPage()時に捕捉する)。
	 */
	private String pendingPageName;

	/** 現在(生成済み)のページの名前です(宣言解決・柱・空白判定用)。 */
	private String pageName;

	/** size:autoの文書既定寸法です(N3/N4——初回nextPageで捕捉)。 */
	private double defaultPageWidth = -1, defaultPageHeight = -1;

	void setPageName(final String pageName) {
		this.pendingPageName = pageName;
	}

	String getPageName() {
		// 境界裁定の比較対象=「これから置く内容のページ名」=pending
		return this.pendingPageName;
	}

	PageBox nextPage() {
		// セグメント窓の刈り込み: 開いている要素だけ残す(M6a)
		this.segment.trimToOpenElements();
		// 名前付きページN2a: このページの名前を確定
		this.pageName = this.pendingPageName;
		// ページスタイル
		// 面(recto/verso)は nextPageSide() が進める。落としたページは面を
		// 消費しないので、進める前の値を覚えておく(discardPage が戻す)
		this.previousPageSide = this.ua.getPassContext().getPageSide();
		this.pageElement = this.imposition.nextPageSide();
		Declaration declaration = this.styleContext.nextPage(this.pageElement, this.pageName);
		CSSStyle pageStyle = CSSStyle.getCSSStyle(this.ua, null, this.pageElement);

		// デフォルトのマージン
		if (this.margins != null) {
			switch (this.margins.length) {
			case 1:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[0]);
				pageStyle.set(Margin.BOTTOM, this.margins[0]);
				pageStyle.set(Margin.LEFT, this.margins[0]);
				break;
			case 2:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[1]);
				pageStyle.set(Margin.BOTTOM, this.margins[0]);
				pageStyle.set(Margin.LEFT, this.margins[1]);
				break;
			case 3:
				pageStyle.set(Margin.TOP, this.margins[1]);
				pageStyle.set(Margin.RIGHT, this.margins[2]);
				pageStyle.set(Margin.BOTTOM, this.margins[3]);
				pageStyle.set(Margin.LEFT, this.margins[2]);
				break;
			case 4:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[1]);
				pageStyle.set(Margin.BOTTOM, this.margins[2]);
				pageStyle.set(Margin.LEFT, this.margins[3]);
				break;
			}
		}

		declaration.applyProperties(pageStyle);

		// ページカウンターリセット
		Value[] resets = CounterReset.get(pageStyle);
		if (resets != null) {
			for (int i = 0; i < resets.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) resets[i];
				String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.reservedCounterWarner.accept(name);
					continue;
				}
				int value = counterSet.getValue();
				this.ua.getPassContext().getCounterScope(0, true).reset(name, value);
			}
		}

		// ページカウンター加算
		Value[] increments = CounterIncrement.get(pageStyle);
		boolean pageIncremented = false;
		if (increments != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < increments.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) increments[i];
				String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.reservedCounterWarner.accept(name);
					continue;
				}
				int delta = counterSet.getValue();
				pc.getCounterScope(0, true).increment(name, delta);
				pageIncremented |= "page".equals(name);
			}
		}
		if (!pageIncremented) {
			// page カウンタはページごとに自動加算される(css-page-3 §6.1)。
			// @page の counter-increment が page を明示した場合はそちらが優先
			this.ua.getPassContext().getCounterScope(0, true).increment("page", 1);
		}
		// 落としたページは番号を消費しない(discardPage が同じ順序で戻す)
		this.appliedPageIncrements = increments;
		this.appliedAutoPageIncrement = !pageIncremented;

		// ルートのスタイルを適用
		if (this.background == null) {
			this.background = Background.NULL_BACKGROUND;
		}

		final BlockParams params = new BlockParams();
		params.flow = this.progression;
		params.fontStyle = pageStyle.getFontStyle();
		params.fontManager = this.ua.getFontManager();
		final LanguageProfile lang = LanguageProfileBundle.getLanguageProfile(pageStyle.getCSSElement().lang);
		params.lineBreakRules = lang.getTextBreakingRules(pageStyle);

		// ページのサイズ(N3/N4: @page sizeがoutput既定を上書きする。
		// size:autoの既定は初回に捕捉した文書既定へ必ず戻す——impositionの
		// 現在値はdrawPageが前ページの寸法へ書き換えるため状態が漏れる)
		if (this.defaultPageWidth <= 0) {
			this.defaultPageWidth = this.imposition.getPageWidth();
			this.defaultPageHeight = this.imposition.getPageHeight();
		}
		// トンボと断ち代(2026-08-02): CSSで明示された場合だけ
		// output.marks / output.trims を上書きする(size:autoと同じ考え方)
		final net.zamasoft.foliojet.css.value.PageMarksValue marks = PageMarks.get(pageStyle);
		if (marks != net.zamasoft.foliojet.css.value.PageMarksValue.UNSPECIFIED) {
			this.imposition.setCrop(marks.isCrop());
			this.imposition.setCross(marks.isCross());
			if ((marks.isCrop() || marks.isCross()) && this.imposition.getTrimTop() == 0
					&& this.imposition.getTrimRight() == 0 && this.imposition.getTrimBottom() == 0
					&& this.imposition.getTrimLeft() == 0) {
				// output.marksがnoneのままだと裁ち口が0にされているので、
				// CSSでトンボを宣言したときは既定(1cm)へ戻す(2026-08-29)。
				// トンボは裁ち口の中に引くので、幅が0だと用紙の外へ出て消える
				final double d = net.zamasoft.pdfg2d.pdf.util.PDFUtils.POINTS_PER_CM;
				this.imposition.setTrims(d, d, d, d);
			}
		}
		final double bleed = PageBleed.get(pageStyle);
		// output.trim-insetが指定されているときは、塗り足しの実体はもう
		// 印刷面の中にある(B-3)。CSSのbleedで裁ち口を広げ直すと二重になる
		if (bleed >= 0 && this.imposition.getTrimInset() == 0) {
			// CSSで塗り足しを宣言したなら、その分だけ仕上り線の外へ描く意思が
			// あるということ(2026-08-29の利用者報告)。断ち代を同じ幅にして、
			// 内容が仕上り線で切り落とされないようにする——以前は断ち代が0のまま
			// だったので、bleedを書いても塗り足しが白いまま出ていた。
			// 裁ち口は塗り足しより狭くしない。ただし**今より狭めない**のも大事で、
			// トンボは裁ち口の中に、塗り足しのさらに外側へ引かれる
			// (PrinterMarksはcuttingMarginの2倍を使う)。裁ち口を塗り足しと
			// 同じ幅まで詰めるとトンボが用紙の外へ出て消える
			final double trim = Math.max(bleed, Math.max(Math.max(this.imposition.getTrimTop(),
					this.imposition.getTrimRight()),
					Math.max(this.imposition.getTrimBottom(), this.imposition.getTrimLeft())));
			this.imposition.setTrims(trim, trim, trim, trim);
			this.imposition.setCuttingMargin(bleed);
		}

		final PageSizeValue pageSize = PageSize.get(pageStyle);
		final double[] resolvedSize = pageSize.resolve(this.defaultPageWidth, this.defaultPageHeight);
		double width = resolvedSize[0];
		double height = resolvedSize[1];

		if ((this.doc.getPageMode() & DocumentBuilder.PAGE_MODE_CONTINUOUS) != 0) {
			if (this.imposition.getBoundSide() == BoundSide.LEFT) {
				// 横書き
				params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.AUTO);
			} else {
				// 縦書き
				params.size = Dimension.create(width, height, LengthType.AUTO, LengthType.ABSOLUTE);
			}
		} else {
			params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		}
		params.overflow = OverflowMode.VISIBLE;

		// マージン
		Value marginTop = Margin.get(pageStyle, Side.TOP);
		Value marginRight = Margin.get(pageStyle, Side.RIGHT);
		Value marginBottom = Margin.get(pageStyle, Side.BOTTOM);
		Value marginLeft = Margin.get(pageStyle, Side.LEFT);
		Insets margin = BoxValueUtils.toInsets(marginTop, marginRight, marginBottom, marginLeft);

		// ページ箱の背景(css-page-3 §3、2026-09-01)。ページ固有の背景は
		// PageBoxが用紙全面へ先に描き、html/bodyから昇格したcanvas背景は
		// 通常のframe背景として余白の内側へ重ねる。枠線は今も引かない
		// (`@page`のborderは未対応)。
		final Background pageBackground = BoxStyleMapper.createBackground(pageStyle);
		params.frame = RectFrame.create(margin, RectBorder.NONE_RECT_BORDER, this.background, Insets.NULL_INSETS);

		this.pageNumber++;
		if (this.maxPageNumber != -1 && this.pageNumber > this.maxPageNumber) {
			short code = MessageCodes.ERROR_OUT_OF_PAGE_LIMIT;
			String[] args = new String[] { String.valueOf(this.maxPageNumber) };
			ua.message(code, args);
			if (UAProps.OUTPUT_PAGE_LIMIT_ABORT.get(ua) == OutputPageLimitAbort.NORMAL) {
				throw new AbortException(AbortException.ABORT_NORMAL);
			}
			throw new AbortException(AbortException.ABORT_FORCE);
		}
		this.ua.message(MessageCodes.INFO_PAGE_NUMBER, String.valueOf(this.pageNumber));
		return new PageBox(params, this.ua, pageBackground);
	}

	/**
	 * このページが<b>紙に何も描かない</b>ので出力しないでよいかを返します
	 * (2026-07-28新設、css-break-3 §4.4。判定の全文はStyleBuilderからの
	 * 移動——本文・{@code @page}背景・固定配置・マージンボックス宣言の
	 * 4種すべてを見る。トンボ・ノンブルは数えない)。
	 */
	private boolean paintsNothing(final PageBox pageBox, final boolean lastPage, final boolean closedByForcedBreak) {
		if (pageBox.isNamedTransitionClosed() && !pageBox.paintsAnything()) {
			// ページ先頭でのページ名遷移により閉じられた白紙ページ(N2b)。
			// 柱の宣言や強制改ページ起点でも落とす——旧名の未確定ページを
			// 新名で作り直す差し替えと等価にする(遷移改ページの後には必ず
			// 遷移先の内容が続くため、0ページのPDFにはならない)
			return true;
		}
		if (this.emittedPages == 0 && (lastPage || closedByForcedBreak)) {
			// **0ページのPDFは作らない**。ただし「まだ1枚も出していない」
			// だけでは落とさない理由にならない(2026-07-29)——後続の
			// ページに内容があるなら、先頭の白紙は落として構わない。
			//
			// 従来は最初の1枚を無条件で残していたため、**内容が2ページ目
			// から始まる文書で1ページ目が白紙のまま出ていた**
			// (掃過 seed 597668 / 1954254。実測では3ページのうち
			// 1ページ目だけが`drawer z=0`のみだった)。
			//
			// `lastPage`は呼び出し元が区別する: `RootBuilder.pageBreak`は
			// 改ページで確定したページなので**後続がある**(false)、
			// `RootBuilder.finish`は文書の最後(true)。
			//
			// `closedByForcedBreak`も残す理由になる——先頭要素の
			// {@code page-break-before:always}は「その前に紙を1枚」という
			// 作者の要求であり、生じた白紙は意図されたものである
			// (`files/unittest/0120-float/float-break-always.html`)。
			// {@code isForcedBreakOrigin}は「強制改ページで**始まった**
			// ページ」を見るので、強制改ページで**閉じられた**先頭ページは
			// そちらでは拾えない。
			return false;
		}
		if (pageBox.isForcedBreakOrigin()) {
			// 作者が意図した白紙
			return false;
		}
		if (pageBox.paintsAnything()) {
			return false;
		}
		// ページマージンボックス(柱・ノンブル)は宣言があれば描くとみなす
		return this.styleContext.pageMarginBoxes(this.pageElement, this.pageName).isEmpty();
	}

	/**
	 * 何も描かないページを取り消します(2026-07-28新設)。
	 *
	 * <p>
	 * <b>ページ番号も面も消費させません。</b> {@link #nextPage()}が進めたもの
	 * だけを、逆順に、そのまま戻します。
	 * </p>
	 */
	private void discardPage() {
		final PassContext pc = this.ua.getPassContext();
		if (this.appliedAutoPageIncrement) {
			pc.getCounterScope(0, true).increment("page", -1);
		}
		if (this.appliedPageIncrements != null) {
			for (int i = 0; i < this.appliedPageIncrements.length; ++i) {
				final CounterSetValue counterSet = (CounterSetValue) this.appliedPageIncrements[i];
				final String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					// nextPage() も加算していない
					continue;
				}
				pc.getCounterScope(0, true).increment(name, -counterSet.getValue());
			}
		}
		pc.setPageSide(this.previousPageSide);
		--this.pageNumber;
	}

	boolean drawPage(final PageBox pageBox, final boolean lastPage, final boolean closedByForcedBreak)
			throws GraphicsException {
		// 何も描かないページは出力しない(css-break-3 §4.4)。判定は
		// imposition.nextPage()(=PDFのページを作る地点)より前に済ませる
		// ——作ってしまってから取り消すのではなく、作らない
		if (this.paintsNothing(pageBox, lastPage, closedByForcedBreak)) {
			this.discardPage();
			return false;
		}
		// ページサイズ決定
		if (UAProps.OUTPUT_EXPAND_WITH_CONTENT.getBoolean(ua)) {
			this.imposition.setPageWidth(pageBox.getVisualWidth());
			this.imposition.setPageHeight(pageBox.getVisualHeight());
		} else {
			this.imposition.setPageWidth(pageBox.getWidth());
			this.imposition.setPageHeight(pageBox.getHeight());
		}
		if (UAProps.OUTPUT_PAPER_WIDTH.getString(ua) == null) {
			this.imposition.fitPaperWidth();
		}
		if (UAProps.OUTPUT_PAPER_HEIGHT.getString(ua) == null) {
			this.imposition.fitPaperHeight();
		}

		if ((this.doc.getPageMode() & DocumentBuilder.PAGE_MODE_CONTINUOUS) != 0) {
			// 自動高さの場合、高さを通知する
			this.ua.message(MessageCodes.INFO_PAGE_HEIGHT, String.valueOf(pageBox.getHeight()));
		}

		// 描画
		final GC gc = this.imposition.nextPage();

		if (UAProps.OUTPUT_EXPAND_WITH_CONTENT.getBoolean(ua)) {
			if (gc != null && pageBox.getVisualWidth() > pageBox.getWidth()) {
				gc.transform(AffineTransform.getTranslateInstance(pageBox.getVisualWidth() - pageBox.getWidth(), 0));
			}
			this.imposition.setPageWidth(pageBox.getWidth());
			this.imposition.setPageHeight(pageBox.getHeight());
			if (UAProps.OUTPUT_PAPER_WIDTH.getString(ua) == null) {
				this.imposition.fitPaperWidth();
			}
			if (UAProps.OUTPUT_PAPER_HEIGHT.getString(ua) == null) {
				this.imposition.fitPaperHeight();
			}
		}

		final AffineTransform marginT;
		GC.State marginState = null;
		if (gc != null) {
			AbsoluteInsets margin = pageBox.getFrame().margin;
			double xoff = margin.left;
			double yoff = margin.top;
			if (xoff != 0 || yoff != 0) {
				marginT = AffineTransform.getTranslateInstance(xoff, yoff);
			} else {
				marginT = null;
			}
			if (marginT != null) {
				marginState = gc.begin();
				gc.transform(marginT);
			}
		} else {
			marginT = null;
		}

		// B-3(2026-07-30): 構造宣言先を表示リスト構築の前に配線する
		// (文書順の走査中に宣言し、描画はz順になっても構造は乱れない)。
		// 欠陥②の修正(2026-07-30): ページ横断レジストリ(this.structContext)
		// も渡し、継続断片が初出時のStructElemへ内容を継ぎ足せるようにする
		if (gc instanceof net.zamasoft.pdfg2d.pdf.gc.PDFGC pdfgc
				&& pdfgc.getPDFGraphicsOutput() instanceof net.zamasoft.pdfg2d.pdf.PDFPageOutput structOut) {
			pageBox.setStructOutput(structOut, this.structContext);
		}
		final Visitor visitor = this.ua.getVisitor(gc);
		visitor.startPage();

		final Drawer drawer = new Drawer(0);

		// フロー
		pageBox.drawFlow(drawer, visitor);

		if (gc != null) {
			// 脚注separator罫線(flow後・fixed前。装飾なのでartifact)
			pageBox.drawFootnoteSeparator(drawer);

			// 固定
			pageBox.drawFixed(drawer, visitor);

			// ページマージンボックス(css-page-3。本文の後に描く=仕様の描画順)
			MarginBoxes.draw(this.ua, this.styleContext, this.pageElement, this.pageName, pageBox, drawer, visitor);

		}
		// NopVisitorもstring-set/named-stringのページ状態を確定する。
		visitor.endPage();

		// 描画処理を非同期で実行
		// PDFでは描画処理は非常に早く終わる
		if (gc != null) {
			DisplayListDumper.dumpPage(drawer, this.pageNumber);
			// 近似描画の報告経路(2822)を載せて描く(2026-08-29)
			drawer.draw(net.zamasoft.foliojet.layout.util.ApproximationGC.wrap(gc, this.ua));
			if (marginState != null) {
				marginState.close();
			}
		}
		// 欠陥②の修正: ページ横断レジストリの清算(このページで継続
		// されなかった要素の宣言を破棄——保持量をページ内要素数に有界化)
		this.structContext.endPage();
		this.imposition.closePage();
		++this.emittedPages;
		return true;
	}

	/**
	 * 面付けを終了し、ページ番号をパス文脈へ保存します
	 * ({@code StyleBuilder.finish()}から呼ばれる)。
	 */
	void finish() throws GraphicsException {
		this.imposition.finish();
		this.ua.getPassContext().setPageNumber(this.pageNumber);
	}
}
