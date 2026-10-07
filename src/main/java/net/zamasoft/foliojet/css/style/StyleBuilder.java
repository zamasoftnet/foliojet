package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Params;

import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * @author MIYABE Tatsuhiko
 */
public class StyleBuilder implements PageGenerator, StyleBuildContext {

	/**
	 * 総ページ数カウンタ名。css-page-3 §6.1相当のUA予約カウンタとして扱い、
	 * 著者の{@code counter-reset}/{@code counter-increment}からは保護する
	 * ({@link #isReservedCounterName(String)}参照)。
	 */
	private static final String PAGES_COUNTER_NAME = "pages";

	static boolean isReservedCounterName(String name) {
		return PAGES_COUNTER_NAME.equalsIgnoreCase(name);
	}

	private final UserAgent ua;

	private final DocumentBuilder doc;
	private final Imposition imposition;
	private StyleContext styleContext;
	private CSSStyle currentStyle;

	private FlowBlockBox htmlRootBlock = null;
	private boolean rightSide = false;
	private boolean inBody = false;
	private boolean inTextBlock = false;

	/**
	 * 本流のスタイル窓の件数です。スタイルと文字への参照は保持しません。
	 * 再生にはsinkのLayoutSourceを使い、疑似要素・生成内容も凍結済みです。
	 */
	private final Segment segment = new Segment();

	/**
	 * M6b v3 のレイアウトソースプロトコルtee(記録+docへの引き渡し)。
	 * 記録の契約・{@code LayoutSource}の寿命は{@link RecordingLayoutSink}参照
	 * (StyleBuilder解体・増分1で抽出、2026-07-30)。
	 */
	private final RecordingLayoutSink sink;

	/**
	 * ページのライフサイクル(作成・@pageカウンタ・白紙判定と
	 * 巻き戻し・描画・面付け終了)。StyleBuilder解体・増分2で抽出
	 * (2026-07-30)。PageGeneratorの実装は引き続きStyleBuilderで、
	 * ページ系のメソッドはここへ委譲する。
	 */
	private final PageSequence pageSequence;

	/**
	 * CSS計算値→Params/Pos/RectFrameの写像群。StyleBuilder解体・
	 * 増分3で抽出(2026-07-30)。
	 */
	private final BoxStyleMapper mapper;

	/**
	 * displayによるボックスdispatchと匿名表補完。StyleBuilder解体・
	 * 増分4aで抽出(2026-07-30。逐語移動——匿名表の再帰は残存し、
	 * 反復化は増分4b。状態は{@link StyleBuildContext}経由で共有)。
	 */
	private final StyleBoxEmitter emitter;

	/**
	 * スタイルイベントの状態機械(カウンタ・string-set・マーカー・quotes・
	 * generated content・::first-letter)。StyleBuilder解体・増分5で抽出
	 * (2026-07-30、逐語移動)。
	 */
	private final StyleEventMachine eventMachine;

	/**
	 * レイアウトソースログを返します(M6b v3)。
	 */
	public LayoutSource getLayoutSource() {
		return this.sink.source();
	}

	@Override
	public long getDeliveredEventEnd() {
		return this.sink.deliveredEventEnd();
	}

	@Override
	public boolean isFootnotePageProbeEnabled() {
		return this.ua.getUAContext().getFootnoteArea().isPageBand()
				&& !this.ua.getUAContext().getFootnoteArea().isHeightFixed()
				&& this.pageSequence.getProgression().isVertical();
	}

	@Override
	public net.zamasoft.foliojet.layout.FootnotePageProbeReport getFootnotePageProbeReport(final long generation) {
		return this.sink.report(generation);
	}

	@Override
	public boolean isFootnotePageProbeFinished() {
		return this.sink.probeFinished();
	}

	public void compactLayoutSource(final long watermark) {
		this.sink.compact(watermark);
	}

	/**
	 * レイアウトソースのspillストア(一時ファイル)を閉じます
	 * (E-6増分3b-2)。変換の終了経路——成功・例外を問わずformatterの
	 * finallyから{@code CSSProcessor.dispose()}経由で呼ばれる。冪等。
	 */
	public void closeLayoutSource() {
		this.sink.close();
	}

