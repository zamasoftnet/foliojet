package net.zamasoft.foliojet.epub;

import java.util.List;

/**
 * Information corresponding to the table of contents (NCX).
 * 
 * @author MIYABE Tatsuhiko
 */
public class Toc {
	/**
	 * The title of the table of contents.
	 */
	public String docTitle;

	/**
	 * The list of root entries.
	 */
	public NavPoint[] navPoints;

	private void addAll(NavPoint[] navPoints, List<NavPoint> points) {
		for (NavPoint navPoint : navPoints) {
			points.add(navPoint);
			this.addAll(navPoint.children, points);
		}
	}
}
