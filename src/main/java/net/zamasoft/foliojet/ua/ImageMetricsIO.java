package net.zamasoft.foliojet.ua;

import java.io.IOException;
import java.io.InputStream;

/**
 * 画像寸法表の読み書きの入口です(2026-08-28)。
 *
 * <p>
 * <b>書くのはJSON</b>({@link ImageMetricsJSON})。ページ分割SVGの他の
 * 成果物({@code manifest.json}・ページJSON)と形式を揃えます。
 * </p>
 *
 * <p>
 * 4.0.0の開発中に出していたXML({@code metrics.xml})の読み取りは、公開した版が一度も
 * 出していない形式の互換のためだけの層だったので2026-10-04に削除した。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ImageMetricsIO {

	private ImageMetricsIO() {
		// ユーティリティ
	}

	/** 書き出し形式のMIME型。 */
	public static final String MEDIA_TYPE = "application/json";

	/** ページ分割SVGが出す寸法表の名前。 */
	public static final String FILE_NAME = "metrics.json";

	/** 寸法表をJSONで書き出します。 */
	public static byte[] write(final ImageMetricsCache cache, final double resolution) throws IOException {
		return ImageMetricsJSON.write(cache, resolution);
	}

	/**
	 * 寸法表(JSON)を読み込みます。
	 *
	 * @return 読み込んだ件数
	 */
	public static int read(final InputStream in, final ImageMetricsCache cache, final double resolution)
			throws IOException {
		return ImageMetricsJSON.read(in, cache, resolution);
	}
}
