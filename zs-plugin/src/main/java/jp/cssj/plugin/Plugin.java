package jp.cssj.plugin;

/**
 * プラグインのインターフェースです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Plugin.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Plugin<E> {
	/**
	 * プラグインが与えられたキーに対応するかどうかを検証します。 キーがどのようなオブジェクトであるかはプラグインの種類に依存します。
	 * 
	 * @param key
	 *            プラグインを選択するためのキー。
	 * @return 与えられたキーに対応していればtrue。そうでなければfalse。
	 */
	public boolean match(E key);
}
