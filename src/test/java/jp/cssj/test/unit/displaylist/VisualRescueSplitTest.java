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
 * 増分6/7(2026-07-25)で、巨大な行(巨大フォント・背の高いインライン
 * ブロック等)・書字方向が幹と食い違うブロック・表セル・段組・浮動体まで
 * 広げました。<b>表全体を幾何学的に切る経路({@code BoxType.TABLE})だけは
 * 見送っています</b>——表は行・行グループ・セルの分割機構を自前で持ち、
 * {@code Keep}/{@code Move}が「内部機構が処理した」の意か「本当に前進
 * できない」かを現状の戻り値からは区別できないためです。
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
	// 増分6: 巨大な行(巨大フォント・背の高いインラインブロック)
	// ------------------------------------------------------------------

	/**
	 * 1行がページより高い段落(巨大フォント)を切る。行分割は
	 * 「フラグメント先頭では必ず1行を残す」ため<b>前進しない</b>——
	 * その非進行点だけを置き換える。
	 */
	public void testHugeFontLineIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/huge-font-line.html", RescuePolicy.ENABLED);
		assertEquals("行500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
		assertTrue("最終断片の後に後続が続く: " + pages.get(2), pages.get(2).contains("y=100.00 Text["));
	}

	/** ページ高さでちょうど割り切れる行で、余分なページを作らない。 */
	public void testHugeFontLineExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/huge-font-exact.html", RescuePolicy.ENABLED);
		assertEquals("行400pt / ページ200pt = ちょうど2断片", 2, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
	}

	/**
	 * 背の高いインラインブロックも<b>同じ経路</b>(巨大な行)で捕捉される。
	 * インラインブロック専用の分岐は作っていない。
	 */
	public void testTallInlineBlockIsSlicedAsAHugeLine() throws Exception {
		final List<String> pages = render("0480-rescue-split/tall-inline-block.html", RescuePolicy.ENABLED);
		assertEquals("インラインブロック500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
	}

	/**
	 * <b>複数行ある段落は救済しない</b>。行分割が実際に前進する(先頭行を
	 * 残して残りを次フラグメントへ送る)ため非進行点ではなく、そこで段落
	 * 全体を幾何学的に切ると「全ページに全行の帯が並ぶ」明確な劣化になる。
	 */
	public void testMultiLineParagraphIsSplitByLinesNotSliced() throws Exception {
		final List<String> enabled = render("2010-LIMIT/image-line.html", RescuePolicy.ENABLED);
		final List<String> disabled = render("2010-LIMIT/image-line.html", RescuePolicy.DISABLED);
		assertEquals(disabled, enabled);
	}

	// ------------------------------------------------------------------
	// 増分6: 書字方向が幹と食い違うブロック
	// ------------------------------------------------------------------

	/**
	 * 書字方向が幹と食い違うブロックは、エンジン自身が<b>atomicに分類</b>して
	 * 置換要素と同じ終端へ落としている。そこが非進行点なので同じ規則で切る。
	 */
	public void testOrthogonalBlockIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/orthogonal-block.html", RescuePolicy.ENABLED);
		assertEquals("ブロック500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		// 枠(背景)は「フレームパス」で描かれる。断片でも先頭は実内容、
		// 続きはartifactとして出る
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
	}

	/** ちょうど割り切れる書字方向不一致ブロックで、余分なページを作らない。 */
	public void testOrthogonalBlockExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/orthogonal-block-exact.html", RescuePolicy.ENABLED);
		assertEquals("ブロック400pt / ページ200pt = ちょうど2断片", 2, pages.size());
		assertNoBlankPage(pages);
	}

	// ------------------------------------------------------------------
	// 増分6: 表セル・段組(フラグメンテナがページでない場合)
	// ------------------------------------------------------------------

	/** 表セルの中でも同じ判定・同じ運搬(フラグメンテナがセルになるだけ)。 */
	public void testTallImageInTableCellIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/cell-tall-image.html", RescuePolicy.ENABLED);
		assertEquals("セル内の画像500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(1), 1, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 1, 0.0, -400.0, true);
	}

	/** 表セルでもちょうど割り切れる高さで余分なページを作らない。 */
	public void testTallImageInTableCellExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/cell-tall-image-exact.html", RescuePolicy.ENABLED);
		assertEquals("セル内の画像400pt / ページ200pt = ちょうど2断片", 2, pages.size());
		assertNoBlankPage(pages);
	}

	/**
	 * 段組では「ページ」ではなく<b>現在のfragmentainer容量</b>で切る
	 * ——次段へ、段が尽きれば次ページへ。500ptのブロックは
	 * 1ページ目の2段(200+200)と2ページ目の2段(段バランスで55+45)に載る。
	 */
	public void testTallBlockInColumnsUsesTheColumnAsFragmentainer() throws Exception {
		final List<String> pages = render("0480-rescue-split/column-tall-block.html", RescuePolicy.ENABLED);
		assertEquals("段(200pt)を単位に切るので2ページ", 2, pages.size());
		assertNoBlankPage(pages);
		// 1ページ目: 1段目が先頭断片(実内容)、2段目が続き(artifact)
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(0), 1, 160.0, -200.0, true);
	}

	/**
	 * 段の高さでちょうど割り切れるブロックは1ページの2段に収まり、
	 * 余分なページを作らない。
	 */
	public void testTallBlockInColumnsExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/column-tall-block-exact.html", RescuePolicy.ENABLED);
		assertEquals("ブロック400pt = 段200pt × 2段でちょうど1ページ", 1, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(0), 1, 160.0, -200.0, true);
	}

	// ------------------------------------------------------------------
	// 増分7: 浮動体
	// ------------------------------------------------------------------

	/**
	 * 分割できない浮動体(ページより背の高い画像)を切る。断片の排除域は
	 * その断片の占有量になるので、<b>続きのページでも本文が浮動体を
	 * 避けて流れる</b>(=救済前は本文が浮動体の下に潜り込んでいた)。
	 */
	public void testTallFloatIsSliced() throws Exception {
		final List<String> pages = render("0480-rescue-split/float-tall.html", RescuePolicy.ENABLED);
		assertEquals("浮動体500pt / ページ200pt = 3断片", 3, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(0), 0, 0.0, 0.0, false);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
		assertDrawableAt(pages.get(2), 0, 0.0, -400.0, true);
		// 続きのページでも本文は浮動体の右へ流れる(排除域が生きている)
		assertTrue("2ページ目の本文が排除域を避けている: " + pages.get(1), pages.get(1).contains("x=100.00 y=0.00 Text["));
	}

	/** 救済を切ると、浮動体の続きは失われ本文が左端から流れる(従来の挙動)。 */
	public void testTallFloatWithoutRescueLosesTheRemainder() throws Exception {
		final List<String> pages = render("0480-rescue-split/float-tall.html", RescuePolicy.DISABLED);
		assertEquals(2, pages.size());
		assertTrue("2ページ目に浮動体の続きはない: " + pages.get(1), pages.get(1).contains("x=0.00 y=0.00 Text["));
	}

	/** ちょうど割り切れる浮動体で、余分なページを作らない。 */
	public void testFloatExactMultipleProducesNoExtraPage() throws Exception {
		final List<String> pages = render("0480-rescue-split/float-exact.html", RescuePolicy.ENABLED);
		assertEquals("浮動体400pt / ページ200pt = ちょうど2断片(3ページ目は作らない)", 2, pages.size());
		assertNoBlankPage(pages);
		assertDrawableAt(pages.get(1), 0, 0.0, -200.0, true);
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
