package net.zamasoft.foliojet.epub;

import java.util.List;

/**
 * 目次(NCX)に相当する情報です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class Toc {
	/**
	 * 目次のタイトルです。
	 */
	public String docTitle;

	/**
	 * ルートの項目のリストです。
	 */
	public NavPoint[] navPoints;

	private void addAll(NavPoint[] navPoints, List<NavPoint> points) {
		for (NavPoint navPoint : navPoints) {
			points.add(navPoint);
			this.addAll(navPoint.children, points);
		}
	}
}
