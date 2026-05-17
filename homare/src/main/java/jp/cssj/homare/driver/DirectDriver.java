package jp.cssj.homare.driver;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import jp.cssj.cti2.CTIDriver;
import jp.cssj.cti2.CTISession;
import jp.cssj.plugin.Plugin;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: DirectDriver.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class DirectDriver implements CTIDriver, Plugin<URI> {
	protected static final URI DIRECT_URI = URI.create("copper:direct:");

	/**
	 * デフォルトの設定ファイルです。
	 */
	public static final String DEFAULT_PROFILE_FILE_KEY = "jp.cssj.driver.default";

	private static final String DEFAULT_PROFILE_DIR = "conf/profiles";
	private static final String DEFAULT_PROFILE_NAME = "default";

	public static File getProfileDir() {
		String file = System.getProperty(DEFAULT_PROFILE_FILE_KEY);
		if (file != null) {
			return new File(file).getParentFile();
		}
		File profileDir = new File(DEFAULT_PROFILE_DIR);
		return profileDir;
	}

	public static File getProfileFile(String profile) {
		String file = System.getProperty(DEFAULT_PROFILE_FILE_KEY);
		if (file != null) {
			return new File(file);
		}
		File profileDir = getProfileDir();
		if (profile == null || profile.length() == 0) {
			profile = DEFAULT_PROFILE_NAME;
		}
		return new File(profileDir, profile + ".properties");
	}

	public boolean match(URI uri) {
		if (uri == null) {
			return true;
		}
		if (DIRECT_URI.getScheme().equals(uri.getScheme())) {
			if (uri.getSchemeSpecificPart().startsWith(DIRECT_URI.getSchemeSpecificPart())) {
				return true;
			}
		}
		return false;
	}

	public CTISession getSession(URI uri, Map<String, String> props) throws IOException, SecurityException {
		final DirectSession session = new DirectSession();
		if (uri != null) {
			String spec = uri.getSchemeSpecificPart();
			int specLength = DIRECT_URI.getSchemeSpecificPart().length();
			if (spec != null && spec.length() > specLength) {
				spec = spec.substring(specLength);
				session.setProfileFile(new File(spec));
			}
		}
		session.setup();
		return session;
	}
}