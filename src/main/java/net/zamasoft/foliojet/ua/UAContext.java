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
}
