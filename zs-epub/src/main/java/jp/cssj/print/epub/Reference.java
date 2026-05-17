package jp.cssj.print.epub;

/**
 * ガイドの要素です。
 */
public class Reference {
	/** タイプ("text", "cover"など)です。 */
	public String type;
	/** タイトルです。 */
	public String title;
	/** OPFからの相対パスです。 */
	public String href;
	/** ZIPファイル内でのパスです。 */
	public String fullPath;
	/** 対応するアイテムです。 */
	public Item item;
}
