package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * PDF/A・PDF/X の適合検証試験が共有する変換ヘルパ(2026-09-05、色管理 I4 で
 * {@link PdfAValidationTest} から抽出)。
 */
final class PdfConversions {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 1×1 の白 PNG。 */
	static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGO4w8AAAAKYAN3rxP+VAAAAAElFTkSuQmCC";

	/** 2×2 の RGB JPEG(赤地に青 1 画素、JFIF、APP14 無し)。 */
	static final String JPEG = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD4U13XdSh1vUI49QukRbiRVVZmAADHAAzRRRX7FgP90o/4Y/kj+msb/vVX/E/zZ//Z";

	private PdfConversions() {
	}

	/**
	 * 生成画像(影のぼかし・filter のラスタ化)・conic メッシュ・透明・埋め込み
	 * フォント・PNG/JPEG 画像を 1 頁に集めた文書。PDF/A と PDF/X で同じものを使う。
	 */
	static String fixtureHtml(final String title) {
		return "<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"UTF-8\"><title>" + title + "</title><style>"
				+ "@page{size:120mm 120mm;margin:8mm}body{margin:0;font:11pt serif}"
				+ ".shadow{width:40mm;height:12mm;background:#fc6;box-shadow:2mm 2mm 3mm rgba(0,0,0,.5)}"
				+ ".filtered{width:40mm;height:12mm;padding:2mm;background:#f36;filter:grayscale(1) blur(1pt)}"
				+ ".conic{width:30mm;height:30mm;background:conic-gradient(red, blue, red)}"
				+ ".fade{width:40mm;height:8mm;background:linear-gradient(to right, rgba(0,0,255,0), #00f)}"
				+ ".half{opacity:.5}img{width:8mm;height:8mm}"
				+ "</style></head><body>"
				+ "<p>日本語の本文と Latin text。</p>"
				+ "<div class=\"shadow\">shadow</div>"
				+ "<div class=\"filtered\">filtered <img alt=\"dot\" src=\"" + PNG + "\"></div>"
				+ "<div class=\"conic\"></div>"
				+ "<div class=\"fade\"></div>"
				+ "<p class=\"half\">half <img alt=\"dot\" src=\"" + PNG + "\"> <img alt=\"photo\" src=\"" + JPEG
				+ "\"></p>"
				+ "</body></html>";
	}

	/** HTML を指定の PDF 版で変換し、{@code build/tmp/<name>.pdf} にも残す。 */
	static byte[] convert(final String html, final String version, final boolean tagged, final String name)
			throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			session.property("output.pdf.version", version);
			if (tagged) {
				session.property("output.pdf.tagged", "true");
				session.property("output.pdf.tagged.lang", "ja");
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///" + name + ".html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		final byte[] pdf = out.toByteArray();
		final File dir = new File("build/tmp");
		dir.mkdirs();
		Files.write(new File(dir, name + ".pdf").toPath(), pdf);
		return pdf;
	}
}
