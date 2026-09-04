package net.zamasoft.foliojet.ua;

import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.SourceWrapper;

/** 画像取得の失敗段階を、安全な固定語で通知します。 */
public final class ImageLoadDiagnostics {
	private static final Logger LOG = Logger.getLogger(ImageLoadDiagnostics.class.getName());

	@FunctionalInterface
	public interface Loader<T> {
		public T load(URI uri, Source source) throws Exception;
	}

	@FunctionalInterface
	public interface UriResolver {
		public URI resolve() throws Exception;
	}

	private ImageLoadDiagnostics() {
		// utility class
	}

	/** 通常の画像を読みます。既知寸法を使える経路では資源を解決しません。 */
	public static Image loadImage(final UserAgent ua, final URI uri, final boolean useKnownMetrics) {
		if (useKnownMetrics) {
			final Image known = ua.getImageMetrics(uri);
			if (known != null) {
				return known;
			}
		}
		return load(ua, uri, (resolvedUri, source) -> ua.getImage(resolvedUri, source));
	}

	/** HTMLのsrcを解決し、指定されたMIME型を優先して画像を読みます。 */
	public static Image loadImage(final UserAgent ua, final String uriText, final UriResolver uriResolver,
			final String mimeType, final boolean useKnownMetrics) {
		final URI uri = resolveUri(ua, uriText, uriResolver);
		if (uri == null) {
			return null;
		}
		if (useKnownMetrics) {
			final Image known = ua.getImageMetrics(uri);
			if (known != null) {
				return known;
			}
		}
		return loadResolved(ua, uri, mimeType, (resolvedUri, source) -> ua.getImage(resolvedUri, source));
	}

	/** URIが既に確定している独自画像ローダを共通の診断境界で実行します。 */
	public static <T> T load(final UserAgent ua, final URI uri, final Loader<T> loader) {
		return loadResolved(ua, uri, null, loader);
	}

	/** 文字列からURIを作る処理も含め、独自画像ローダを共通の診断境界で実行します。 */
	public static <T> T load(final UserAgent ua, final String uriText, final UriResolver uriResolver,
			final Loader<T> loader) {
		final URI uri = resolveUri(ua, uriText, uriResolver);
		return uri == null ? null : loadResolved(ua, uri, null, loader);
	}

	/** FileImageInputStream等、Sourceの外で起きた取得I/O失敗を追跡状態へ戻します。 */
	public static void recordFetchFailure(final Source source, final IOException failure) {
		if (source instanceof TrackingSource tracking) {
			tracking.failed(failure);
		}
	}

	private static URI resolveUri(final UserAgent ua, final String uriText, final UriResolver resolver) {
		try {
			return resolver.resolve();
		} catch (final Exception e) {
			report(ua, safeUri(uriText), "resolve");
			return null;
		}
	}

	private static <T> T loadResolved(final UserAgent ua, final URI uri, final String mimeType,
			final Loader<T> loader) {
		final Source source;
		try {
			source = ua.resolve(uri);
		} catch (final Exception e) {
			report(ua, safeUri(uri), "resolve");
			return null;
		}

		final TrackingSource tracking = new TrackingSource(source, mimeType);
		T result = null;
		boolean failed = false;
		try {
			result = loader.load(uri, tracking);
			failed = result == null;
		} catch (final Exception e) {
			failed = true;
		} finally {
			try {
				// MySourceResolverは具象型へcastするため、wrapperではなく元を返す。
				ua.release(source);
			} catch (final RuntimeException e) {
				failed = true;
			}
		}
		if (failed) {
			report(ua, safeUri(uri), failureDetail(tracking));
			return null;
		}
		return result;
	}

	private static String failureDetail(final TrackingSource source) {
		final int status = source.httpStatus();
		if (source.fetchFailed()) {
			return status >= 100 && status <= 599 ? "fetch: HTTP " + status : "fetch";
		}
		if (status >= 300 && status <= 599) {
			return "fetch: HTTP " + status;
		}
		return "decode";
	}

	private static void report(final UserAgent ua, final String uri, final String detail) {
		LOG.log(Level.FINE, "Missing image {0} ({1})", new Object[] { uri, detail });
		ua.message(MessageCodes.WARN_MISSING_IMAGE, uri, detail);
	}

