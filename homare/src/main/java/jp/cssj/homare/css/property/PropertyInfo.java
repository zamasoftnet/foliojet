package jp.cssj.homare.css.property;

import java.net.URI;

import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * プロパティ情報と解析のためのオブジェクトです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: PropertyInfo.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface PropertyInfo {
	/**
	 * プロパティ名を返します。
	 * 
	 * @return
	 */
	public String getName();

	/**
	 * プロパティを解析します。
	 * 
	 * @param lu
	 * @param uri
	 * @param ua
	 * @return
	 */
	public Property parseProperty(LexicalUnit lu, UserAgent ua, URI uri, boolean important) throws PropertyException;
}