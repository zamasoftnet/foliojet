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
import java.util.HashSet;
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

import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.SourceValidity;
import net.zamasoft.zstream.resolver.cache.CachedSourceResolver;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.AbstractSource;
import net.zamasoft.zstream.resolver.util.SourceWrapper;
import net.zamasoft.zstream.resolver.restricted.RestrictedSourceResolver;

class MySourceResolver implements SourceResolver {
	protected CachedSourceResolver cachedResolver = new CachedSourceResolver();
	protected SourceResolver userResolver = null;
	protected RestrictedSourceResolver restrictedResolver = new RestrictedSourceResolver();
	private MyHttpSourceResolver httpResolver = null;
	private InputByteBudget resourceBudget;
	private int resourceCountLimit = -1;
	private final Set<URI> resourceUris = new HashSet<>();

	/**
	 * {@code input.include} / {@code input.exclude} が1つでも設定されたか。
	 *
	 * <p>
	 * <b>設定されているなら、それが全スキームの取得を縛る。</b> 設定が
	 * 無いときは縛りが存在しないので、差し込まれたリゾルバを先に使う従来の
	 * 順序のままにする(制限の既定は「一致するものが無ければ拒否」なので、
	 * 無条件に先へ出すと設定していない利用者の取得が全部止まる)。
	 * </p>
	 */
	private boolean restricted = false;

	/**
	 * 遠隔から取得してよいスキーム。これ以外は「ローカル資源」として扱う。
	 */
	private static final Set<String> REMOTE_SCHEMES = Set.of("http", "https", "data");

	/**
	 * ローカル資源({@code file:}など)の取得を許すか。
	 *
	 * <p>
	 * <b>これは入出力プロパティではない。</b> クライアントからは変更できず、
	 * サーバー(デーモン)が認証済みの利用者ごとに決める。既定は許可で、
	 * 組み込み利用とコマンドラインの動作は変わらない——それらを動かす主体は
	 * 元々そのプロセスのファイルを読めるので、制限しても意味がない。
	 * </p>
	 *
	 * <p>
	 * {@code input.include}/{@code input.exclude}ではこの用途を満たせない。
	 * それらはクライアントが自分を縛るためのもので、セッションごとに
	 * {@link #reset()}され、しかも主文書は{@code force}でACLを迂回する。
	 * </p>
	 */
	private boolean localAccessAllowed = true;

	void setLocalAccessAllowed(final boolean allowed) {
		this.localAccessAllowed = allowed;
	}

	/**
	 * 遠隔から取ってよいスキームか。スキームを持たないURIは現在の作業
	 * ディレクトリのファイルを指しうるので「ローカル」として扱います。
	 */
	private static boolean isRemoteScheme(final URI uri) {
		final String scheme = uri.getScheme();
		return scheme != null && REMOTE_SCHEMES.contains(scheme.toLowerCase(java.util.Locale.ROOT));
	}

	public void setup(URI uri, Map<String, String> props, MessageHandler mh) {
		this.closeHttpResolver();
		final long resourceSizeLimit = UAProps.INPUT_RESOURCE_SIZE_LIMIT.getInteger(props, mh);
		this.resourceBudget = resourceSizeLimit < 0 ? null
				: new InputByteBudget(resourceSizeLimit, UAProps.INPUT_RESOURCE_SIZE_LIMIT.getName());
		this.resourceCountLimit = UAProps.INPUT_RESOURCE_COUNT_LIMIT.getInteger(props, mh);
		this.resourceUris.clear();
		CompositeSourceResolver resolver = CompositeSourceResolver.createGenericCompositeSourceResolver();
		MyHttpSourceResolver httpResolver = new MyHttpSourceResolver();
		this.httpResolver = httpResolver;
		httpResolver.setMainUri(uri);
		if (UAProps.INPUT_HTTP_REFERER.getBoolean(props, mh)) {
			httpResolver.setReferer(uri);
		}

		// ヘッダー
		for (int i = 0;; ++i) {
			String prefix = UAProps.INPUT_HTTP_HEADER + i + ".";
			String name = (String) props.get(prefix + "name");
			if (name == null) {
				break;
			}
			String value = (String) props.get(prefix + "value");
			httpResolver.addHeader(name, value);
		}

		httpResolver.setConnectionTimeout(UAProps.INPUT_HTTP_CONNECTION_TIMEOUT.getInteger(props, mh));
		httpResolver.setRequestTimeout(UAProps.INPUT_HTTP_SOCKET_TIMEOUT.getInteger(props, mh));
		httpResolver.setCacheTtl(UAProps.INPUT_HTTP_CACHE.getBoolean(props, mh)
				? UAProps.INPUT_HTTP_CACHE_TTL.getInteger(props, mh)
				: 0);

		// プロクシ
		String proxyHost = UAProps.INPUT_HTTP_PROXY_HOST.getString(props);
		if (proxyHost != null) {
			int proxyPort = UAProps.INPUT_HTTP_PROXY_PORT.getInteger(props, mh);
			httpResolver.setProxy(proxyHost, proxyPort);
			String user = UAProps.INPUT_HTTP_PROXY_AUTHENTICATION_USER.getString(props);
			String password = UAProps.INPUT_HTTP_PROXY_AUTHENTICATION_PASSWORD.getString(props);
			if (password == null) {
				password = "";
			}
			if (user != null) {
				httpResolver.addAuthentication(proxyHost, proxyPort, user, password);
			}
		}

		// 認証
		boolean preemptive = UAProps.INPUT_HTTP_AUTHENTICATION_PREEMPTIVE.getBoolean(props, mh);
		httpResolver.setPreemptiveAuthentication(preemptive);
		for (int i = 0;; ++i) {
			String prefix = UAProps.INPUT_HTTP_AUTHENTICATION + i + ".";
			String host = (String) props.get(prefix + "host");
			if (host == null) {
				break;
			}
			String user = (String) props.get(prefix + "user");
			if (user == null) {
				break;
			}
			String _port = (String) props.get(prefix + "port");
			int port;
			if (_port == null) {
				port = -1;
			} else {
				try {
					port = Integer.parseInt(_port);
				} catch (NumberFormatException e) {
					port = -1;
				}
			}
			String password = (String) props.get(prefix + "password");
			if (password == null) {
				password = "";
			}

			httpResolver.addAuthentication(host, port, user, password);
		}

		// Cookie
		for (int i = 0;; ++i) {
			String prefix = UAProps.INPUT_HTTP_COOKIE + i + ".";
			String domain = (String) props.get(prefix + "domain");
			if (domain == null) {
				break;
			}
			String name = (String) props.get(prefix + "name");
			if (name == null) {
				break;
			}
			String value = (String) props.get(prefix + "value");
			if (value == null) {
				value = "";
			}
			String path = (String) props.get(prefix + "path");
			if (path == null) {
				path = "/";
			}

			httpResolver.addCookie(domain, path, name, value);
		}

		resolver.addSourceResolver("http", httpResolver);
		resolver.addSourceResolver("https", httpResolver);

		this.restrictedResolver.setEnclosedSourceResolver(resolver);
	}

