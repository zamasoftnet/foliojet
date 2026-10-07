package net.zamasoft.foliojet.epub;

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
	 * The side on which the page starts.
	 */
	public byte pageSpread = PAGE_SPREAD_DEFAULT;

	/**
	 * The item's properties (itemref/@properties in the OPF)
	 */
	public List<String> properties;
}
