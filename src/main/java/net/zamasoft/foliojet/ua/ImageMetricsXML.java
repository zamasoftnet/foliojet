package net.zamasoft.foliojet.ua;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * {@link ImageMetricsCache}のXML表現です。
 *
 * <p>
 * 画像の固有寸法は、同じ本を文字サイズや画面サイズだけ変えて組み直しても
 * 変わりません。一度測ったものを外に出しておけば、次回以降は
 * {@code input.image-metrics}で渡すだけで済み、寸法しか要らないパスでは
 * 画像資源を<b>一度も開かずに</b>組版できます。リモート資源ではその往復が
 * まるごと無くなります。
 * </p>
 *
 * <p>
 * <b>記録する幅と高さは出力単位(pt)です。</b> 画素値ではありません——
 * 値は{@code getImage}が返したもの、すなわち{@code output.resolution}による
 * px→pt変換の適用後だからです。EXIFの回転も適用済みです。
 * </p>
 *
 * <p>
 * そのため<b>解像度が違う設定で作った寸法表を混ぜてはいけません</b>。
 * 根拠にした{@code output.resolution}を{@code resolution}属性として記録し、
 * 読み込み時に食い違っていればその寸法表を丸ごと捨てます。黙って
 * 誤った寸法で組むより、測り直したほうが安全です。
 * </p>
 *
 * <pre>
 * &lt;image-metrics version="1" resolution="96"&gt;
 *   &lt;image uri="..." width="900" height="600"/&gt;
 * &lt;/image-metrics&gt;
 * </pre>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ImageMetricsXML {
	/** 根要素名。 */
	public static final String ROOT = "image-metrics";

	private ImageMetricsXML() {
		// ユーティリティ
	}

	/**
	 * XMLを読んでキャッシュに入れます。既にある記録は上書きしません——
	 * 実測した寸法のほうが確かなためです。
	 *
	 * @return 読み込んだ件数
	 */
	public static int read(final InputStream in, final ImageMetricsCache cache, final double resolution)
			throws IOException, SAXException {
		final int[] count = { 0 };
		final boolean[] usable = { true };
		final DefaultHandler handler = new DefaultHandler() {
			@Override
			public void startElement(final String uri, final String localName, final String qName,
					final Attributes attributes) throws SAXException {
				final String name = localName == null || localName.isEmpty() ? qName : localName;
				if (ROOT.equals(name)) {
					// 解像度が違えば寸法の意味が変わる。黙って誤った寸法を
					// 使うより捨てて測り直す
					final String recorded = attributes.getValue("resolution");
					if (recorded != null) {
						try {
							usable[0] = Math.abs(Double.parseDouble(recorded) - resolution) < 1e-6;
						} catch (final NumberFormatException e) {
							usable[0] = false;
						}
					}
					return;
				}
				if (!"image".equals(name) || !usable[0]) {
					return;
				}
				final String href = attributes.getValue("uri");
				final String width = attributes.getValue("width");
				final String height = attributes.getValue("height");
				if (href == null || width == null || height == null) {
					return;
				}
				final double w, h;
				try {
					w = Double.parseDouble(width);
					h = Double.parseDouble(height);
				} catch (final NumberFormatException e) {
					return;
				}
				if (!(w > 0) || !(h > 0) || cache.get(href) != null) {
					return;
				}
				cache.putSize(href, w, h);
				++count[0];
			}
		};
		newParser().parse(new InputSource(in), handler);
		return count[0];
	}

	/**
	 * キャッシュの内容をXMLにします。URI順に並べるので、同じ内容からは
	 * 必ず同じバイト列になります。
	 */
	public static byte[] write(final ImageMetricsCache cache, final double resolution) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(256 + cache.size() * 96);
		try (Writer out = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
			out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<" + ROOT + " version=\"1\" resolution=\""
					+ number(resolution) + "\">\n");
			for (final Map.Entry<String, Image> entry : new TreeMap<>(cache.entries()).entrySet()) {
				final Image image = entry.getValue();
				out.write("  <image uri=\"");
				escape(out, entry.getKey());
				out.write("\" width=\"");
				out.write(number(image.getWidth()));
				out.write("\" height=\"");
				out.write(number(image.getHeight()));
				out.write("\"/>\n");
			}
			out.write("</" + ROOT + ">\n");
		}
		return bytes.toByteArray();
	}

	private static SAXParser newParser() throws IOException, SAXException {
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		try {
			// 外部実体を読まない。寸法表は信用できない場所から来うる
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			return factory.newSAXParser();
		} catch (final ParserConfigurationException e) {
			throw new IOException(e);
		}
	}

	private static String number(final double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value)) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}

	private static void escape(final Writer out, final String value) throws IOException {
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '&' -> out.write("&amp;");
			case '<' -> out.write("&lt;");
			case '>' -> out.write("&gt;");
			case '"' -> out.write("&quot;");
			default -> {
				if (ch < 0x20) {
					out.write("&#" + (int) ch + ';');
				} else {
					out.write(ch);
				}
			}
			}
		}
	}
}
