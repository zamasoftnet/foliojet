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
	 * ローカル資源を許していない利用者に対して、このURIを拒むかどうかです。
	 *
	 * <p>
	 * スキームで「遠隔かどうか」を見るだけでは足りません。{@code http:}でも
	 * <b>宛先がサーバー自身やその隣のネットワークなら、サーバーを踏み台にして
	 * 内側へ届いてしまう</b>からです(2026-09-08)。名前を解決して、
	 * ループバック・リンクローカル・私設アドレスなら拒みます。
	 * </p>
	 *
	 * <p>
	 * <b>公開アドレスは通します。</b>許可されている外部の資源は今までどおり取れます。
	 * </p>
	 *
	 * <p>
	 * 判定した時刻と実際に接続する時刻は違うので、その間に名前解決の結果が
	 * 変われば擦り抜けます(DNSリバインディング)。それを塞ぐには解決した
	 * アドレスを固定して接続する必要があり、ここでは扱いません。
	 * </p>
	 */
	boolean resolvesToLocalNetwork(final URI uri) {
		// **ホストを持つネットワークのスキームだけが対象**。data: のように
		// ホストの無いものは、そもそもどこへも接続しないので判定しない
		// (ここを「内側」と誤判定して埋め込み画像を拒む回帰を出した。2026-09-08)
		final String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			return false;
		}
		final String host = uri.getHost();
		if (host == null || host.isEmpty()) {
			// http(s) なのにホストを取り出せない形は通さない
			return true;
		}
		final java.net.InetAddress[] addresses;
		try {
			addresses = java.net.InetAddress.getAllByName(host);
		} catch (final java.net.UnknownHostException e) {
			// **確かめられないものは内側とみなす。** かつては「解決できない
			// ものは取得しても失敗する」として通していたが、判定の時刻と
			// 接続の時刻は違う。ここで解決できなくても、接続の時点では
			// 解決できることがある(DNSのキャッシュ、split-horizon)。
			// 検証が失敗したら拒むのがこの判定の筋(2026-09-08)
			return true;
		}
		for (final java.net.InetAddress address : addresses) {
			if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
					|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
				return true;
			}
			// IPv4射影・変換アドレスで私設アドレスを包んだ形も見る
			final byte[] raw = address.getAddress();
			if (raw.length == 16 && isUniqueLocalIPv6(raw)) {
				return true;
			}
		}
		return false;
	}

	/** IPv6のユニークローカル(fc00::/7)かどうかです。 */
	private static boolean isUniqueLocalIPv6(final byte[] raw) {
		return (raw[0] & 0xFE) == 0xFC;
	}

	/**
	 * <b>この URL へ実際に接続してよいか</b>を返します。
	 *
	 * <p>
	 * 押し込み資源や差し込まれたリゾルバを
	 * <b>考慮しません</b>。転送先のように「これから網へ出る宛先」を判定するための
	 * もので、そこは呼び出し側が供給しうる余地がないからです。
	 * 「差し込まれたリゾルバがあるから許す」という緩い判定を使うと、
	 * 何でも許してしまいます(2026-09-08に実測)。
	 * </p>
	 */
	boolean permitsNetworkTarget(final URI uri) {
		if (uri == null) {
			return false;
		}
		if (!this.localAccessAllowed && (!isRemoteScheme(uri) || this.resolvesToLocalNetwork(uri))) {
			return false;
		}
		return this.restrictedResolver.permits(uri);
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
		// **転送先も同じ判定に掛ける**(2026-09-08)。HttpClient に追従を任せると、
		// 許可した公開ホストがサーバーの内側へ 302 したときに判定を通らない。
		// ローカル資源を許している利用者は従来どおり HttpClient に任せる
		// ACL は要求の設定が進んでから決まるので、判定するかどうかは遅延評価にする
		httpResolver.setRedirectGuard(target -> this.permitsNetworkTarget(target),
				() -> !this.localAccessAllowed || this.restricted);
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
		if (!this.localAccessAllowed && this.resolvesToLocalNetwork(uri)) {
			PREFETCH_LOG.fine(() -> "prefetch local target deny: " + uri);
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
			// **遠隔スキームだが宛先がサーバーの内側**。ここで確定して拒む。
			// 差し込まれたリゾルバへ回してはいけない——汎用リゾルバなら
			// そのままネットワークへ取りに行ってしまう(2026-09-08に実測)
			if (!this.localAccessAllowed && this.resolvesToLocalNetwork(uri)) {
				throw new SecurityException(
						"Access to the server's own network is not permitted for this user: " + uri);
			}
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
