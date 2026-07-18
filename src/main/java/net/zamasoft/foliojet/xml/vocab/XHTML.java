package net.zamasoft.foliojet.xml.vocab;

import net.zamasoft.foliojet.xml.AttributeNode;
import net.zamasoft.foliojet.xml.ElementNode;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: XHTML.java 1587 2019-06-10 01:42:25Z miyabe $
 */
public final class XHTML {
	private XHTML() {
		// unused
	}

	public static final String PREFIX = "html";

	public static final String URI = "http://www.w3.org/1999/xhtml";

	/** ルート。 */
	public static final ElementNode HTML_ELEM = new ElementNode(URI, PREFIX, "html");

	/** 文書のスタイル。(SPEC CSS2 2.1) */
	public static final ElementNode STYLE_ELEM = new ElementNode(URI, PREFIX, "style");

	/** タイトル。 */
	public static final ElementNode TITLE_ELEM = new ElementNode(URI, PREFIX, "title");

	/** メタ情報。 */
	public static final ElementNode META_ELEM = new ElementNode(URI, PREFIX, "meta");

	/** 文書の内容。 */
	public static final ElementNode BODY_ELEM = new ElementNode(URI, PREFIX, "body");

	/** ベースURI変更のための要素。 */
	public static final ElementNode BASE_ELEM = new ElementNode(URI, PREFIX, "base");

	/** リンクのための要素。(SPEC ASSX1.0) */
	public static final ElementNode LINK_ELEM = new ElementNode(URI, PREFIX, "link");

	/** アンカー要素。 */
	public static final ElementNode A_ELEM = new ElementNode(URI, PREFIX, "a");

	/** 画像要素。 */
	public static final ElementNode IMG_ELEM = new ElementNode(URI, PREFIX, "img");

	/** 埋め込み要素。 */
	public static final ElementNode EMBED_ELEM = new ElementNode(URI, PREFIX, "embed");

	/** オブジェクト要素。 */
	public static final ElementNode OBJECT_ELEM = new ElementNode(URI, PREFIX, "object");

	/** INPUT要素。 */
	public static final ElementNode INPUT_ELEM = new ElementNode(URI, PREFIX, "input");

	/** BUTTON要素。 */
	public static final ElementNode BUTTON_ELEM = new ElementNode(URI, PREFIX, "button");
	//
	// /** RUBY要素。 */
	// public static final ElementNode RUBY_ELEM = new ElementNode(URI, PREFIX,
	// "ruby");
	//
	// /** RB要素。 */
	// public static final ElementNode RB_ELEM = new ElementNode(URI, PREFIX,
	// "rb");

	public static final ElementNode OL_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "ol");

	public static final ElementNode UL_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "ul");

	public static final ElementNode LI_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "li");

	public static final ElementNode SPAN_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "span");

	public static final ElementNode P_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "p");

	public static final ElementNode TD_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "td");

	public static final ElementNode TH_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "th");

	public static final ElementNode H1_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h1");

	public static final ElementNode H2_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h2");

	public static final ElementNode H3_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h3");

	public static final ElementNode H4_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h4");

	public static final ElementNode H5_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h5");

	public static final ElementNode H6_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h6");

	public static final ElementNode BR_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "br");

	/** ID選択子のための属性。 */
	public static final AttributeNode ID_ATTR = new AttributeNode("id");

	/** クラス選択子のための属性。 */
	public static final AttributeNode CLASS_ATTR = new AttributeNode("class");

	/** インラインスタイルのための属性。 */
	public static final AttributeNode STYLE_ATTR = new AttributeNode("style");

	/** 言語選択子のための属性。 */
	public static final AttributeNode LANG_ATTR = new AttributeNode("lang");

	/** :dir() 選択子のための属性。 */
	public static final AttributeNode DIR_ATTR = new AttributeNode("dir");

	/** 補助説明文属性。 */
	public static final AttributeNode TITLE_ATTR = new AttributeNode("title");

	/** テーブルカラムの結合属性。 */
	public static final AttributeNode SPAN_ATTR = new AttributeNode("span");

	/** テーブルセルの行方向結合属性。 */
	public static final AttributeNode COLSPAN_ATTR = new AttributeNode("colspan");

	/** テーブルカラムの列方向結合属性。 */
	public static final AttributeNode ROWSPAN_ATTR = new AttributeNode("rowspan");

	public static final AttributeNode HREF_ATTR = new AttributeNode("href");

	public static final AttributeNode NAME_ATTR = new AttributeNode("name");

	/** Image map reference. */
	public static final AttributeNode USEMAP_ATTR = new AttributeNode("usemap");
}