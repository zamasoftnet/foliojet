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
	/**
	 * 元フォント・向き・合成斜体で1つのサブセット。
	 *
	 * <p>
	 * {@code document}はサブセットの範囲になる文書(EPUBのspine項目のパス、
	 * 単一の文書なら空)。項目ごとにサブセットを持つ(2026-09-02)ので、
	 * 持ち越しも項目ごとに引く——同じフォントでも章が違えば字形の並びが違う。
	 * </p>
	 */
	public record Key(String document, String fontName, String mode, boolean oblique) {
	}

	/** 前回組み上げた1サブセットの控え。 */
	public record Entry(int id, int version, int[] gids, byte[] bytes, String sha256) {
	}

	private final Map<Key, Entry> entries = new LinkedHashMap<>();
	/**
	 * 文書ごとの次の番号。<b>番号の空間は文書(EPUBの項目)ごと</b>である
	 * (2026-09-02)——項目は自分の{@code assets/fonts/}を持つので、重ならなければ
	 * ならないのはその中だけ。全体で1つの採番にすると、並列に組んだとき
	 * 項目のフォントの番号が走った順で変わり、出力が非決定的になる。
	 */
	private final Map<String, Integer> nextIds = new java.util.HashMap<>();

	public synchronized Entry get(final Key key) {
		return this.entries.get(key);
	}

	public synchronized void put(final Key key, final Entry entry) {
		this.entries.put(key, entry);
		this.nextIds.merge(key.document(), entry.id() + 1, Math::max);
	}

	/** その文書の中で、変換をまたいで重複しない番号を払い出します。 */
	public synchronized int allocateId(final String document) {
		final int id = this.nextIds.getOrDefault(document, 1);
		this.nextIds.put(document, id + 1);
		return id;
	}

	public synchronized List<Entry> entries() {
		return new ArrayList<>(this.entries.values());
	}

	/** 文書(EPUBの項目、単一なら空)の控えだけ。 */
	public synchronized List<Entry> entries(final String document) {
		final List<Entry> list = new ArrayList<>();
		for (final Map.Entry<Key, Entry> e : this.entries.entrySet()) {
			if (e.getKey().document().equals(document)) {
				list.add(e.getValue());
			}
		}
		return list;
	}

	public synchronized boolean isEmpty() {
		return this.entries.isEmpty();
	}

	public synchronized void clear() {
		this.entries.clear();
		this.nextIds.clear();
	}
}
