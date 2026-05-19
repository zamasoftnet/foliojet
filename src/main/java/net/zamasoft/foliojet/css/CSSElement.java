package net.zamasoft.foliojet.css;

import java.util.Locale;

import org.xml.sax.Attributes;

/**
 * CSS要素の情報です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSElement.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class CSSElement {
	private static final boolean DEBUG_CHAIN = false;

	public static final byte PC_FIRST = 1;
	public static final byte PC_LEFT = 2;
	public static final byte PC_RIGHT = 3;
	public static final byte PC_EVEN = 4;
	public static final byte PC_ODD = 5;
	public static final byte PC_FIRST_CHILD = 6;
	public static final byte PC_LINK = 7;
	public static final byte PC_ROOT = 8;
	
	/**
	 * 前に文字列がないfirst-childです。
	 */
	public static final byte PC_CSSJ_FIRST_CHILD = 101;

	/**
	 * at-page 左綴じ両面の最初のページです。
	 */
	public static final CSSElement PAGE_FIRST_RIGHT = new CSSElement(new byte[] { PC_FIRST, PC_RIGHT, PC_ODD });

	/**
	 * at-page 左綴じ左ページです。
	 */
	public static final CSSElement PAGE_LEFT_EVEN = new CSSElement(new byte[] { PC_LEFT, PC_EVEN });

	/**
	 * at-page 左綴じ右ページです。
	 */
	public static final CSSElement PAGE_RIGHT_ODD = new CSSElement(new byte[] { PC_RIGHT, PC_ODD });

	/**
	 * at-page 右綴じ両面の最初のページです。
	 */
	public static final CSSElement PAGE_FIRST_LEFT = new CSSElement(new byte[] { PC_FIRST, PC_LEFT, PC_ODD });

	/**
	 * at-page 右綴じ左ページです。
	 */
	public static final CSSElement PAGE_LEFT_ODD = new CSSElement(new byte[] { PC_LEFT, PC_ODD });

	/**
	 * at-page 右綴じ右ページです。
	 */
	public static final CSSElement PAGE_RIGHT_EVEN = new CSSElement(new byte[] { PC_RIGHT, PC_EVEN });

	/**
	 * at-page 片面の最初のページです。
	 */
	public static final CSSElement PAGE_SINGLE_FIRST = new CSSElement(new byte[] { PC_FIRST });

	/**
	 * at-page 片面のページです。
	 */
	public static final CSSElement PAGE_SINGLE = new CSSElement((byte[]) null);

	/**
	 * at-page first-line 擬似要素です。
	 */
	public static final CSSElement FIRST_LINE = new CSSElement("first-line");

	/**
	 * at-page first-letter 擬似要素です。
	 */
	public static final CSSElement FIRST_LETTER = new CSSElement("first-letter");

	/**
	 * at-page before 擬似要素です。
	 */
	public static final CSSElement BEFORE = new CSSElement("before");

	/**
	 * at-page after 擬似要素です。
	 */
	public static final CSSElement AFTER = new CSSElement("after");

	/**
	 * 匿名要素です。
	 */
	public static final CSSElement ANON = new CSSElement((String)null);
	public static final CSSElement ANON_RUBY = new CSSElement("ruby");
	public static final CSSElement ANON_RB = new CSSElement("rb");
	public static final CSSElement ANON_TABLE = new CSSElement("table");
	public static final CSSElement ANON_TBODY = new CSSElement("tbody");
	public static final CSSElement ANON_TR = new CSSElement("tr");
	public static final CSSElement ANON_TD = new CSSElement("td");

	/** XML/HTML要素です。 */
	public final String uri, lName;

	/** CSS IDセレクタに対応するIDです。 */
	public final String id;

	/** CSS classセレクタに対応する全てのクラスです。 */
	public final String[] styleClasses;

	/** CSS擬似クラスです。 */
	public final byte[] pseudoClasses;

	/** 言語です。 */
	public final Locale lang;

	/** XML/HTML属性です。 */
	public final Attributes atts;

	/** 先行する要素です。 */
	public final CSSElement precedingElement;

	/** 文書中の位置です。 */
	public final int charOffset;

	/**
	 * HTML要素を構築します。
	 * 
	 * @param uri
	 * @param lName
	 * @param id
	 * @param styleClasses
	 * @param pseudoClasses
	 * @param atts
	 * @param precedingElement
	 * @param charOffset
	 */
	public CSSElement(String uri, String lName, String id, String[] styleClasses, byte[] pseudoClasses, Locale lang,
			Attributes atts, CSSElement precedingElement, int charOffset) {
		this.uri = uri;
		this.lName = lName;
		this.id = id;
		this.styleClasses = styleClasses;
		this.pseudoClasses = pseudoClasses;
		this.lang = lang;
		this.atts = atts;
		this.precedingElement = precedingElement;
		this.charOffset = charOffset;
	}

	/**
	 * 擬似要素を構築します。
	 * 
	 * @param pseudoElement
	 */
	private CSSElement(String pseudoElement) {
		this(null, pseudoElement, null, null, null, null, null, null, -1);
	}

	/**
	 * 擬似クラスを構築します。
	 * 
	 * @param pseudoClasses
	 */
	private CSSElement(byte[] pseudoClasses) {
		this(null, null, null, null, pseudoClasses, null, null, null, -1);
	}

	/**
	 * 与えられたクラスであればtrueを返します。
	 * 
	 * @param styleClass
	 * @return
	 */
	public boolean isStyleClass(String styleClass) {
		if (this.styleClasses != null) {
			for (int i = 0; i < this.styleClasses.length; ++i) {
				if (styleClass.equalsIgnoreCase(this.styleClasses[i])) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 与えられた擬似クラスであればtrueを返します。
	 * 
	 * @param pseudoClass
	 * @return
	 */
	public boolean isPseudoClass(byte pseudoClass) {
		if (pseudoClass == 0) {
			return false;
		}
		if (this.pseudoClasses != null) {
			for (int i = 0; i < this.pseudoClasses.length; ++i) {
				if (pseudoClass == this.pseudoClasses[i]) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isPseudoElement() {
		return this.atts == null && this.lName != null;
	}

	public String toString() {
		StringBuffer buff = new StringBuffer();
		buff.append(super.toString());
		buff.append("@uri='");
		buff.append(this.uri);
		buff.append("',lName='");
		buff.append(this.lName);
		buff.append("'");
		if (this.id != null) {
			buff.append(",id='");
			buff.append(this.id);
			buff.append("'");
		}
		if (this.pseudoClasses != null) {
			buff.append(",pseudoClasses='");
			for (int i = 0; i < this.pseudoClasses.length; ++i) {
				if (i > 0) {
					buff.append(",");
				}
				buff.append(this.pseudoClasses[i]);
			}
			buff.append("'");
		}
		if (this.styleClasses != null) {
			buff.append(",styleClasses='");
			for (int i = 0; i < this.styleClasses.length; ++i) {
				if (i > 0) {
					buff.append(",");
				}
				buff.append(this.styleClasses[i]);
			}
			buff.append("'");
		}
		if (this.lang != null) {
			buff.append(",lang='");
			buff.append(this.lang);
			buff.append("'");
		}
		if (this.precedingElement != null) {
			buff.append(",precedingElement='");
			buff.append(this.precedingElement.chain());
			buff.append("'");
		}
		if (this.atts != null && this.atts.getLength() > 0) {
			buff.append("[");
			for (int i = 0; i < this.atts.getLength(); ++i) {
				if (i > 0) {
					buff.append(",");
				}
				buff.append(this.atts.getLocalName(i));
				buff.append('=');
				buff.append(this.atts.getValue(i));
			}
			buff.append("]");
		}
		return buff.toString();
	}

	private String chain() {
		if (DEBUG_CHAIN) {
			if (this.precedingElement == null) {
				return this.lName;
			}
			return this.lName + "/" + this.precedingElement.chain();
		}
		return this.lName;
	}
}