package net.zamasoft.foliojet.epub;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * An {@link ArchiveFile} for EPUB content supplied as a directory.
 *
 * <p>
 * Passes each item's path to the resolver to retrieve it as needed, instead of opening a ZIP.
 * <b>Retrieves only the required items</b>. If the client sets a source resolver over CTIP,
 * only the required items are sent from the client's EPUB,
 * so images are not transferred when the output does not include them.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class ResolvedArchiveFile implements ArchiveFile {
	private final SourceResolver resolver;

	public ResolvedArchiveFile(final SourceResolver resolver) {
		this.resolver = resolver;
	}

	private URI toURI(final String path) {
		try {
			return URIHelper.create("UTF-8", path);
		} catch (final Exception e) {
			return URI.create(path);
		}
	}

	@Override
	public boolean exists(final String path) {
		Source source = null;
		try {
			source = this.resolver.resolve(this.toURI(path));
			return source.exists();
		} catch (final IOException | SecurityException e) {
			return false;
		} finally {
			if (source != null) {
				this.resolver.release(source);
			}
		}
	}

	@Override
	public InputStream getInputStream(final String path) throws IOException {
		final Source source = this.resolver.resolve(this.toURI(path));
		// Always release the resource after reading, following the same pattern as the reopening
		// variant in ZipArchiveFile, so callers need no changes
		return new FilterInputStream(source.getInputStream()) {
			@Override
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					ResolvedArchiveFile.this.resolver.release(source);
				}
			}
		};
	}

	@Override
	public void close() throws IOException {
		// Each retrieval releases its resource, so there is nothing to close collectively
	}
}
