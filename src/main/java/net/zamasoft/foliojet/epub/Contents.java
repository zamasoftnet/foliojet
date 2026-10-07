package net.zamasoft.foliojet.epub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OPF information.
 *
 * @author MIYABE Tatsuhiko
 */
public class Contents {
	/** Unspecified (usually left to right). */
	public static final byte PAGE_PROGRESSION_DIRECTION_DEFAULT = 0;
	/** Left to right (horizontal writing). */
	public static final byte PAGE_PROGRESSION_DIRECTION_LTR = 1;
	/** Right to left (vertical writing). */
	public static final byte PAGE_PROGRESSION_DIRECTION_RTL = 2;

	/** The OPF path within the ZIP file. */
	public String base;

	/** The package's unique ID. */
	public PropertiedString id;

	/** The package title. */
	public PropertiedString title;
	/** The package description. */
	public PropertiedString description;
	/** The package language. */
	public List<PropertiedString> language = new ArrayList<PropertiedString>();
	/** The package ID. */
	public List<PropertiedString> identifier = new ArrayList<PropertiedString>();
	/** The package author. */
	public List<PropertiedString> author = new ArrayList<PropertiedString>();
	/** The package publisher. */
	public List<PropertiedString> publisher = new ArrayList<PropertiedString>();
	/** The package rights information. */
	public List<PropertiedString> rights = new ArrayList<PropertiedString>();

	Map<String, String> meta;

	/**
	 * Returns metadata.
	 *
	 * @param key
	 * @return
	 */
	public String getMeta(String key) {
		return this.meta.get(key);
	}

	/** The table of contents. */
	public Item toc;

	/** The cover. */
	public Item coverImage;

	/** An array of all items. */
	public Item[] items;

	Map<String, Item> fullPathToItem;

	/** Returns the item for a path. */
	public Item getItem(String fullPath) {
		return this.fullPathToItem.get(fullPath);
	}

	/** An array of book items in page order. */
	public ItemRef[] spine;

	/** The page progression direction. */
	public byte pageProgressionDirection = PAGE_PROGRESSION_DIRECTION_DEFAULT;

	/** Guide information. */
	public Reference[] guide;
}
