package net.zamasoft.foliojet.ua;

import java.util.Collections;
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
		this.assets = null;
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

	/**
	 * 出力済み資源の同一性です(2026-08-28、Paged SVGの再変換用)。
	 *
	 * <p>
	 * Paged SVGのページは画像を{@code assets/images/<sha256>.<ext>}という
	 * <b>内容ハッシュの名前</b>で参照します。そのため
	 * {@code output.paged-svg.resources=omit}(実体を出し直さない再変換)でも、
	 * 名前を決めるためだけに画像のバイト列を読み直す必要がありました。
	 * 寸法と一緒にこの同一性も控えておけば、次回は画像を<b>一度も開かずに</b>
	 * 同じ参照を書けます。
	 * </p>
	 *
	 * @param sha256     資源の内容ハッシュ
	 * @param mediaType  資源のMIME型
	 * @param extension  資源のファイル拡張子
	 * @param pixelWidth 画素数の幅(manifest用)
	 * @param pixelHeight 画素数の高さ(manifest用)
	 */
	public record Asset(String sha256, String mediaType, String extension, int pixelWidth, int pixelHeight) {
	}

	private Map<String, Asset> assets;

	/** 出力済み資源の同一性を記録します。 */
	public void putAsset(final String uri, final Asset asset) {
		if (uri == null || asset == null) {
			return;
		}
		if (this.assets == null) {
			this.assets = new HashMap<String, Asset>();
		}
		this.assets.put(uri, asset);
	}

	/** 記録済みの資源同一性(無ければnull)。 */
	public Asset getAsset(final String uri) {
		if (this.assets == null || uri == null) {
			return null;
		}
		return this.assets.get(uri);
	}

	/** 記録済みの資源同一性(書き出し用)。 */
	public Map<String, Asset> assets() {
		return this.assets == null ? Map.of() : Collections.unmodifiableMap(this.assets);
	}

	/** 記録済みのURIと寸法(書き出し用)。空でも{@code null}は返しません。 */
	public Map<String, Image> entries() {
		return this.metrics == null ? Map.of() : Collections.unmodifiableMap(this.metrics);
	}

	/**
	 * 幅と高さだけを持つ{@link Image}を記録します。
	 * {@code input.image-metrics}から読み込んだ寸法を入れるのに使います。
	 */
	public void putSize(final String uri, final double width, final double height) {
		this.put(uri, new SizeOnlyImage(width, height));
	}

	/**
	 * 画素を持たない寸法だけの画像です。{@code drawTo}は何もしません——
	 * この値が使われるのは寸法しか要らないパスだけで、実際に描く最終パスでは
	 * {@link net.zamasoft.foliojet.ua.impl.AbstractUserAgent#loadImage}が
	 * このキャッシュを引かないためです。
	 */
	private static final class SizeOnlyImage implements Image {
		private final double width, height;

		SizeOnlyImage(final double width, final double height) {
			this.width = width;
			this.height = height;
		}

		@Override
		public double getWidth() {
			return this.width;
		}

		@Override
		public double getHeight() {
			return this.height;
		}

		@Override
		public void drawTo(final net.zamasoft.pdfg2d.gc.GC gc) {
			// 寸法だけの画像なので描くものが無い
		}

		@Override
		public String getAltString() {
			return null;
		}
	}
}
