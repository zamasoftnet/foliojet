package net.zamasoft.foliojet.driver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An HTTP response cache shared across conversions (2026-08-10).
 *
 * <p>
 * Motivated by eliminating the delay of fetching the same external resources on every conversion.
 * Measurements showed that {@code @import url(https://fonts.googleapis.com/...)} in head CSS
 * made a network round trip for every conversion and caused most of the perceived startup delay
 * (law3, 2026-08-09). Blocking retrieval with {@code input.exclude} was rejected because
 * it degraded output by losing fonts (owner's decision).
 * </p>
 *
 * <h2>Safety conditions (what must never be cached)</h2>
 *
 * <p>
 * The daemon handles conversions for multiple users, so correctness requires
 * <b>user-specific responses never to leak to another user</b>. Checks occur in two stages:
 * </p>
 *
 * <ul>
 * <li><b>Request side</b> ({@code MyHttpSourceResolver.resolve}): requests with authentication
 * (an Authorization header or credentials matching the host), or outgoing cookies,
 * are ineligible for caching from the outset.</li>
 * <li><b>Response side</b> ({@code MyHttpSourceResolver.MyHttpSource}): does not store non-200 responses,
 * responses with {@code Set-Cookie}, {@code Cache-Control} containing no-store/no-cache/private,
 * or {@code Vary: *}.</li>
 * </ul>
 *
 * <p>
 * The key is <b>URI + all outgoing headers</b>. Different User-Agent, Referer, or administrator-defined
 * custom headers produce separate entries, conservatively handling servers whose responses vary
 * with {@code Vary} (e.g., Referer-based hotlink protection). Headers not sent remain absent,
 * so only outgoing headers need to be included in the key.
 * </p>
 *
 * <h2>Freshness</h2>
 *
 * <p>
 * Records the response's {@code max-age} on storage. On lookup, discards entries older than
 * the shorter of the caller's TTL ({@code input.http.cache.ttl}) and max-age.
 * Evaluates TTL at lookup time so that each conversion uses its own setting correctly,
 * even when TTL settings differ across conversions.
 * </p>
 *
 * <h2>Capacity</h2>
 *
 * <p>
 * An LRU with a 4 MB per-entry limit and a 64 MB total limit. Bodies exceeding the limit
 * pass through without storage (the caller decides).
 * </p>
 */
final class HttpResponseCache {

	private HttpResponseCache() {
		// unused
	}

	/** The per-entry body limit (larger bodies are not stored). */
	static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;

	/** The total limit for all entries (evicts the oldest entries when exceeded). */
	static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;

	/** A cached response (body contains decompressed bytes). */
	record Entry(byte[] body, String mimeType, String encoding, long lastModified, long storedAtMillis,
			long maxAgeSeconds) {

		/** Returns true if the entry is still fresh under the caller's TTL (seconds). */
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
	 * Returns a fresh entry. Discards expired entries here and returns {@code null}.
	 *
	 * @param key        the cache key (URI + outgoing headers)
	 * @param ttlSeconds the caller's TTL (seconds)
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

	/** Stores an entry (evicts oldest entries when capacity is exceeded). */
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

	/** For tests only: discards all entries. */
	static synchronized void clear() {
		ENTRIES.clear();
		totalBytes = 0;
	}
}
