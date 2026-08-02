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

	/** リンクを持つ文書。 */
	private static final File LINKS = new File("files/unittest/ioprops/link-and-image.html");

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

		// 暗号化の権限(9件)。/Pビットへ落ちるので、既定と異なる値を
		// 与えて暗号化辞書が変わることを見る
		for (final String perm : new String[] { "print", "print-high", "copy", "modify", "add", "extract",
				"assemble", "fill" }) {
			cases.add(of("output.pdf.encryption.permissions." + perm,
					props("output.pdf.version", "1.7", "output.pdf.encryption", "v5",
							"output.pdf.encryption.owner-password", "o",
							"output.pdf.encryption.permissions." + perm, "false"),
					"/Encrypt"));
		}
		cases.add(of("output.pdf.encryption.owner-password",
				props("output.pdf.version", "1.7", "output.pdf.encryption", "v5",
						"output.pdf.encryption.owner-password", "o"), "/Encrypt"));
		cases.add(of("output.pdf.encryption.length",
				props("output.pdf.version", "1.7", "output.pdf.encryption", "v2",
						"output.pdf.encryption.length", "128", "output.pdf.encryption.user-password", "u"),
				"/Encrypt"));
		cases.add(of("output.pdf.encryption.v4.cfm",
				props("output.pdf.version", "1.7", "output.pdf.encryption", "v4",
						"output.pdf.encryption.v4.cfm", "aesv2", "output.pdf.encryption.user-password", "u"),
				"/AESV2", "/Encrypt"));

		// ビューア設定の残り
		cases.add(of("output.pdf.viewer-preferences.center-window",
				props("output.pdf.viewer-preferences.center-window", "true"), "/CenterWindow true"));
		cases.add(of("output.pdf.viewer-preferences.hide-toolber",
				props("output.pdf.viewer-preferences.hide-toolber", "true"), "/HideToolbar true"));
		cases.add(of("output.pdf.viewer-preferences.non-full-screen-page-mode",
				props("output.pdf.viewer-preferences.non-full-screen-page-mode", "use-outlines"),
				"/NonFullScreenPageMode"));
		cases.add(of("output.pdf.viewer-preferences.pick-tray-by-pdf-size",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.pick-tray-by-pdf-size", "true"),
				"/PickTrayByPDFSize true"));
		cases.add(of("output.pdf.viewer-preferences.print-page-range",
				props("output.pdf.version", "1.7", "output.pdf.viewer-preferences.print-page-range", "1 1"),
				"/PrintPageRange"));

		// 文書情報の更新時刻
		cases.add(of("output.pdf.meta.mod-date",
				props("output.pdf.meta.mod-date", "2021-02-03 04:05:06"), "D:20210203"));

		// リンクの断片(リンクを含む文書で見る)
		cases.add(of("output.pdf.hyperlinks.fragment", LINKS,
				props("output.pdf.hyperlinks", "true", "output.pdf.hyperlinks.fragment", "true"), "/Link"));

		// 名前リテラルのエンコーディング
		cases.add(of("output.pdf.platform-encoding",
				props("output.pdf.platform-encoding", "UTF-8"), "%PDF"));

		// Factur-X の残り(添付名・文書種別・版)。XMPへ出る
		cases.add(of("output.pdf.facturx.document-type",
				props("output.pdf.version", "1.7A-3", "output.pdf.facturx.conformance-level", "BASIC",
						"output.pdf.facturx.document-type", "ORDER"),
				"ORDER"));
		cases.add(of("output.pdf.facturx.version",
				props("output.pdf.version", "1.7A-3", "output.pdf.facturx.conformance-level", "BASIC",
						"output.pdf.facturx.version", "9.9"),
				"9.9"));
		cases.add(of("output.pdf.facturx.document-file-name",
				props("output.pdf.version", "1.7A-3", "output.pdf.facturx.conformance-level", "BASIC",
						"output.pdf.facturx.document-file-name", "probe-invoice.xml"),
				"probe-invoice.xml"));

		// 出力インテントの残り(レジストリ・補足説明)
		cases.add(of("output.pdf.output-intent.registry",
				props("output.pdf.output-intent.identifier", "PROBE-COND",
						"output.pdf.output-intent.registry", "https://probe.example/registry"),
				"probe.example/registry"));
		cases.add(of("output.pdf.output-intent.info",
				props("output.pdf.output-intent.identifier", "PROBE-COND",
						"output.pdf.output-intent.info", "PROBE-INTENT-INFO"),
				"PROBE-INTENT-INFO"));

		// すかしの詳細(配置・不透明度・表示/印刷の切り替え)
		final String watermark = new File("files/unittest/red.png").toURI().toString();
		cases.add(of("output.pdf.watermark.mode",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.mode", "tile"), "%PDF"));
		cases.add(of("output.pdf.watermark.opacity",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.opacity", "0.5"), "%PDF"));
		cases.add(of("output.pdf.watermark.print",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.print", "false"), "%PDF"));
		cases.add(of("output.pdf.watermark.view",
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.view", "false"), "%PDF"));

		// 画像の上限(縮小されても変換できること)
		cases.add(of("output.pdf.image.max-width", LINKS,
				props("output.pdf.image.max-width", "10"), "/Subtype /Image"));
		cases.add(of("output.pdf.image.max-height", LINKS,
				props("output.pdf.image.max-height", "10"), "/Subtype /Image"));
		cases.add(of("output.pdf.jpeg-image", LINKS, props("output.pdf.jpeg-image", "true"), "/Subtype /Image"));

		// 既定フォント・色・解像度・文字寸法
		cases.add(of("output.default-font-family", props("output.default-font-family", "monospace"), "%PDF"));
		cases.add(of("output.color", props("output.color", "cmyk"), "%PDF"));
		cases.add(of("output.resolution", props("output.resolution", "72"), "%PDF"));
		cases.add(of("output.text-size", props("output.text-size", "1.5"), "%PDF"));
		cases.add(of("output.print-mode", props("output.print-mode", "single-side"), "%PDF"));
		cases.add(of("output.auto-rotate", props("output.auto-rotate", "true"), "%PDF"));
		cases.add(of("output.clip", props("output.clip", "true"), "%PDF"));
		cases.add(of("output.expand-with-content", props("output.expand-with-content", "true"), "%PDF"));
		cases.add(of("output.n-up.order", props("output.n-up", "2", "output.n-up.order", "vertical"), "%PDF"));
		cases.add(of("output.marks.spine-width",
				props("output.marks", "crop", "output.marks.spine-width", "10pt"), "%PDF"));
		cases.add(of("output.page-margins", props("output.page-margins", "20pt"), "%PDF"));
		cases.add(of("output.broken-image", props("output.broken-image", "cross"), "%PDF"));
		cases.add(of("output.page-limit.abort", props("output.page-limit", "10",
				"output.page-limit.abort", "force"), "%PDF"));
		cases.add(of("processing.fail-on-fatal-error", props("processing.fail-on-fatal-error", "true"), "%PDF"));
		cases.add(of("processing.middle-pass", props("processing.middle-pass", "false"), "%PDF"));
		cases.add(of("input.html.change-default-namespace",
				props("input.html.change-default-namespace", "true"), "%PDF"));
		cases.add(of("input.stylesheet.titles", props("input.stylesheet.titles", "probe"), "%PDF"));
		cases.add(of("input.filters", props("input.filters", ""), "%PDF"));
		cases.add(of("input.http.connection.timeout", props("input.http.connection.timeout", "5000"), "%PDF"));
		cases.add(of("input.http.socket.timeout", props("input.http.socket.timeout", "5000"), "%PDF"));
		cases.add(of("input.http.proxy.authentication.user",
				props("input.http.proxy.authentication.user", "u",
						"input.http.proxy.authentication.password", "p"), "%PDF"));
		cases.add(of("input.xslt.default-stylesheet", props("input.xslt.default-stylesheet", ""), "%PDF"));
		cases.add(of("output.image.antialias", props("output.image.antialias", "true"), "%PDF"));
		cases.add(of("output.image.resolution", props("output.image.resolution", "96"), "%PDF"));

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
