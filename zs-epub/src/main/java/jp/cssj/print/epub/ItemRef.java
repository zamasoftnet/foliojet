package jp.cssj.print.epub;

import java.util.List;

public class ItemRef {
	public ItemRef(Item item) {
		this.item = item;
	}

	public final Item item;

	public static final byte PAGE_SPREAD_DEFAULT = 0;
	public static final byte PAGE_SPREAD_LEFT = 1;
	public static final byte PAGE_SPREAD_RIGHT = 2;
	/**
	 * ページの開始方向です。
	 */
	public byte pageSpread = PAGE_SPREAD_DEFAULT;

	/**
	 * アイテムのプロパティ(OPFのitemref/@propertiesです)
	 */
	public List<String> properties;
}
