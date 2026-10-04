package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 和文の約物の直後の原子インライン(数式・画像・inline-block)が行に収まらなければ、次の行へ送ることを
 * 固定します(2026-10-04、出版の報告: 行末の行中の数式が版面の右端を 1〜2mm 越えた)。
 *
 * <p>
 * 行を詰める候補を集めるとき、箱の後ろでも箱の前の「。」を行末の字として扱い、その後ろ半分を詰められる
 * と見込んで箱を同じ行に置いていた。実際には「。」は行末にないので詰められず、はみ出した。
 * </p>
 */
public class AtomicAfterPunctuationLineEndTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final Pattern BOX = Pattern
			.compile("x=([-0-9.]+) y=[-0-9.]+ AbsoluteRectFrame\\[w=([-0-9.]+) h=10\\.00\\]");

	/** 全角 8 字(160pt)の後ろの 25pt の箱は、180pt の行に収まらない。 */
	private static double[] box(final String before) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:200pt 200pt;margin:10pt} body{margin:0;font-size:20pt}"
				+ "p{width:180pt;margin:0}</style></head><body><p>" + before
				+ "<span style='display:inline-block;width:25pt;height:10pt;background:red'></span>も同じく</p>"
				+ "</body></html>";
		final Path dir = Files.createTempDirectory("atomic-after-punct");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString());
				AutoCloseable g = DisplayListDumper.scopedDetailedGeometry(true)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), URI.create("file:///a.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		final String dump = Files.readString(dir.resolve("page-0001.txt"));
		final Matcher m = BOX.matcher(dump);
		assertTrue(dump, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
	}

	public void testBoxAfterFullStopGoesToTheNextLine() throws Exception {
		for (final String before : new String[] { "あいうえおです。", "あいうえおです、", "あいうえおです」" }) {
			final double[] box = box(before);
			assertTrue(before + ": the box must stay inside the line (x=" + box[0] + ")", box[0] + box[1] <= 180.01);
		}
	}

	/** 収まる箱は、従来どおり同じ行に置く。 */
	public void testBoxThatFitsStaysOnTheLine() throws Exception {
		final double[] box = box("あいうえおです");
		assertEquals(140, box[0], 0.01);
	}
}