	public void include(URI uriPattern) {
		this.restricted = true;
		this.restrictedResolver.include(uriPattern);
	}

	public void exclude(URI uriPattern) {
		this.restricted = true;
		this.restrictedResolver.exclude(uriPattern);
	}

	public File putFile(SourceMetadata metaSource) throws IOException {
		return this.cachedResolver.putFile(metaSource);
	}

	public void setUserResolver(SourceResolver userResolver) {
		this.userResolver = userResolver;
	}

	public void reset() {
		this.closeHttpResolver();
		this.restrictedResolver.reset();
		this.cachedResolver.reset();
		this.userResolver = null;
		this.restricted = false;
		this.resourceBudget = null;
		this.resourceCountLimit = -1;
		this.resourceUris.clear();
	}

	private void closeHttpResolver() {
		if (this.httpResolver == null) {
			return;
		}
		this.httpResolver.close();
		this.httpResolver = null;
	}

	/**
	 * 外部リソースの非同期先読みを要求します(input.prefetch、2026-08-27)。
	 * 対象はhttp(s)のみで、同期経路と同じ判定を通す: クライアントが
	 * CTIPで送ってきた資源(cachedResolver)はネットワーク不要なので
	 * 対象外、ACL(input.include/exclude——httpは常にACL先行)を通過した
	 * URLだけをhttpリゾルバへ渡す。拒否・失敗は黙って捨てる(実要求時に
	 * 正規のSecurityException等が出る)。資源バイト・件数の予算は先読みでは
	 * 計上しない——文書が実際に要求したときに従来どおり一度だけ計上する。
	 */
	public void prefetch(final URI uri) {
		this.prefetch(uri, true);
	}

