package net.zamasoft.foliojet.epub;

import java.net.URI;

/**
 * 目次の項目です。
 */
public class NavPoint {
	/** 項目のラベルです。 */
	public String label;
	/** リンク先のURIです。 */
	public URI uri;
	/** 対応するアイテムです。 */
	public Item item;

	/** 項目のIDです(NCXのみ)。 */
	public String id;
	/** 項目の再生順序です(NCXのみ)。 */
	public int playOrder;

	/** 子項目です。 */
	public NavPoint[] children;
}
