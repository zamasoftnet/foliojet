package net.zamasoft.foliojet.message;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import jp.cssj.cti2.helpers.CTIMessageHelper;

/**
 * メッセージコードを可読なテキストにします。
 * 
 * @author MIYABE Tatsuhiko
 */
public class MessageCodeUtils {
	private MessageCodeUtils() {
		// unused
	}

	private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(MessageCodes.class.getName());

	/**
	 * メッセージコードに対応するメッセージフォーマットを返します。
	 * 
	 * @param code
	 * @return
	 */
	public static String getFormat(short code, String[] args) {
		if ((code & 0x0FFF) < 0x800) {
			return CTIMessageHelper.getFormat(code);
		}
		String str = Integer.toHexString(code).toUpperCase();
		try {
			str = BUNDLE.getString(str);
		} catch (Exception e) {
			if (args.length > 0) {
				str = args[0];
			}
		}
		return str;
	}

	/**
	 * メッセージの引数に埋める補足文(「近似の内容」など)を、コードと同じ
	 * カタログから引きます。見つからなければ鍵をそのまま返す(2026-08-29)。
	 *
	 * @param key カタログの鍵(例 {@code 2822.blur-rings})
	 */
	public static String detail(final String key) {
		try {
			return BUNDLE.getString(key);
		} catch (Exception e) {
			return key;
		}
	}

	/**
	 * メッセージを文字列化します。
	 * 
	 * @param code
	 * @return
	 */
	public static String toString(short code, String[] args) {
		String str = getFormat(code, args);
		if (args != null) {
			for (int i = 0; i < args.length; ++i) {
				if (args[i] != null && args[i].length() > 2083) {
					args[i] = args[i].substring(0, 2080) + "...";
				}
			}
		}
		str = MessageFormat.format(str, (Object[]) args);
		return str;
	}
}
