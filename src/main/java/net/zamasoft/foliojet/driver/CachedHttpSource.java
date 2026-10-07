package net.zamasoft.foliojet.driver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map.Entry;
import net.zamasoft.zstream.resolver.SourceValidity;
import net.zamasoft.zstream.resolver.util.AbstractSource;

// Split from MyHttpSourceResolver.java on 2026-09-02 (body only moved; design review: 10 classes, 1,560 lines).
/**
 * A Source that provides an entry from {@link HttpResponseCache}. Its contents are
 * decompressed bytes, so it accesses neither the network nor {@code HttpClient}.
 */
final class CachedHttpSource extends AbstractSource {
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
		// The bytes are in memory, so there is nothing to release.
	}
}
