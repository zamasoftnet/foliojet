package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paged SVGのフォントサブセットを、同じセッションの次の変換へ持ち越す控えです
 * (2026-08-29)。
 *
 * <p>
 * サブセットは変換のたびに組み直され、全ページを書き終えてからしか出なかった
 * (実測: 1ページ目が出た4.2秒時点でフォントはHTTP 500、揃うのは10.6秒。
 * {@code docs/history/2026-08-28-paged-svg-streaming.md})。文字サイズを
 * 変えても字の集合は変わらないので、同一入力なら同一のサブセットになる。
 * そこで、組み上げたバイト列と<b>字形の並び</b>(元フォントのGID→サブセット
 * GIDの順)をここに控え、次の変換では
 * </p>
 * <ol>
 * <li>同じ順で符号を割り当て直し(前回のページSVGと同じ私用領域符号になる)、</li>
 * <li>1ページ目より<b>先に</b>前回のバイト列をそのまま出す。</li>
 * </ol>
 * <p>
 * 前回に無かった字形が現れたときだけサブセットは「育ち」、版を1つ進めた
 * 別URI({@code font-0001-2.woff2})で末尾に出し直す。育つ前のページは前の版で
 * 完結しているので、そのままで正しい。育った版は前の版の上位集合であり、
 * 符号は変わらない。
 * </p>
 *
 * <p>
 * 寿命はセッション({@code DirectSession}が持ち、変換ごとに作り直される
 * UAへ{@link net.zamasoft.foliojet.ua.UAContext}経由で渡す)。画像寸法
 * ({@code ImageMetricsCache})が文書ごとにリセットされるのと違い、ここは
 * 文書をまたいで保つ——別の文書でも符号の割り当てが決まるだけで、内容は
 * 実際に使われた字形で決まるため害がない。サブセットは小さい(実測0.1MB)。
 * </p>
 */
public final class PagedSvgFontCarry {
	/** 元フォント・向き・合成斜体で1つのサブセット。 */
	public record Key(String fontName, String mode, boolean oblique) {
	}

	/** 前回組み上げた1サブセットの控え。 */
	public record Entry(int id, int version, int[] gids, byte[] bytes, String sha256) {
	}

	private final Map<Key, Entry> entries = new LinkedHashMap<>();
	private int nextId = 1;

	public synchronized Entry get(final Key key) {
		return this.entries.get(key);
	}

	public synchronized void put(final Key key, final Entry entry) {
		this.entries.put(key, entry);
		this.nextId = Math.max(this.nextId, entry.id() + 1);
	}

	/** 変換をまたいで重複しない番号を払い出します。 */
	public synchronized int allocateId() {
		return this.nextId++;
	}

	public synchronized List<Entry> entries() {
		return new ArrayList<>(this.entries.values());
	}

	public synchronized boolean isEmpty() {
		return this.entries.isEmpty();
	}

	public synchronized void clear() {
		this.entries.clear();
		this.nextId = 1;
	}
}
