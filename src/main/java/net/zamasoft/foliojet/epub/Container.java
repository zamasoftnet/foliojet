package net.zamasoft.foliojet.epub;

/**
 * Information corresponding to the META-INFO/container.xml file.
 *
 * @author MIYABE Tatsuhiko
 */
public class Container {
	/** Root file information. */
	public static class Rootfile {
		/** The data format. */

		public String mediaType;
		/** The file path within the archive. */
		public String fullPath;
	}

	/** All root files. */
	public Rootfile[] rootfiles;
}
