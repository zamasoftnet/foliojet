package net.zamasoft.foliojet.plugin;

/**
 * Plugin interface.
 *
 * @author MIYABE Tatsuhiko
 */
public interface Plugin<E> {
	/**
	 * Checks whether the plugin supports the given key.
	 * The kind of object used as a key depends on the plugin type.
	 *
	 * @param key
	 *            key used to select a plugin.
	 * @return true if the given key is supported; otherwise false.
	 */
	boolean match(E key);

	/**
	 * Priority when multiple plugins support the same key. Higher values take precedence.
	 *
	 * @return priority.
	 */
	default int priority() {
		return 0;
	}
}
