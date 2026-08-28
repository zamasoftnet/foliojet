package net.zamasoft.foliojet.ua;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;

/**
 * 画像寸法表の読み書きの入口です(2026-08-28)。
 *
 * <p>
 * <b>書くのはJSON</b>({@link ImageMetricsJSON})。ページ分割SVGの他の
 * 成果物({@code manifest.json}・ページJSON)と形式を揃えます。
 * </p>
 *
 * <p>
 * <b>読むのは両方</b>。4.0.0の開発中はXML({@code metrics.xml})で出して
 * いたので、手元に残っている寸法表や、それを前提にした手順書がそのまま
 * 動くようにします。先頭の非空白文字が{@code <}ならXML、それ以外はJSONと
 * みなすだけの判別で、形式の指定は要りません。
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
	 * 寸法表を読み込みます。JSONとXMLのどちらでも受け付けます。
	 *
	 * @return 読み込んだ件数
	 */
	public static int read(final InputStream in, final ImageMetricsCache cache, final double resolution)
			throws IOException, SAXException {
		final BufferedInputStream buffered = in instanceof BufferedInputStream b ? b : new BufferedInputStream(in);
		buffered.mark(64);
		int c;
		while ((c = buffered.read()) != -1 && Character.isWhitespace(c)) {
			// 先頭の空白を読み飛ばす
		}
		buffered.reset();
		if (c == '<') {
			return ImageMetricsXML.read(buffered, cache, resolution);
		}
		return ImageMetricsJSON.read(buffered, cache, resolution);
	}
}
