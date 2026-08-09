package net.zamasoft.foliojet.driver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 変換をまたぐHTTP応答キャッシュです(2026-08-10)。
 *
 * <p>
 * 動機は「毎変換、同じ外部リソースを取り直す」遅延の解消——実測では
 * ヘッドCSSの{@code @import url(https://fonts.googleapis.com/...)}が
 * 変換のたびにネットワーク往復し、開始までの体感遅延の主因だった
 * (law3、2026-08-09)。取得を{@code input.exclude}で塞ぐ案は
 * フォント消失という劣化を招くため却下済み(オーナー判断)。
 * </p>
 *
 * <h2>安全条件(何を絶対にキャッシュしないか)</h2>
 *
 * <p>
 * デーモンは複数の利用者の変換を同居させるため、<b>利用者固有の応答が
 * 別の利用者へ漏れない</b>ことが正しさの条件になる。判定は2段:
 * </p>
 *
 * <ul>
 * <li><b>要求側</b>({@code MyHttpSourceResolver.resolve}): 認証情報
 * (Authorizationヘッダ・当該ホストに一致する資格情報)または送信される
 * Cookieを伴う要求は、最初からキャッシュ対象にしない。</li>
 * <li><b>応答側</b>({@code MyHttpSourceResolver.MyHttpSource}): 200以外、
 * {@code Set-Cookie}付き、{@code Cache-Control}に
 * no-store/no-cache/private を含む応答、{@code Vary: *}は保存しない。</li>
 * </ul>
 *
 * <p>
 * キーは<b>URI+送信ヘッダ全体</b>。User-Agent・Referer・管理者設定の
 * カスタムヘッダが違えば別エントリになるため、{@code Vary}で応答が
 * 変わるサーバー(hotlink保護のReferer判定等)にも安全側で働く
 * (送らないヘッダは常に送らないので、キーに含める必要があるのは
 * 送るものだけ)。
 * </p>
 *
 * <h2>鮮度</h2>
 *
 * <p>
 * 保存時に応答の{@code max-age}を記録し、参照時に「呼び出し側のTTL
 * ({@code input.http.cache.ttl})とmax-ageの短い方」を超えたエントリは
 * 捨てる。TTLを参照時に評価するのは、変換ごとにTTL設定が違っても
 * それぞれの設定で正しく判定するため。
 * </p>
 *
 * <h2>容量</h2>
 *
 * <p>
 * エントリ4MB・全体64MBのLRU。上限を超える本文は保存せず素通しする
 * (呼び出し側が判断)。
 * </p>
 */
final class HttpResponseCache {

	private HttpResponseCache() {
		// unused
	}

	/** 1エントリの本文上限(これを超える本文は保存しない)。 */
	static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;

	/** 全エントリ合計の上限(超過分は古い順に捨てる)。 */
	static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;

	/** キャッシュされた応答です(本文は解凍済みバイト列)。 */
	record Entry(byte[] body, String mimeType, String encoding, long lastModified, long storedAtMillis,
			long maxAgeSeconds) {

		/** 呼び出し側TTL(秒)の下で、今なお新鮮ならtrueを返します。 */
		boolean isFresh(final int ttlSeconds, final long nowMillis) {
			long limit = ttlSeconds;
			if (this.maxAgeSeconds >= 0 && this.maxAgeSeconds < limit) {
				limit = this.maxAgeSeconds;
			}
			return (nowMillis - this.storedAtMillis) / 1000 < limit;
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>(64, 0.75f, true);
	private static long totalBytes = 0;

	/**
	 * 新鮮なエントリを返します。期限切れはこの場で捨てて{@code null}。
	 *
	 * @param key        キャッシュキー(URI+送信ヘッダ)
	 * @param ttlSeconds 呼び出し側のTTL(秒)
	 */
	static synchronized Entry get(final String key, final int ttlSeconds) {
		final Entry entry = ENTRIES.get(key);
		if (entry == null) {
			return null;
		}
		if (!entry.isFresh(ttlSeconds, System.currentTimeMillis())) {
			ENTRIES.remove(key);
			totalBytes -= entry.body().length;
			return null;
		}
		return entry;
	}

	/** エントリを保存します(容量超過分は古い順に追い出す)。 */
	static synchronized void put(final String key, final Entry entry) {
		if (entry.body().length > MAX_ENTRY_BYTES) {
			return;
		}
		final Entry old = ENTRIES.remove(key);
		if (old != null) {
			totalBytes -= old.body().length;
		}
		ENTRIES.put(key, entry);
		totalBytes += entry.body().length;
		final java.util.Iterator<Map.Entry<String, Entry>> it = ENTRIES.entrySet().iterator();
		while (totalBytes > MAX_TOTAL_BYTES && it.hasNext()) {
			final Map.Entry<String, Entry> eldest = it.next();
			totalBytes -= eldest.getValue().body().length;
			it.remove();
		}
	}

	/** テスト専用: 全エントリを破棄します。 */
	static synchronized void clear() {
		ENTRIES.clear();
		totalBytes = 0;
	}
}
