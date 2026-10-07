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

// Split from MyHttpSourceResolver.java on 2026-09-02 (body only moved; design review: 10 classes, 1,560 lines).
class MySourceResolver implements SourceResolver {
	protected CachedSourceResolver cachedResolver = new CachedSourceResolver();
	protected SourceResolver userResolver = null;
	protected RestrictedSourceResolver restrictedResolver = new RestrictedSourceResolver();
	private MyHttpSourceResolver httpResolver = null;
	private InputByteBudget resourceBudget;
	private int resourceCountLimit = -1;
	private final Set<URI> resourceUris = new HashSet<>();

	/**
	 * Whether any {@code input.include} / {@code input.exclude} setting has been supplied.
	 *
	 * <p>
	 * <b>If configured, it constrains retrieval for all schemes.</b> Without configuration,
	 * no restriction exists, so retains the previous order of trying the injected resolver first.
	 * The restriction defaults to denying unmatched resources; putting it first unconditionally
	 * would block all retrieval for users who have not configured it.
	 * </p>
	 */
	private boolean restricted = false;

	/** Schemes allowed for remote retrieval. All others are treated as local resources. */
	private static final Set<String> REMOTE_SCHEMES = Set.of("http", "https", "data");

	/**
	 * Whether to allow retrieval of local resources (such as {@code file:}).
	 *
	 * <p>
	 * <b>This is not an I/O property.</b> Clients cannot change it; the server (daemon)
	 * decides for each authenticated user. Defaults to allowing access, so embedded use
	 * and command-line behavior remain unchanged: the principals running them can already
	 * read files accessible to the process, making such restrictions pointless.
	 * </p>
	 *
	 * <p>
	 * {@code input.include}/{@code input.exclude} cannot serve this purpose. They let clients
	 * restrict themselves, are {@link #reset()} for each session, and the main document
	 * bypasses the ACL with {@code force}.
	 * </p>
	 */
	private boolean localAccessAllowed = true;

	void setLocalAccessAllowed(final boolean allowed) {
		this.localAccessAllowed = allowed;
	}

