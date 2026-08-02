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
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

	public void setup(URI uri, Map<String, String> props, MessageHandler mh) {
		this.closeHttpResolver();
		CompositeSourceResolver resolver = CompositeSourceResolver.createGenericCompositeSourceResolver();
		MyHttpSourceResolver httpResolver = new MyHttpSourceResolver();
		this.httpResolver = httpResolver;
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
		this.restrictedResolver.include(uriPattern);
	}

	public void exclude(URI uriPattern) {
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
	}

	private void closeHttpResolver() {
		if (this.httpResolver == null) {
			return;
		}
		this.httpResolver.close();
		this.httpResolver = null;
	}

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
			Source source = this.cachedResolver.resolve(uri);
			return new MySource(source, this.cachedResolver);
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
			if (this.userResolver != null && !http) {
				try {
					Source source = this.userResolver.resolve(uri);
					return new MySource(source, this.userResolver);
				} catch (FileNotFoundException e1) {
					// ignore
				}
			}
			try {
				Source source = this.restrictedResolver.resolve(uri, force);
				return new MySource(source, this.restrictedResolver);
			} catch (FileNotFoundException e2) {
				if (this.userResolver == null || !http) {
					throw e2;
				}
				// 設定済み経路で見つからないHTTPは、差し込まれた
				// リゾルバ(独自の取得手段を持つ埋め込み利用)へ回す
				Source source = this.userResolver.resolve(uri);
				return new MySource(source, this.userResolver);
			}
		}
	}

	public void release(Source source) {
		((MySource) source).release();
	}

}

class MySource extends SourceWrapper {
	final SourceResolver resolver;

	MySource(Source source, SourceResolver resolver) {
		super(source);
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

	public Source resolve(URI uri) throws IOException {
		return new MyHttpSource(uri, this.httpClient(), this.createHttpRequest(uri));
	}

	public void release(Source source) {
		try {
			source.close();
		} catch (IOException e) {
			// ignore
		}
	}

	public synchronized void close() {
		if (this.executor != null) {
			this.executor.shutdownNow();
			this.executor = null;
		}
		this.httpClient = null;
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

	class MyHttpSource extends AbstractSource {
		private final HttpClient httpClient;
		private final HttpRequest request;
		private CompletableFuture<HttpResponse<InputStream>> responseFuture;
		private HttpResponse<InputStream> response;
		private InputStream in;
		private String mimeType;
		private String contentEncoding;
		private String encoding;
		private boolean exists;
		private long lastModified = -1;
		private long contentLength = -1;

		MyHttpSource(URI uri, HttpClient httpClient, HttpRequest request) {
			super(uri);
			this.httpClient = httpClient;
			this.request = request;
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
			InputStream body = this.response.body();
			if (body == null) {
				throw new FileNotFoundException();
			}
			// HttpClient は Content-Encoding を自動解凍しないため、ここで
			// 明示的に解凍する(未対応のままだと圧縮バイト列がそのまま
			// パーサに渡り、大量の文字化けとして観測される)。
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
			this.in = body;
			return this.in;
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
