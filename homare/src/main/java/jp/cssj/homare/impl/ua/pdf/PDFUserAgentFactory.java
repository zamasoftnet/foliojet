package jp.cssj.homare.impl.ua.pdf;

import java.util.Arrays;
import java.util.Iterator;

import jp.cssj.homare.ua.UserAgent;
import jp.cssj.homare.ua.UserAgentFactory;

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
