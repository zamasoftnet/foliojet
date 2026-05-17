package jp.cssj.homare.css.style;

import jp.cssj.homare.css.StyleContext;

/**
 * 再生成ボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PageContent.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class PageContent extends StyleBuffer {
	public final byte[] pages;
	public final StyleContext styleContext;
	public final String name;

	public PageContent(StyleContext styleContext, byte[] pages, String name) {
		this.pages = pages;
		this.styleContext = styleContext;
		this.name = name;
	}
}
