package jp.cssj.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * プラグインを読み込みます。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PluginLoader.java 1593 2019-12-03 07:02:17Z miyabe $
 */
public class PluginLoader {
	private static final Logger LOG = Logger.getLogger(PluginLoader.class.getName());
	private static final File[] EMPTY_FILES = new File[0];
	private static final PluginLoader INSTANCE = new PluginLoader();

	public static final PluginLoader getPluginLoader() {
		return INSTANCE;
	}

	private URLClassLoader loader = null;

	private Map<Class<Plugin<?>>, Collection<Plugin<?>>> roles = null;

	private File[] libs = null;
	private long[] times = null;

	private PluginLoader() {
		this.reload();
		Thread th = new Thread(PluginLoader.class.getName()) {
			public void run() {
				for (;;) {
					try {
						PluginLoader.this.reload();
						Thread.sleep(180000L);
					} catch (Throwable t) {
						// ignore
					}
				}
			}
		};
		th.setDaemon(true);
		th.start();
	}

	/**
	 * プラグインのjarファイルを再読み込みします。
	 */
	public synchronized void reload() {
		File libDir = new File(System.getProperty("jp.cssj.plugin.lib", "plugins"));
		File[] libs = libDir.listFiles();
		if (libs == null) {
			libs = EMPTY_FILES;
		}
		if (this.libs != null) {
			if (this.libs.length == libs.length) {
				Arrays.sort(libs);
				boolean same = true;
				for (int i = 0; i < libs.length; ++i) {
					File a = this.libs[i];
					File b = libs[i];
					if (!a.equals(b)) {
						same = false;
						break;
					}
					if (this.times[i] != b.lastModified()) {
						same = false;
						break;
					}
				}
				if (same) {
					return;
				}
			}
		}
		this.libs = libs;
		this.times = new long[libs.length];
		for (int i = 0; i < libs.length; ++i) {
			this.times[i] = libs[i].lastModified();
		}
		List<URL> urlList = new ArrayList<URL>();
		for (int i = 0; i < this.libs.length; ++i) {
			File lib = this.libs[i];
			if (lib.getName().endsWith(".jar")) {
				try {
					urlList.add(lib.toURI().toURL());
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
		}
		URL[] urls = (URL[]) urlList.toArray(new URL[urlList.size()]);
		ClassLoader loader = this.getClass().getClassLoader();
		this.loader = new URLClassLoader(urls, loader);
		this.roles = new HashMap<Class<Plugin<?>>, Collection<Plugin<?>>>();
	}

	/**
	 * プラグインの実装を読み込みます。
	 * 
	 * @param plugins
	 * @param name
	 * @throws IOException
	 */
	private void loadImpl(Collection<Plugin<?>> plugins, String name) throws IOException {
		for (Enumeration<URL> i = this.loader.getResources(name); i.hasMoreElements();) {
			URL url = i.nextElement();
			try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
				for (String line = in.readLine(); line != null; line = in.readLine()) {
					line = line.trim();
					if (line.length() > 0 && !line.startsWith("#")) {
						try {
							@SuppressWarnings("unchecked")
							Class<Plugin<?>> clazz = (Class<Plugin<?>>) this.loader.loadClass(line);
							Plugin<?> plugin = clazz.newInstance();
							plugins.add(plugin);
						} catch (Exception e) {
							LOG.log(Level.WARNING, "プラグインクラスを作成できませんでした:" + line, e);
						}
					}
				}
			}
		}
	}

	/**
	 * プラグインの全ての実装を返します。
	 * 
	 * @param role
	 *            プラグインの種類です。これはインターフェースの名前です。
	 * @return プラグインの全ての実装のオブジェクトを返すIterator。
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public synchronized Iterator<Plugin<?>> plugins(Class role) {
		String name = role.getName();
		Collection<Plugin<?>> plugins = this.roles.get(role);
		if (plugins == null) {
			plugins = new ArrayList<Plugin<?>>();
			this.roles.put(role, plugins);
			try {
				this.loadImpl(plugins, "META-INF/plugin/" + name + ".user");
				this.loadImpl(plugins, "META-INF/plugin/" + name + ".vendor");
				this.loadImpl(plugins, "META-INF/plugin/" + name + ".impl");
				this.loadImpl(plugins, "META-INF/plugin/" + name + ".default");
			} catch (Exception e) {
				LOG.log(Level.WARNING, "プラグインファイルを読み込めませんでした", e);
			}
		}
		return plugins.iterator();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public synchronized void add(Class role, Plugin<?> plugin) {
		Collection<Plugin<?>> plugins = this.roles.get(role);
		if (plugins == null) {
			plugins = new ArrayList<Plugin<?>>();
			this.roles.put(role, plugins);
		}
		plugins.add(plugin);
	}

	/**
	 * プラグインを検索します。
	 * 
	 * @param role
	 *            プラグインの種類です。これはインターフェースの名前です。
	 * @param key
	 *            プラグインを選択すためのキーです。このオブジェクトの種類はプラグインの種類に依存します。
	 * @return プラグインの実装のオブジェクト。
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public synchronized Plugin<?> search(Class role, Object key) {
		for (Iterator<Plugin<?>> i = this.plugins(role); i.hasNext();) {
			Plugin plugin = i.next();
			if (plugin.match(key)) {
				return plugin;
			}
		}
		return null;
	}

	/**
	 * プラグインの読み込みに使われるクラスローダーを返します。
	 * 
	 * @return
	 */
	public synchronized ClassLoader getClassLoader() {
		return this.loader;
	}
}
