package net.zamasoft.foliojet.epub;

import java.util.ArrayList;
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

	/**
	 * 全ての項目を返します。
	 * 
	 * @return
	 */
	public NavPoint[] getAllNavPoints() {
		List<NavPoint> points = new ArrayList<NavPoint>();
		this.addAll(this.navPoints, points);
		return points.toArray(new NavPoint[points.size()]);
	}

	private void addAll(NavPoint[] navPoints, List<NavPoint> points) {
		for (NavPoint navPoint : navPoints) {
			points.add(navPoint);
			this.addAll(navPoint.children, points);
		}
	}
}
