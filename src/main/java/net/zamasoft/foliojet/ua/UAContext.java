package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.pdfg2d.font.FontSourceManager;

/**
 * 現在のUAでの処理に関連する状態です。
 */
public class UAContext {
	private int passCount = 0;

	private final PageRef pageRef = new PageRef();

	private final SelectorFacts selectorFacts = new SelectorFacts();

	private final CounterStyles counterStyles = new CounterStyles();

	private FontSourceManager fsm;
	
	private Map<Object, ImageMap> maps = new HashMap<Object, ImageMap> ();

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

	/**
	 * 著者定義カウンタスタイル({@code @counter-style})の登録簿です
	 * (2026-08-02)。名前からコードへの割り当てを複数パスで保つため、
	 * パスごとに作り直される{@code DocumentContext}ではなくここに置く
	 * ({@link PageRef}と同じ寿命)。
	 */
	public CounterStyles getCounterStyles() {
		return this.counterStyles;
	}
	
	public Map<Object, ImageMap> getImageMaps() {
		return this.maps;
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
