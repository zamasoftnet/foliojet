package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.HttpStatusSource;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.impl.image.ImageUserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.SourceValidity;
import net.zamasoft.zstream.resolver.util.AbstractSource;

/** 2811が画像取得の失敗段階を失わず通知することを固定します。 */
public class ImageLoadStageTest extends TestCase {
	private static final URI URI = java.net.URI.create("https://example.com/image.png");
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	private final List<Message> messages = new ArrayList<>();

	public void testResolveExceptionIsResolve() {
		final ImageUserAgent ua = this.userAgent(new FakeResolver(new IOException("secret")));
		assertNull(ImageLoadDiagnostics.loadImage(ua, URI, false));
		this.assertStage("resolve");
	}

	public void testOpenIOExceptionIsFetch() {
		final FakeSource source = new FakeSource(URI, PNG, true, -1, false);
		final FakeResolver resolver = new FakeResolver(source);
		final ImageUserAgent ua = this.userAgent(resolver);
		assertNull(ImageLoadDiagnostics.loadImage(ua, URI, false));
		this.assertStage("fetch");
		assertSame(source, resolver.released);
	}

	public void testReadMidwayIOExceptionIsFetch() {
		final FakeSource source = new FakeSource(URI, PNG, false, 32, false);
		final ImageUserAgent ua = this.userAgent(new FakeResolver(source));
		assertNull(ImageLoadDiagnostics.loadImage(ua, URI, false));
		this.assertStage("fetch");
	}

	public void testDirectFileIOExceptionIsFetch() {
		final FakeSource source = new FakeSource(URI, PNG, true, -1, true);
		final ImageUserAgent ua = this.userAgent(new FakeResolver(source));
		assertNull(ImageLoadDiagnostics.loadImage(ua, URI, false));
		this.assertStage("fetch");
	}

	public void testNoReaderIsDecode() {
		final FakeSource source = new FakeSource(URI, "not an image".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
				false, -1, false);
		final ImageUserAgent ua = this.userAgent(new FakeResolver(source));
		assertNull(ImageLoadDiagnostics.loadImage(ua, URI, false));
		this.assertStage("decode");
	}

	public void testCorruptPngIsDecode() {
		final FakeSource source = new FakeSource(URI, Arrays.copyOf(PNG, 24), false, -1, false);
		final ImageUserAgent ua = this.userAgent(new FakeResolver(source));
		assertNull(ImageLoadDiagnostics.loadImage(ua, URI, false));
		this.assertStage("decode");
	}

	public void testValidPngHasNoMessage() {
		final FakeSource source = new FakeSource(URI, PNG, false, -1, false);
		final ImageUserAgent ua = this.userAgent(new FakeResolver(source));
		final Image image = ImageLoadDiagnostics.loadImage(ua, URI, false);
		assertNotNull(image);
		assertTrue(this.messages.isEmpty());
	}

	public void testHttpStatusAndUserInfoAreSafe() {
		final URI privateUri = java.net.URI
				.create("https://user:password@example.com/image.png?token=uri-value");
		final FakeSource source = new HttpFakeSource(privateUri,
				"missing".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 404);
		final ImageUserAgent ua = this.userAgent(new FakeResolver(source));
		assertNull(ImageLoadDiagnostics.loadImage(ua, privateUri, false));
		final Message message = this.assertStage("fetch: HTTP 404");
		assertEquals("https://example.com/image.png?token=uri-value", message.args[0]);
	}

	private ImageUserAgent userAgent(final SourceResolver resolver) {
		this.messages.clear();
		final ImageUserAgent ua = new ImageUserAgent();
		ua.setSourceResolver(resolver);
		ua.setMessageHandler((code, args, mes) -> this.messages.add(new Message(code, args)));
		return ua;
	}

	private Message assertStage(final String stage) {
		assertEquals(1, this.messages.size());
		final Message message = this.messages.get(0);
		assertEquals(MessageCodes.WARN_MISSING_IMAGE, message.code);
		assertNotNull(message.args);
		assertEquals(2, message.args.length);
		assertEquals(stage, message.args[1]);
		return message;
	}

	private record Message(short code, String[] args) {
	}

	private static final class FakeResolver implements SourceResolver {
		private final Source source;
		private final IOException failure;
		private Source released;

		FakeResolver(final Source source) {
			this.source = source;
			this.failure = null;
		}

		FakeResolver(final IOException failure) {
			this.source = null;
			this.failure = failure;
		}

		@Override
		public Source resolve(final URI uri) throws IOException {
			if (this.failure != null) {
				throw this.failure;
			}
			return this.source;
		}

		@Override
		public void release(final Source source) {
			this.released = source;
		}
	}

	private static class FakeSource extends AbstractSource {
		private final byte[] bytes;
		private final boolean openFailure;
		private final int failAfter;
		private final boolean file;

		FakeSource(final URI uri, final byte[] bytes, final boolean openFailure, final int failAfter,
				final boolean file) {
			super(uri);
			this.bytes = bytes;
			this.openFailure = openFailure;
			this.failAfter = failAfter;
			this.file = file;
		}

		@Override
		public String getMimeType() {
			return "image/png";
		}

		@Override
		public String getEncoding() {
			return null;
		}

		@Override
		public long getLength() {
			return this.bytes.length;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public boolean isInputStream() {
			return true;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			if (this.openFailure) {
				throw new IOException("secret response body");
			}
			if (this.failAfter < 0) {
				return new ByteArrayInputStream(this.bytes);
			}
			return new InputStream() {
				private int position;

				@Override
				public int read() throws IOException {
					if (this.position >= failAfter) {
						throw new IOException("secret response body");
					}
					return this.position >= bytes.length ? -1 : bytes[this.position++] & 0xFF;
				}

				@Override
				public int read(final byte[] target, final int off, final int len) throws IOException {
					if (this.position >= failAfter) {
						throw new IOException("secret response body");
					}
					if (this.position >= bytes.length) {
						return -1;
					}
					final int count = Math.min(len, Math.min(bytes.length - this.position, failAfter - this.position));
					System.arraycopy(bytes, this.position, target, off, count);
					this.position += count;
					return count;
				}
			};
		}

		@Override
		public boolean isReader() {
			return false;
		}

		@Override
		public Reader getReader() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isFile() {
			return this.file;
		}

		@Override
		public File getFile() {
			return new File("local/unittest/__missing_2811__/image.png");
		}

		@Override
		public SourceValidity getValidity() {
			return null;
		}
	}

	private static final class HttpFakeSource extends FakeSource implements HttpStatusSource {
		private final int status;

		HttpFakeSource(final URI uri, final byte[] bytes, final int status) {
			super(uri, bytes, false, -1, false);
			this.status = status;
		}

		@Override
		public int httpStatus() {
			return this.status;
		}
	}
}
