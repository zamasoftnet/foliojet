package net.zamasoft.foliojet.ua.props;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * UAプロパティ関連のユーティリティクラスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PropHelper.java 1566 2018-07-04 11:52:15Z miyabe $
 */
public class PropHelper {
	/**
	 * デフォルトのプロパティ設定を返します。
	 */
	public static void setDefaults(Map<Object, Object> props) {
		Field[] fields = UAProps.class.getFields();
		for (int i = 0; i < fields.length; ++i) {
			Field field = fields[i];
			if (PropManager.class.isAssignableFrom(field.getType())) {
				PropManager prop;
				try {
					prop = (PropManager) field.get(UAProps.class);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
				String value = prop.getDefaultString();
				if (value != null) {
					String name = prop.getName();
					props.put(name, value);
				}
			}
		}
	}

	/**
	 * デフォルトのプロパティ設定を消去します。
	 * 
	 * @param props
	 */
	public static void removeDefaults(Map<Object, Object> props) {
		Map<Object, Object> map = new HashMap<Object, Object>();
		setDefaults(map);
		for (Iterator<?> i = map.entrySet().iterator(); i.hasNext();) {
			Entry<?, ?> e = (Entry<?, ?>) i.next();
			String name = (String) e.getKey();
			String value = (String) e.getValue();
			if (value.equals(props.get(name))) {
				props.remove(name);
			}
		}
	}

	/**
	 * ブール値のプロパティにあらかじめfalseを設定します。
	 */
	public static void setBooleanPropsToFalse(Map<Object, Object> props) {
		Field[] fields = UAProps.class.getFields();
		for (int i = 0; i < fields.length; ++i) {
			Field field = fields[i];
			if (BooleanPropManager.class.isAssignableFrom(field.getType())) {
				BooleanPropManager prop;
				try {
					prop = (BooleanPropManager) field.get(UAProps.class);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
				String name = prop.getName();
				props.put(name, "false");
			}
		}
	}
}
