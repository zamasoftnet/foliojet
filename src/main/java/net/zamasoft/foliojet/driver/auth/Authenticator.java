package net.zamasoft.foliojet.driver.auth;

import java.util.Map;

import net.zamasoft.foliojet.plugin.Plugin;

/**
 * @author MIYABE Tatsuhiko
 */
public interface Authenticator extends Plugin<Map<String, String>> {
	public boolean authenticate(Map<String, String> props);
}
