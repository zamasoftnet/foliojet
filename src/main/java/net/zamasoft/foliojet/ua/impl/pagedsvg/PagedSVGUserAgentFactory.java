package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.util.Arrays;
import java.util.Iterator;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.UserAgentFactory;

/** Creates the multi-result paged SVG bundle output. */
public class PagedSVGUserAgentFactory implements UserAgentFactory {
	/** Vendor media type used to select the bundle user agent. */
	public static final String MIME_TYPE = "application/vnd.copper.paged-svg";

	@Override
	public boolean match(final String key) {
		return MIME_TYPE.equals(key);
	}

	@Override
	public Iterator<Type> types() {
		return Arrays.asList(new Type[] { new Type("Paged SVG bundle", MIME_TYPE, "json") }).iterator();
	}

	@Override
	public UserAgent createUserAgent() {
		return new PagedSVGUserAgent();
	}
}
