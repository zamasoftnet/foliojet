package net.zamasoft.foliojet.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ServiceLoaderを使ってプラグインを管理します。
 * 
 * @author MIYABE Tatsuhiko
 */
public class PluginRegistry {
	private static final Logger LOG = Logger.getLogger(PluginRegistry.class.getName());
	private static final File[] EMPTY_FILES = new File[0];
	private static final PluginRegistry INSTANCE = new PluginRegistry();

	public static final String PLUGIN_LIB_PROPERTY = "net.zamasoft.foliojet.plugin.lib";

	public static PluginRegistry getInstance() {
		return INSTANCE;
	}

	private URLClassLoader classLoader;

	private File[] libs = EMPTY_FILES;

	private final Map<Class<?>, List<?>> roles = new HashMap<Class<?>, List<?>>();

	private PluginRegistry() {
		this.reload();
	}

	/**
	 * プラグインディレクトリを再読み込みします。
	 */
	public synchronized void reload() {
		this.closeClassLoader();
		File libDir = new File(System.getProperty(PLUGIN_LIB_PROPERTY, "plugins"));
		File[] libs = libDir.listFiles();
		if (libs == null) {
			libs = EMPTY_FILES;
		}
		List<URL> urls = new ArrayList<URL>();
		for (int i = 0; i < libs.length; ++i) {
			File lib = libs[i];
			if (lib.isFile() && lib.getName().endsWith(".jar")) {
				try {
					urls.add(lib.toURI().toURL());
				} catch (MalformedURLException e) {
					LOG.log(Level.WARNING, "プラグインjarを読み込めませんでした: " + lib, e);
				}
			}
		}
		this.libs = libs;
		this.classLoader = new URLClassLoader((URL[]) urls.toArray(new URL[urls.size()]),
				PluginRegistry.class.getClassLoader());
		this.roles.clear();
	}

	private void closeClassLoader() {
		if (this.classLoader == null) {
			return;
		}
		try {
			this.classLoader.close();
		} catch (IOException e) {
			LOG.log(Level.FINE, "プラグインクラスローダーを閉じられませんでした", e);
		}
		this.classLoader = null;
	}

	/**
	 * プラグインの全ての実装を返します。
	 * 
	 * @param role
	 *            プラグインの種類です。これはインターフェースの名前です。
	 * @return プラグインの全ての実装のオブジェクトを返すIterator。
	 */
	@SuppressWarnings("unchecked")
	public synchronized <T> Iterator<T> plugins(Class<T> role) {
		List<T> plugins = (List<T>) this.roles.get(role);
		if (plugins == null) {
			plugins = this.load(role);
			this.roles.put(role, plugins);
		}
		return plugins.iterator();
	}

	private <T> List<T> load(Class<T> role) {
		List<T> plugins = new ArrayList<T>();
		ServiceLoader<T> loader = ServiceLoader.load(role, this.classLoader);
		try {
			for (T plugin : loader) {
				plugins.add(plugin);
			}
		} catch (ServiceConfigurationError e) {
			LOG.log(Level.WARNING, "プラグインを読み込めませんでした: " + role.getName(), e);
		}
		plugins.sort(new Comparator<T>() {
			public int compare(T a, T b) {
				return Integer.compare(priority(b), priority(a));
			}
		});
		return plugins;
	}

	private static int priority(Object plugin) {
		if (plugin instanceof Plugin<?>) {
			return ((Plugin<?>) plugin).priority();
		}
		return 0;
	}

	/**
	 * プラグインを検索します。
	 * 
	 * @param role
	 *            プラグインの種類です。これはインターフェースの名前です。
	 * @param key
	 *            プラグインを選択するためのキーです。このオブジェクトの種類はプラグインの種類に依存します。
	 * @return プラグインの実装のオブジェクト。
	 */
	@SuppressWarnings("unchecked")
	public synchronized <T> T search(Class<T> role, Object key) {
		for (Iterator<T> i = this.plugins(role); i.hasNext();) {
			T candidate = i.next();
			if (candidate instanceof Plugin<?>) {
				Plugin<Object> plugin = (Plugin<Object>) candidate;
				if (plugin.match(key)) {
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * プラグインの読み込みに使われるクラスローダーを返します。
	 * 
	 * @return クラスローダー。
	 */
	public synchronized ClassLoader getClassLoader() {
		return this.classLoader;
	}

	public synchronized File[] getPluginFiles() {
		return (File[]) this.libs.clone();
	}
}
