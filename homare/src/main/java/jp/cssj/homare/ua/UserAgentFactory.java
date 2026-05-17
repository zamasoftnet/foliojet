package jp.cssj.homare.ua;

import java.util.Iterator;

import jp.cssj.plugin.Plugin;

/**
 * 出力形式のMIME型に応じたUAを生成します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: UserAgentFactory.java 1566 2018-07-04 11:52:15Z miyabe $
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
