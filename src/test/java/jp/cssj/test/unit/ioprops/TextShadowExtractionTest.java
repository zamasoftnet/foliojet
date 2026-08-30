package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>{@code text-shadow}が抽出テキストを汚さないこと</b>の検査です
 * (2026-08-30新設)。
 *
 * <p>
 * 影を字形のまま描くと、それがそのままPDFの本文として抽出される。実文書で
 * 報告された症状は縦組み+圏点で「減減税税と と…」と1文字ずつ交互に出る
 * というもので、圏点が付くと描画単位が1文字ごとに割れ、各単位が
 * 「影→本体」を出すために起きていた。<b>影の重複そのものは縦組みでも
 * 横組みでも、圏点が無くても起きる</b>——鮮明な影で2回、ぼかし付きの影は
 * 12段の重ね描き近似なので13回。
 *
 * <p>
 * 直し方は影を<b>輪郭のパス</b>で描くこと。字形情報を持たないので抽出に
 * 出ず、タグ付きPDFでは{@code /Artifact}にもなる。ただし輪郭が取れるのは
 * フォントの字形データがある方針({@code embedded}・{@code cid-identity}等)
 * のときだけで、<b>外部参照の{@code cid-keyed}では字形データが手元に無い</b>
 * ためテキストのまま描くしかない——その差もここで固定する。
 */
public class TextShadowExtractionTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File DOCUMENT = new File("files/unittest/ioprops/text-shadow-vertical.html");

	/** 上から影だけを外した対照文書。 */
	private static final File CONTROL = new File("files/unittest/ioprops/text-shadow-vertical-none.html");

	/**
	 * <b>影を付けてもPDFのテキスト表示演算子は1つも増えない</b>こと。
	 *
	 * <p>
	 * 抽出したテキストの文字列で数えると、抽出器によって重なった同じ文字を
	 * 1つに畳むもの(PDFBox)とそのまま2つ返すもの(pdfium)があり判定が揺れる。
	 * ここでは曖昧さの無い<b>コンテンツストリームの{@code Tj}/{@code TJ}の個数</b>
	 * を、影の有無だけが違う2文書で比べる。
	 */
	public void testShadowAddsNoTextOperators() throws Exception {
		final Map<String, String> embedded = props("output.pdf.fonts.policy", "embedded cid-keyed");
		final int withShadow = textOperators(this.convert(DOCUMENT, embedded, "with"));
		final int without = textOperators(this.convert(CONTROL, embedded, "without"));
		assertTrue("対照文書がテキストを描いていない(検査が空虚)", without > 0);
		assertEquals("影がテキスト表示演算子を増やしている", without, withShadow);
	}

	/** 抽出テキストにも影の複製が出ないこと。 */
	public void testShadowDoesNotDuplicateExtractedText() throws Exception {
		final String text = this.extract(props("output.pdf.fonts.policy", "embedded cid-keyed"));
		assertEquals("鮮明な影の文字が二重にならないこと", 1, count(text, "減税"));
		assertEquals("ぼかし付きの影が13重にならないこと", 1, count(text, "社会"));
		assertEquals("縦組み+圏点で1文字ずつ交互にならないこと", 1, count(text, "保"));
		assertEquals("同上", 1, count(text, "障"));
	}

	/**
	 * <b>字形データの無いフォントでは輪郭にできない</b>ので、影はテキストの
	 * ままになる(タグ付きPDFでは{@code /Artifact}にはなる)。
	 *
	 * <p>
	 * 外部参照の{@code cid-keyed}はフォントファイルを持たず、Core-14の
	 * Type1フォント(Times/Helvetica/Courier)も字形の輪郭を持たない。これは
	 * 近似ではなく原理的な限界で、<b>直せるようになったらここが落ちて
	 * 気づける</b>ように残す。実運用で抽出テキストをきれいにしたいなら
	 * {@code output.pdf.fonts.policy}へ{@code embedded}を先に置くこと。
	 */
	public void testFontsWithoutGlyphDataStillDuplicateShadowText() throws Exception {
		final Map<String, String> cidKeyed = props("output.pdf.fonts.policy", "cid-keyed");
		final int withShadow = textOperators(this.convert(DOCUMENT, cidKeyed, "cid-with"));
		final int without = textOperators(this.convert(CONTROL, cidKeyed, "cid-without"));
		assertTrue("字形データが無いと影はテキストのまま増える", withShadow > without);
	}

	/** コンテンツストリームの{@code Tj}/{@code TJ}の個数を数えます。 */
	private static int textOperators(final String pdf) {
		final java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("<[0-9A-Fa-f]+>\\s*Tj|\\]\\s*TJ").matcher(pdf);
		int n = 0;
		while (matcher.find()) {
			++n;
		}
		return n;
	}

	private String convert(final File document, final Map<String, String> properties, final String label)
			throws Exception {
		final File out = this.write(document, properties, label);
		return new String(java.nio.file.Files.readAllBytes(out.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
	}

	private static int count(final String text, final String needle) {
		int n = 0;
		for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
			++n;
		}
		return n;
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private String extract(final Map<String, String> properties) throws Exception {
		final File out = this.write(DOCUMENT, properties, "extract");
		try (PDDocument pdf = Loader.loadPDF(out)) {
			return new PDFTextStripper().getText(pdf);
		}
	}

	private File write(final File document, final Map<String, String> properties, final String label)
			throws Exception {
		final File out = new File(
				"local/unittest/pdf/" + this.getClass().getName() + '-' + label + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
		return out;
	}
}