	/**
	 * Whether to reject this URI for users without permission to access local resources.
	 *
	 * <p>
	 * Checking the scheme for remote access is insufficient. Even with {@code http:},
	 * <b>a destination on the server itself or its adjacent network lets callers use the server
	 * to reach internal resources</b> (2026-09-08). Resolves the name and rejects
	 * loopback, link-local, and private addresses.
	 * </p>
	 *
	 * <p>
	 * <b>Allows public addresses.</b> Permitted external resources remain retrievable as before.
	 * </p>
	 *
	 * <p>
	 * The check and actual connection occur at different times, so a change in name resolution
	 * between them can bypass the check (DNS rebinding). Preventing this requires connecting
	 * to the pinned resolved address, which is outside the scope here.
	 * </p>
	 */
	boolean resolvesToLocalNetwork(final URI uri) {
		// **Only network schemes with hosts are subject to this check.** Hostless schemes
		// such as data: do not connect anywhere, so do not check them.
		// (Misclassifying them as internal caused a regression rejecting embedded images. 2026-09-08.)
		final String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			return false;
		}
		final String host = uri.getHost();
		if (host == null || host.isEmpty()) {
			// Do not allow http(s) forms from which the host cannot be extracted.
			return true;
		}
		final java.net.InetAddress[] addresses;
		try {
			addresses = java.net.InetAddress.getAllByName(host);
		} catch (final java.net.UnknownHostException e) {
			// **Treat unverifiable destinations as internal.** Previously allowed unresolved names
			// on the assumption that retrieval would fail anyway, but the check and connection
			// occur at different times. A name unresolved here may resolve
			// at connection time (DNS caching, split-horizon).
			// Rejecting failed verification is consistent with this check's purpose (2026-09-08).
			return true;
		}
		for (final java.net.InetAddress address : addresses) {
			if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
					|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
				return true;
			}
			// Also check private addresses wrapped in IPv4-mapped or translated addresses.
			final byte[] raw = address.getAddress();
			if (raw.length == 16 && isUniqueLocalIPv6(raw)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the address is IPv6 unique-local (fc00::/7). */
	private static boolean isUniqueLocalIPv6(final byte[] raw) {
		return (raw[0] & 0xFE) == 0xFC;
	}

	/**
	 * Returns <b>whether an actual connection to this URL is allowed</b>.
	 *
	 * <p>
	 * <b>Does not consider</b> uploaded resources or injected resolvers. This checks destinations
	 * about to be accessed over the network, such as redirect targets, where the caller
	 * has no opportunity to supply the resource. A permissive check that allows access merely
	 * because an injected resolver exists would allow everything (measured on 2026-09-08).
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
	 * Whether the scheme allows remote retrieval. Treats schemeless URIs as local,
	 * since they can refer to files in the current working directory.
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
		// **Apply the same check to redirect targets** (2026-09-08). Letting HttpClient follow redirects
		// bypasses the check when an allowed public host sends a 302 into the server's internal network.
		// For users allowed local resources, delegate to HttpClient as before.
		// Evaluate whether to check lazily, since the ACL is determined as request configuration progresses.
		httpResolver.setRedirectGuard(target -> this.permitsNetworkTarget(target),
				() -> !this.localAccessAllowed || this.restricted);
		httpResolver.setMainUri(uri);
		if (UAProps.INPUT_HTTP_REFERER.getBoolean(props, mh)) {
			httpResolver.setReferer(uri);
		}

		// Headers.
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

		// Proxy.
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

		// Authentication.
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
	 * Requests asynchronous prefetch of external resources (input.prefetch, 2026-08-27).
	 * Targets only http(s), using the same checks as the synchronous path: resources sent by
	 * the client over CTIP (cachedResolver) need no network access and are excluded. Passes only
	 * URLs allowed by the ACL (input.include/exclude; ACL always comes first for http) to the HTTP
	 * resolver. Silently discards denials and failures (the actual request raises the normal
	 * SecurityException, etc.). Prefetch does not count against resource-byte or resource-count
	 * budgets; counts only once, as before, when the document actually requests the resource.
	 */
	public void prefetch(final URI uri) {
		this.prefetch(uri, true);
	}

	/**
	 * @param scanCss whether to follow {@code url()}/{@code @import} in retrieved stylesheets
	 *                for one level of prefetch (a flag to stop recursion on URLs discovered in CSS)
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
			// Not a client-uploaded resource: eligible for prefetch.
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

	/** Logger for tracing prefetch activity (FINE logs each decision and join). */
	static final java.util.logging.Logger PREFETCH_LOG = java.util.logging.Logger
			.getLogger("net.zamasoft.foliojet.driver.prefetch");

	/**
	 * Looks for resources in this order.
	 *
	 * 1. Cached resources 2. Configured resolver 3. Server-side resources
	 */
	public Source resolve(URI uri) throws IOException, FileNotFoundException {
		return this.resolve(uri, false);
	}

	public Source resolve(URI uri, boolean force) throws IOException, SecurityException {
		try {
			// Resources sent by the client with CTISession.resource().
			// These are **the client's own contents**, so even a file: URI
			// does not read a server file.
			Source source = this.cachedResolver.resolve(uri);
			return this.wrap(source, this.cachedResolver, force);
		} catch (FileNotFoundException e) {
			// **Prefer the configured resolver for HTTP/HTTPS** (2026-08-02).
			// If an injected resolver (the general resolver installed by the CLI or CTI driver
			// via setSourceResolver) fetches first, **none** of the User-Agent, headers, proxy,
			// cookies, or authentication configured with I/O properties
			// take effect. Measured: neither the default User-Agent (CopperPDF)
			// nor input.http.header.* settings were sent; instead, the JDK default
			// Java/21.0.11 was sent (Wikipedia returned 403 and could not be fetched).
			final String scheme = uri.getScheme();
			final boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
			// **Apply configured restrictions first** (2026-08-03, owner's decision).
			// If an injected resolver resolves local files first,
			// input.include/input.exclude are bypassed.
			// Both the command line and webapp inject general resolvers, so
			// servers converting untrusted HTML could not prevent
			// local file reads. Decide denial (SecurityException)
			// here and do not pass the request to the injected resolver.
			final boolean aclFirst = this.restricted || http;
			if (this.userResolver != null && !aclFirst) {
				try {
					Source source = this.userResolver.resolve(uri);
					return this.wrap(source, this.userResolver, force);
				} catch (FileNotFoundException e1) {
					// ignore
				}
			}
			// **Beyond this point, retrieval accesses the server's own filesystem.**
			// Do not enter this path for remote users without permission.
			// The injected resolver (in CTIP, the path where the client sends each resource
			// when the server requests it) supplies **the client's own resources**,
			// so leave it open and try only that path.
			// **A remote scheme whose destination is internal to the server.** Reject it definitively here.
			// Do not pass it to the injected resolver: a general resolver would
			// fetch it over the network directly (measured on 2026-09-08).
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
						// The client does not have it either.
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
				// Pass resources **allowed but not retrieved** to the injected
				// resolver (embedded use with a custom retrieval mechanism, or
				// a request to the client in CTIP). Denials throw
				// SecurityException and never reach here.
				// Pass exceptions besides FileNotFoundException as well because custom schemes
				// produce MalformedURLException("unknown protocol")
				// (2026-08-16). URIs resolvable only by the client could not
				// be used with input.include.
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
