package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PageValueUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class PageValueUtils {
	private PageValueUtils() {
		// unused
	}

	/**
	 * &lt;margin-width&gt; を値に変換します(emやexなどのフォント相対長さは不可)。
	 */
	public static Value toMarginWidth(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Percent percent) {
			return ValueUtils.toPercentage(percent);
		}
		return ValueUtils.toAbsoluteLength(ua, token);
	}
}
