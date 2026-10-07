package net.zamasoft.foliojet.ua.props;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Utilities for UA properties.
 *
 * @author MIYABE Tatsuhiko
 */
public final class PropHelper {
	private PropHelper() {
		// utility
	}

	/**
	 * Returns default property settings.
	 */
	public static void setDefaults(Map<Object, Object> props) {
		for (PropManager prop : UAProps.all()) {
			String value = prop.getDefaultString();
			if (value != null) {
				props.put(prop.getName(), value);
			}
		}
	}

	/**
	 * Clears default property settings.
	 */
	public static void removeDefaults(Map<Object, Object> props) {
		Map<Object, Object> map = new HashMap<Object, Object>();
		setDefaults(map);
		for (Entry<Object, Object> e : map.entrySet()) {
			if (e.getValue().equals(props.get(e.getKey()))) {
				props.remove(e.getKey());
			}
		}
	}

	/**
	 * Presets boolean properties to false.
	 */
	public static void setBooleanPropsToFalse(Map<Object, Object> props) {
		for (PropManager prop : UAProps.all()) {
			if (prop instanceof BooleanPropManager) {
				props.put(prop.getName(), "false");
			}
		}
	}
}
