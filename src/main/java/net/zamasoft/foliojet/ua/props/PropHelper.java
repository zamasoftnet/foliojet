package net.zamasoft.foliojet.ua.props;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * UAプロパティ関連のユーティリティクラスです。
 *
 * @author MIYABE Tatsuhiko
 */
public final class PropHelper {
	private PropHelper() {
		// utility
	}

	/**
	 * デフォルトのプロパティ設定を返します。
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
	 * デフォルトのプロパティ設定を消去します。
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
	 * ブール値のプロパティにあらかじめfalseを設定します。
	 */
	public static void setBooleanPropsToFalse(Map<Object, Object> props) {
		for (PropManager prop : UAProps.all()) {
			if (prop instanceof BooleanPropManager) {
				props.put(prop.getName(), "false");
			}
		}
	}
}