	public StyleBuilder(StyleContext styleContext, UserAgent ua, Imposition imposition) {
		this.styleContext = styleContext;
		this.ua = ua;
		this.imposition = imposition;
		this.doc = new DocumentBuilder(this);
		// E-6増分3b-2: text payloadのspill予算(bytes)はsinkが注入する
		this.sink = new RecordingLayoutSink(this.doc, UAProps.PROCESSING_TEXT_SPILL_BUDGET.getLong(ua));
		this.sink.setAssignments(ua.getPassContext().getRunningRegistry());

		byte pageMode = 0;
		// 自動高さ
		if (UAProps.OUTPUT_AUTO_HEIGHT.getBoolean(ua)) {
			pageMode |= DocumentBuilder.PAGE_MODE_CONTINUOUS;
		}

		// 改ページ禁止
		if (UAProps.OUTPUT_NO_PAGE_BREAK.getBoolean(ua)) {
			pageMode |= DocumentBuilder.PAGE_MODE_NO_BREAK;
		}
		this.doc.setPageMode(pageMode);

		// ページ幅・高さ・マージン・最大ページ数の初期化は
		// PageSequenceのコンストラクタへ移動(増分2、2026-07-30。
		// 警告メッセージの順序も従来と同一)
		this.pageSequence = new PageSequence(ua, styleContext, imposition, this.doc, this.segment,
				this::warnReservedCounter);
		this.mapper = new BoxStyleMapper(ua, styleContext);
		this.emitter = new StyleBoxEmitter(this, this.sink, this.mapper, this.pageSequence, ua, imposition);
		this.eventMachine = new StyleEventMachine(this, this.segment, this.sink, this.mapper, this.emitter,
				this.pageSequence, ua, styleContext);
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public CSSElement getPageElement() {
		return this.pageSequence.getPageElement();
	}

	public CSSStyle getCurrentStyle() {
		final CSSStyle captured = this.eventMachine == null ? null : this.eventMachine.capturedStyle();
		return captured == null ? this.currentStyle : captured;
	}

	public void startStyle(final CSSStyle style) {
		this.eventMachine.startStyle(style);
	}

	public void characters(final int charOffset, final char[] ch, final int off, final int len) {
		this.eventMachine.characters(charOffset, ch, off, len);
	}

	public void endStyle() {
		this.eventMachine.endStyle();
	}

	@Override
	public void checkMarker() {
		this.eventMachine.checkMarker();
	}

	/** PageSequenceの予約カウンタ警告の委譲先(実体は増分5で機械側へ)。 */
	void warnReservedCounter(final String name) {
		this.eventMachine.warnReservedCounter(name);
	}

	public PageBreakMode getPageSide() {
		return this.pageSequence.getPageSide();
	}

	public PageBox nextPage() {
		return this.pageSequence.nextPage();
	}

	@Override
	public void pageStarted(final PageBox page, final double innerWidth, final double innerHeight) {
		this.sink.pageStarted(page, innerWidth, innerHeight, this.pageSequence.getPageName(), this.pageSequence::footnotePageGeometry);
	}

	@Override
	public String getPageName() {
		return this.pageSequence.getPageName();
	}

	@Override
	public void setPageName(String pageName) {
		this.pageSequence.setPageName(pageName);
	}

	public boolean drawPage(final PageBox pageBox, final boolean lastPage, final boolean closedByForcedBreak)
			throws GraphicsException {
		return this.pageSequence.drawPage(pageBox, lastPage, closedByForcedBreak);
	}

	public void finish() throws GraphicsException {
		this.sink.finishProbes();
		this.doc.end();
		this.pageSequence.finish();
		// E-6増分3b-2: 最終ページ確定後はソース再生が発生しないため、
		// spillストアの一時ファイルをここで早期解放する(例外経路は
		// formatterのfinally→CSSProcessor.dispose→closeLayoutSourceが清算)
		this.sink.close();
	}

	// ---- StyleBuildContext(増分4a)——状態の物理置き場は当面ここのまま ----

	// getCurrentStyle()は既存のpublicメソッドを流用(StyleBuildContext実装)

	@Override
	public void setCurrentStyle(final CSSStyle style) {
		this.currentStyle = style;
	}

	@Override
	public FlowBlockBox getHtmlRootBlock() {
		return this.htmlRootBlock;
	}

	@Override
	public void setHtmlRootBlock(final FlowBlockBox box) {
		this.htmlRootBlock = box;
	}

	@Override
	public boolean isInBody() {
		return this.inBody;
	}

	@Override
	public void setInBody(final boolean inBody) {
		this.inBody = inBody;
	}

	@Override
	public boolean isInTextBlock() {
		return this.inTextBlock;
	}

	@Override
	public void setInTextBlock(final boolean inTextBlock) {
		this.inTextBlock = inTextBlock;
	}

	@Override
	public boolean isRightSide() {
		return this.rightSide;
	}

	@Override
	public void setRightSide(final boolean rightSide) {
		this.rightSide = rightSide;
	}
}
