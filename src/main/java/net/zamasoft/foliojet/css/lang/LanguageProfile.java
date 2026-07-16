package net.zamasoft.foliojet.css.lang;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.TextTransformValue;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.layout.text.breaking.LineBreakRules;

/**
 * 各言語のための固有の機能です。
 * 
 * @author MIYABE Tatsuhiko
 */
public interface LanguageProfile {
	/**
	 * 2文字の言語コードを返します。
	 * 
	 * @return
	 */
	public String getLanguage();

	/**
	 * 与えられた文字が空白であればtrueを返します。
	 * 
	 * @param ch
	 * @return
	 */
	public boolean isWhitespace(char ch);

	/**
	 * :first-letter擬似要素によって切り出される文字数をカウントします。
	 * 
	 * @param ch
	 *            文字配列。
	 * @param off
	 *            文字列の開始位置。
	 * @param len
	 *            文字列の長さ。
	 * @return offの後に切り出す文字数。
	 */
	public int countFirstLetter(char[] ch, int off, int len);

	/**
	 * 引用符のペア(Quotes)のリストを返します。
	 * 
	 * @return
	 */
	public ValueListValue getQuotes();

	/**
	 * 大文字小文字の変換を行います。 SPEC CSS2.1 16.5
	 * 
	 * @param transform
	 *            See jp.cssj.style.text.valus.TextTransform
	 * @param ch
	 * @param off
	 * @param len
	 */
	public void transform(TextTransformValue transform, char[] ch, int off, int len);
	
	/**
	 * ハイフネーションを返します。
	 * @param style
	 * @return
	 */
	public LineBreakRules getLineBreakRules(final CSSStyle style);
}
