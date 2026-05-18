package net.zamasoft.foliojet.epub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OPFの情報です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class Contents {
	/** 指定なし（通常は左から右）。 */
	public static final byte PAGE_PROGRESSION_DIRECTION_DEFAULT = 0;
	/** 左から右へ（横書き）。 */
	public static final byte PAGE_PROGRESSION_DIRECTION_LTR = 1;
	/** 右から左へ（縦書き）。 */
	public static final byte PAGE_PROGRESSION_DIRECTION_RTL = 2;

	/** OPFのZIPファイル内でのパスです。 */
	public String base;

	/** パッケージの唯一のIDです。 */
	public PropertiedString id;

	/** パッケージのタイトルです。 */
	public PropertiedString title;
	/** パッケージの説明です。 */
	public PropertiedString description;
	/** パッケージの言語です。 */
	public List<PropertiedString> language = new ArrayList<PropertiedString>();
	/** パッケージのIDです。 */
	public List<PropertiedString> identifier = new ArrayList<PropertiedString>();
	/** パッケージの作者です。 */
	public List<PropertiedString> author = new ArrayList<PropertiedString>();
	/** パッケージの出版社です。 */
	public List<PropertiedString> publisher = new ArrayList<PropertiedString>();
	/** パッケージの権利情報です。 */
	public List<PropertiedString> rights = new ArrayList<PropertiedString>();

	Map<String, String> meta;

	/**
	 * メタデータを返します。
	 * 
	 * @param key
	 * @return
	 */
	public String getMeta(String key) {
		return this.meta.get(key);
	}

	/** 目次です。 */
	public Item toc;

	/** 表紙です。 */
	public Item coverImage;

	/** 全ての項目の配列です。 */
	public Item[] items;

	Map<String, Item> fullPathToItem;

	/** パスから項目を返します。 */
	public Item getItem(String fullPath) {
		return this.fullPathToItem.get(fullPath);
	}

	/** ページ順に並べられた本の項目の配列です。 */
	public ItemRef[] spine;

	/** ページ進行方法です。 */
	public byte pageProgressionDirection = PAGE_PROGRESSION_DIRECTION_DEFAULT;

	/** ガイド情報です。 */
	public Reference[] guide;
}
