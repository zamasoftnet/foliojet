package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.rescue.RescuePolicy;
import net.zamasoft.foliojet.layout.rescue.RescueStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 救済分割(visual rescue split)の振る舞いテストです(2026-07-25新設、増分5。
 * {@code docs/consultations/consult-rescue-split-codex.md}、
 * {@code docs/history/2026-07-25-rescue-split-spec.md})。
 *
 * <p>
 * 増分5で有効なのは<b>通常フロー中の置換要素</b>だけです。巨大行・ブロック・
 * 表セル・段組・floatは増分6以降なので、ここでは対象外であることを
 * 確かめません(有効化していないため既存の挙動のまま)。
 * </p>
 *
 * <p>
 * 幾何は表示リスト(座標つき)で固定します。断片ごとのclipの交差そのものは
 * {@code VisualRescueBoxTest}が単体で固定しているため、ここでは
 * <b>ページ数・各ページに描画があること・断片の座標・artifact印</b>を
 * 見ます——「意図しない白紙(実質白紙)ページを作らない」という絶対要件が
 * 直接見えるのがこの3点だからです。
 * </p>
 */
public class VisualRescueSplitTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public VisualRescueSplitTest(final String name) {
		super(name);
	}

	// ------------------------------------------------------------------
	// 横書き
	// ------------------------------------------------------------------

	/**
	 * ページより背の高い画像が、上下の断片で元画像を過不足なく覆う。
	 * 断片は「元ボックス全体を消費済み量だけずらしてclipする」ので、
	 * 描画原点のyが 0, -200, -400 と単調に進む=断片の継ぎ目が
	 * ぴったり繋がっている、が固定される。
	 */
	public void testTallImageIsSlicedAcrossPages() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-tall.html", RescuePolicy.ENABLED);
		assertEquals("画像500pt / ページ200pt = 3ページ(+後続は3ページ目に収まる)", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
		// 後続の内容は最終断片の直後に続く
		assertTrue("3ページ目に後続テキストがある: " + pages.get(2), pages.get(2).contains("Text["));
	}

	/** 救済を切ると、従来どおり1ページではみ出したまま描かれる。 */
	public void testDisabledPolicyKeepsLegacyOverflow() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-tall.html", RescuePolicy.DISABLED);
		assertEquals("従来の挙動: 画像ははみ出したまま1ページ目に描かれ、後続だけが2ページ目へ送られる", 2,
				pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
	}

	/**
	 * 3ページ以上にまたがっても前進し、有限で終わる(1000pt = 5断片)。
	 * 余分な白紙ページが挟まらないことも同時に見る。
	 */
	public void testHugeImageAdvancesAndTerminates() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-huge.html", RescuePolicy.ENABLED);
		assertEquals("画像1000pt / ページ200pt = 5断片 + 後続1ページ", 6, pages.size());
		assertNoBlankPage(pages);
		for (int i = 0; i < 5; ++i) {
			assertDrawableAt(pages.get(i), 0, 0.0, i == 0 ? 0.0 : -200.0 * i, i > 0);
		}
		assertTrue("6ページ目は後続テキストだけ: " + pages.get(5), pages.get(5).contains("Text["));
		assertFalse("6ページ目に画像断片は残らない: " + pages.get(5), pages.get(5).contains("AbsoluteRectFrame"));
	}

	/**
	 * ページ高さでちょうど割り切れる画像で、残余0の断片ページ(=実質
	 * 白紙)を作らない。
	 */
	public void testExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-exact.html", RescuePolicy.ENABLED);
		assertEquals("画像400pt / ページ200pt = ちょうど2ページ", 2, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
	}

	// ------------------------------------------------------------------
	// 縦書き
	// ------------------------------------------------------------------

	/**
	 * 縦書きではページ軸が右→左に進む。断片の描画原点xが -300, -100,
	 * 100 と<b>増えて</b>いくのは、元ボックスの右端(ページ方向始端)から
	 * 順に消費していくため。
	 */
	public void testVerticalWritingSlicesAlongTheRightToLeftPageAxis() throws Exception {
		final List<String> pages = render("3050-IMG/rescue-tall-vert.html", RescuePolicy.ENABLED);
		assertEquals("画像500pt / ページ200pt = 3ページ", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, -300.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, -100.0, 0.0, true);
		assertDrawableAt(pages.get(2), 0, 100.0, 0.0, true);
	}

	// ------------------------------------------------------------------
	// 対象外
	// ------------------------------------------------------------------

	/**
	 * 絶対配置の画像は救済しない(合意仕様——透かし・裁ち落としのように
	 * 意図的なはみ出しを壊さない)。ENABLED/DISABLEDで出力が完全一致する
	 * ことで「配線が触っていない」ことを固定する。
	 */
	public void testAbsoluteImageIsNotRescued() throws Exception {
		final List<String> enabled = render("3050-IMG/rescue-absolute.html", RescuePolicy.ENABLED);
		final List<String> disabled = render("3050-IMG/rescue-absolute.html", RescuePolicy.DISABLED);
		assertEquals("従来どおり1ページではみ出す", 1, enabled.size());
		assertEquals(disabled, enabled);
		assertNoBlankPage(enabled);
	}

	/**
	 * 救済の判定そのものが「非進行点」以外では一度も走らないこと
	 * (通常経路への非侵襲性)。既存コーパスの代表としてgolden対象の
	 * 文書を1本使う。
	 */
	public void testNormalDocumentNeverReachesTheRescuePoint() throws Exception {
		RescueStats.reset();
		render("0120-float/auto-width.html", RescuePolicy.ENABLED);
		assertEquals("通常文書では非進行点に到達しない", 0, RescueStats.CANDIDATES.get());
	}

	// ------------------------------------------------------------------
	// Tagged PDF
	// ------------------------------------------------------------------

	/**
	 * 継続断片は{@code /Artifact}として出力され、構造要素({@code Figure})は
	 * 先頭断片が開く1個だけ(答申§3。テキスト抽出・読み上げ・構造タグの
	 * 二重化を防ぐ)。
	 */
	public void testTaggedPdfOpensOneFigureAndMarksContinuationsAsArtifact() throws Exception {
		final File pdf = new File("local/unittest/rescue/tagged.pdf");
		pdf.getParentFile().mkdirs();
		try (RescuePolicy.Scope scope = RescuePolicy.ENABLED.scoped();
				OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				session.property("output.pdf.version", "1.7UA-1");
				session.property("output.pdf.tagged.lang", "ja");
				// コンテンツストリームを非圧縮にして、マーク付き内容を直接検査できるようにする
				session.property("output.pdf.compression", "none");
				CTISessionHelper.transcodeFile(session, new File("files/unittest/3050-IMG/rescue-tall.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		final String bytes = new String(Files.readAllBytes(pdf.toPath()), StandardCharsets.ISO_8859_1);
		assertEquals("画像の構造要素(Figure)は1個だけ", 1, count(bytes, "/S /Figure"));
		assertTrue("継続断片はartifactとして出力される", bytes.contains("/Artifact"));
	}

	// ------------------------------------------------------------------
	// 補助
	// ------------------------------------------------------------------

	/** 各ページに描画があること(実質白紙のページを作っていないこと)。 */
	private static void assertNoBlankPage(final List<String> pages) {
		for (int i = 0; i < pages.size(); ++i) {
			final String page = pages.get(i);
			assertTrue("ページ" + (i + 1) + "の表示リストが空です(意図しない白紙):\n" + page, page.contains("  x="));
		}
	}

	/**
	 * ページの{@code index}番目の描画命令の座標とartifact印を固定します。
	 */
	private static void assertDrawableAt(final String page, final int index, final double x, final double y,
			final boolean artifact) {
		final List<String> drawables = new ArrayList<>();
		for (final String line : page.split("\n")) {
			if (line.startsWith("  x=")) {
				drawables.add(line);
			}
		}
		assertTrue("描画命令が足りません(index=" + index + "):\n" + page, index < drawables.size());
		final String line = drawables.get(index);
		final String expected = String.format(java.util.Locale.ROOT, "  x=%.2f y=%.2f %s", x, y,
				artifact ? "artifact " : "");
		assertTrue("座標/artifact印が一致しません: expected prefix=[" + expected + "] actual=[" + line + "]",
				line.startsWith(expected));
	}

	private static int count(final String haystack, final String needle) {
		int n = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
			++n;
		}
		return n;
	}

	/**
	 * 文書を変換し、ページごとの表示リストのダンプを返します。
	 */
	private static List<String> render(final String doc, final RescuePolicy policy) throws Exception {
		final String name = doc.replace('/', '_').replace(".html", "") + "-" + policy;
		final File outDir = new File("local/unittest/rescue/" + name);
		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try (RescuePolicy.Scope scope = policy.scoped()) {
			final File pdf = new File("local/unittest/rescue/" + name + ".pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, new File("files/unittest/" + doc), "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
		final File[] files = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + doc, files);
		Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
