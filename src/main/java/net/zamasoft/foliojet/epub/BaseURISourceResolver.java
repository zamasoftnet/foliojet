package net.zamasoft.foliojet.epub;

import java.io.IOException;
import java.net.URI;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;

/**
 * A resolver that maps relative paths within an EPUB to resources under a base URI.
 *
 * <p>
 * For ZIP EPUBs, the {@code zip:} scheme and {@link ZIPFileSourceResolver} serve this role.
 * When EPUB contents are supplied as a directory, <b>resolution relative to the base URI</b>
 * serves the same purpose directly. An {@code http:} base URI lets you lay out an unpacked EPUB
 * on the web. If a CTIP client installs a source resolver, it lets you lay out a client-side EPUB.
 * In either case, fetches <b>only the required items</b>.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class BaseURISourceResolver implements SourceResolver {
	private final SourceResolver enclosed;

	private final URI base;

	/**
	 * @param enclosed the resolver that retrieves resources
	 * @param base     a hierarchical URI ending in {@code /}
	 */
	public BaseURISourceResolver(final SourceResolver enclosed, final URI base) {
		this.enclosed = enclosed;
		this.base = base;
	}

	/** Converts to an absolute URI under the base URI. */
	public URI toAbsolute(final URI uri) {
		return this.base.resolve(uri);
	}

	@Override
	public Source resolve(final URI uri) throws IOException {
		return this.enclosed.resolve(this.toAbsolute(uri));
	}

	@Override
	public void release(final Source source) {
		this.enclosed.release(source);
	}
}
