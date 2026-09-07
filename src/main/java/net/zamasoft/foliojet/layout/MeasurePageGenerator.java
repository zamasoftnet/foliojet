package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 測定用のページ生成器です(M2c)。
 *
 * <p>
 * 指定寸法の scratch ページを生成し、描画は何もしません。
 * ソースイベントの範囲を {@link SourceReplayer} でこの生成器へ再生する
 * ことで、実レイアウトによる計測(min/max-content、収まりのプローブ)を
 * ライブの状態に一切触れずに行えます。scratch 再生は新品のボックスを
 * 作るため、旧2パス計測が抱えていた「可変ボックスの共有・一回消費」の
 * 制約(M2 再ステージの原因)はここには存在しません。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class MeasurePageGenerator implements PageGenerator {
	private final UserAgent ua;

	private final BlockParams pageParams;
	private final LayoutSource layoutSource;
	private final boolean countRetainedText;

	private PageBox lastPage;

	private int pageCount = 0;
	private java.util.function.IntSupplier deliveredCharEnd;
	private net.zamasoft.foliojet.layout.segment.BlockParamsTemplate probeTemplate;
	private java.util.Map<Long, Double> footnoteMeasurements;
	private String pendingPageName, pageName;
	private double pageInnerWidth, pageInnerHeight;
	private int emittedPages;
	private boolean namedPageGeometry;
	private java.util.function.BiFunction<String, Integer, FootnotePageProbe.PageGeometry> pageGeometry;
	private java.util.function.Consumer<PageMeasurement> pageObserver;
	private java.util.function.LongConsumer compactionObserver;

	/** draw時点の値。次のページや後着本文で書き換えません。 */
	record PageMeasurement(long generation, String pageName, boolean emitted, double innerWidth, double innerHeight,
			net.zamasoft.foliojet.layout.box.params.WritingMode flow, double h0, java.util.Set<Long> callIds,
			java.util.Map<Long, Double> heights, java.util.Set<Long> unmeasuredIds, boolean lastPage) { }

	void setPageObserver(final java.util.function.Consumer<PageMeasurement> observer) {
		this.pageObserver = observer;
	}

	void setCompactionObserver(final java.util.function.LongConsumer observer) {
		this.compactionObserver = observer;
	}

	@Override
	public void compactLayoutSource(final long watermark) {
		// Bの水位はpinの前進にだけ使う。主ログのcompactはCの要求に限る。
		if (this.compactionObserver != null) this.compactionObserver.accept(watermark);
	}

	@Override
	public String getPageName() {
		return this.pendingPageName;
	}

	@Override
	public void setPageName(final String pageName) {
		this.pendingPageName = pageName;
	}

	/**
	 * 測定用ページ生成器を作ります。
	 *
	 * @param ua       ユーザーエージェント
	 * @param template 書体・書字方向等を引き継ぐ計算済みパラメータ
	 * @param width    scratch ページの幅
	 * @param height   scratch ページの高さ
	 */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height) {
		this(ua, template, width, height, null);
	}

	/** 再生元を借用します。scratch側から追記・compact・closeはしません。 */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height, final LayoutSource layoutSource) {
		this(ua, template, width, height, layoutSource, true);
	}

	/** マージンボックス・runningのミニレイアウトだけは文字会計から除外します。 */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height, final LayoutSource layoutSource, final boolean countRetainedText) {
		this.ua = ua;
		this.layoutSource = layoutSource;
		this.countRetainedText = countRetainedText;
		final BlockParams params = new BlockParams();
		params.fontStyle = template.fontStyle;
		params.fontManager = template.fontManager;
		params.lineBreakRules = template.lineBreakRules;
		params.flow = template.flow;
		params.writingModeVariant = template.writingModeVariant;
		params.direction = template.direction;
		params.unicodeBidi = template.unicodeBidi;
		params.paragraphBidi = template.paragraphBidi;
		params.bidiSemanticAlias = template.bidiSemanticAlias;
		params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		this.pageParams = params;
	}

	/** 生のtee専用。用紙の余白を含まない予約前の版面を複製します。 */
	MeasurePageGenerator(final UserAgent ua,
			final net.zamasoft.foliojet.layout.segment.BlockParamsTemplate template,
			final double width, final double height, final LayoutSource source) {
		this.ua = ua;
		this.layoutSource = source;
		this.countRetainedText = true;
		final BlockParams params = template.materialize();
		params.frame = net.zamasoft.foliojet.layout.box.params.RectFrame.NULL_FRAME;
		params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		params.minSize = Dimension.ZERO_DIMENSION;
		params.maxSize = Dimension.AUTO_DIMENSION;
		params.boxSizing = net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX;
		this.pageParams = params;
		this.probeTemplate = net.zamasoft.foliojet.layout.segment.BlockParamsTemplate.freeze(params);
		this.footnoteMeasurements = new java.util.LinkedHashMap<>();
	}

	/** 未配達のshaperバッファを尾部再生しないため、B自身の現在値を接続します。 */
	public void setDeliveredCharEnd(final java.util.function.IntSupplier deliveredCharEnd) {
		this.deliveredCharEnd = deliveredCharEnd;
	}

	@Override
	public int getDeliveredCharEnd() {
		return this.deliveredCharEnd == null ? Integer.MAX_VALUE : this.deliveredCharEnd.getAsInt();
	}

	void setPageGeometry(final java.util.function.BiFunction<String, Integer, FootnotePageProbe.PageGeometry> geometry) {
		this.pageGeometry = geometry;
	}

	private java.util.function.LongSupplier deliveredEventEnd;

	public void setDeliveredEventEnd(final java.util.function.LongSupplier deliveredEventEnd) {
		this.deliveredEventEnd = deliveredEventEnd;
	}

	@Override
	public long getDeliveredEventEnd() {
		return this.deliveredEventEnd == null ? Long.MAX_VALUE : this.deliveredEventEnd.getAsLong();
	}

	public boolean isFootnoteProbe() {
		return this.probeTemplate != null;
	}

	/** TwoPass本文のMEASURE bind完了後にだけ登録します。番号の解決・本番登録はしません。 */
	public void measureFootnote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox box) {
		if (this.footnoteMeasurements != null && box.getParams().footnoteId >= 0) {
			this.footnoteMeasurements.putIfAbsent(box.getParams().footnoteId,
					net.zamasoft.foliojet.layout.builder.impl.RootBuilder.footnoteBandExtent(box));
		}
	}

	/** 再生可能な入力範囲とpending台帳の両方から外れた注だけを忘れます。 */
	public void forgetFootnote(final long id) {
		if (this.footnoteMeasurements != null) this.footnoteMeasurements.remove(id);
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public boolean isRetainedTextCounted() {
		return this.countRetainedText;
	}

	@Override
	public LayoutSource getLayoutSource() {
		return this.layoutSource;
	}

	public PageBreakMode getPageSide() {
		return PageBreakMode.AUTO;
	}

	public PageBox nextPage() {
		++this.pageCount;
		this.pageName = this.pendingPageName;
		final BlockParams params = this.probeTemplate == null ? this.pageParams : this.probeTemplate.materialize();
		this.namedPageGeometry |= this.pageName != null;
		// 無名だけの既存文書は初回幾何を維持する。名前遷移後は無名へ戻る場合も照会する。
		if (this.pageGeometry != null && this.namedPageGeometry) {
			final var geometry = this.pageGeometry.apply(this.pageName, this.emittedPages);
			params.size = Dimension.create(geometry.width(), geometry.height(), LengthType.ABSOLUTE, LengthType.ABSOLUTE);
			params.flow = geometry.flow();
		}
		this.lastPage = new PageBox(params, this.ua);
		this.pageInnerWidth = this.lastPage.getInnerWidth();
		this.pageInnerHeight = this.lastPage.getInnerHeight();
		return this.lastPage;
	}

	public boolean drawPage(final PageBox page, final boolean lastPage, final boolean closedByForcedBreak) {
		if (!this.isFootnoteProbe()) return true;
		// 描画はしない。本文・強制改頁・最後の一枚によるB自身の出力資格を記録する。
		// 柱等はBに無いため、Cの出力有無と一致するという契約ではない。
		final boolean paints = page.paintsAnything();
		final boolean emitted = !(page.isNamedTransitionClosed() && !paints)
				&& (paints || page.isForcedBreakOrigin() || (this.emittedPages == 0 && lastPage));
		if (emitted) ++this.emittedPages;
		final java.util.Set<Long> calls = net.zamasoft.foliojet.layout.builder.impl.RootBuilder.collectFootnoteCalls(page);
		final java.util.Map<Long, Double> heights = new java.util.LinkedHashMap<>();
		final java.util.Set<Long> unmeasured = new java.util.HashSet<>(calls);
		for (final long id : calls) {
			final Double height = this.footnoteMeasurements.get(id);
			if (height != null) {
				heights.put(id, height);
				unmeasured.remove(id);
			}
		}
		if (this.pageObserver != null) this.pageObserver.accept(new PageMeasurement(this.pageCount, this.pageName, emitted,
				this.pageInnerWidth, this.pageInnerHeight, page.getBlockParams().flow, page.getFootInset(),
				java.util.Set.copyOf(calls), java.util.Map.copyOf(heights), java.util.Set.copyOf(unmeasured), lastPage));
		return emitted;
	}

	/**
	 * 生成されたページ数を返します(収まりのプローブ用)。
	 */
	public int getPageCount() {
		return this.pageCount;
	}

	/**
	 * 最後に生成されたページを返します(内容の実測用)。
	 */
	public PageBox getLastPage() {
		return this.lastPage;
	}

	/** 名前付きページへ遷移したBの現在幅。未遷移時は従来のCの幅を使います。 */
	double namedPageWidth() {
		return this.namedPageGeometry && this.lastPage != null ? this.pageInnerWidth : Double.NaN;
	}
}
