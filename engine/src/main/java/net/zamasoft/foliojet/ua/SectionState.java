package net.zamasoft.foliojet.ua;

public class SectionState {
	/**
	 * 文書のタイトルです
	 */
	public String title = null;
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

	/**
	 * 処理中のセクションと上位にあるセクションのタイトルです。
	 */
	public String[] firstSections = new String[32];
	public boolean[] firstChangedSections = new boolean[32];

	/**
	 * 処理中のセクションと上位にあるセクションのタイトルです。
	 */
	public String[] lastSections = new String[32];
}
