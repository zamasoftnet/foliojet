package net.zamasoft.foliojet.xml.vocab;

import net.zamasoft.foliojet.xml.AttributeNode;
import net.zamasoft.foliojet.xml.ElementNode;

/**
 * CSSJ独自のマークアップ(CSSJML)です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJML.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class CSSJML {
	private CSSJML() {
		// unused
	}

	/**
	 * CSSJMLの接頭辞です。
	 */
	public static final String PREFIX = "cssj";

	/**
	 * CSSJMLの名前空間URIです。
	 */
	public static final String URI = "http://www.cssj.jp/ns/cssjml";

	/**
	 * 注釈を出力します。
	 */
	public static final AttributeNode ANNOT_ATTR = new AttributeNode(URI, PREFIX, "annot");

	/**
	 * ヘッダーとしてのレベルを指定します。
	 */
	public static final AttributeNode HEADER_ATTR = new AttributeNode(URI, PREFIX, "header");

	/**
	 * 目次を生成します。
	 */
	public static final ElementNode MAKE_TOC_ELEM = new ElementNode(URI, PREFIX, "make-toc");

	/**
	 * 索引を生成します。
	 */
	public static final ElementNode MAKE_INDEX_ELEM = new ElementNode(URI, PREFIX, "make-index");

	/**
	 * 索引のキーワードとしてマークします。
	 */
	public static final ElementNode INDEX_ELEM = new ElementNode(URI, PREFIX, "index");

	/**
	 * エラーで停止します（テスト用）。
	 */
	public static final ElementNode FAIL_ELEM = new ElementNode(URI, PREFIX, "fail");

	/**
	 * HTMLの文書内にスタイルシートを埋め込みます。dataの値がスタイルシートそのものです。
	 */
	public static final String PI_STYLESHEET = "jp.cssj.stylesheet";

	/**
	 * 文書情報です。擬似属性name,valueで名前と値を設定します。
	 */
	public static final String PI_DOCUMENT_INFO = "jp.cssj.document-info";

	/**
	 * スタイルシートのデフォルトのキャラクタ・エンコーディングです。
	 */
	public static final String PI_DEFAULT_ENCODING = "jp.cssj.default-encoding";

	/**
	 * デフォルトのスタイルシートMIME型です。
	 */
	public static final String PI_DEFAULT_STYLE_TYPE = "jp.cssj.default-style-type";

	/**
	 * 文書の基準URIです。
	 */
	public static final String PI_BASE_URI = "jp.cssj.base-uri";

	/** プロパティ設定のためのPI。 */
	public static final String PI_PROPERTY = "jp.cssj.property";
}