	private static String safeUri(final URI uri) {
		if (uri == null) {
			return "";
		}
		final String rawUserInfo = uri.getRawUserInfo();
		if (rawUserInfo == null) {
			return uri.toString();
		}
		final String rawAuthority = uri.getRawAuthority();
		final String text = uri.toString();
		final int marker = text.indexOf("//");
		if (rawAuthority == null || marker == -1) {
			return text;
		}
		final int authorityStart = marker + 2;
		return text.substring(0, authorityStart) + rawAuthority.substring(rawUserInfo.length() + 1)
				+ text.substring(authorityStart + rawAuthority.length());
	}

	private static String safeUri(final String text) {
		if (text == null) {
			return "";
		}
		try {
			return safeUri(URI.create(text));
		} catch (final IllegalArgumentException e) {
			// 不正URIでも、明示されたauthority内のuserinfoだけは漏らさない。
			final int marker = text.indexOf("//");
			if (marker == -1) {
				return text;
			}
			final int authorityStart = marker + 2;
			int authorityEnd = text.length();
			for (int i = authorityStart; i < text.length(); ++i) {
				final char c = text.charAt(i);
				if (c == '/' || c == '?' || c == '#') {
					authorityEnd = i;
					break;
				}
			}
			final int at = text.lastIndexOf('@', authorityEnd - 1);
			return at < authorityStart ? text : text.substring(0, authorityStart) + text.substring(at + 1);
		}
	}

	/** SourceのI/O失敗を副状態として保持し、ローダが例外を握り潰しても失わない。 */
	private static final class TrackingSource extends SourceWrapper implements HttpStatusSource {
		private final String mimeType;
		private IOException firstFetchFailure;

		TrackingSource(final Source source, final String mimeType) {
			super(source);
			this.mimeType = mimeType;
		}

		private void failed(final IOException e) {
			if (this.firstFetchFailure == null) {
				this.firstFetchFailure = e;
			}
		}

		boolean fetchFailed() {
			return this.firstFetchFailure != null;
		}

		@Override
		public int httpStatus() {
			return this.source instanceof HttpStatusSource http ? http.httpStatus() : -1;
		}

		@Override
		public String getMimeType() throws IOException {
			if (this.mimeType != null) {
				return this.mimeType;
			}
			try {
				return super.getMimeType();
			} catch (final IOException e) {
				this.failed(e);
				throw e;
			}
		}

		@Override
		public InputStream getInputStream() throws IOException {
			final InputStream in;
			try {
				in = super.getInputStream();
			} catch (final IOException e) {
				this.failed(e);
				throw e;
			}
			return new FilterInputStream(in) {
				@Override
				public int read() throws IOException {
					try {
						return super.read();
					} catch (final IOException e) {
						failed(e);
						throw e;
					}
				}

				@Override
				public int read(final byte[] bytes, final int off, final int len) throws IOException {
					try {
						return super.read(bytes, off, len);
					} catch (final IOException e) {
						failed(e);
						throw e;
					}
				}

				@Override
				public long skip(final long count) throws IOException {
					try {
						return super.skip(count);
					} catch (final IOException e) {
						failed(e);
						throw e;
					}
				}
			};
		}

		@Override
		public Reader getReader() throws IOException {
			final Reader reader;
			try {
				reader = super.getReader();
			} catch (final IOException e) {
				this.failed(e);
				throw e;
			}
			return new FilterReader(reader) {
				@Override
				public int read() throws IOException {
					try {
						return super.read();
					} catch (final IOException e) {
						failed(e);
						throw e;
					}
				}

				@Override
				public int read(final char[] chars, final int off, final int len) throws IOException {
					try {
						return super.read(chars, off, len);
					} catch (final IOException e) {
						failed(e);
						throw e;
					}
				}

				@Override
				public long skip(final long count) throws IOException {
					try {
						return super.skip(count);
					} catch (final IOException e) {
						failed(e);
						throw e;
					}
				}
			};
		}

		@Override
		public boolean isReader() throws IOException {
			try {
				return super.isReader();
			} catch (final IOException e) {
				this.failed(e);
				throw e;
			}
		}

		@Override
		public boolean isInputStream() throws IOException {
			try {
				return super.isInputStream();
			} catch (final IOException e) {
				this.failed(e);
				throw e;
			}
		}

		@Override
		public boolean isFile() throws IOException {
			try {
				return super.isFile();
			} catch (final IOException e) {
				this.failed(e);
				throw e;
			}
		}
	}
}
