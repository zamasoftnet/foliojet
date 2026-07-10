package net.zamasoft.foliojet.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

	private volatile State state = new State(null, EMPTY_FILES, new ConcurrentHashMap<>());

	private record State(URLClassLoader classLoader, File[] libs, ConcurrentMap<Class<?>, List<?>> roles) {
	}

	private PluginRegistry() {
		this.reload();
	}

	/**
	 * プラグインディレクトリを再読み込みします。
	 */
	public synchronized void reload() {
		this.closeClassLoader(this.state.classLoader());
		File libDir = new File(System.getProperty(PLUGIN_LIB_PROPERTY, "plugins"));
		File[] libs = libDir.listFiles();
		if (libs == null) {
			libs = EMPTY_FILES;
		}
		List<URL> urls = new ArrayList<>();
		for (File lib : libs) {
			if (lib.isFile() && lib.getName().endsWith(".jar")) {
				try {
					urls.add(lib.toURI().toURL());
				} catch (MalformedURLException e) {
					LOG.log(Level.WARNING, "プラグインjarを読み込めませんでした: " + lib, e);
				}
			}
		}
		URLClassLoader classLoader = new URLClassLoader(urls.toArray(URL[]::new), PluginRegistry.class.getClassLoader());
		this.state = new State(classLoader, libs.clone(), new ConcurrentHashMap<>());
	}

	private void closeClassLoader(URLClassLoader classLoader) {
		if (classLoader == null) {
			return;
		}
		try {
			classLoader.close();
		} catch (IOException e) {
			LOG.log(Level.FINE, "プラグインクラスローダーを閉じられませんでした", e);
		}
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
		State snapshot = this.state;
		List<T> plugins = (List<T>) snapshot.roles().computeIfAbsent(role, key -> this.load(role, snapshot.classLoader()));
		return plugins.iterator();
	}

	private <T> List<T> load(Class<T> role, ClassLoader classLoader) {
		List<T> plugins = new ArrayList<>();
		ServiceLoader<T> loader = ServiceLoader.load(role, classLoader);
		try {
			for (T plugin : loader) {
				plugins.add(plugin);
			}
		} catch (ServiceConfigurationError e) {
			LOG.log(Level.WARNING, "プラグインを読み込めませんでした: " + role.getName(), e);
		}
		plugins.sort((a, b) -> Integer.compare(priority(b), priority(a)));
		return List.copyOf(plugins);
	}

	private static int priority(Object plugin) {
		if (plugin instanceof Plugin<?> typedPlugin) {
			return typedPlugin.priority();
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
	public <T> T search(Class<T> role, Object key) {
		for (Iterator<T> i = this.plugins(role); i.hasNext();) {
			T candidate = i.next();
			if (candidate instanceof Plugin<?> typedPlugin && ((Plugin<Object>) typedPlugin).match(key)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * プラグインの読み込みに使われるクラスローダーを返します。
	 * 
	 * @return クラスローダー。
	 */
	public ClassLoader getClassLoader() {
		return this.state.classLoader();
	}

	public File[] getPluginFiles() {
		return this.state.libs().clone();
	}
}
