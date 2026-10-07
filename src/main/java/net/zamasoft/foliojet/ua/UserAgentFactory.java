package net.zamasoft.foliojet.ua;

import java.util.Iterator;

import net.zamasoft.foliojet.plugin.Plugin;

/**
 * Creates a UA for the output format's MIME type.
 *
 * @author MIYABE Tatsuhiko
 */
public interface UserAgentFactory extends Plugin<String> {
	public static final class Type {
		public final String name;
		public final String mimeType;
		public final String suffix;

		public Type(String name, String mimeType, String suffix) {
			this.name = name;
			this.mimeType = mimeType;
			this.suffix = suffix;
		}
	}

	/**
	 * Returns supported types.
	 *
	 * @return
	 */
	public Iterator<Type> types();

	/**
	 * Creates a UA.
	 *
	 * @return
	 */
	public UserAgent createUserAgent();
}
