package net.zamasoft.foliojet.layout.text.breaking;

/**
 * 言語ごとの行分割規則(禁則)です。分割可能位置(atomic)と
 * 均等割りの伸長可能位置(canSeparate)を判定します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: LineBreakRules.java 1572 2018-07-11 07:22:55Z miyabe $
 */
public interface LineBreakRules {
	/**
	 * 前後の文字が分割不可能であればtrueを返します。
	 * @param c1
	 * @param c2
	 * @return
	 */
	public boolean atomic(char c1, char c2);

	/**
	 * 前後の文字の間を広げることが可能であればtrueを返します。
	 * 
	 * @param c1
	 * @param c2
	 * @return
	 */
	public boolean canSeparate(char c1, char c2);
}
