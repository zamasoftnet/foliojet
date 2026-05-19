package net.zamasoft.foliojet.driver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.cache.CachedSourceResolver;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.SourceWrapper;
import net.zamasoft.zstream.resolver.protocol.http.HTTPSource;
import net.zamasoft.zstream.resolver.protocol.http.HTTPSourceResolver;
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

		RequestConfig.Builder config = httpResolver.config;
		CredentialsProvider credsProvider = httpResolver.credsProvider;
		config.setConnectionRequestTimeout(UAProps.INPUT_HTTP_CONNECTION_TIMEOUT.getInteger(props, mh));
		config.setSocketTimeout(UAProps.INPUT_HTTP_SOCKET_TIMEOUT.getInteger(props, mh));

		// プロクシ
		String proxyHost = UAProps.INPUT_HTTP_PROXY_HOST.getString(props);
		if (proxyHost != null) {
			int proxyPort = UAProps.INPUT_HTTP_PROXY_PORT.getInteger(props, mh);
			HttpHost proxy = new HttpHost(proxyHost, proxyPort);
			config.setProxy(proxy);
			String user = UAProps.INPUT_HTTP_PROXY_AUTHENTICATION_USER.getString(props);
			String password = UAProps.INPUT_HTTP_PROXY_AUTHENTICATION_PASSWORD.getString(props);
			if (password == null) {
				password = "";
			}
			if (user != null) {
				Credentials credentials = new UsernamePasswordCredentials(user, password);
				credsProvider.setCredentials(new AuthScope(proxyHost, proxyPort), credentials);
			}
		}

		// 認証
		httpResolver.preemptiveAuth = null;
		boolean preemptive = UAProps.INPUT_HTTP_AUTHENTICATION_PREEMPTIVE.getBoolean(props, mh);
		if (preemptive) {
			HttpRequestInterceptor preemptiveAuth = new HttpRequestInterceptor() {
				public void process(final HttpRequest request, final HttpContext context)
						throws HttpException, IOException {
					HttpClientContext clientContext = (HttpClientContext) context;
					AuthState authState = clientContext.getTargetAuthState();
					CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
					HttpHost targetHost = clientContext.getTargetHost();
					// If not auth scheme has been initialized yet
					if (authState.getAuthScheme() == null) {
						AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
						// Obtain credentials matching the target host
						Credentials creds = credsProvider.getCredentials(authScope);
						// If found, generate BasicScheme preemptively
						if (creds != null) {
							authState.update(new BasicScheme(), creds);
						}
					}
				}

			};
			httpResolver.preemptiveAuth = preemptiveAuth;
		}
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
			String realm = (String) props.get(prefix + "realm");
			String scheme = (String) props.get(prefix + "scheme");
			String password = (String) props.get(prefix + "password");
			if (password == null) {
				password = "";
			}

			AuthScope authScope = new AuthScope(host, port, realm, scheme);
			Credentials credentials = new UsernamePasswordCredentials(user, password);
			credsProvider.setCredentials(authScope, credentials);
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

			BasicClientCookie cookie = new BasicClientCookie(name, value);
			cookie.setDomain(domain);
			cookie.setPath(path);
			cookie.setSecure(false);
			httpResolver.cookieStore.addCookie(cookie);
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

class MyHttpSourceResolver extends HTTPSourceResolver {
	protected RequestConfig.Builder config = RequestConfig.custom();
	protected CredentialsProvider credsProvider = new BasicCredentialsProvider();
	protected CookieStore cookieStore = new BasicCookieStore();
	protected HttpRequestInterceptor preemptiveAuth = null;
	protected URI refURI = null;

	protected List<Header> headers = null;

	public void setReferer(URI refURI) {
		this.refURI = refURI;
	}

	public void addHeader(String name, String value) {
		if (this.headers == null) {
			this.headers = new ArrayList<Header>();
		}
		this.headers.add(new BasicHeader(name, value));
	}

	protected CloseableHttpClient createHttpClient() {
		HttpClientBuilder builder = HttpClientBuilder.create();
		final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		builder.setConnectionManager(cm);
		builder.setDefaultRequestConfig(this.config.build());
		builder.setDefaultCredentialsProvider(this.credsProvider);
		builder.setDefaultCookieStore(this.cookieStore);
		if (this.preemptiveAuth != null) {
			builder.addInterceptorFirst(this.preemptiveAuth);
		}
		final CloseableHttpClient client = builder.build();
		return client;
	}

	public Source resolve(URI uri) throws IOException {
		final CloseableHttpClient client = this.createHttpClient();
		final MyHttpSource source = new MyHttpSource(uri, client);
		return source;
	}

	class MyHttpSource extends HTTPSource {
		public MyHttpSource(URI uri, CloseableHttpClient httpClient) {
			super(uri, httpClient);
		}

		protected HttpUriRequest createHttpRequest() {
			HttpUriRequest req = super.createHttpRequest();
			if (refURI != null && !refURI.equals(this.uri)) {
				req.addHeader("Referer", refURI.toASCIIString());
			}
			if (headers != null) {
				for (int i = 0; i < headers.size(); ++i) {
					Header header = (Header) headers.get(i);
					req.addHeader(header);
				}
			}
			return req;
		}
	}
}
