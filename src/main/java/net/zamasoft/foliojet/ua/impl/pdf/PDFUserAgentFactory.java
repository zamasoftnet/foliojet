package net.zamasoft.foliojet.ua.impl.pdf;

import java.util.Arrays;
import java.util.Iterator;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.UserAgentFactory;

public class PDFUserAgentFactory implements UserAgentFactory {
	public static String MIME_TYPE = "application/pdf";

	public boolean match(String key) {
		return key.equals(MIME_TYPE);
	}

	public Iterator<Type> types() {
		return Arrays.asList(new Type[] { new Type("PDF", MIME_TYPE, "pdf") }).iterator();
	}

	public UserAgent createUserAgent() {
		return new PDFUserAgent();
	}
}
