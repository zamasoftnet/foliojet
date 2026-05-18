package net.zamasoft.foliojet.formatter;

import jp.cssj.cti2.TranscoderException;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.plugin.Plugin;
import net.zamasoft.zstream.resolver.Source;

/**
 * データをフォーマットします。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Formatter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Formatter extends Plugin<Source> {
	public void format(Source source, UserAgent ua) throws AbortException, TranscoderException;
}
