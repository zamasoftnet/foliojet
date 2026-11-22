package net.zamasoft.foliojet.driver;

import junit.framework.TestCase;

/**
 * The HTTP response cache is bounded whatever the size of the bodies. Only the bodies counted toward the total limit,
 * so responses with empty bodies (unique URLs that answer with an empty style sheet) were never evicted.
 */
public class HttpResponseCacheTest extends TestCase {
	private static final int TTL_SECONDS = 3600;

	@Override
	protected void setUp() {
		HttpResponseCache.clear();
	}

	@Override
	protected void tearDown() {
		HttpResponseCache.clear();
	}

	private static String key(final int i) {
		return "https://qa.invalid/empty.css?" + i;
	}

	public void testEmptyBodiesAreEvictedToo() {
		// More entries than the total limit holds when each one is charged at least 200 bytes (its key, the map entry
		// and the record take more than that)
		final int count = (int) (HttpResponseCache.MAX_TOTAL_BYTES / 200);
		final long now = System.currentTimeMillis();
		for (int i = 0; i < count; ++i) {
			HttpResponseCache.put(key(i), new HttpResponseCache.Entry(new byte[0], "text/css", null, 0L, now, 3600L));
		}
		assertNull("the oldest entry with an empty body was never evicted",
				HttpResponseCache.get(key(0), TTL_SECONDS));
		assertNotNull("the newest entry is cached", HttpResponseCache.get(key(count - 1), TTL_SECONDS));
	}

	public void testAnEntryWithABodyIsStillCached() {
		final byte[] body = new byte[1024];
		HttpResponseCache.put(key(0),
				new HttpResponseCache.Entry(body, "text/css", null, 0L, System.currentTimeMillis(), 3600L));
		final HttpResponseCache.Entry entry = HttpResponseCache.get(key(0), TTL_SECONDS);
		assertNotNull(entry);
		assertSame(body, entry.body());
	}
}
