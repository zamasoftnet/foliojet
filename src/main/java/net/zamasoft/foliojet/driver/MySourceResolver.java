package net.zamasoft.foliojet.driver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.cache.CachedSourceResolver;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.restricted.RestrictedSourceResolver;

// 2026-09-02 に MyHttpSourceResolver.java から分けた(本文は移しただけ。設計レビュー「10クラス 1,560行」)。
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
