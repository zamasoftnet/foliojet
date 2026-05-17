package jp.cssj.version;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * バージョン管理のためのクラスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Version.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Version {
	public final String name, version, build, copyrights, credits;

	public final String longVersion;

	protected Version() {
		try {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(this.getClass().getResourceAsStream("VERSION"), "UTF-8"))) {
				this.name = in.readLine();
				this.version = in.readLine();
				this.build = in.readLine();
				this.copyrights = in.readLine();
				StringBuffer credits = new StringBuffer();
				String line;
				while ((line = in.readLine()) != null) {
					credits.append(line);
					credits.append('\n');
				}
				this.credits = credits.toString();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.longVersion = this.name + " " + this.version + "/" + this.build;
	}
}