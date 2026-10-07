package net.zamasoft.foliojet.epub;

/**
 * A guide entry.
 */
public class Reference {
	/** The type ("text", "cover", etc.). */
	public String type;
	/** The title. */
	public String title;
	/** The relative path from the OPF. */
	public String href;
	/** The path within the ZIP file. */
	public String fullPath;
	/** The corresponding item. */
	public Item item;
}
