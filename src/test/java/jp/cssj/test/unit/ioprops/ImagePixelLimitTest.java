package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 画像の画素数の上限({@code input.image-pixel-limit}・{@code output.image-pixel-limit})
 * を固定します(2026-10-03、共有サービスの資源の上限 増分1。
 * {@code copperpdf4/docs/design/shared-service-limits-design.md} §3-1)。
 *
 * <p>
 * 画素を展開する<b>前に</b>ヘッダの寸法で断ることが肝心なので、試験の画像は
 * <b>ヘッダだけ</b>を正しく作り、画素は持たせない(展開しようとすれば
 * 「decode」で失敗し、断れば「too-large」になる——段階の文言で区別できる)。
 * </p>
 */
public class ImagePixelLimitTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 1x1 の PNG(本物)。 */
	private static final byte[] PNG_1X1 = Base64.getDecoder()
			.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	/** 1x1 の透明 GIF(本物、43 バイト)。論理画面の寸法は 6〜9 バイト目。 */
	private static final byte[] GIF_1X1 = { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80,
			0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x21, (byte) 0xF9, 0x04, 0x01, 0x00,
			0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00,
			0x3B };

	private final List<String> image2811 = new ArrayList<>();
	private final List<Short> codes = new ArrayList<>();
	private boolean failed;

	/** 上限を超える PNG は展開せずに断る(2811 の段階が too-large)。 */
	public void testLargePngIsRefusedBeforeDecoding() throws Exception {
		final String pdf = this.convert(page("big.png"), Map.of("big.png", pngHeaderOnly(20000, 20000)),
				props("input.image-pixel-limit", "40000000"));
		assertFalse("文書は変換できること", this.failed);
		assertFalse("画像を埋め込まないこと", pdf.contains("/Subtype /Image"));
		assertEquals(List.of("too-large 20000x20000 > 40000000"), this.image2811);
	}

	/** 上限が無ければ従来どおり展開を試みる(この画像は画素が無いので decode で失敗する)。 */
	public void testWithoutLimitTheDecoderRuns() throws Exception {
		this.convert(page("big.png"), Map.of("big.png", pngHeaderOnly(20000, 20000)), props());
		assertFalse("文書は変換できること", this.failed);
		assertEquals(List.of("decode"), this.image2811);
	}

	/** 境界: 幅×高さが上限ちょうどなら読み、1 画素でも超えれば断る。 */
	public void testBoundary() throws Exception {
		final String exact = this.convert(page("one.png"), Map.of("one.png", PNG_1X1),
				props("input.image-pixel-limit", "1"));
		assertFalse(this.failed);
		assertTrue("1x1 は上限 1 で読むこと", exact.contains("/Subtype /Image"));
		assertTrue(this.image2811.isEmpty());

		final String zero = this.convert(page("one.png"), Map.of("one.png", PNG_1X1),
				props("input.image-pixel-limit", "0"));
		assertFalse(this.failed);
		assertFalse("上限 0 では読まないこと", zero.contains("/Subtype /Image"));
		assertEquals(List.of("too-large 1x1 > 0"), this.image2811);
	}

	/**
	 * 透明度の無い PNG(RGB・グレー・パレット)も上限の下で読む(2026-10-04)。寸法を読んだリーダが
	 * ヘッダを読んだ状態を覚えていて、先頭へ戻した後の型の判定が失敗し、「読めない画像」として
	 * 消えていた(19088〜19095 の本番。上の境界の試験の PNG は透明度付きで通っていた)。
	 */
	public void testPngWithoutAlphaIsReadUnderALimit() throws Exception {
		final Map<String, byte[]> files = new java.util.LinkedHashMap<>();
		final StringBuilder html = new StringBuilder("<html><body>");
		for (final int type : new int[] { java.awt.image.BufferedImage.TYPE_INT_RGB,
				java.awt.image.BufferedImage.TYPE_BYTE_GRAY, java.awt.image.BufferedImage.TYPE_BYTE_INDEXED }) {
			final java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
			javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 10, type), "png", png);
			files.put("t" + type + ".png", png.toByteArray());
			html.append("<p><img src='t").append(type).append(".png'/></p>");
		}
		html.append("</body></html>");
		final String pdf = this.convert(html.toString(), files, props("input.image-pixel-limit", "40000000"));
		assertFalse(this.failed);
		assertEquals(List.of(), this.image2811);
		assertEquals(3, pdf.split("/Subtype /Image", -1).length - 1);
	}

	/** data: の画像も同じ判定(測定のパスでも全展開する経路)。 */
	public void testDataUri() throws Exception {
		final String html = "<html><body><p>x</p><img src='data:image/png;base64,"
				+ Base64.getEncoder().encodeToString(pngHeaderOnly(20000, 20000)) + "'/></body></html>";
		this.convert(html, Map.of(), props("input.image-pixel-limit", "40000000", "processing.pass-count", "2"));
		assertFalse(this.failed);
		assertFalse(this.image2811.isEmpty());
		for (final String detail : this.image2811) {
			assertEquals("too-large 20000x20000 > 40000000", detail);
		}
	}

	/** 複数パス(測定のパスはヘッダだけ読む経路)でも同じく断ること。 */
	public void testMeasurePassAgrees() throws Exception {
		final String pdf = this.convert(page("big.png"), Map.of("big.png", pngHeaderOnly(20000, 20000)),
				props("input.image-pixel-limit", "40000000", "processing.pass-count", "2"));
		assertFalse(this.failed);
		assertFalse(pdf.contains("/Subtype /Image"));
		assertFalse(this.image2811.isEmpty());
		for (final String detail : this.image2811) {
			assertEquals("too-large 20000x20000 > 40000000", detail);
		}
	}

	/**
	 * GIF は論理画面の寸法も数える。ImageIO は 1x1 のフレームを読むが、AWT の
	 * 代替経路は論理画面全体を描くため。
	 */
	public void testGifLogicalScreenCounts() throws Exception {
		final byte[] gif = GIF_1X1.clone();
		gif[6] = 0x30; // 30000 = 0x7530(リトルエンディアン)
		gif[7] = 0x75;
		gif[8] = 0x30;
		gif[9] = 0x75;
		this.convert(page("screen.gif"), Map.of("screen.gif", gif), props("input.image-pixel-limit", "1000000"));
		assertFalse(this.failed);
		assertEquals(List.of("too-large 30000x30000 > 1000000"), this.image2811);
	}

	/** JPEG(PDF へ素通しする経路)も展開の前に断る。 */
	public void testLargeJpegIsRefused() throws Exception {
		final String pdf = this.convert(page("big.jpg"), Map.of("big.jpg", jpegWithSize(20000, 20000)),
				props("input.image-pixel-limit", "40000000"));
		assertFalse(this.failed);
		assertFalse(pdf.contains("/Subtype /Image"));
		assertEquals(List.of("too-large 20000x20000 > 40000000"), this.image2811);
	}

	/** 画像出力の版面が上限を超えると変換を失敗させる(3812)。 */
	public void testOutputRasterLimit() throws Exception {
		this.convert("<html><body><p>x</p></body></html>", Map.of(),
				props("output.type", "image/png", "output.image-pixel-limit", "1000"));
		assertTrue("変換が失敗すること", this.failed);
		assertTrue("3812 が出ること", this.codes.contains(MessageCodes.ERROR_OUTPUT_IMAGE_TOO_LARGE));
	}

	/** 版面が上限以内なら従来どおり出る。 */
	public void testOutputRasterWithinLimit() throws Exception {
		this.convert("<html><body><p>x</p></body></html>", Map.of(),
				props("output.type", "image/png", "output.image-pixel-limit", "100000000"));
		assertFalse(this.failed);
		assertFalse(this.codes.contains(MessageCodes.ERROR_OUTPUT_IMAGE_TOO_LARGE));
	}

	private static String page(final String image) {
		return "<html><body><p>x</p><img src='" + image + "'/></body></html>";
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	/** IHDR と IEND だけの PNG(画素が無いので、展開しようとすれば失敗する)。 */
	private static byte[] pngHeaderOnly(final int width, final int height) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		final ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		final DataOutputStream h = new DataOutputStream(ihdr);
		h.writeInt(width);
		h.writeInt(height);
		h.writeByte(8); // bit depth
		h.writeByte(0); // grayscale
		h.writeByte(0);
		h.writeByte(0);
		h.writeByte(0);
		chunk(out, "IHDR", ihdr.toByteArray());
		chunk(out, "IEND", new byte[0]);
		return bytes.toByteArray();
	}

	private static void chunk(final DataOutputStream out, final String type, final byte[] data) throws IOException {
		out.writeInt(data.length);
		final byte[] t = type.getBytes(StandardCharsets.US_ASCII);
		out.write(t);
		out.write(data);
		final CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(data);
		out.writeInt((int) crc.getValue());
	}

	/** 8x8 の本物の JPEG を作り、SOF の寸法だけを書き換えます。 */
	private static byte[] jpegWithSize(final int width, final int height) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
		final byte[] jpeg = bytes.toByteArray();
		for (int i = 2; i + 9 < jpeg.length; ++i) {
			if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xC0) {
				jpeg[i + 5] = (byte) (height >> 8);
				jpeg[i + 6] = (byte) height;
				jpeg[i + 7] = (byte) (width >> 8);
				jpeg[i + 8] = (byte) width;
				return jpeg;
			}
		}
		throw new IOException("SOF0 not found");
	}

	private String convert(final String html, final Map<String, byte[]> files, final Map<String, String> properties)
			throws Exception {
		final File dir = new File("local/unittest/image-pixel-limit/" + this.getName());
		dir.mkdirs();
		for (final Map.Entry<String, byte[]> e : files.entrySet()) {
			Files.write(new File(dir, e.getKey()).toPath(), e.getValue());
		}
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		final File out = new File(dir, "out.bin");
		this.image2811.clear();
		this.codes.clear();
		this.failed = false;
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> {
					this.codes.add(code);
					if (code == MessageCodes.WARN_MISSING_IMAGE && args != null && args.length > 1) {
						this.image2811.add(args[1]);
					}
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} catch (final Exception e) {
				this.failed = true;
			} finally {
				try {
					session.close();
				} catch (final Exception e) {
					this.failed = true;
				}
			}
		}
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
