package net.zamasoft.foliojet.plugin;

/**
 * プラグインのインターフェースです。
 * 
 * @author MIYABE Tatsuhiko
 */
public interface Plugin<E> {
	/**
	 * プラグインが与えられたキーに対応するかどうかを検証します。 キーがどのようなオブジェクトであるかはプラグインの種類に依存します。
	 * 
	 * @param key
	 *            プラグインを選択するためのキー。
	 * @return 与えられたキーに対応していればtrue。そうでなければfalse。
	 */
	boolean match(E key);

	/**
	 * 同じキーに対応するプラグインが複数ある場合の優先順位です。大きい値が優先されます。
	 * 
	 * @return 優先順位。
	 */
	default int priority() {
		return 0;
	}
}
