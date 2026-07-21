package net.zamasoft.foliojet.ua;

public class SectionState {
	/**
	 * 処理中のセクションのレベル(H1~H6の数字部分)です。
	 */
	public int sectionLevel = 1;
	/**
	 * 処理中のセクションの深さです(例えばH1の下のH3を処理中であれば深さは2です)。
	 */
	public int sectionDepth = 0;

	/**
	 * 処理したセクションの数です。
	 */
	public int sectionCount = 0;
}