	/**
	 * @param scanCss 取得したスタイルシートの{@code url()}/{@code @import}を
	 *                深さ1だけ追って先読みするか(CSSから発見したURLの
	 *                再帰を止めるためのフラグ)
	 */
	void prefetch(final URI uri, final boolean scanCss) {
		final MyHttpSourceResolver http = this.httpResolver;
		if (http == null || uri == null) {
			return;
		}
		final String scheme = uri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			return;
		}
		try {
			final Source cached = this.cachedResolver.resolve(uri);
			this.cachedResolver.release(cached);
			return;
		} catch (final FileNotFoundException e) {
			// クライアント押し込み資源ではない——先読み対象
		} catch (final IOException e) {
			return;
		}
		if (!this.restrictedResolver.permits(uri)) {
			PREFETCH_LOG.fine(() -> "prefetch ACL deny: " + uri);
			return;
		}
		PREFETCH_LOG.fine(() -> "prefetch request: " + uri);
		http.prefetch(uri, scanCss ? this : null);
	}

	/** 先読みの動きを追うためのロガー(FINEで各判定・合流を出す)。 */
	static final java.util.logging.Logger PREFETCH_LOG = java.util.logging.Logger
			.getLogger("net.zamasoft.foliojet.driver.prefetch");

	/**
	 * 次の順でリソースを探します。
	 *
	 * 1. キャッシュされたリソース 2. 設定されたリゾルバ 3. サーバー側リソース
	 */
	public Source resolve(URI uri) throws IOException, FileNotFoundException {
		return this.resolve(uri, false);
	}

	public Source resolve(URI uri, boolean force) throws IOException, SecurityException {
		try {
			// クライアントが CTISession.resource() で送ってきた資源。
			// これは**クライアント自身の内容**なので、URIが file: でも
			// サーバーのファイルを読むことにはならない
			Source source = this.cachedResolver.resolve(uri);
			return this.wrap(source, this.cachedResolver, force);
		} catch (FileNotFoundException e) {
			// **HTTP/HTTPSは設定済みのリゾルバを優先する**(2026-08-02)。
			// 差し込まれたリゾルバ(CLIやCTIドライバがsetSourceResolverで
			// 入れる汎用リゾルバ)が先に取ってしまうと、入出力プロパティで
			// 設定したUser-Agent・ヘッダ・プロキシ・Cookie・認証が
			// **どれも効かない**。実測: 既定のUser-Agent(CopperPDF)も
			// input.http.header.*の指定も送られず、JDK既定の
			// Java/21.0.11が飛んでいた(Wikipediaが403で取得できない)
			final String scheme = uri.getScheme();
			final boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
			// **制限が設定されているなら、それを先に効かせる**(2026-08-03、
			// オーナー裁定)。差し込まれたリゾルバが先にローカルファイルを
			// 解決してしまうと、input.include/input.excludeが素通りになる。
			// コマンドラインもウェブアプリも汎用リゾルバを差し込むため、
			// 信頼できないHTMLを変換するサーバー用途でローカルファイルの
			// 読み出しを止められない状態だった。拒否(SecurityException)は
			// ここで確定し、差し込まれたリゾルバへは回さない
			final boolean aclFirst = this.restricted || http;
			if (this.userResolver != null && !aclFirst) {
				try {
					Source source = this.userResolver.resolve(uri);
					return this.wrap(source, this.userResolver, force);
				} catch (FileNotFoundException e1) {
					// ignore
				}
			}
			// **ここから先はサーバー自身のファイルシステムを引く**。
			// 遠隔の利用者に許していなければ、この経路へは入らせない。
			// 差し込まれたリゾルバ(CTIPでは「サーバーから要求された資源を
			// クライアントが都度送る」経路)は**クライアント自身の資源**なので
			// 塞がず、そちらだけを試す
			if (!this.localAccessAllowed && !isRemoteScheme(uri)) {
				if (this.userResolver != null) {
					try {
						Source source = this.userResolver.resolve(uri);
						return this.wrap(source, this.userResolver, force);
					} catch (FileNotFoundException e2) {
						// クライアントも持っていない
					}
				}
				throw new SecurityException("Access to local resources is not permitted for this user: " + uri);
			}
			try {
				Source source = this.restrictedResolver.resolve(uri, force);
				return this.wrap(source, this.restrictedResolver, force);
			} catch (IOException e2) {
				if (this.userResolver == null || !aclFirst) {
					throw e2;
				}
				// **許可されているが取れなかった**ものは、差し込まれた
				// リゾルバ(独自の取得手段を持つ埋め込み利用、CTIPなら
				// クライアントへの要求)へ回す。拒否された場合は
				// SecurityExceptionなのでここへ来ない。
				// FileNotFoundException以外も回すのは、独自スキームが
				// MalformedURLException("unknown protocol")になるため
				// (2026-08-16)。クライアントだけが解決できるURIを
				// input.includeと併用できなかった
				Source source = this.userResolver.resolve(uri);
				return this.wrap(source, this.userResolver, force);
			}
		}
	}

	private Source wrap(final Source source, final SourceResolver owner, final boolean mainDocument)
			throws IOException {
		if (!mainDocument && this.resourceCountLimit >= 0) {
			final boolean overLimit;
			synchronized (this.resourceUris) {
				this.resourceUris.add(source.getURI());
				overLimit = this.resourceUris.size() > this.resourceCountLimit;
			}
			if (overLimit) {
				owner.release(source);
				throw new IOException(UAProps.INPUT_RESOURCE_COUNT_LIMIT.getName() + " exceeded: "
						+ this.resourceCountLimit);
			}
		}
		return new MySource(source, owner, mainDocument ? null : this.resourceBudget);
	}

	public void release(Source source) {
		((MySource) source).release();
	}

}

class MySource extends InputLimitedSource {
	final SourceResolver resolver;

	MySource(Source source, SourceResolver resolver, InputByteBudget budget) {
		super(source, budget);
		this.resolver = resolver;
	}

