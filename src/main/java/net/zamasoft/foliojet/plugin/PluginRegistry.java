package net.zamasoft.foliojet.plugin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ServiceLoaderを使ってプラグインを管理します。
 * プラグインは起動時に一度だけ読み込まれ、以後の検索はロックフリーです。
 *
 * @author MIYABE Tatsuhiko
 */
public final class PluginRegistry {
	private static final Logger LOG = Logger.getLogger(PluginRegistry.class.getName());

	private static final File[] EMPTY_FILES = new File[0];

	public static final String PLUGIN_LIB_PROPERTY = "net.zamasoft.foliojet.plugin.lib";

	private static final PluginRegistry INSTANCE = new PluginRegistry();

	public static PluginRegistry getInstance() {
		return INSTANCE;
	}

	private final ClassLoader classLoader;

	private final File[] libs;

	private final ConcurrentMap<Class<?>, List<?>> roles = new ConcurrentHashMap<>();

	private PluginRegistry() {
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
		this.libs = libs;
		this.classLoader = urls.isEmpty() ? PluginRegistry.class.getClassLoader()
				: new URLClassLoader(urls.toArray(URL[]::new), PluginRegistry.class.getClassLoader());
	}

	/**
	 * プラグインの全ての実装を優先順位の高い順で返します。
	 *
	 * @param role プラグインの種類です。これはインターフェースの名前です。
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> plugins(Class<T> role) {
		return (List<T>) this.roles.computeIfAbsent(role, key -> this.load(role));
	}

	private <T> List<T> load(Class<T> role) {
		List<T> plugins = new ArrayList<>();
		ServiceLoader<T> loader = ServiceLoader.load(role, this.classLoader);
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
	 * キーに対応するプラグインを検索します。
	 *
	 * @param role プラグインの種類です。これはインターフェースの名前です。
	 * @param key  プラグインを選択するためのキーです。
	 * @return 最初にマッチしたプラグイン。なければnull。
	 */
	public <T extends Plugin<? super K>, K> T search(Class<T> role, K key) {
		for (T candidate : this.plugins(role)) {
			if (candidate.match(key)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * プラグインの読み込みに使われるクラスローダーを返します。
	 */
	public ClassLoader getClassLoader() {
		return this.classLoader;
	}

	public File[] getPluginFiles() {
		return this.libs.clone();
	}
}
