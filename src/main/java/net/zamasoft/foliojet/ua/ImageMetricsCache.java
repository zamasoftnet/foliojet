package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * 画像の固有寸法(と適用済みEXIF方向)のキャッシュです(2026-08-16)。
 *
 * <p>
 * レイアウトに必要なのは幅と高さだけで、画素は要りません。そのため
 * 測定パスと{@code STRUCTURE_SCAN}は既に
 * {@link net.zamasoft.foliojet.ua.impl.image.RasterImageLoader#loadImageForLayout}
 * で<b>ヘッダだけ</b>を読んでいますが、読んだ結果はどこにも残しておらず、
 * 同じ画像が何度現れても、またパスが変わるたびに、資源を開き直して
 * ヘッダを読み直していました。ローカルでも無駄ですが、
 * <b>リモート資源では取得の往復がそのぶん増えます</b>。
 * </p>
 *
 * <p>
 * ここではURI文字列をキーに寸法だけを持ちます。画素を持たないので
 * 容量は無視できます({@code loadImageForLayout}が返すのは幅・高さと
 * 何もしない{@code drawTo}だけの軽量な{@link Image})。
 * </p>
 *
 * <p>
 * 寿命は{@link SelectorFacts}・{@link ContainerFacts}と揃え、
 * {@code STRUCTURE_SCAN}(多パス)と{@code DOCUMENT}(単一パス)の開始で
 * リセットします——別の文書で同じURIが違う内容を指しうるためです。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ImageMetricsCache {
	private Map<String, Image> metrics;

	/** 文書の開始でクリアします。 */
	public void reset() {
		this.metrics = null;
	}

	/** 記録済みの寸法(無ければnull)。 */
	public Image get(final String uri) {
		if (this.metrics == null || uri == null) {
			return null;
		}
		return this.metrics.get(uri);
	}

	/** 寸法を記録します。 */
	public void put(final String uri, final Image image) {
		if (uri == null || image == null) {
			return;
		}
		if (this.metrics == null) {
			this.metrics = new HashMap<String, Image>();
		}
		this.metrics.put(uri, image);
	}

	/** 記録件数(診断用)。 */
	public int size() {
		return this.metrics == null ? 0 : this.metrics.size();
	}
}
