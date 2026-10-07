package net.zamasoft.foliojet.driver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.zamasoft.foliojet.ua.HttpStatusSource;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.SourceValidity;
import net.zamasoft.zstream.resolver.util.AbstractSource;

class MyHttpSourceResolver implements SourceResolver {
	private int connectionTimeout = 0;
	private int requestTimeout = 0;
	private String proxyHost = null;
	private int proxyPort = -1;
	private final CookieManager cookieManager = new CookieManager();
	private final List<HttpCredential> credentials = new ArrayList<HttpCredential>();
	private boolean preemptiveAuth = false;
	private int cacheTtl = 0;
	protected URI refURI = null;

	protected List<Entry<String, String>> headers = null;
	private HttpClient httpClient = null;
	private ExecutorService executor = null;

	public void setReferer(URI refURI) {
		this.refURI = refURI;
	}

	public void addHeader(String name, String value) {
		if (this.headers == null) {
			this.headers = new ArrayList<Entry<String, String>>();
		}
		this.headers.add(new SimpleImmutableEntry<String, String>(name, value));
	}

	public void setConnectionTimeout(int timeout) {
		this.connectionTimeout = timeout;
	}

	/**
	 * The TTL (seconds) of the HTTP response cache shared across conversions. Zero disables caching.
	 * {@link HttpResponseCache} documents the safety conditions and design.
	 */
	public void setCacheTtl(int cacheTtl) {
		this.cacheTtl = cacheTtl;
	}

	public void setRequestTimeout(int timeout) {
		this.requestTimeout = timeout;
	}

	public void setProxy(String host, int port) {
		this.proxyHost = host;
		this.proxyPort = port;
	}

	public void addAuthentication(String host, int port, String user, String password) {
		this.credentials.add(new HttpCredential(host, port, user, password));
	}

	public void setPreemptiveAuthentication(boolean preemptiveAuth) {
		this.preemptiveAuth = preemptiveAuth;
	}

	public void addCookie(String domain, String path, String name, String value) {
		HttpCookie cookie = new HttpCookie(name, value);
		cookie.setDomain(domain);
		cookie.setPath(path);
		this.cookieManager.getCookieStore().add(URI.create("http://" + domain), cookie);
	}

	/**
	 * The check for whether a redirect target may be fetched. {@code null} means no recheck.
	 *
	 * <p>
	 * When present, <b>follows 3xx redirects itself and applies the same check at every hop</b>,
	 * instead of letting {@link HttpClient} follow them. Delegating would let an allowed public host
	 * redirect into the server's internal network without any check along the way
	 * (2026-09-08).
	 * </p>
	 */
	private java.util.function.Predicate<URI> redirectGuard = null;

	/**
	 * Whether to recheck redirect targets. <b>Evaluated lazily</b>: the ACL is determined
	 * only as request configuration progresses, so it is unknown when the resolver is created.
	 */
	private java.util.function.BooleanSupplier revalidateRedirects = () -> false;

	void setRedirectGuard(final java.util.function.Predicate<URI> guard,
			final java.util.function.BooleanSupplier revalidate) {
		this.redirectGuard = guard;
		this.revalidateRedirects = revalidate;
	}

	private boolean revalidating() {
		return this.redirectGuard != null && this.revalidateRedirects.getAsBoolean();
	}

	/** The maximum redirects to follow. Ensures termination even if a loop is created. */
	private static final int MAX_REDIRECTS = 5;

