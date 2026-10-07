package net.zamasoft.foliojet.epub;

import java.util.List;

public class Item {
	/** The item ID. */
	public String id;
	/** The item's data format. */
	public String mediaType;
	/** The relative path from the OPF to the item. */
	public String href;
	/** The path to the item within the ZIP file. */
	public String fullPath;

	/**
	 * The item's title, taken from the guide or the document's TITLE element.
	 */
	public String title;

	/**
	 * The first guide entry corresponding to the item, or null if no guide entry exists.
	 */
	public Reference guide;

	/**
	 * The item's properties (item/@properties in the OPF)
	 */
	public List<String> properties;
}
