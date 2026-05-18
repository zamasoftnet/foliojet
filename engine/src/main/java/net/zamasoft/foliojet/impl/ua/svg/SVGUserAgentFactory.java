package net.zamasoft.foliojet.impl.ua.svg;

import java.util.Arrays;
import java.util.Iterator;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.UserAgentFactory;

public class SVGUserAgentFactory implements UserAgentFactory {
	public static String MIME_TYPE = "image/svg+xml";

	public boolean match(String key) {
		return key.equals(MIME_TYPE);
	}

	public Iterator<Type> types() {
		return Arrays.asList(new Type[] { new Type("SVG", MIME_TYPE, "svg") }).iterator();
	}

	public UserAgent createUserAgent() {
		return new SVGUserAgent();
	}
}