	protected HttpClient createHttpClient(ExecutorService executor) {
		HttpClient.Builder builder = HttpClient.newBuilder();
		builder.executor(executor);
		// **Always follow redirects ourselves.** A single HttpClient is created and reused here,
		// so choosing NORMAL / NEVER from the state at creation would let an existing
		// NORMAL client follow redirects even after an ACL is added.
		// Following them ourselves allows a check at every hop (followRedirects()).
		builder.followRedirects(HttpClient.Redirect.NEVER);
		if (this.connectionTimeout > 0) {
			builder.connectTimeout(Duration.ofMillis(this.connectionTimeout));
		}
		if (this.proxyHost != null) {
			builder.proxy(ProxySelector.of(new InetSocketAddress(this.proxyHost, this.proxyPort)));
		} else {
			// **Without an explicit setting, the JVM's default proxy is used.** Measured on 2026-09-08:
			// setting http.proxyHost routes even newBuilder().build() through it.
			// (Moreover, client.proxy() returns empty, so its getter cannot reveal this.)
			// The ACL checks the request URI's host, so a default proxy creates
			// a mismatch between the checked destination and the actual connection.
			builder.proxy(HttpClient.Builder.NO_PROXY);
		}
		builder.cookieHandler(this.cookieManager);
		if (!this.credentials.isEmpty()) {
			builder.authenticator(new Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					for (HttpCredential credential : credentials) {
						if (credential.matches(this.getRequestingHost(), this.getRequestingPort())) {
							return new PasswordAuthentication(credential.user, credential.password.toCharArray());
						}
					}
					return null;
				}
			});
		}
		return builder.build();
	}

	private synchronized HttpClient httpClient() {
		if (this.httpClient == null) {
			this.executor = Executors.newVirtualThreadPerTaskExecutor();
			this.httpClient = this.createHttpClient(this.executor);
		}
		return this.httpClient;
	}

	/**
	 * The maximum concurrent prefetch requests. HTTP/2 multiplexes them over one connection.
	 * Measured (Wikipedia, about 100 images, 24.3 s baseline): 6.7–7.4 s at 8, 6.4–6.7 s at 16;
	 * beyond this, bandwidth and RTT dominate.
	 */
	private static final int PREFETCH_PARALLELISM = 12;

	/**
	 * <b>The maximum concurrent fetches per host</b> (2026-08-28). A global limit alone
	 * still sends a burst to one site, triggering its rate limit. Measured: 16 concurrent requests
	 * from the production server to {@code upload.wikimedia.org} produced six <b>HTTP 429</b> responses.
	 * Even the required stylesheet fetch failed as collateral damage, aborting conversion.
	 * Limit concurrency to roughly the browser connection count (around 6).
	 */
	private static final int PREFETCH_PARALLELISM_PER_HOST = 4;

	/** Prevents delayed prefetch registration after close() (avoids contamination across sessions). */
	private volatile boolean prefetchClosed;

	/** The maximum URIs to attempt prefetching in one conversion session (limits runaway or excessive fetching). */
	private static final int PREFETCH_MAX_URIS = 256;

	/**
	 * In-flight prefetches. The key is the request URI (HttpClient follows redirects,
	 * so identical logical requests join here). Always removes the entry and completes its Future
	 * on completion, failure, or cancellation: resolve() awaits it here, and leaving it incomplete
	 * would hang the actual request.
	 */
	private final ConcurrentHashMap<URI, Inflight> prefetching = new ConcurrentHashMap<>();

	/**
	 * One in-flight prefetch. {@code started} means <b>the HTTP request has actually started</b>,
	 * distinguishing it from queued work. Joining a queued prefetch makes an actual request
	 * slower than serial fetching; if the prefetch then fails after the wait, even a resource
	 * that should have succeeded is lost (2026-08-28, observed in production).
	 */
	private record Inflight(CompletableFuture<Void> future, java.util.concurrent.atomic.AtomicBoolean started) {
		Inflight() {
			this(new CompletableFuture<>(), new java.util.concurrent.atomic.AtomicBoolean());
		}
	}

	/** Limits concurrent fetches per host. */
	private final ConcurrentHashMap<String, Semaphore> hostSlots = new ConcurrentHashMap<>();

	/**
	 * Hosts that returned rate limits (429/503). Stops further prefetching for those hosts.
	 * It defeats the purpose if speculative fetches upset the server and lose required fetches too.
	 */
	private final java.util.Set<String> throttledHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * A session-local store of prefetch results (keyed by request URI). Kept separately from
	 * the process-wide {@link HttpResponseCache} because Set-Cookie on the main-document response
	 * (e.g., Wikipedia's GeoIP) makes all subsequent requests to that domain ineligible for request-side
	 * caching, preventing transfer through the shared cache (measured: hit 0/miss 256).
	 * This store retains responses based only on response-side conditions
	 * (200, no Set-Cookie, no no-store/private/no-cache), and resolve within the same conversion
	 * uses it first. Using a discovery-time fetch at consumption time has the same semantics
	 * as Chrome's preload scanner. Discarded on resolver close() (=session end).
	 */
	private final ConcurrentHashMap<URI, HttpResponseCache.Entry> prefetched = new ConcurrentHashMap<>();

	/** The total byte limit of the session-local store (does not retain excess data). */
	private static final long PREFETCHED_MAX_TOTAL_BYTES = 64L * 1024 * 1024;

	private final java.util.concurrent.atomic.AtomicLong prefetchedBytes = new java.util.concurrent.atomic.AtomicLong();

	/**
	 * The main document URI. Used to decide how to <b>avoid fetching the same resource
	 * repeatedly within one conversion</b> (2026-08-28).
	 *
	 * <p>
	 * Resources ineligible for the shared cache, such as images and CSS backgrounds on hosts receiving
	 * cookies, trigger an outbound fetch for every reference unless the actual-request path retains
	 * the body. Measured: a second Paged SVG conversion reusing the size table fetched the same
	 * background SVG 66 times, increasing conversion time from 5.0 s to 13.4 s. Therefore,
	 * subresource bodies are retained in the session-local store. <b>Only the main document is not retained</b>: 
	 * the design starts layout while streaming, and passing it on only after reading it all
	 * would delay the first page.
	 * </p>
	 */
	private volatile URI mainUri;

	void setMainUri(final URI uri) {
		this.mainUri = uri;
	}

	private final Semaphore prefetchSlots = new Semaphore(PREFETCH_PARALLELISM);

	private final AtomicInteger prefetchStarted = new AtomicInteger();

	/**
	 * Asynchronous URI prefetch (input.prefetch). Successfully fetched bodies enter
	 * {@link HttpResponseCache} under the same conditions as the synchronous path, and subsequent
	 * {@link #resolve(URI)} calls receive them as cache hits. Does not prefetch requests with
	 * authentication, cookies, or caching disabled (TTL=0), since {@link #cacheKey} returns null.
	 * This avoids the risk that parallel fetching differs from serial fetching
	 * (cookie application order and user-specific responses). Silently discards failures;
	 * the actual request refetches through the normal path.
	 */
	void prefetch(final URI uri, final MySourceResolver cssGate) {
		if (this.prefetchClosed || this.prefetchStarted.get() >= PREFETCH_MAX_URIS) {
			return;
		}
		if (this.prefetched.containsKey(uri)) {
			return;
		}
		final HttpRequest request = this.createHttpRequest(uri);
		// Do not prefetch authenticated requests (parallelism must not change authentication
		// or user-specific response semantics). Requests with only cookies are eligible:
		// Set-Cookie on the main-document response (e.g., Wikipedia's GeoIP) commonly adds cookies
		// to all later requests on real sites. Discovery-time fetching matches Chrome's preload scanner.
		if (request.headers().firstValue("Authorization").isPresent()
				|| this.findCredential(uri.getHost(), uri.getPort()) != null) {
			MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch auth skip: " + uri);
			return;
		}
		final String cacheKey = this.cacheKey(uri, request);
		if (cacheKey != null) {
			final HttpResponseCache.Entry entry = HttpResponseCache.get(cacheKey, this.cacheTtl);
			if (entry != null) {
				// **Copy to the session-local store even when present in the shared cache**
				// (2026-08-28). Simply stopping here makes consumption-time
				// {@link #cacheKey} return null for requests with cookies (after the main document's
				// Set-Cookie, all requests to the same domain have cookies),
				// preventing shared-cache access and causing refetching. Measurements showed
				// prefetch joins dropping from 253 to about 100 on subsequent conversions,
				// and conversion time returning from 11 s to 29 s.
				this.store(uri, entry);
				return;
			}
		}
		final String host = uri.getHost();
		if (host != null && this.throttledHosts.contains(host)) {
			MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch throttled host, skip: " + uri);
			return;
		}
		final Inflight inflight = new Inflight();
		if (this.prefetching.putIfAbsent(uri, inflight) != null) {
			return;
		}
		if (this.prefetchStarted.incrementAndGet() > PREFETCH_MAX_URIS) {
			this.prefetching.remove(uri, inflight);
			inflight.future().complete(null);
			return;
		}
		final HttpClient client;
		final ExecutorService executor;
		synchronized (this) {
			client = this.httpClient();
			executor = this.executor;
		}
		try {
			executor.execute(() -> {
				Semaphore hostSlot = null;
				try {
					this.prefetchSlots.acquire();
					try {
						hostSlot = host == null ? null
								: this.hostSlots.computeIfAbsent(host,
										h -> new Semaphore(PREFETCH_PARALLELISM_PER_HOST));
						if (hostSlot != null) {
							hostSlot.acquire();
						}
						// If an actual request fetches this URI while the prefetch is queued,
						// withdraw the speculation (avoid duplicate retrieval and unnecessary load).
						if (this.prefetching.get(uri) != inflight || this.prefetchClosed) {
							return;
						}
						inflight.started().set(true);
						final MyHttpSource source = new MyHttpSource(uri, client, request, cacheKey, false, false);
						try {
							// Read the body up to the limit and, if it meets response-side conditions,
							// store it locally in the session (also in the shared cache,
							// but only if request-side conditions hold). Do not use responses
							// that fail the conditions; leave retrieval to the actual request.
							final HttpResponseCache.Entry entry = source.readEntryForPrefetch();
							if (entry != null) {
								this.store(uri, entry);
								if (cacheKey != null && source.isSharedCacheable()) {
									HttpResponseCache.put(cacheKey, entry);
								}
								// If the fetched resource is a stylesheet, prefetch
								// its url()/@import references too (only one level deep:
								// cssGate=null stops recursion). Repeated resolution of CSS background images
								// at the point of consumption is especially expensive
								// (measured: a single Wikipedia magnifying-glass icon
								// was fetched serially 64 times).
								if (cssGate != null && isCssEntry(uri, entry)) {
									for (final URI found : extractCssUris(uri, entry)) {
										cssGate.prefetch(found, false);
									}
								}
							}
							final boolean stored = entry != null;
							MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch done(" + stored + "): " + uri);
						} finally {
							source.close();
						}
					} finally {
						if (hostSlot != null) {
							hostSlot.release();
						}
						this.prefetchSlots.release();
					}
				} catch (final Throwable ignore) {
					// Prefetch is always optional. Leave failure reporting to the actual request's normal path too.
				} finally {
					this.prefetching.remove(uri, inflight);
					inflight.future().complete(null);
				}
			});
		} catch (final RejectedExecutionException e) {
			// For example, immediately after close(). Abandon prefetch.
			this.prefetching.remove(uri, inflight);
			inflight.future().complete(null);
		}
	}

	/** Determines whether a prefetch result is CSS (eligible for url()/@import scanning). */
	private static boolean isCssEntry(final URI uri, final HttpResponseCache.Entry entry) {
		final String mime = entry.mimeType();
		if (mime != null) {
			return mime.toLowerCase(java.util.Locale.ROOT).contains("css");
		}
		final String path = uri.getPath();
		return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".css");
	}

	private static final java.util.regex.Pattern CSS_URL = java.util.regex.Pattern.compile(
			"(?:url\\(\\s*(['\"]?)([^'\"()\\s]+)\\1\\s*\\))|(?:@import\\s+['\"]([^'\"]+)['\"])",
			java.util.regex.Pattern.CASE_INSENSITIVE);

	/** Extracts url()/@import targets from the CSS body (relative to the CSS URI). */
	private static java.util.List<URI> extractCssUris(final URI cssUri, final HttpResponseCache.Entry entry) {
		final java.util.List<URI> result = new java.util.ArrayList<>();
		final String text = new String(entry.body(), java.nio.charset.StandardCharsets.UTF_8);
		final java.util.regex.Matcher m = CSS_URL.matcher(text);
		while (m.find() && result.size() < 64) {
			final String ref = m.group(2) != null ? m.group(2) : m.group(3);
			if (ref == null || ref.isEmpty() || ref.startsWith("data:") || ref.startsWith("#")) {
				continue;
			}
			try {
				result.add(net.zamasoft.zstream.resolver.util.URIHelper.resolve(entry.encoding(), cssUri, ref));
			} catch (final java.net.URISyntaxException | RuntimeException e) {
				// Only a reference could not be parsed; the actual request's normal path is authoritative.
			}
		}
		return result;
	}

	/** Adds to the session-local store (does not retain entries exceeding the total limit). */
	private void store(final URI uri, final HttpResponseCache.Entry entry) {
		final int length = entry.body().length;
		if (this.prefetchedBytes.addAndGet(length) <= PREFETCHED_MAX_TOTAL_BYTES) {
			this.prefetched.put(uri, entry);
		} else {
			this.prefetchedBytes.addAndGet(-length);
		}
	}

	public Source resolve(URI uri) throws IOException {
		// Join an in-flight prefetch for the same URI (avoid duplicate retrieval). Ignore prefetch
		// failure or cancellation here and refetch via the normal path below.
		final Inflight inflight = this.prefetching.get(uri);
		if (inflight != null) {
			if (inflight.started().get()) {
				// Join if already fetching (do not pay for the same round trip twice).
				try {
					inflight.future().join();
				} catch (final CancellationException | CompletionException ignore) {
					// Continue through the normal path.
				}
			} else {
				// **Do not join queued work** (2026-08-28). After the wait, a failed prefetch
				// can lose a resource that would otherwise have been retrievable.
				// Remove it from the map, withdraw speculation, and fetch immediately ourselves.
				this.prefetching.remove(uri, inflight);
				inflight.future().complete(null);
			}
		}
		// Session-local prefetch results take priority (transfer within the same conversion).
		final HttpResponseCache.Entry pre = this.prefetched.get(uri);
		if (pre != null) {
			MySourceResolver.PREFETCH_LOG.fine(() -> "resolve hit(session): " + uri);
			return new CachedHttpSource(uri, pre);
		}
		final HttpRequest request = this.createHttpRequest(uri);
		final String cacheKey = this.cacheKey(uri, request);
		if (cacheKey != null) {
			final HttpResponseCache.Entry entry = HttpResponseCache.get(cacheKey, this.cacheTtl);
			if (entry != null) {
				MySourceResolver.PREFETCH_LOG.fine(() -> "resolve hit(shared): " + uri);
				return new CachedHttpSource(uri, entry);
			}
		}
		MySourceResolver.PREFETCH_LOG.fine(() -> "resolve miss: " + uri);
		// Retain subresources after reading their entire bodies. Retain the main document **while streaming**:
		// waiting to pass it on until fully read delays layout start (see mainUri's Javadoc).
		final boolean main = uri.equals(this.mainUri);
		final boolean remember = !main;
		return new MyHttpSource(uri, this.httpClient(), request, cacheKey, remember, main);
	}

	/**
	 * Returns this request's cache key, or {@code null} if it is ineligible for caching
	 * (safety conditions and design are documented in {@link HttpResponseCache}).
	 *
	 * <p>
	 * Three request-side exclusions: (1) sends an Authorization header (preemptive authentication
	 * or custom headers); (2) has credentials matching the host (Authenticator's response to a 401
	 * may produce a user-specific response); (3) has cookies to send to the URI (checked whenever
	 * a request is built, since Set-Cookie can add cookies during conversion).
	 * </p>
	 */
	private String cacheKey(final URI uri, final HttpRequest request) {
		if (this.cacheTtl <= 0) {
			return null;
		}
		if (request.headers().firstValue("Authorization").isPresent()) {
			return null;
		}
		if (this.findCredential(uri.getHost(), uri.getPort()) != null) {
			return null;
		}
		try {
			final List<String> cookies = this.cookieManager.get(uri, Map.of()).get("Cookie");
			if (cookies != null && !cookies.isEmpty()) {
				return null;
			}
		} catch (final IOException e) {
			return null;
		}
		// The key is URI + route (proxy) + all outgoing headers. Servers that vary responses
		// by headers (e.g., Referer-based hotlink protection) must not be mixed, so a difference
		// in even one outgoing header produces a separate entry.
		final StringBuilder key = new StringBuilder(uri.toASCIIString());
		key.append('\n').append(this.proxyHost).append(':').append(this.proxyPort);
		new java.util.TreeMap<>(request.headers().map())
				.forEach((name, values) -> key.append('\n').append(name).append(':').append(values));
		return key.toString();
	}

	public void release(Source source) {
		try {
			source.close();
		} catch (IOException e) {
			// ignore
		}
	}

	public synchronized void close() {
		this.prefetchClosed = true;
		if (this.executor != null) {
			this.executor.shutdownNow();
			this.executor = null;
		}
		this.httpClient = null;
		// Prefetch tasks discarded before execution never enter finally. Complete remaining Futures
		// here so resolve() cannot hang in join.
		this.prefetching.forEach((uri, inflight) -> inflight.future().complete(null));
		this.prefetching.clear();
		this.hostSlots.clear();
		this.throttledHosts.clear();
		this.prefetched.clear();
		this.prefetchedBytes.set(0);

	}

	private static final String DEFAULT_USER_AGENT = "CopperPDF";

	private boolean hasCustomHeader(String name) {
		if (this.headers == null) {
			return false;
		}
		for (int i = 0; i < this.headers.size(); ++i) {
			if (this.headers.get(i).getKey().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the status indicates a redirect. */
	private static boolean isRedirect(final int status) {
		return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
	}

	/**
	 * Whether two URIs have the same origin.
	 *
	 * <p>
	 * A difference in scheme, host, or port makes them different origins. Follows prevailing
	 * standards, with the same handling as fetch's rules and {@code curl}'s default
	 * (without {@code --location-trusted}).
	 * </p>
	 */
	static boolean sameOrigin(final URI a, final URI b) {
		if (a == null || b == null) {
			return false;
		}
		return equalsIgnoreCaseOrNull(a.getScheme(), b.getScheme())
				&& equalsIgnoreCaseOrNull(a.getHost(), b.getHost()) && a.getPort() == b.getPort();
	}

	private static boolean equalsIgnoreCaseOrNull(final String a, final String b) {
		return a == null ? b == null : a.equalsIgnoreCase(b);
	}

	/**
	 * Headers that <b>must not be sent to another origin</b>.
	 *
	 * <p>
	 * Also applies to headers added by the caller via {@code input.http.header.N},
	 * since redirects could otherwise send credentials intended for one site to another host.
	 * </p>
	 */
	private static boolean isCredentialHeader(final String name) {
		return name.equalsIgnoreCase("Authorization") || name.equalsIgnoreCase("Proxy-Authorization")
				|| name.equalsIgnoreCase("Cookie") || name.equalsIgnoreCase("Cookie2");
	}

	private HttpRequest createHttpRequest(URI uri) {
		return this.createHttpRequest(uri, true);
	}

	private HttpRequest createHttpRequest(URI uri, boolean sameOrigin) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
		// java.net.http.HttpClient does not automatically send Accept-Encoding or decompress
		// response Content-Encoding (request compression explicitly to save bandwidth,
		// and decompress in getInputStream()). Responses from static object stores
		// (such as S3) can return Content-Encoding: gzip even without Accept-Encoding,
		// so decompression support is essential regardless of whether compression was requested.
		builder.header("Accept-Encoding", "gzip, deflate");
		// Without a User-Agent setting, HttpClient sends its default "Java-http-client/x.x".
		// Sites with bot policies then cannot be fetched (e.g., Wikipedia explicitly requires
		// a User-Agent for robots policy compliance and rejects requests without it
		// with 403). This actual bug was found in field testing on 2026-07-18.
		// If the administrator explicitly sets User-Agent via input.http-header*.name,
		// give it priority and do not overwrite it.
		if (!this.hasCustomHeader("User-Agent")) {
			builder.header("User-Agent", DEFAULT_USER_AGENT);
		}
		if (this.requestTimeout > 0) {
			builder.timeout(Duration.ofMillis(this.requestTimeout));
		}
		if (this.preemptiveAuth) {
			HttpCredential credential = this.findCredential(uri.getHost(), uri.getPort());
			if (credential != null) {
				String raw = credential.user + ":" + credential.password;
				builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(Charset.forName("ISO-8859-1"))));
			}
		}
		if (refURI != null && !refURI.equals(uri)) {
			builder.header("Referer", refURI.toASCIIString());
		}
		if (headers != null) {
			for (int i = 0; i < headers.size(); ++i) {
				Entry<String, String> header = headers.get(i);
				if (!sameOrigin && isCredentialHeader(header.getKey())) {
					// Redirected to another origin. Do not send anything that could be credentials.
					continue;
				}
				builder.header(header.getKey(), header.getValue());
			}
		}
		return builder.build();
	}

	private HttpCredential findCredential(String host, int port) {
		for (HttpCredential credential : this.credentials) {
			if (credential.matches(host, port)) {
				return credential;
			}
		}
		return null;
	}

	class MyHttpSource extends AbstractSource implements HttpStatusSource {
		private final HttpClient httpClient;
		private final HttpRequest request;
		private final String cacheKey;
		/** Whether to retain the body in the session-local store (prevents refetching within one conversion). */
		private final boolean remember;
		/**
		 * Whether this is the main document. Retains it <b>while streaming</b> (2026-08-28).
		 * When converting again in the same session, such as after changing the webapp's text size,
		 * refetching could change the page content, so use the first body read.
		 */
		private final boolean main;
		private CompletableFuture<HttpResponse<InputStream>> responseFuture;
		private HttpResponse<InputStream> response;
		private InputStream in;
		private String mimeType;
		private String contentEncoding;
		private String encoding;
		private boolean exists;
		private int status = -1;
		private long lastModified = -1;
		private long contentLength = -1;

		MyHttpSource(URI uri, HttpClient httpClient, HttpRequest request, String cacheKey, boolean remember,
				boolean main) {
			super(uri);
			this.httpClient = httpClient;
			this.request = request;
			this.cacheKey = cacheKey;
			this.remember = remember;
			this.main = main;
			this.startConnection();
		}

		public String getMimeType() throws IOException {
			this.tryConnect();
			return this.mimeType;
		}

		public String getEncoding() throws IOException {
			this.tryConnect();
			return this.encoding;
		}

		public long getLength() throws IOException {
			this.tryConnect();
			return this.contentLength;
		}

		public boolean exists() throws IOException {
			this.tryConnect();
			return this.exists;
		}

		@Override
		public int httpStatus() {
			return this.status;
		}

		public boolean isInputStream() throws IOException {
			return true;
		}

		public boolean isReader() throws IOException {
			this.tryConnect();
			return this.encoding != null;
		}

		public synchronized InputStream getInputStream() throws IOException {
			if (this.response != null || this.in != null) {
				this.close();
				this.startConnection();
			}
			this.tryConnect();
			InputStream body = this.decodedBody();
			// Response-side cache eligibility (the request side uses cacheKey in resolve()).
			// Read ahead up to the body limit and store it. Saving on EOF detection would fail
			// for the main use case of gzip-served CSS: GZIPInputStream does not necessarily
			// read the underlying stream through -1 (the trailer may be consumed within its buffer).
			// If the limit is exceeded, concatenate the bytes already read with the remainder
			// and pass them through (design documented in HttpResponseCache).
			// Even resources ineligible for the shared cache go into the session-local store
			// when known to be subresources (2026-08-28; avoid repeatedly fetching the same
			// resource within a conversion: see {@link #discovered}'s Javadoc).
			final boolean shared = this.cacheKey != null && this.isCacheableResponse();
			if (shared || (this.remember && this.response.statusCode() == 200)) {
				final byte[] head = body.readNBytes(HttpResponseCache.MAX_ENTRY_BYTES + 1);
				if (head.length <= HttpResponseCache.MAX_ENTRY_BYTES) {
					final HttpResponseCache.Entry entry = new HttpResponseCache.Entry(head, this.mimeType,
							this.encoding, this.lastModified, System.currentTimeMillis(),
							parseMaxAge(this.response.headers().firstValue("Cache-Control").orElse(null)));
					if (shared) {
						HttpResponseCache.put(this.cacheKey, entry);
					}
					if (this.remember) {
						store(this.getURI(), entry);
					}
					body.close();
					body = new java.io.ByteArrayInputStream(head);
				} else {
					body = new java.io.SequenceInputStream(new java.io.ByteArrayInputStream(head), body);
				}
			}
			if (this.main) {
				// **Retain while streaming** (2026-08-28). Refetching for another conversion in
				// the same session could change the page content (real sites return different HTML
				// on each load). Waiting to pass it on until fully read delays layout start,
				// so copy while passing it on. If not read to the end, do not retain it:
				// using an incomplete copy for the next conversion would cause greater harm.
				final URI uri = this.getURI();
				final String type = this.mimeType;
				final String charset = this.encoding;
				final long modified = this.lastModified;
				final Long maxAge = parseMaxAge(this.response.headers().firstValue("Cache-Control").orElse(null));
				body = new TeeInputStream(body, HttpResponseCache.MAX_ENTRY_BYTES, bytes -> store(uri,
						new HttpResponseCache.Entry(bytes, type, charset, modified, System.currentTimeMillis(),
								maxAge)));
			}
			this.in = body;
			return this.in;
		}

		/**
		 * Returns the decoded body of the connected response (stall timeout + Content-Encoding
		 * decompression).
		 *
		 * <p>
		 * Stall timeout (2026-08-08): HttpRequest.timeout() protects only until response headers arrive.
		 * If body streaming stops, the layout thread hangs forever: one external resource on kakaku.com
		 * hung the whole conversion for over 2000 s in an actual bug. Uses input.http.socket.timeout
		 * (requestTimeout) as the stall limit for each read.
		 * Decompression: HttpClient does not automatically decompress Content-Encoding. Without support,
		 * compressed bytes reach the parser unchanged and appear as extensive garbled text.
		 * </p>
		 */
		private InputStream decodedBody() throws IOException {
			InputStream body = this.response.body();
			if (body == null) {
				throw new FileNotFoundException();
			}
			if (requestTimeout > 0) {
				body = new StallGuardInputStream(body, MyHttpSourceResolver.this.executor, requestTimeout);
			}
			if (this.contentEncoding != null) {
				switch (this.contentEncoding.trim().toLowerCase()) {
				case "gzip":
				case "x-gzip":
					body = new GZIPInputStream(body);
					break;
				case "deflate":
					body = new InflaterInputStream(body);
					break;
				default:
					// Pass unsupported encodings such as br (Brotli) through unchanged (normally unreachable,
					// since br is not currently requested).
					break;
				}
			}
			return body;
		}

		/**
		 * For prefetch: reads the entire body up to the limit into an entry. Non-200 responses
		 * or bodies exceeding the limit return {@code null} (the actual request refetches normally).
		 *
		 * <p>
		 * Does not impose response-side cache conditions (Set-Cookie, etc.) here: session-local transfer
		 * merely avoids fetching the same resource twice within one conversion, matching Chrome's
		 * reuse in its memory cache within one load regardless of headers. cookieManager already
		 * processed Set-Cookie side effects when the response arrived; these are independent of body reuse.
		 * For example, upload.wikimedia.org adds a WMF-Uniq tracking cookie to some image responses,
		 * and strict conditions prevented retaining half the images. Eligibility for storage in
		 * the process-wide {@link HttpResponseCache} is still checked separately
		 * by {@link #isCacheableResponse()}.
		 * </p>
		 */
		HttpResponseCache.Entry readEntryForPrefetch() throws IOException {
			this.tryConnect();
			final int status = this.response.statusCode();
			if (status != 200) {
				if (status == 429 || status == 503) {
					// Rate limited. Stop prefetching from this host (it defeats the purpose if speculation
					// upsets the server and loses required fetches too).
					final String host = this.getURI().getHost();
					if (host != null && throttledHosts.add(host)) {
						MySourceResolver.PREFETCH_LOG
								.fine(() -> "prefetch disabled for throttled host: " + host + " (HTTP " + status + ")");
					}
				}
				return null;
			}
			final byte[] head;
			final InputStream body = this.decodedBody();
			try {
				head = body.readNBytes(HttpResponseCache.MAX_ENTRY_BYTES + 1);
			} finally {
				body.close();
			}
			if (head.length > HttpResponseCache.MAX_ENTRY_BYTES) {
				return null;
			}
			return new HttpResponseCache.Entry(head, this.getMimeType(), this.getEncoding(), this.lastModified,
					System.currentTimeMillis(),
					parseMaxAge(this.response.headers().firstValue("Cache-Control").orElse(null)));
		}

		/** Whether the response meets shared-cache conditions (checked by prefetch tasks). */
		boolean isSharedCacheable() {
			return this.isCacheableResponse();
		}

		/**
		 * Returns whether the response may be stored in the shared cache.
		 * Must be a 200 GET response, without Set-Cookie, without no-store/no-cache/private
		 * in Cache-Control, and without Vary:*.
		 */
		private boolean isCacheableResponse() {
			if (this.response.statusCode() != 200) {
				return false;
			}
			if (this.response.headers().firstValue("Set-Cookie").isPresent()) {
				return false;
			}
			if ("*".equals(this.response.headers().firstValue("Vary").orElse(null))) {
				return false;
			}
			final String cacheControl = this.response.headers().firstValue("Cache-Control").orElse(null);
			if (cacheControl != null) {
				final String lower = cacheControl.toLowerCase();
				if (lower.contains("no-store") || lower.contains("no-cache") || lower.contains("private")) {
					return false;
				}
			}
			return true;
		}

		public Reader getReader() throws IOException {
			this.tryConnect();
			if (this.encoding == null) {
				throw new UnsupportedOperationException("Encoding not set");
			}
			return new InputStreamReader(this.getInputStream(), this.encoding);
		}

		public File getFile() {
			throw new UnsupportedOperationException();
		}

		public SourceValidity getValidity() {
			return new HttpValidity(this.lastModified);
		}

		public synchronized void close() throws IOException {
			if (this.in != null) {
				this.in.close();
			} else if (this.response != null && this.response.body() != null) {
				this.response.body().close();
			}
			if (this.responseFuture != null && !this.responseFuture.isDone()) {
				this.responseFuture.cancel(true);
			}
			this.in = null;
			this.response = null;
			this.responseFuture = null;
		}

		private synchronized void tryConnect() throws IOException {
			if (this.response != null) {
				return;
			}
			if (this.responseFuture == null) {
				this.startConnection();
			}
			try {
				this.response = this.responseFuture.join();
			} catch (CancellationException | CompletionException e) {
				throw this.toIOException(e);
			}
			this.status = this.response.statusCode();
			this.followRedirects();
			this.exists = this.status != 404;
			this.mimeType = this.response.headers().firstValue("Content-Type").orElse(null);
			this.contentEncoding = this.response.headers().firstValue("Content-Encoding").orElse(null);
			this.encoding = parseCharset(this.mimeType);
			// Content-Length is the compressed byte count, which differs from the decompressed
			// length when getInputStream() decompresses. Reporting unknown (-1) is safer
			// than reporting an incorrect length.
			this.contentLength = this.contentEncoding != null ? -1
					: this.response.headers().firstValueAsLong("Content-Length").orElse(-1);
			this.lastModified = parseLastModified(this.response.headers().firstValue("Last-Modified").orElse(null));
		}

		/**
		 * Follows 3xx redirects itself.
		 *
		 * <p>
		 * Since {@link HttpClient} is always created with {@code Redirect.NEVER},
		 * <b>this is the only redirect handler</b>. Choosing {@code NORMAL}/{@code NEVER}
		 * from the state at creation would let existing clients follow redirects before
		 * a check added later could run.
		 * </p>
		 *
		 * <p>
		 * <b>Checks only when the session imposes restrictions</b>. Calling
		 * {@code restrictedResolver.permits()} in a session with no ACL ever set would stop
		 * all redirects, since the default is <b>deny everything except {@code data:}</b>.
		 * Even without checking, enforces the hop limit and rejects HTTPS→HTTP downgrades
		 * (the same as {@code Redirect.NORMAL}).
		 * </p>
		 */
		private void followRedirects() throws IOException {
			final java.util.function.Predicate<URI> guard = MyHttpSourceResolver.this.revalidating()
					? MyHttpSourceResolver.this.redirectGuard
					: null;
			URI current = this.request.uri();
			for (int hop = 0; isRedirect(this.status); ++hop) {
				if (hop >= MAX_REDIRECTS) {
					throw new IOException("too many redirects: " + current);
				}
				final String location = this.response.headers().firstValue("Location").orElse(null);
				if (location == null || location.isEmpty()) {
					return;
				}
				final URI next;
				try {
					next = current.resolve(location);
				} catch (final IllegalArgumentException e) {
					throw new IOException("bad redirect target: " + location);
				}
				// Like HttpClient's NORMAL, do not follow HTTPS-to-HTTP downgrades.
				if ("https".equalsIgnoreCase(current.getScheme())
						&& !"https".equalsIgnoreCase(next.getScheme())) {
					throw new IOException("refusing to follow a redirect from https to " + next.getScheme());
				}
				if (guard != null && !guard.test(next)) {
					throw new SecurityException("Access to the redirect target is not permitted: " + next);
				}
				// Close the unused body.
				try {
					this.response.body().close();
				} catch (final IOException e) {
					// ignore
				}
				current = next;
				this.responseFuture = this.httpClient.sendAsync(
						MyHttpSourceResolver.this.createHttpRequest(next,
								sameOrigin(this.request.uri(), next)),
						HttpResponse.BodyHandlers.ofInputStream());
				try {
					this.response = this.responseFuture.join();
				} catch (CancellationException | CompletionException e) {
					throw this.toIOException(e);
				}
				this.status = this.response.statusCode();
			}
		}

		private void startConnection() {
			this.status = -1;
			this.responseFuture = this.httpClient.sendAsync(this.request, HttpResponse.BodyHandlers.ofInputStream());
		}

		private IOException toIOException(RuntimeException e) {
			Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
			if (cause instanceof IOException ioe) {
				return ioe;
			}
			return new IOException(cause);
		}

		private String parseCharset(String contentType) {
			if (contentType == null) {
				return null;
			}
			String[] parts = contentType.split(";");
			for (int i = 1; i < parts.length; ++i) {
				String part = parts[i].trim();
				int eq = part.indexOf('=');
				if (eq != -1 && part.substring(0, eq).trim().equalsIgnoreCase("charset")) {
					String charset = part.substring(eq + 1).trim();
					if (charset.length() >= 2 && charset.startsWith("\"") && charset.endsWith("\"")) {
						charset = charset.substring(1, charset.length() - 1);
					}
					try {
						if (!charset.equalsIgnoreCase("ISO-8859-1") && Charset.isSupported(charset)) {
							return charset;
						}
					} catch (Exception e) {
						return null;
					}
				}
			}
			return null;
		}

		private long parseLastModified(String lastModified) {
			if (lastModified == null) {
				return -1;
			}
			try {
				return Date.from(ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
						.getTime();
			} catch (Exception e) {
				return -1;
			}
		}
	}

	/**
	 * Extracts max-age (seconds) from the response's {@code Cache-Control}.
	 * Since this is a shared cache, s-maxage takes priority. Returns -1 if neither is present.
	 */
	static long parseMaxAge(final String cacheControl) {
		if (cacheControl == null) {
			return -1;
		}
		long maxAge = -1;
		long sMaxAge = -1;
		for (final String part : cacheControl.split(",")) {
			final String token = part.trim().toLowerCase();
			try {
				if (token.startsWith("s-maxage=")) {
					sMaxAge = Long.parseLong(token.substring("s-maxage=".length()).trim());
				} else if (token.startsWith("max-age=")) {
					maxAge = Long.parseLong(token.substring("max-age=".length()).trim());
				}
			} catch (final NumberFormatException e) {
				// Treat an invalid value as unspecified.
			}
		}
		return sMaxAge >= 0 ? sMaxAge : maxAge;
	}

}
