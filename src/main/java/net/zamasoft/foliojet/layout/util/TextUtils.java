package net.zamasoft.foliojet.layout.util;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class TextUtils {
	private TextUtils() {
		// unused
	}

	public static boolean isWhiteSpace(char c) {
		switch (c) {
		case 0x09:
		case 0x0A:
		case 0x0C:
		case 0x0D:
		case 0x20:
		case 0x7F:
			return true;
		}
		return false;
	}

	public static boolean isControl(char c) {
		switch (c) {
		case 0x09:
		case 0x0A:
		case 0x0C:
		case 0x0D:
			return true;
		}
		return false;
	}
}