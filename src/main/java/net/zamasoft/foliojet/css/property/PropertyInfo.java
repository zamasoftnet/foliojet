package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * プロパティ情報と解釈のためのオブジェクトです。
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
	 * 宣言値のトークン列を解釈してプロパティを生成します。
	 *
	 * @param tokens    宣言値
	 * @param ua        ユーザーエージェント
	 * @param uri       宣言のあるスタイルシートのURI
	 * @param important !important か
	 * @return 解釈されたプロパティ
	 * @throws PropertyException 値を解釈できない場合
	 */
	public Property parse(TokenStream tokens, UserAgent ua, URI uri, boolean important) throws PropertyException;
}
