package net.zamasoft.foliojet.epub;

import java.util.List;

public class Item {
	/** アイテムのIDです。 */
	public String id;
	/** アイテムのデータ形式です。 */
	public String mediaType;
	/** OPFからアイテムへの相対パスです。 */
	public String href;
	/** ZIPファイル内でのアイテムへのパスです。 */
	public String fullPath;

	/**
	 * アイテムのタイトルです。 これはガイドによるタイトルか、文書のTITLE要素の内容です。
	 */
	public String title;

	/**
	 * アイテムに対応する最初のガイドです。 ガイドが存在しない場合はnullです。
	 */
	public Reference guide;

	/**
	 * アイテムのプロパティ(OPFのitem/@propertiesです)
	 */
	public List<String> properties;
}
