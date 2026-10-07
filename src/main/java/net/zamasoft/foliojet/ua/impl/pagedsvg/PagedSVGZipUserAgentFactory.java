package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.util.Arrays;
import java.util.Iterator;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.UserAgentFactory;

/**
 * Output that returns page-split SVG in <b>one ZIP</b> (B-2, 2026-08-29).
 *
 * <p>
 * Contents are the same as a {@link PagedSVGUserAgentFactory} bundle: extraction places
 * {@code pages/0001.svg}, {@code assets/…}, and {@code manifest.json} in the same structure
 * as directory output. The only difference is <b>one result</b>, allowing receipt through
 * a sessionless, single-request REST call ({@code POST /transcode})
 * (a multi-result bundle returns 4001 there). User report B-2.
 * </p>
 *
 * <p>
 * Do not compress contents (ignore {@code output.paged-svg.compression}).
 * ZIP itself compresses them, making additional compression redundant;
 * extracted names should end in {@code .svg}/{@code .json}.
 * </p>
 */
public class PagedSVGZipUserAgentFactory implements UserAgentFactory {
	/** Media type of a bundle returned as ZIP. */
	public static final String MIME_TYPE = "application/vnd.copper.paged-svg+zip";

	@Override
	public boolean match(final String key) {
		return MIME_TYPE.equals(key);
	}

	@Override
	public Iterator<Type> types() {
		return Arrays.asList(new Type[] { new Type("Paged SVG bundle (ZIP)", MIME_TYPE, "zip") }).iterator();
	}

	@Override
	public UserAgent createUserAgent() {
		return new PagedSVGUserAgent(true);
	}
}
