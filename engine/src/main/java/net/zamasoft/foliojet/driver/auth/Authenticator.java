package net.zamasoft.foliojet.driver.auth;

import java.util.Map;

import net.zamasoft.foliojet.plugin.Plugin;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Authenticator.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Authenticator extends Plugin<Map<String, String>> {
	public boolean authenticate(Map<String, String> props);
}
