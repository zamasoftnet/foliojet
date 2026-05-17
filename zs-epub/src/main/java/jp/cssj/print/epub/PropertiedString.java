package jp.cssj.print.epub;

import java.util.HashMap;
import java.util.Map;

public class PropertiedString {
	final public String text;
	Map<String, String> meta = new HashMap<String, String>();

	public PropertiedString(String text) {
		this.text = text;
	}

	public String getMeta(String key) {
		return this.meta.get(key);
	}
}