	public void release() {
		this.resolver.release(this.source);
	}
}

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
	 * 変換をまたぐHTTP応答キャッシュのTTL(秒)です。0はキャッシュ無効。
	 * 安全条件と設計は{@link HttpResponseCache}に集約しています。
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

	protected HttpClient createHttpClient(ExecutorService executor) {
		HttpClient.Builder builder = HttpClient.newBuilder();
		builder.executor(executor);
		// HttpClient は既定で Redirect.NEVER(3xx をリダイレクト先に追従せず
		// そのまま応答として返す)。HTTPS→HTTP への格下げだけは追従しない
		// NORMAL を明示する
		builder.followRedirects(HttpClient.Redirect.NORMAL);
		if (this.connectionTimeout > 0) {
			builder.connectTimeout(Duration.ofMillis(this.connectionTimeout));
		}
		if (this.proxyHost != null) {
			builder.proxy(ProxySelector.of(new InetSocketAddress(this.proxyHost, this.proxyPort)));
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
	 * 同時に走らせる先読み取得の上限。HTTP/2なら1接続に多重化される。
	 * 実測(wikipedia・画像約100点、素24.3s): 8で6.7〜7.4s、16で6.4〜6.7s
	 * ——ここから先は帯域・RTT側が支配的。
	 */
	private static final int PREFETCH_PARALLELISM = 12;

	/**
	 * <b>同一ホストへの同時取得の上限</b>(2026-08-28)。全体だけを絞っても
	 * 1つのサイトへ束で当たるため、配信側のレート制限に触れる。実測:
	 * risuから{@code upload.wikimedia.org}へ16並列で当てると6本が
	 * <b>HTTP 429</b>になり、巻き添えで本来のスタイルシート取得まで失敗して
	 * 変換が中止した。ブラウザの同時接続数(6前後)に倣って抑える。
	 */
	private static final int PREFETCH_PARALLELISM_PER_HOST = 4;

	/** close()後の遅延した先読み登録を止める(セッション跨ぎの汚染防止)。 */
	private volatile boolean prefetchClosed;

	/** 1変換セッションで先読みを試みるURIの上限(暴走・過剰取得の抑え)。 */
	private static final int PREFETCH_MAX_URIS = 256;

	/**
	 * 取得中の先読み。キーは要求URI(リダイレクトはHttpClientが追従する
	 * ため、同じ論理要求はここで合流する)。完了・失敗・中止で必ず除去し、
	 * Futureを完了させる——resolve()がここでawaitするため、未完了のまま
	 * 放置すると実要求が固まる。
	 */
	private final ConcurrentHashMap<URI, Inflight> prefetching = new ConcurrentHashMap<>();

	/**
	 * 取得中の先読み1件。{@code started}は<b>実際にHTTP要求を始めたか</b>で、
	 * 順番待ちのものと区別するために要る。実要求が順番待ちの先読みに
	 * 合流すると、直列より遅くなるうえ、待たされた末に失敗すると本来
	 * 成功したはずの資源まで落ちる(2026-08-28、risuで実際に発生)。
	 */
	private record Inflight(CompletableFuture<Void> future, java.util.concurrent.atomic.AtomicBoolean started) {
		Inflight() {
			this(new CompletableFuture<>(), new java.util.concurrent.atomic.AtomicBoolean());
		}
	}

	/** ホスト別の同時取得を絞る。 */
	private final ConcurrentHashMap<String, Semaphore> hostSlots = new ConcurrentHashMap<>();

	/**
	 * レート制限(429/503)を返したホスト。以降そのホストの先読みをやめる。
	 * 投機的な取得で配信側を怒らせて本来の取得まで失うのは本末転倒。
	 */
	private final java.util.Set<String> throttledHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * セッション局所の先読み結果ストア(キーは要求URI)。プロセス共通の
	 * {@link HttpResponseCache}とは別に持つ理由: 主文書応答のSet-Cookie
	 * (例: wikipediaのGeoIP)で以降の同ドメイン要求が全て「要求側
	 * キャッシュ対象外」になり、共有キャッシュ経由の受け渡しが成立しない
	 * (実測: hit 0/miss 256)。ここは応答側の条件
	 * (200・Set-Cookieなし・no-store/private/no-cacheなし)だけで保持し、
	 * 同一変換内のresolveが最優先で使う。発見時点の取得を消費時点で使う
	 * 意味論はChromeのpreload scannerと同じ。resolverのclose()
	 * (=セッション終了)で破棄する。
	 */
	private final ConcurrentHashMap<URI, HttpResponseCache.Entry> prefetched = new ConcurrentHashMap<>();

	/** セッション局所ストアの合計バイト上限(超過分は保持しない)。 */
	private static final long PREFETCHED_MAX_TOTAL_BYTES = 64L * 1024 * 1024;

	private final java.util.concurrent.atomic.AtomicLong prefetchedBytes = new java.util.concurrent.atomic.AtomicLong();

	/**
	 * 主文書のURI。<b>同じ資源を同一変換内で何度も取りに行かない</b>ための
	 * 判定に使います(2026-08-28)。
	 *
	 * <p>
	 * 共有キャッシュに載らない資源——Cookieを送るホストの画像・CSS背景など
	 * ——は、実要求の経路が本文を控えないと参照のたびに外向き取得が起きます。
	 * 実測: 寸法表を再利用した2回目のPaged SVG変換が同じ背景SVGを66回
	 * 取りに行き、5.0秒の変換が13.4秒になっていました。そこで副資源の本文は
	 * セッション局所ストアへ控えます。<b>主文書だけは控えません</b>——
	 * 流し込みのまま組版を始める設計で、読み切ってから渡すと最初のページが
	 * 出るまでが遅くなるためです。
	 * </p>
	 */
	private volatile URI mainUri;

	void setMainUri(final URI uri) {
		this.mainUri = uri;
	}

	private final Semaphore prefetchSlots = new Semaphore(PREFETCH_PARALLELISM);

	private final AtomicInteger prefetchStarted = new AtomicInteger();

	/**
	 * URIの非同期先読み(input.prefetch)。取得できた本文は同期経路と同じ
	 * 条件で{@link HttpResponseCache}へ入り、後続の{@link #resolve(URI)}が
	 * キャッシュ命中として受け取る。認証情報・Cookie・キャッシュ無効
	 * (TTL=0)の要求は{@link #cacheKey}がnullを返すため先読みしない——
	 * 並列取得が直列時と同値にならない(Cookie適用順・利用者固有応答)
	 * リスクを避ける。失敗は静かに捨て、実要求が正規経路で取り直す。
	 */
	void prefetch(final URI uri, final MySourceResolver cssGate) {
		if (this.prefetchClosed || this.prefetchStarted.get() >= PREFETCH_MAX_URIS) {
			return;
		}
		if (this.prefetched.containsKey(uri)) {
			return;
		}
		final HttpRequest request = this.createHttpRequest(uri);
		// 認証情報が付く要求は先読みしない(並列化で認証・利用者固有応答の
		// 意味論を変えない)。Cookieだけの要求は対象——主文書応答の
		// Set-Cookie(例: wikipediaのGeoIP)で以降の全要求にCookieが付くのが
		// 実サイトの通常で、発見時点の取得はChromeのpreload scannerと同じ
		if (request.headers().firstValue("Authorization").isPresent()
				|| this.findCredential(uri.getHost(), uri.getPort()) != null) {
			MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch auth skip: " + uri);
			return;
		}
		final String cacheKey = this.cacheKey(uri, request);
		if (cacheKey != null) {
			final HttpResponseCache.Entry entry = HttpResponseCache.get(cacheKey, this.cacheTtl);
			if (entry != null) {
				// **共有キャッシュにあってもセッション局所ストアへ写す**
				// (2026-08-28)。ここで単に打ち切ると、消費時点の
				// {@link #cacheKey}がCookie付き要求でnullになり(主文書の
				// Set-Cookie以降は同一ドメインの全要求にCookieが付く)、
				// 共有キャッシュを参照できずに取り直しになる。実測では
				// 2回目以降の変換で先読み合流が253件→約100件へ落ち、
				// 変換時間が11秒→29秒に戻っていた
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
						// 順番待ちのあいだに実要求がこのURIを取りに行ったら、
						// 投機は降りる(二重取得と余計な負荷を避ける)
						if (this.prefetching.get(uri) != inflight || this.prefetchClosed) {
							return;
						}
						inflight.started().set(true);
						final MyHttpSource source = new MyHttpSource(uri, client, request, cacheKey, false, false);
						try {
							// 本文を上限まで読み、応答側の条件を満たせば
							// セッション局所ストアへ(共有キャッシュへも、
							// 要求側条件を満たす場合のみ)。条件を満たさない
							// 応答は使わず、実要求に任せる
							final HttpResponseCache.Entry entry = source.readEntryForPrefetch();
							if (entry != null) {
								this.store(uri, entry);
								if (cacheKey != null && source.isSharedCacheable()) {
									HttpResponseCache.put(cacheKey, entry);
								}
								// 取得したのがスタイルシートなら、その中の
								// url()/@importも先読みする(深さ1のみ——
								// cssGate=nullで再帰を止める)。CSS背景画像は
								// 消費点解決の繰り返しが特に高くつく
								// (実測: wikipediaの虫めがねアイコン1つが
								// 64回直列取得されていた)
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
					// 先読みは常に任意。失敗の報告も実要求の正規経路に任せる
				} finally {
					this.prefetching.remove(uri, inflight);
					inflight.future().complete(null);
				}
			});
		} catch (final RejectedExecutionException e) {
			// close()直後など。先読みを断念する
			this.prefetching.remove(uri, inflight);
			inflight.future().complete(null);
		}
	}

	/** 先読み結果がCSSか(url()/@import走査の対象か)を判定します。 */
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

	/** CSS本文からurl()/@importの参照先を取り出します(CSSのURI基準)。 */
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
				// 参照が読めないだけ——実要求の正規経路が正
			}
		}
		return result;
	}

	/** セッション局所ストアへ入れます(合計上限を超える分は保持しない)。 */
	private void store(final URI uri, final HttpResponseCache.Entry entry) {
		final int length = entry.body().length;
		if (this.prefetchedBytes.addAndGet(length) <= PREFETCHED_MAX_TOTAL_BYTES) {
			this.prefetched.put(uri, entry);
		} else {
			this.prefetchedBytes.addAndGet(-length);
		}
	}

	public Source resolve(URI uri) throws IOException {
		// 同じURIの先読みが取得中なら合流する(二重取得しない)。先読みの
		// 失敗・キャンセルはここでは無視し、以降の正規経路で取り直す
		final Inflight inflight = this.prefetching.get(uri);
		if (inflight != null) {
			if (inflight.started().get()) {
				// 取得中なら合流する(同じ往復を二度払わない)
				try {
					inflight.future().join();
				} catch (final CancellationException | CompletionException ignore) {
					// 正規経路へ
				}
			} else {
				// **順番待ちには合流しない**(2026-08-28)。待たされたうえ、
				// 先読みが失敗すると本来取れるはずの資源まで落ちる。
				// mapから外して投機を降ろし、すぐ自分で取りに行く
				this.prefetching.remove(uri, inflight);
				inflight.future().complete(null);
			}
		}
		// セッション局所の先読み結果が最優先(同一変換内の受け渡し)
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
		// 副資源は本文を読み切ってから控える。主文書は**流しながら**控える
		// ——読み切ってから渡すと組版の開始が遅れる(mainUriのjavadoc参照)
		final boolean main = uri.equals(this.mainUri);
		final boolean remember = !main;
		return new MyHttpSource(uri, this.httpClient(), request, cacheKey, remember, main);
	}

	/**
	 * この要求のキャッシュキーを返します。キャッシュ対象外なら
	 * {@code null}(安全条件と設計は{@link HttpResponseCache}に集約)。
	 *
	 * <p>
	 * 要求側の除外は3つ: (1)Authorizationヘッダを送る(preemptive認証・
	 * カスタムヘッダ)、(2)当該ホストに一致する資格情報がある(401応答への
	 * Authenticator反応で利用者固有の応答になり得る)、(3)当該URIへ送られる
	 * Cookieがある(変換中のSet-Cookieで増えるため、要求を作る都度検査)。
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
		// キーはURI+経路(プロクシ)+送信ヘッダ全体。ヘッダで応答を変える
		// サーバー(Referer判定のhotlink保護等)が混線しないよう、送る
		// ヘッダが1つでも違えば別エントリにする
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
		// 実行前に破棄された先読みタスクはfinallyを通らない。resolve()が
		// joinで固まらないよう、残ったFutureをここで完了させる
		this.prefetching.forEach((uri, inflight) -> inflight.future().complete(null));
		this.prefetching.clear();
		this.hostSlots.clear();
		this.throttledHosts.clear();
		this.prefetched.clear();
		this.prefetchedBytes.set(0);

	}

	/**
	 * 読まれたバイトを写し取り、末尾まで読み切ったら渡します(2026-08-28)。
	 *
	 * <p>
	 * 上限を超えたら写しを捨てます(控えないだけで、読み出しは素通し)。
	 * 途中で捨てられた・読み切られなかった場合は何もしません——欠けた写しを
	 * 次の変換で使うと、黙って壊れた結果が出るためです。
	 * </p>
	 */
	static final class TeeInputStream extends InputStream {
		private final InputStream in;
		private final java.io.ByteArrayOutputStream copy = new java.io.ByteArrayOutputStream(1 << 16);
		private final int limit;
		private final java.util.function.Consumer<byte[]> sink;
		private boolean overflow, done;

		TeeInputStream(final InputStream in, final int limit, final java.util.function.Consumer<byte[]> sink) {
			this.in = in;
			this.limit = limit;
			this.sink = sink;
		}

		@Override
		public int read() throws IOException {
			final int b = this.in.read();
			if (b < 0) {
				this.finish();
			} else {
				this.record(new byte[] { (byte) b }, 0, 1);
			}
			return b;
		}

		@Override
		public int read(final byte[] buffer, final int off, final int len) throws IOException {
			final int n = this.in.read(buffer, off, len);
			if (n < 0) {
				this.finish();
			} else {
				this.record(buffer, off, n);
			}
			return n;
		}

		private void record(final byte[] buffer, final int off, final int len) {
			if (this.overflow) {
				return;
			}
			if (this.copy.size() + len > this.limit) {
				this.overflow = true;
				this.copy.reset();
				return;
			}
			this.copy.write(buffer, off, len);
		}

		private void finish() {
			if (this.done || this.overflow) {
				return;
			}
			this.done = true;
			this.sink.accept(this.copy.toByteArray());
			this.copy.reset();
		}

		@Override
		public int available() throws IOException {
			return this.in.available();
		}

		@Override
		public void close() throws IOException {
			this.in.close();
		}
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

	private HttpRequest createHttpRequest(URI uri) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
		// java.net.http.HttpClient は Accept-Encoding を自動送信せず、応答の
		// Content-Encoding も自動で解凍しない(帯域節約のため明示的に要求し、
		// getInputStream() 側で解凍する。static object store 由来のレスポンス
		// (S3 等)は Accept-Encoding 無指定でも Content-Encoding: gzip を
		// 返すことがあるため、要求の有無に関わらず解凍側の対応が本質)。
		builder.header("Accept-Encoding", "gzip, deflate");
		// User-Agent 未設定のままだと HttpClient 既定の "Java-http-client/x.x"
		// が送られ、bot policy を敷くサイト(実例: Wikipedia が
		// robots policy 遵守目的で明示的な User-Agent を要求し、無ければ
		// 403 で拒否する)からコンテンツを取得できない実バグを2026-07-18の
		// 実地テストで発見。管理者が input.http-header*.name で明示的に
		// User-Agent を設定している場合はそちらを優先し、上書きしない
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

	private static class HttpCredential {
		final String host;
		final int port;
		final String user;
		final String password;

		HttpCredential(String host, int port, String user, String password) {
			this.host = host;
			this.port = port;
			this.user = user;
			this.password = password;
		}

		boolean matches(String host, int port) {
			return this.host.equalsIgnoreCase(host) && (this.port == -1 || this.port == port);
		}
	}

	/**
	 * 読み取り毎にストール上限を課すラッパーです(2026-08-08)。
	 * {@code java.net.http}の応答ボディ({@code HttpResponseInputStream})は
	 * ソケットタイムアウトに相当する仕組みを持たず、配信が止まると
	 * {@code read}が永久にブロックする。読み取りを仮想スレッドへ委ねて
	 * 時限を課し、超過時は下位ストリームを閉じて{@link IOException}にする
	 * (閉じないと委ねた読み取りが残り続ける)。
	 */
	private static final class StallGuardInputStream extends InputStream {
		private final InputStream delegate;
		private final ExecutorService executor;
		private final long timeoutMillis;
		private final byte[] one = new byte[1];

		StallGuardInputStream(final InputStream delegate, final ExecutorService executor, final long timeoutMillis) {
			this.delegate = delegate;
			this.executor = executor;
			this.timeoutMillis = timeoutMillis;
		}

		@Override
		public int read() throws IOException {
			final int n = this.read(this.one, 0, 1);
			return n <= 0 ? -1 : (this.one[0] & 0xFF);
		}

		@Override
		public int read(final byte[] b, final int off, final int len) throws IOException {
			final java.util.concurrent.Future<Integer> f = this.executor.submit(() -> this.delegate.read(b, off, len));
			try {
				return f.get(this.timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
			} catch (final java.util.concurrent.TimeoutException e) {
				f.cancel(true);
				try {
					this.delegate.close();
				} catch (final IOException ignore) {
					// 停止したストリームの後始末失敗は握りつぶす
				}
				throw new IOException("応答の読み取りが " + this.timeoutMillis + "ms 停止しました", e);
			} catch (final InterruptedException e) {
				f.cancel(true);
				Thread.currentThread().interrupt();
				throw new java.io.InterruptedIOException();
			} catch (final java.util.concurrent.ExecutionException e) {
				final Throwable cause = e.getCause();
				if (cause instanceof IOException ioe) {
					throw ioe;
				}
				if (cause instanceof RuntimeException re) {
					throw re;
				}
				throw new IOException(cause);
			}
		}

		@Override
		public int available() throws IOException {
			return this.delegate.available();
		}

		@Override
		public void close() throws IOException {
			this.delegate.close();
		}
	}

	class MyHttpSource extends AbstractSource {
		private final HttpClient httpClient;
		private final HttpRequest request;
		private final String cacheKey;
		/** 本文をセッション局所ストアへ控えるか(同一変換内の再取得防止)。 */
		private final boolean remember;
		/**
		 * 主文書か。<b>流しながら</b>控えます(2026-08-28)。同じセッションで
		 * もう一度変換するとき——webappの文字サイズ変更のように——
		 * 取り直すとページの内容が変わりうるので、最初に読んだものを使う。
		 */
		private final boolean main;
		private CompletableFuture<HttpResponse<InputStream>> responseFuture;
		private HttpResponse<InputStream> response;
		private InputStream in;
		private String mimeType;
		private String contentEncoding;
		private String encoding;
		private boolean exists;
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
			// 応答側のキャッシュ判定(要求側は resolve() の cacheKey)。
			// 本文を上限まで先読みして保存する——EOF検出契機の保存は
			// GZIPInputStreamが下位ストリームを-1まで読み切るとは限らず
			// (トレーラはバッファ内で消費され得る)、gzip配信のCSSという
			// 主目的で不発になるため。上限超過時は読んだ分+残りを
			// 連結して素通しする(設計は HttpResponseCache に集約)
			// 共有キャッシュに載らない資源も、副資源と分かっていれば
			// セッション局所ストアへ控える(2026-08-28。同一変換内で同じ
			// 資源を何度も取りに行かない——{@link #discovered}のjavadoc)
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
				// **流しながら控える**(2026-08-28)。同じセッションでもう一度
				// 変換するとき、取り直すとページの内容が変わりうる(実サイトは
				// 読み込むたびに違うHTMLを返す)。読み切ってから渡すと組版の
				// 開始が遅れるので、渡しながら写しを取る。読み切らなかったら
				// 控えない——中途半端な写しを次の変換で使うほうが害が大きい
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
		 * 接続済み応答の復号ボディを返します(ストール時限+Content-Encoding
		 * 解凍)。
		 *
		 * <p>
		 * ストール時限(2026-08-08): HttpRequest.timeout()は応答ヘッダ到着
		 * までしか守らず、ボディのストリーミングが止まるとレイアウト
		 * スレッドが永久に固まる——kakaku.comの外部リソース1本で変換全体が
		 * 2000秒超ハングした実バグ。input.http.socket.timeout
		 * (requestTimeout)を読み取り毎のストール上限として使う。
		 * 解凍: HttpClientはContent-Encodingを自動解凍しない。未対応の
		 * ままだと圧縮バイト列がそのままパーサに渡り、大量の文字化けとして
		 * 観測される。
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
					// br(Brotli)等、未対応の符号化はそのまま渡す(現状 br は
					// 要求していないため通常は到達しない)
					break;
				}
			}
			return body;
		}

		/**
		 * 先読み用: 本文を上限まで読み切ってエントリにします。200以外・
		 * 上限超過は{@code null}(その資源は実要求が正規経路で取り直す)。
		 *
		 * <p>
		 * ここでは応答側のキャッシュ条件(Set-Cookie等)を課さない——
		 * セッション局所の受け渡しは「同一変換内で同じ資源を2回取らない」
		 * だけで、Chromeが同一ロード内のmemory cacheでヘッダに関わらず
		 * 再利用するのと同じ意味論。Set-Cookieの副作用は応答受信時に
		 * cookieManagerが処理済みで、本文の再利用とは独立している
		 * (実例: upload.wikimedia.orgが画像応答の一部にWMF-Uniq追跡
		 * Cookieを付け、厳格条件では画像の半数が保持できなかった)。
		 * プロセス共通の{@link HttpResponseCache}への保存可否は、従来
		 * どおり{@link #isCacheableResponse()}で別途判定する。
		 * </p>
		 */
		HttpResponseCache.Entry readEntryForPrefetch() throws IOException {
			this.tryConnect();
			final int status = this.response.statusCode();
			if (status != 200) {
				if (status == 429 || status == 503) {
					// レート制限。このホストの先読みは以降やめる(投機で
					// 配信側を怒らせて本来の取得まで失うのは本末転倒)
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

		/** 応答が共有キャッシュ条件を満たすか(先読みタスクからの判定用)。 */
		boolean isSharedCacheable() {
			return this.isCacheableResponse();
		}

		/**
		 * 応答が共有キャッシュへ保存してよいものかを返します。
		 * 200のGET応答で、Set-Cookieが無く、Cache-Controlが
		 * no-store/no-cache/privateのいずれも含まず、Vary:*でないこと。
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
			this.exists = this.response.statusCode() != 404;
			this.mimeType = this.response.headers().firstValue("Content-Type").orElse(null);
			this.contentEncoding = this.response.headers().firstValue("Content-Encoding").orElse(null);
			this.encoding = parseCharset(this.mimeType);
			// Content-Length は圧縮後のバイト数であり、解凍後の長さとは
			// 一致しない(getInputStream() が解凍する場合)。誤った長さを
			// 伝えるより不明(-1)の方が安全
			this.contentLength = this.contentEncoding != null ? -1
					: this.response.headers().firstValueAsLong("Content-Length").orElse(-1);
			this.lastModified = parseLastModified(this.response.headers().firstValue("Last-Modified").orElse(null));
		}

		private void startConnection() {
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
	 * 応答の{@code Cache-Control}からmax-age(秒)を取り出します。
	 * 共有キャッシュなのでs-maxageを優先し、どちらも無ければ-1。
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
				// 不正な値は無指定とみなす
			}
		}
		return sMaxAge >= 0 ? sMaxAge : maxAge;
	}

	/**
	 * {@link HttpResponseCache}のエントリを提供するSourceです。中身は
	 * 解凍済みバイト列なので、ネットワークにも{@code HttpClient}にも
	 * 触れません。
	 */
	static final class CachedHttpSource extends AbstractSource {
		private final HttpResponseCache.Entry entry;

		CachedHttpSource(final URI uri, final HttpResponseCache.Entry entry) {
			super(uri);
			this.entry = entry;
		}

		public String getMimeType() {
			return this.entry.mimeType();
		}

		public String getEncoding() {
			return this.entry.encoding();
		}

		public long getLength() {
			return this.entry.body().length;
		}

		public boolean exists() {
			return true;
		}

		public boolean isInputStream() {
			return true;
		}

		public boolean isReader() {
			return this.entry.encoding() != null;
		}

		public InputStream getInputStream() {
			return new java.io.ByteArrayInputStream(this.entry.body());
		}

		public Reader getReader() throws IOException {
			if (this.entry.encoding() == null) {
				throw new UnsupportedOperationException("Encoding not set");
			}
			return new InputStreamReader(this.getInputStream(), this.entry.encoding());
		}

		public File getFile() {
			throw new UnsupportedOperationException();
		}

		public SourceValidity getValidity() {
			return new HttpValidity(this.entry.lastModified());
		}

		public void close() {
			// メモリ上のバイト列なので解放するものがない
		}
	}

	private static class HttpValidity implements SourceValidity {
		private static final long serialVersionUID = 0;

		private final long lastModified;

		HttpValidity(long lastModified) {
			this.lastModified = lastModified;
		}

		public Validity getValid() {
			return Validity.UNKNOWN;
		}

		public Validity getValid(SourceValidity validity) {
			if (!(validity instanceof HttpValidity)) {
				return Validity.UNKNOWN;
			}
			long other = ((HttpValidity) validity).lastModified;
			if (this.lastModified == -1 || other == -1) {
				return Validity.UNKNOWN;
			}
			return this.lastModified == other ? Validity.VALID : Validity.INVALID;
		}
	}
}
