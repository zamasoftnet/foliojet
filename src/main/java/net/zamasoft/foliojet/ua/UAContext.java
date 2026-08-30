package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.font.FontFeatureValues;
import net.zamasoft.foliojet.css.font.FontPaletteValues;
import net.zamasoft.pdfg2d.font.FontSourceManager;

/**
 * 現在のUAでの処理に関連する状態です。
 */
public class UAContext {
	private int passCount = 0;

	private final PageRef pageRef = new PageRef();

	private final SelectorFacts selectorFacts = new SelectorFacts();

	private final ContainerFacts containerFacts = new ContainerFacts();

	private final ImageMetricsCache imageMetrics = new ImageMetricsCache();

	private final CounterStyles counterStyles = new CounterStyles();

	private final FontFeatureValues fontFeatureValues = new FontFeatureValues();

	private final FontPaletteValues fontPaletteValues = new FontPaletteValues();

	private net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry pagedSvgFontCarry = new net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry();

	private FontSourceManager fsm;
	
	private Map<Object, ImageMap> maps = new HashMap<Object, ImageMap> ();

	/**
	 * この変換で既に報告した近似描画の鍵(2026-08-29、
	 * {@code ApproximationGC}参照)。同じ文書で同じ近似(プロパティ×内容)を
	 * 何度も警告しないためで、変換1回(=UA1つ)の寿命で持つ。
	 */
	private final java.util.Set<String> reportedApproximations = new java.util.HashSet<String>();

	public FontSourceManager getFontSourceManager() {
		return this.fsm;
	}

	public void setFontSourceManager(FontSourceManager fsm) {
		this.fsm = fsm;
	}

	public int getPassCount() {
		return this.passCount;
	}

	public void setPassCount(int passCount) {
		this.passCount = passCount;
	}

	public PageRef getPageRef() {
		return this.pageRef;
	}

	public SelectorFacts getSelectorFacts() {
		return this.selectorFacts;
	}

	/** {@code @container}クエリのための要素の事実(2026-08-15段4)。 */
	public ContainerFacts getContainerFacts() {
		return this.containerFacts;
	}

	/**
	 * 画像の固有寸法のキャッシュです(2026-08-16)。同じ画像の重複出現と
	 * パスの繰り返しで、資源を開き直してヘッダを読み直すのを避けます。
	 */
	public ImageMetricsCache getImageMetrics() {
		return this.imageMetrics;
	}

	/**
	 * 著者定義カウンタスタイル({@code @counter-style})の登録簿です
	 * (2026-08-02)。名前からコードへの割り当てを複数パスで保つため、
	 * パスごとに作り直される{@code DocumentContext}ではなくここに置く
	 * ({@link PageRef}と同じ寿命)。
	 */
	public CounterStyles getCounterStyles() {
		return this.counterStyles;
	}

	/** 複数の組版パスで共有する{@code @font-feature-values}登録簿です。 */
	public FontFeatureValues getFontFeatureValues() {
		return this.fontFeatureValues;
	}

	/**
	 * 複数の組版パスで共有する{@code @font-palette-values}登録簿です。
	 * 定義は名前解決にだけ使い、描画には反映しません。
	 */
	public FontPaletteValues getFontPaletteValues() {
		return this.fontPaletteValues;
	}
	
	public Map<Object, ImageMap> getImageMaps() {
		return this.maps;
	}

	/** 報告済みの近似描画の鍵({@code ApproximationGC.report}が使う)。 */
	public java.util.Set<String> getReportedApproximations() {
		return this.reportedApproximations;
	}

	/**
	 * Paged SVGのフォントサブセットの持ち越し(2026-08-29)。UAは変換ごとに
	 * 作り直されるので、実体はセッション({@code DirectSession})が持ち、
	 * 変換の開始時にここへ渡す。同じ本を文字サイズだけ変えて組み直すときに、
	 * 前回のサブセットを1ページ目より先に出すため。
	 */
	public net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry getPagedSvgFontCarry() {
		return this.pagedSvgFontCarry;
	}

	public void setPagedSvgFontCarry(final net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry carry) {
		this.pagedSvgFontCarry = carry;
	}

	/**
	 * パスをまたいで持ち越すスタイルシートです(2026-08-08)。
	 * <p>
	 * 1パスのストリーミングでは、文書の後方(body内)に現れる
	 * {@code <style>}は前方の要素へ遡及適用できない。Nuxt等のSSRは
	 * コンポーネントのスタイルをbody内へ挿すため、ヘッダなど前方の
	 * 内容がほぼ素のHTMLで組まれてしまう(metro.tokyo.lg.jpで発覚)。
	 * そこで{@code processing.pass-count>=2}のときは、前のパス
	 * (STRUCTURE_SCANを含む)で収集したスタイルシートをここに保持し、
	 * 次のパスが最初から全規則を適用できるようにする
	 * ({@link SelectorFacts}と同じ寿命管理——STRUCTURE_SCAN開始と
	 * 単一パス変換(DOCUMENT)の開始でリセット)。後続パスの再収集で
	 * 同じ規則が重複追加されるが、同一規則の重複はカスケードの結果を
	 * 変えない(後勝ちが同じ値を選ぶだけ)。
	 */
	private net.zamasoft.foliojet.css.CSSStyleSheet carriedStyleSheet;

	public net.zamasoft.foliojet.css.CSSStyleSheet getCarriedStyleSheet() {
		return this.carriedStyleSheet;
	}

	public void setCarriedStyleSheet(net.zamasoft.foliojet.css.CSSStyleSheet styleSheet) {
		this.carriedStyleSheet = styleSheet;
	}
}
