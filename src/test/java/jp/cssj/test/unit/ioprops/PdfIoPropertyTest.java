package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 入出力プロパティが<b>実際に出力へ効くか</b>の検査です(2026-08-02新設)。
 *
 * <p>
 * 説明書(5100_io-properties.md)には120個の入出力プロパティが載っているが、
 * 何らかのテストが触れているのは25個だけだった。「書いてあるが配線されて
 * いない」欠陥は実際に起きている——HTTP取得のUser-Agentは指定しても
 * 送られず、bot対策のあるサイトが全滅していたのに、単体テスト1,121件と
 * imageTest 591文書はすべて緑だった(2026-08-02、実地で発覚)。
 * </p>
 *
 * <p>
 * ここでは<b>設定した値が出力PDFに現れること</b>を1プロパティ1件で
 * 検査する。組版の正しさではなく<b>配線の有無</b>を見るテストなので、
 * 判定はPDFのバイト列に対する単純な包含で足りる(圧縮を切って読む)。
 * </p>
 */
public class PdfIoPropertyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 変換に使う最小の文書(内容はどのプロパティの検査にも影響しない)。 */
	private static final File DOCUMENT = new File("files/unittest/3080-MODERN-CSS/calc.html");

	/**
	 * 1件の検査です。
	 *
	 * @param props    設定する入出力プロパティ
	 * @param expected PDFに現れるべき文字列(いずれか1つで合格)
	 */
	private record Case(String name, File document, Map<String, String> props, List<String> expected) {
	}

	private static Case of(final String name, final Map<String, String> props, final String... expected) {
		return new Case(name, DOCUMENT, props, List.of(expected));
	}

	private static Case of(final String name, final File document, final Map<String, String> props,
			final String... expected) {
		return new Case(name, document, props, List.of(expected));
	}

	/** 見出しを持つ文書(しおりの検査用)。 */
	private static final File HEADINGS = new File("files/unittest/0010-link/absolute.html");

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	/** 検査表。**新しい入出力プロパティを足したらここへ1行足すこと。** */
	private static List<Case> cases() {
		final List<Case> cases = new ArrayList<>();

		// 文書情報(output.meta.<n>.name / .value の対で指定する)
		cases.add(of("output.meta(author)",
				props("output.meta.0.name", "author", "output.meta.0.value", "PROBE-AUTHOR"), "PROBE-AUTHOR"));
		cases.add(of("output.meta(keywords)",
				props("output.meta.0.name", "keywords", "output.meta.0.value", "PROBE-KEYWORDS"),
				"PROBE-KEYWORDS"));

		// PDFのバージョン
		cases.add(of("output.pdf.version", props("output.pdf.version", "1.7"), "/Version /1.7", "%PDF-1.7"));

		// ファイルID
		cases.add(of("output.pdf.file-id", props("output.pdf.file-id", "0123456789abcdef0123456789abcdef"),
				"0123456789abcdef0123456789abcdef", "<0123456789ABCDEF0123456789ABCDEF>"));

		// 作成・更新時刻
		// 書式は説明書の例に合わせる("2009-05-22 21:10:14")
		cases.add(of("output.pdf.meta.creation-date",
				props("output.pdf.meta.creation-date", "2020-01-02 03:04:05"), "D:20200102"));

		// しおり
		// しおりは見出しから作られるので、見出しのある文書で見る
		cases.add(of("output.pdf.bookmarks", HEADINGS, props("output.pdf.bookmarks", "true"), "/Outlines"));

		// タグ付きPDF(論理構造)
		cases.add(of("output.pdf.tagged", props("output.pdf.tagged", "true", "output.pdf.tagged.lang", "ja"),
				"/StructTreeRoot"));

		// 表示設定(ビューア)
		cases.add(of("output.pdf.viewer-preferences.hide-menubar",
				props("output.pdf.viewer-preferences.hide-menubar", "true"), "/HideMenubar true"));
		cases.add(of("output.pdf.viewer-preferences.fit-window",
				props("output.pdf.viewer-preferences.fit-window", "true"), "/FitWindow true"));
		cases.add(of("output.pdf.viewer-preferences.display-doc-title",
				props("output.pdf.viewer-preferences.display-doc-title", "true"), "/DisplayDocTitle true"));
		cases.add(of("output.pdf.viewer-preferences.num-copies",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.num-copies", "3"), "/NumCopies 3"));
		cases.add(of("output.pdf.viewer-preferences.duplex",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.duplex", "simplex"), "/Duplex"));
		// **print-scalingは開発ビルドのライセンスで使えない**(2026-08-02実測:
		// 「Cannot use I/O property ... under current license.」で無視される)。
		// エンジンの欠陥ではないので、ここでは検査しない。ライセンスに
		// 依存しない検査環境を用意できたら戻すこと

		// 開いたときに実行するJavaScript
		cases.add(of("output.pdf.open-action.java-script",
				props("output.pdf.open-action.java-script", "app.alert('PROBE-JS')"), "PROBE-JS"));

		// 暗号化(値そのものは出ないので、暗号化辞書の存在で見る)
		// v5(AES-256)はPDF 1.7以降
		cases.add(of("output.pdf.encryption", props("output.pdf.version", "1.7", "output.pdf.encryption", "v5",
				"output.pdf.encryption.user-password", "u"), "/Encrypt"));

		return cases;
	}

	public void testIoPropertiesReachTheOutput() throws Exception {
		final List<String> failures = new ArrayList<>();
		for (final Case c : cases()) {
			final String pdf = this.convert(c.document(), c.props());
			boolean ok = false;
			for (final String expected : c.expected()) {
				if (pdf.contains(expected)) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				failures.add(c.name() + "(期待: " + String.join(" または ", c.expected()) + ")");
			}
		}
		if (!failures.isEmpty()) {
			fail("出力に反映されない入出力プロパティが" + failures.size() + "件: " + String.join(" / ", failures));
		}
	}

	/** 圧縮を切って変換し、PDFを文字列として返します。 */
	private String convert(final File document, final Map<String, String> properties) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				// 圧縮を切らないと辞書の中身が読めない(検査は配線の有無だけ)
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
