package jp.cssj.print.epub;

/**
 * META-INFO/container.xmlファイルに相当する情報です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class Container {
	/**
	 * ルートファイルの情報です。
	 */
	public static class Rootfile {
		/**
		 * データ形式です。
		 */

		public String mediaType;
		/**
		 * アーカイブ内でのファイルパスです。
		 */
		public String fullPath;
	}

	/**
	 * 全てのルートファイルです。
	 */
	public Rootfile[] rootfiles;
}
