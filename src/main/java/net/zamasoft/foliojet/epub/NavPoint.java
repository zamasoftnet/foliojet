package net.zamasoft.foliojet.epub;

import java.net.URI;

/**
 * An entry in the table of contents.
 */
public class NavPoint {
	/** The entry's label. */
	public String label;
	/** The target URI. */
	public URI uri;
	/** The corresponding item. */
	public Item item;

	/** The entry's ID (NCX only). */
	public String id;
	/** The entry's playback order (NCX only). */
	public int playOrder;

	/** The child entries. */
	public NavPoint[] children;
}
