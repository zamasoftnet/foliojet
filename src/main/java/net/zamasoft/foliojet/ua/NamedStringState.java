package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

/**
 * GCPM {@code string-set}/{@code string()}用の名前つき文字列ストアです。
 * {@link SectionState}と同じ「ページ境界でスナップショットする」
 * ライフサイクルを持ちます。
 *
 * <p>代入の先後は呼び出し順(build時解決/draw時完成の実行タイミング)
 * ではなく、常に{@code elementKey}(文書順の安定な通し番号)で判定
 * します。{@code string-set}の値に{@code content()}が含まれる場合は
 * 要素のボックスが確定するdraw時まで解決を遅延するため、同じページ内で
 * {@code content()}を伴う代入と伴わない代入が混在すると、呼び出しが
 * 起きる実際の順序は文書順と一致しません。
 *
 * @author MIYABE Tatsuhiko
 */
public class NamedStringState {
	public static final byte FIRST = 1;
	public static final byte START = 2;
	public static final byte LAST = 3;
	public static final byte FIRST_EXCEPT = 4;

	/** exit value(そのページ終了時点で有効な値、文書全体を通して単調)。 */
	private final Map<String, String> lastValue = new HashMap<String, String>();
	private final Map<String, Long> lastElementKey = new HashMap<String, Long>();

	/** entry value(前ページ終了時点の値、{@link #endPage()}で更新)。 */
	private final Map<String, String> entryValue = new HashMap<String, String>();

	/** そのページで最初に行われた代入の値(ページ境界で{@link #endPage()}によりクリア)。 */
	private final Map<String, String> firstOnPage = new HashMap<String, String>();
	private final Map<String, Long> firstElementKey = new HashMap<String, Long>();

	/**
	 * 名前つき文字列を代入します。
	 *
	 * @param name        {@code string-set}のカスタム識別子
	 * @param value       解決済みの値
	 * @param elementKey  代入元要素の文書順キー(順序判定に使う。呼び出し順は無視する)
	 */
	public void set(String name, String value, long elementKey) {
		Long prevLastKey = this.lastElementKey.get(name);
		if (prevLastKey == null || elementKey > prevLastKey) {
			this.lastValue.put(name, value);
			this.lastElementKey.put(name, elementKey);
		}
		Long prevFirstKey = this.firstElementKey.get(name);
		if (prevFirstKey == null || elementKey < prevFirstKey) {
			this.firstOnPage.put(name, value);
			this.firstElementKey.put(name, elementKey);
		}
	}

	/**
	 * 名前つき文字列を読み出します。未代入の場合は{@code null}。
	 *
	 * @param name 対象の名前
	 * @param mode {@link #FIRST}/{@link #START}/{@link #LAST}/{@link #FIRST_EXCEPT}
	 */
	public String get(String name, byte mode) {
		switch (mode) {
		case LAST:
			return this.lastValue.get(name);
		case FIRST:
			return this.firstOnPage.containsKey(name) ? this.firstOnPage.get(name) : this.entryValue.get(name);
		case START:
			// 本来は「ページ先頭ボックスが代入元の場合のみその値」だが、
			// 判定の複雑さに見合わないため簡略化してentry valueに固定する
			// (CSS-SUPPORT.md参照)。
			return this.entryValue.get(name);
		case FIRST_EXCEPT:
			// firstと同じだが、その値がまさに今のページで新規に代入された場合は空文字列。
			return this.firstOnPage.containsKey(name) ? "" : this.entryValue.get(name);
		default:
			throw new IllegalArgumentException(String.valueOf(mode));
		}
	}

	/**
	 * ページ境界の処理です。exit valueを次ページのentry valueへ引き継ぎ、
	 * 「このページで最初に代入された値」の記録をクリアします。
	 */
	public void endPage() {
		this.entryValue.putAll(this.lastValue);
		this.firstOnPage.clear();
		this.firstElementKey.clear();
	}
}
