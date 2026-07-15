package net.zamasoft.foliojet.ua;

import java.util.Iterator;

import net.zamasoft.foliojet.plugin.Plugin;

/**
 * 出力形式のMIME型に応じたUAを生成します。
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
	 * サポートする型を返します。
	 * 
	 * @return
	 */
	public Iterator<Type> types();

	/**
	 * UAを生成します。
	 * 
	 * @return
	 */
	public UserAgent createUserAgent();
}
