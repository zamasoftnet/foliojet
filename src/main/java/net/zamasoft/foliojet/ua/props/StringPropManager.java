package net.zamasoft.foliojet.ua.props;

import java.util.Map;

import net.zamasoft.foliojet.ua.UserAgent;

public class StringPropManager extends AbstractPropManager {
	public final String defaultStr;

	public StringPropManager(String name, String defaultStr) {
		super(name);
		this.defaultStr = defaultStr;
	}

	public String getDefaultString() {
		return this.defaultStr;
	}

	public String getString(UserAgent ua) {
		String str = ua.getProperty(this.name);
		if (str == null) {
			return this.defaultStr;
		}
		return str;
	}

	public String getString(Map<String, String> props) {
		String str = (String) props.get(this.name);
		if (str == null) {
			str = this.defaultStr;
		}
		return str;
	}
}
