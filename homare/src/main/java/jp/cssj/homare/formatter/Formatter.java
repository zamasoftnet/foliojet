package jp.cssj.homare.formatter;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.homare.ua.AbortException;
import jp.cssj.homare.ua.UserAgent;
import jp.cssj.plugin.Plugin;
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
