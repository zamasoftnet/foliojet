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

	public void setup(URI uri, Map<String, String> props, MessageHandler mh) {
		CompositeSourceResolver resolver = CompositeSourceResolver.createGenericCompositeSourceResolver();
		MyHttpSourceResolver httpResolver = new MyHttpSourceResolver();
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
		this.restrictedResolver.reset();
		this.cachedResolver.reset();
		this.userResolver = null;
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
			if (this.userResolver != null) {
				try {
					Source source = this.userResolver.resolve(uri);
					return new MySource(source, this.userResolver);
				} catch (FileNotFoundException e1) {
					// ignore
				}
			}
			Source source = this.restrictedResolver.resolve(uri, force);
			return new MySource(source, this.restrictedResolver);
		}
	}

	public void release(Source source) {
		((MySource) source).release();
	}

	protected void finalize() throws Throwable {
		this.reset();
		super.finalize();
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

	protected HttpClient createHttpClient() {
		HttpClient.Builder builder = HttpClient.newBuilder();
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

	public Source resolve(URI uri) throws IOException {
		return new MyHttpSource(uri, this.createHttpClient());
	}

	public void release(Source source) {
		try {
			source.close();
		} catch (IOException e) {
			// ignore
		}
	}

	private HttpRequest createHttpRequest(URI uri) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
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
		private HttpResponse<InputStream> response;
		private InputStream in;
		private String mimeType;
		private String encoding;
		private boolean exists;
		private long lastModified = -1;
		private long contentLength = -1;

		MyHttpSource(URI uri, HttpClient httpClient) {
			super(uri);
			this.httpClient = httpClient;
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
			this.close();
			this.tryConnect();
			this.in = this.response.body();
			if (this.in == null) {
				throw new FileNotFoundException();
			}
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
			this.in = null;
			this.response = null;
		}

		private void tryConnect() throws IOException {
			if (this.response != null) {
				return;
			}
			try {
				this.response = this.httpClient.send(createHttpRequest(this.uri), HttpResponse.BodyHandlers.ofInputStream());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException(e);
			}
			this.exists = this.response.statusCode() != 404;
			this.mimeType = this.response.headers().firstValue("Content-Type").orElse(null);
			this.encoding = parseCharset(this.mimeType);
			this.contentLength = this.response.headers().firstValueAsLong("Content-Length").orElse(-1);
			this.lastModified = parseLastModified(this.response.headers().firstValue("Last-Modified").orElse(null));
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
