package jp.cssj.test.unit._3200_line_breaker;

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

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * CSSプロパティ{@code text-wrap-style}(および短縮形{@code text-wrap})の
 * 意味を固定するテストです(2026-07-25新設。独自プロパティ
 * {@code text.line-breaker}廃止・CSS一本化に伴う)。
 *
 * <p>
 * 本文が完全に同一で{@code <style>}だけが異なる文書群を生成し、
 * display listを突き合わせて次を検証する:
 * </p>
 * <ul>
 * <li>継承する——{@code body}に{@code pretty}を指定すると子の段落に効く</li>
 * <li>要素ごとに切り替えられる——段落に直接指定しても効き、
 * 逆に{@code body: pretty}を段落の{@code auto}で打ち消せる</li>
 * <li>短縮形{@code text-wrap: pretty}が{@code text-wrap-style}へ落ちる</li>
 * <li>{@code balance}/{@code stable}は構文として受理されるが{@code auto}扱い</li>
 * <li>不正値({@code text-wrap-style: no-such-value})とmode側の値
 * ({@code text-wrap: nowrap}——短縮形は受理しない)は宣言ごと無視され、
 * 既定の{@code auto}のまま</li>
 * </ul>
 *
 * <p>
 * 「実際にK-Pが効いている」ことは、pretty群と auto群の出力が
 * <b>異なる</b>ことで担保する(両群が偶然一致するとテストが失敗する)。
 * </p>
 */
public class TextWrapStyleTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * K-P適格かつ貪欲法と結果が変わる段落です(optimized-en-hyphen.htmlと
	 * 同内容——ハイフネーション付き欧文justifyはK-Pが連続ハイフン行や
	 * 過度な詰まりを避けるため、貪欲法と選択が変わる)。
	 */
	private static final String BODY = ""
			+ "<p class=\"justify\" lang=\"en\">The quick brown fox jumps over the lazy dog and keeps"
			+ " running through the quiet forest until the evening light fades away completely."
			+ " Considerable improvements materialize whenever paragraphs receive comprehensive"
			+ " optimization treatment.</p>\n";

	/** K-Pで組まれることを期待する変種(指定の書き方だけが異なる)。 */
	private static final Map<String, String> PRETTY = new LinkedHashMap<>();

	/** 貪欲法(auto)で組まれることを期待する変種。 */
	private static final Map<String, String> AUTO = new LinkedHashMap<>();

	static {
		// 継承: bodyへの指定が子の段落に効く
		PRETTY.put("inherit", "body { text-wrap-style: pretty; }");
		// 要素ごと: 段落へ直接指定しても効く
		PRETTY.put("element", "p { text-wrap-style: pretty; }");
		// 短縮形
		PRETTY.put("shorthand", "body { text-wrap: pretty; }");

		// 既定(無指定)
		AUTO.put("default", "");
		// balance/stable は受理するがauto扱い(未対応)
		AUTO.put("balance", "body { text-wrap-style: balance; }");
		AUTO.put("stable", "body { text-wrap-style: stable; }");
		AUTO.put("shorthand-balance", "body { text-wrap: stable; }");
		// 要素ごとの打ち消し: 継承したprettyを段落のautoで戻せる
		AUTO.put("override", "body { text-wrap-style: pretty; } p { text-wrap-style: auto; }");
		// 不正値は宣言ごと無視(継承値=初期値autoのまま)
		AUTO.put("invalid", "body { text-wrap-style: no-such-value; }");
		// mode側の値は短縮形text-wrapでは受理しない(折り返しはwhite-space)
		// text-wrap: nowrap は2026-08-29からwhite-space:nowrap相当として有効になった
		// (折り返さないので貪欲法との一致検証の対象外)
	}

	public TextWrapStyleTest(String name) {
		super(name);
	}

	public void testTextWrapStyle() throws Exception {
		final List<String> failures = new ArrayList<>();

		final String prettyReference = this.render("pretty-inherit", PRETTY.get("inherit"));
		final String autoReference = this.render("auto-default", AUTO.get("default"));

		// K-Pが本当に効いていること(効いていなければ以降の一致検証は無意味)
		if (prettyReference.equals(autoReference)) {
			fail("text-wrap-style: pretty の出力が既定(auto)と同一です。"
					+ "K-Pが起動していないか、fixtureが両者で同じ改行になる内容になっています");
		}

		for (final Map.Entry<String, String> e : PRETTY.entrySet()) {
			final String got = this.render("pretty-" + e.getKey(), e.getValue());
			if (!prettyReference.equals(got)) {
				failures.add("pretty-" + e.getKey() + " (" + e.getValue() + "): K-Pで組まれていません");
			}
		}
		for (final Map.Entry<String, String> e : AUTO.entrySet()) {
			final String got = this.render("auto-" + e.getKey(), e.getValue());
			if (!autoReference.equals(got)) {
				failures.add("auto-" + e.getKey() + " (" + e.getValue() + "): 貪欲法で組まれていません");
			}
		}

		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	/**
	 * 与えられた{@code <style>}断片で文書を組み、全ページのdisplay listを
	 * 連結して返します。
	 */
	private String render(final String name, final String style) throws Exception {
		final File dir = new File("local/unittest/text-wrap-style");
		dir.mkdirs();
		final File source = new File(dir, name + ".html");
		Files.writeString(source.toPath(), document(style), StandardCharsets.UTF_8);

		final File outDir = new File(dir, name);
		deleteChildren(outDir);
		outDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File(dir, name + ".pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, source, "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + name, pages);
		assertTrue("表示リストが出力されていません: " + name, pages.length > 0);
		java.util.Arrays.sort(pages);
		final StringBuilder sb = new StringBuilder();
		for (final File page : pages) {
			sb.append("=== ").append(page.getName()).append('\n');
			sb.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	private static String document(final String style) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n"
				+ "<html>\n<head>\n"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n"
				+ "<title>text-wrap-style</title>\n"
				+ "<style type=\"text/css\">\n"
				+ "@page { margin: 0; }\n"
				+ "body { margin: 0; font-size: 10pt; line-height: 1.2; }\n"
				+ ".justify { text-align: justify; }\n"
				+ "p { hyphens: auto; }\n"
				+ style + "\n"
				+ "</style>\n</head>\n<body>\n" + BODY + "</body>\n</html>\n";
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}
}
