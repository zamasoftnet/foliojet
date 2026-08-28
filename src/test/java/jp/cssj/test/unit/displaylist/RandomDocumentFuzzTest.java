package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * ランダムに生成した文書で<b>絶対要件</b>を検査します(2026-07-25新設)。
 *
 * <p>
 * これまでの検証は「人間が書いた固定の文書」(単体コーパス414文書・視覚
 * コーパス591文書)に依存しており、<b>誰も思いつかなかった組み合わせ</b>は
 * 原理的に踏めませんでした。本テストは入力空間を直接サンプリングします。
 * レビューが推定するのは「レビューで見つかりうる欠陥」だけなので、
 * これは<b>別の母集団</b>を測る手段です
 * (`copperpdf4/docs/REVIEW-STATISTICS.md` の観点一覧を参照)。
 * </p>
 *
 * <h2>検査する不変条件</h2>
 *
 * <ol>
 * <li><b>例外で中断しない</b></li>
 * <li><b>停止する</b>(watchdog内に終わる)</li>
 * <li><b>ページ数が有界</b>(内容量に対して爆発しない)</li>
 * <li><b>内容が失われない</b>——文書に埋めた一意なトークンが、すべて
 * 出力の表示リストに現れる。STRICTモード限定(下記)</li>
 * <li><b>意図しない白紙ページがない</b>——STRICTモード限定</li>
 * <li><b>説明のつかない紙面外への配置がない</b>——はみ出し量が文書中の
 * 最大の明示サイズの2倍を超えない。CSSの{@code overflow}既定は
 * {@code visible}なので「紙面内」は要求できない。STRICTモード限定</li>
 * <li><b>読み順が保たれる</b>——文書順で先のトークンが、後のトークンより
 * 後のページに現れない。ページ<b>内</b>の描画順は実装の都合なので問わない。
 * フロートは正当に読み順を変えるので対象外。STRICTモード限定</li>
 * </ol>
 *
 * <h2>2つのモード</h2>
 *
 * <p>
 * <b>STRICT</b>は「作者が白紙や消失を意図しようがない」部分集合だけを
 * 生成します——絶対配置・{@code visibility:hidden}・強制改ページ・
 * {@code overflow:hidden}を使いません。ここでは不変条件4・5まで検査できます。
 * </p>
 *
 * <p>
 * <b>WILD</b>はそれらも含めて生成し、不変条件1〜3だけを検査します
 * (意図した白紙・意図したはみ出しと区別がつかないため)。
 * </p>
 *
 * <h2>実行</h2>
 *
 * <p>
 * 既定は各モード{@value #DEFAULT_SEEDS}シードの回帰用。掃過するときは
 * {@code -Dfoliojet.fuzzSeeds=2000} のように増やす(件数はそのまま
 * 統計的な主張の根拠になるので、実行数を記録すること)。
 * 失敗したシードは再現用にHTMLを{@code local/fuzz/}へ残します。
 * </p>
 */
public class RandomDocumentFuzzTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * 生成器の入力分布の版。掃過manifestへこの値とコードの識別子を記録する。
	 * v1のseedを正確に再現するときは、manifestに記録した旧タグをcheckoutする。
	 */
	static final int GENERATOR_VERSION = 2;

	/** v2を変えずに重い局所構造を追加する、opt-in掃過プロファイルの版。 */
	static final int EXTREME_PROFILE_VERSION = 1;

	private static boolean extremeProfile() {
		final String value = System.getProperty("foliojet.fuzzExtreme");
		return value != null && !value.equals("0") && !value.equalsIgnoreCase("false");
	}

	private static String generatorProfile() {
		return extremeProfile() ? "extreme-v" + EXTREME_PROFILE_VERSION : "standard";
	}

	private static String generatorLabel() {
		return extremeProfile() ? GENERATOR_VERSION + "/" + generatorProfile()
				: String.valueOf(GENERATOR_VERSION);
	}

	/** 既定のシード数(回帰用。掃過は -Dfoliojet.fuzzSeeds で増やす)。 */
	private static final int DEFAULT_SEEDS = 60;

	/** 1文書あたりの上限時間。通常は1秒未満で終わる。 */
	private static final long WATCHDOG_MS = Long.getLong("foliojet.fuzzWatchdogMs", 30_000L);

	/** ページ数の上限。生成する内容量から見て明らかに過大な値。 */
	private static final int MAX_PAGES = Integer.getInteger("foliojet.fuzzMaxPages", 300);

	/** extremeの絶対上限。通常は要素数×2のほうが先に効く。 */
	private static final int EXTREME_MAX_PAGES = Integer.getInteger("foliojet.fuzzExtremeMaxPages", 4_000);

	private static int pageLimit(final Generated doc) {
		if (!doc.html().contains("data-fuzz-profile=\"extreme-v")) {
			return MAX_PAGES;
		}
		// 60x60pt・内容領域40x40ptのseed 541は1,066要素から1,242ページを
		// 正常生成した。固定300ではextremeの内容量そのものを異常扱いする。
		// 要素数は長いテキスト量を表さない。縦書き13ptのseed 8676は655要素から
		// 1,443ページで正常停止した(2.20ページ/要素)ため、3倍まで許容する。
		// 無制限に広げず、絶対上限4,000ページも残す。
		final int contentBound = Math.multiplyExact(inspectGeneratedStructure(doc.html()).elements(), 3);
		return Math.max(MAX_PAGES, Math.min(EXTREME_MAX_PAGES, contentBound));
	}

	/** ページ数オラクルの上限で意図的に変換を止めた印。 */
	private static final class PageCountLimitExceeded extends AssertionError {
		private static final long serialVersionUID = 1L;

		PageCountLimitExceeded(final int limit, final Throwable cause) {
			super("ページ数が過大 " + (limit + 1) + "以上");
			initCause(cause);
		}
	}

	/**
	 * 表示リストに現れる「文字として描かれたもの」を拾います。
	 *
	 * <p>
	 * <b>ルビは{@code RubyUnit["親文字" ruby="ふりがな"]}という別表記で出る</b>
	 * ため、{@code Text[...]}だけを見ると「消えた」と誤判定します
	 * (2026-07-26、生成器にルビを足して発覚。エンジンではなく
	 * <b>オラクル側の誤り</b>だった)。画像の{@code alt}は描かれないので、
	 * 生成器はaltへトークンを埋めません。
	 * </p>
	 */
	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("(?:Text|RubyUnit)\\[\"([^\"]*)\"(?: ruby=\"([^\"]*)\")?");
	/** 生成器の ordered list が生成するマーカー(本文の文字ではない)。 */
	private static final Pattern ORDERED_LIST_MARKER = Pattern
			.compile("(?:[0-9]+|[ivxlcdm]+)\\.|[〇一二三四五六七八九十百千万]+、");
	/** 生成器のフォーム部品が固定で描画する、fuzzトークンではない文字。 */
	private static final Set<String> GENERATED_CONTROL_TEXT = Set.of("x", "y", "mixed", "甲", "乙", "日本語", "العربية",
			"日本語 العربية");

	/** 表示リスト上の1つの文字実行と、そのページ番号(0基点)。 */
	private record ObservedText(String text, int page, boolean artifact) {
	}

	/** 詳細ダンプ中の、寸法を持つ描画物の外接矩形。 */
	private static final Pattern DRAWING_GEOMETRY_IN_DUMP = Pattern.compile(
			"x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?[^\\n]*?w=([\\d.]+) h=([\\d.]+)");

	public RandomDocumentFuzzTest(String name) {
		super(name);
	}

	public void testStrictDocumentsPreserveEverything() throws Exception {
		checkFuzzImage();
		sweep(true);
	}

	public void testWildDocumentsNeverCrashOrHang() throws Exception {
		checkFuzzImage();
		sweep(false);
	}

	/** v2で追加した構造とサイズ語彙が、固定範囲のseedから実際に到達できること。 */
	public void testGeneratorV2VocabularyIsReachable() {
		boolean cellChild = false, flex = false, grid = false, intrinsic = false;
		boolean relativeSize = false, complexWild = false, longRuby = false;
		for (int seed = 0; seed < 512; ++seed) {
			final String strictHtml = generate(seed, true).html();
			final String wildHtml = generate(seed, false).html();
			final String html = strictHtml + wildHtml;
			cellChild |= html.contains("data-fuzz-role=\"cell-child\"");
			flex |= html.contains("display:flex") && html.contains("flex-direction:")
					&& html.contains("flex-wrap:") && html.contains("gap:");
			grid |= html.contains("display:grid") && html.contains("grid-template-columns:");
			intrinsic |= html.contains("width:min-content") && html.contains("width:max-content")
					&& html.contains("width:fit-content(");
			relativeSize |= html.contains("min-width:8em") && html.contains("max-width:90%")
					&& html.contains("width:calc(");
			complexWild |= wildHtml.contains("data-fuzz-role=\"wild-complex\"");
			longRuby |= html.contains("class=\"fuzz-long-ruby\"");
		}
		assertTrue("表セル内の再帰的な子へ到達しない", cellChild);
		assertTrue("複数itemのflexへ到達しない", flex);
		assertTrue("複数itemのgridへ到達しない", grid);
		assertTrue("intrinsic size語彙へ到達しない", intrinsic);
		assertTrue("相対・calc size語彙へ到達しない", relativeSize);
		assertTrue("WILDの複雑な部分木へ到達しない", complexWild);
		assertTrue("長いrubyへ到達しない", longRuby);
		assertEquals("同一seedの生成結果が変動する", generate(12345, true), generate(12345, true));
	}

	/** extreme-v1の狙った高密度構造が、全seedで入り、かつ決定的であること。 */
	public void testExtremeProfileIsDenseAndDeterministic() {
		for (int seed = 0; seed < 32; ++seed) {
			final Generated strict = generate(seed, true, false, true);
			final String html = strict.html();
			assertTrue("extreme表へ到達しない", html.contains("data-fuzz-role=\"extreme-table\""));
			assertTrue("thead/tfoot/caption/colgroupを生成しない", html.contains("<thead>")
					&& html.contains("<tfoot>") && html.contains("<caption>") && html.contains("<colgroup>"));
			assertTrue("高密度flex/gridへ到達しない", html.contains("data-fuzz-role=\"extreme-layout\"")
					&& html.contains("grid-row:") && html.contains("align-items:")
					&& html.contains("justify-content:"));
			assertTrue("複合リスト項目へ到達しない", html.contains("data-fuzz-role=\"extreme-list\""));
			assertTrue("多言語/bidi/長語へ到達しない", html.contains("data-fuzz-role=\"extreme-text\"")
					&& html.contains("dir=\"rtl\"") && html.contains("fuzzUnbreakable"));
			assertTrue("v2標準より十分に密でない: tokens=" + strict.tokens().size(),
					strict.tokens().size() >= 80);
			assertTrue("extremeのページ上限が内容量に比例しない", pageLimit(strict) > MAX_PAGES);
			assertEquals("extreme同一seedの生成結果が変動する", strict,
					generate(seed, true, false, true));
		}
		final String wild = generate(0, false, false, true).html();
		assertTrue("WILDの複雑な強制改ページ境界へ到達しない",
				wild.contains("data-fuzz-role=\"extreme-wild-break\""));
		final Generated denseVertical = generate(8676, true, false, true);
		assertTrue("長文を持つ縦書きextreme文書の正常停止前にページ上限が切れる: "
				+ pageLimit(denseVertical), pageLimit(denseVertical) >= 1_443);
	}

	/** overflow-wrap:anywhere等でトークン内部が分割されても内容消失と誤判定しない。 */
	public void testContentOracleRestoresSplitTokensAcrossInterleavedDraws() {
		final Set<String> expected = Set.of("T100", "T423", "T575", "T576", "T657", "T57");
		final List<ObservedText> runs = List.of(new ObservedText("prefix T57", 0, false),
				new ObservedText("T100", 0, false), new ObservedText("5", 0, false),
				new ObservedText("T", 1, false), new ObservedText("1.", 2, false),
				new ObservedText("一、", 2, false), new ObservedText("\u200b", 2, false),
				new ObservedText("42", 2, false), new ObservedText("3", 2, false),
				new ObservedText("T57", 3, false), new ObservedText("6", 3, false),
				new ObservedText("T", 4, false), new ObservedText("x\u200b", 5, false),
				new ObservedText("6", 5, false), new ObservedText("x\u200b", 6, true),
				new ObservedText("5", 6, false), new ObservedText("7", 6, false));
		assertEquals(0, firstObservedTokenPage("T575", runs, expected));
		assertEquals(1, firstObservedTokenPage("T423", runs, expected));
		assertEquals(3, firstObservedTokenPage("T576", runs, expected));
		assertEquals(4, firstObservedTokenPage("T657", runs, expected));
		assertEquals(-1, firstObservedTokenPage("T577", runs, expected));
		assertEquals(0, firstObservedTokenPage("T57", runs, expected));
	}

	/** 部分一致や普通の本文越しをトークンの存在と誤認しない。 */
	public void testContentOracleRejectsPrefixAndTextSkipping() {
		final Set<String> expected = Set.of("T9", "T57", "T91", "T575");
		assertEquals(-1, firstObservedTokenPage("T57",
				List.of(new ObservedText("T575", 0, false)), expected));
		assertEquals(-1, firstObservedTokenPage("T575",
				List.of(new ObservedText("T57", 0, false), new ObservedText("ordinary text", 0, false),
						new ObservedText("5", 0, false)), expected));
		assertEquals(1, firstObservedTokenPage("T91",
				List.of(new ObservedText("T9", 0, false), new ObservedText("1.", 0, false),
						new ObservedText("T91", 1, false)), expected));
		assertEquals(-1, firstObservedTokenPage("T501",
				List.of(new ObservedText("T50", 0, true), new ObservedText("T51", 0, false),
						new ObservedText("1", 1, false)), Set.of("T50", "T51", "T501")));
		assertEquals(2, firstObservedTokenPage("T11",
				List.of(new ObservedText("T1", 1, false), new ObservedText("T2", 1, false),
						new ObservedText("1", 1, false), new ObservedText("T11", 2, false)),
				Set.of("T1", "T2", "T11")));
		assertEquals(1, firstObservedTokenPage("T435",
				List.of(new ObservedText("T43", 0, false), new ObservedText("T89", 0, false),
						new ObservedText("T105", 1, false), new ObservedText("T43", 1, false),
						new ObservedText("5", 1, false)),
				Set.of("T43", "T89", "T105", "T435")));
		assertEquals(-1, firstObservedTokenPage("T575",
				List.of(new ObservedText("T", 0, false), new ObservedText("5", 1, false),
						new ObservedText("7", 1, false), new ObservedText("6", 1, false)), expected));
	}

	/** 中断時checkpointにも分類件数だけでなく再現用seedを残す。 */
	public void testFuzzManifestCheckpointRetainsSeeds() throws Exception {
		final Path manifest = Files.createTempFile("foliojet-fuzz-manifest-", ".json");
		final String previous = System.getProperty("foliojet.fuzzManifest");
		try {
			System.setProperty("foliojet.fuzzManifest", manifest.toString());
			final var classCount = new java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger>();
			final var seedsOf = new java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>>();
			final var defectCount = new java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger>();
			final var defectSeeds = new java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>>();
			classCount.put("ページ数過大", new java.util.concurrent.atomic.AtomicInteger(2));
			rememberSeed(seedsOf, "ページ数過大", 123);
			rememberSeed(seedsOf, "ページ数過大", 456);
			defectCount.put("normalized|site=x", new java.util.concurrent.atomic.AtomicInteger(1));
			rememberSeed(defectSeeds, "normalized|site=x", 456);

			writeFuzzManifest(false, 100, 200, 2, 17, false,
					classCount, seedsOf, defectCount, defectSeeds);
			final String json = Files.readString(manifest, StandardCharsets.UTF_8);
			assertTrue(json.contains("\"processed\":17"));
			assertTrue(json.contains("\"completed\":false"));
			assertTrue(json.contains("\"generatorProfile\":\"" + generatorProfile() + "\""));
			assertTrue(json.contains("\"ページ数過大\":[123,456]"));
			assertTrue(json.contains("\"normalized|site=x\":[456]"));
		} finally {
			if (previous == null) {
				System.clearProperty("foliojet.fuzzManifest");
			} else {
				System.setProperty("foliojet.fuzzManifest", previous);
			}
			Files.deleteIfExists(manifest);
		}
	}

	/**
	 * 2026-08-14の100万件掃過で「全描画が紙面外」になった先頭8シードを固定する。
	 * 正常化して通るか、機械的に限定した専用除外になることだけを許す。
	 */
	public void testStrictHistoricalAllDrawingOffPageSeeds() throws Exception {
		final int[] seeds = { 36607, 82162, 97953, 132786, 139166, 143513, 157106, 175497 };
		final List<String> unexpected = new ArrayList<>();
		for (final int seed : seeds) {
			try {
				checkOneV1(seed, true);
			} catch (final Throwable t) {
				final String kind = classify(t);
				final String expected = switch (seed) {
				case 36607, 82162 -> "(除外)同軸逆進行フローの組版不能幅";
				case 132786, 143513 -> "(除外)組版できない幅の浮動体";
				default -> null;
				};
				if (!kind.equals(expected)) {
					unexpected.add(seed + "=" + kind + ": " + t);
				}
			}
		}
		assertTrue("過去の全描画紙面外シードが未分類のまま残った: " + unexpected, unexpected.isEmpty());
	}

	/**
	 * 2026-08-15の修正後100万件再掃過に残った「全描画が紙面外」12件を固定する。
	 * 作者指定で組版不能な3件は狭い専用除外、実装欠陥だった9件は正常完走を要求する。
	 */
	public void testStrictHistoricalRemainingAllDrawingOffPageSeeds() throws Exception {
		final int[] seeds = { 266476, 324423, 372387, 591475, 763450, 778506,
				799286, 835421, 848433, 865035, 911787, 951004 };
		final List<String> unexpected = new ArrayList<>();
		for (final int seed : seeds) {
			try {
				checkOneV1(seed, true);
			} catch (final Throwable t) {
				final String kind = classify(t);
				final String expected = switch (seed) {
				case 372387 -> "(除外)直交フローの組版不能幅";
				case 324423, 865035 -> "(除外)組版できない幅の浮動体";
				default -> null;
				};
				if (!kind.equals(expected)) {
					unexpected.add(seed + "=" + kind + ": " + t);
				}
			}
		}
		assertTrue("残存した全描画紙面外シードが未解決のまま残った: " + unexpected,
				unexpected.isEmpty());
	}

	/**
	 * 同じ縦軸でも進行方向が変われば独立BFCを作り、内側のfloatを包含する。
	 *
	 * <p>
	 * strict seed 97953の最小形。以前は{@code vertical-rl}直下の
	 * {@code vertical-lr}を同じビルダーへ流し、floatの文字が紙面の開始辺
	 * ちょうどから外側へ出ていた。軸の縦横だけでなくwriting-mode値全体を
	 * 比較する回帰を、百万件掃過とは独立して固定する。
	 * </p>
	 */
	public void testSameAxisWritingModeChangeContainsFloat() throws Exception {
		final String htmlText = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n"
				+ "<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n"
				+ "<html><head><style>@page{margin:0}body{margin:0;font:normal 12pt/1.2 serif;"
				+ "writing-mode:vertical-rl}</style></head><body>"
				+ "<div style=\"writing-mode:vertical-lr\"><div style=\"float:left\">T4</div></div>"
				+ "</body></html>";
		final Generated doc = new Generated(htmlText, List.of("T4"), Set.of("T4"), 120, 400, 0, false, false);
		final File root = Files.createTempDirectory("same-axis-writing-mode-float-").toFile();
		final File html = new File(root, "input.html");
		final File outDir = new File(root, "display-list");
		try {
			checkDocument(doc, html, outDir, true, "same-axis-writing-mode-float");
		} finally {
			final File[] outputs = outDir.listFiles();
			if (outputs != null) {
				for (final File output : outputs) {
					assertTrue("一時出力を削除できない: " + output, output.delete());
				}
			}
			assertTrue("一時出力ディレクトリを削除できない", !outDir.exists() || outDir.delete());
			assertTrue("一時HTMLを削除できない", !html.exists() || html.delete());
			assertTrue("一時ディレクトリを削除できない", root.delete());
		}
	}

	/**
	 * 断片化後に開始のないインライン終了だけが残っても、空行を確定しない。
	 *
	 * <p>
	 * extreme-v1 WILD seed 7593の最小形。表の後に空の脚注を含む段組リストを
	 * 置くと、回復処理が捨てたINLINE_ENDを{@code drawLine()}が内容ありと誤認し、
	 * 空の行ボックスを{@code align()}して変換が失敗していた。
	 * </p>
	 */
	public void testFragmentRecoveryDoesNotAlignEmptyLine() throws Exception {
		final String htmlText = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="60pt"?>
				<?jp.cssj.property name="output.page-height" value="60pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 6pt/1.2 serif}
				table{border-collapse:collapse}
				td{border:1pt solid black}
				</style></head><body>
				<table>
				<thead><th>T99</th></thead>
				<td><div style="display:grid">T113<div></div></div></td><td></td>
				<tfoot><td>T136</td></tfoot>
				</table>
				<ol><li><div style="column-count:2"><div style="float:footnote"></div></div></li></ol>
				</body></html>
				""";
		final Generated doc = new Generated(htmlText, List.of(), Set.of(), 60, 60, 0, false, true);
		final File root = Files.createTempDirectory("fragment-empty-line-").toFile();
		final File html = new File(root, "input.html");
		final File outDir = new File(root, "display-list");
		try {
			checkDocument(doc, html, outDir, false, "fragment-empty-line");
		} finally {
			final File[] outputs = outDir.listFiles();
			if (outputs != null) {
				for (final File output : outputs) {
					assertTrue("一時出力を削除できない: " + output, output.delete());
				}
			}
			assertTrue("一時出力ディレクトリを削除できない", !outDir.exists() || outDir.delete());
			assertTrue("一時HTMLを削除できない", !html.exists() || html.delete());
			assertTrue("一時ディレクトリを削除できない", root.delete());
		}
	}

	/**
	 * 2026-08-14の百万件掃過で唯一の白紙ページだったseed 78906を固定する。
	 *
	 * <p>
	 * 2026-08-21まで「(除外)組版できない幅の浮動体」だったが、END側
	 * フロートの行頭クランプ(帯より広いフロートを行頭より前=紙面の外へ
	 * 出さない。BlockBuilder.tryFloatPlacement)により**正常に組める**
	 * ようになった。除外が不要になったのはクランプの改善効果なので、
	 * 新しい挙動(両モード成功)を固定する。
	 * </p>
	 */
	public void testStrictHistoricalBlankPageSeed() throws Exception {
		checkOneV1(78906, true);
		checkOneV1(78906, false);
	}

	/** 2026-08-14の百万件掃過で「紙面外への配置」になった7シードを固定する。 */
	public void testStrictHistoricalOffPagePlacementSeeds() throws Exception {
		final int[] seeds = { 321621, 473636, 473924, 526411, 651439, 776967, 867178 };
		final List<String> unexpected = new ArrayList<>();
		for (final int seed : seeds) {
			try {
				checkOneV1(seed, true);
			} catch (final Throwable t) {
				final String kind = classify(t);
				final String expected = switch (seed) {
				case 473924 -> "(除外)flex内の段組表によるmin-content幅";
				case 776967 -> "(除外)同軸逆進行フローの組版不能幅";
				default -> null;
				};
				if (!kind.equals(expected)) {
					unexpected.add(seed + "=" + kind + ": " + t);
				}
			}
		}
		assertTrue("過去の紙面外配置シードが未解決のまま残った: " + unexpected, unexpected.isEmpty());
	}

	/**
	 * {@code -Dfoliojet.fuzzImage}で差し替えた画像が、既定の画像と
	 * <b>同じ寸法か</b>を確かめます(2026-08-02)。
	 *
	 * <p>
	 * この指定はI/Oの速い場所へ画像を移すためのもので、<b>同じ画像の
	 * 置き場所を変えるだけ</b>が前提である。寸法の違う画像を指すと版面が
	 * 変わり、シード番号が指す文書が黙って別物になる——過去の掃過結果と
	 * 突き合わせられなくなるため、続行せずに落とす。
	 * </p>
	 */
	private static void checkFuzzImage() throws Exception {
		final String path = System.getProperty("foliojet.fuzzImage");
		if (path == null) {
			return;
		}
		final int[] replaced = pngSize(new File(path));
		final int[] original = pngSize(new File(DEFAULT_FUZZ_IMAGE));
		if (replaced == null || original == null) {
			return;
		}
		if (replaced[0] != original[0] || replaced[1] != original[1]) {
			fail("-Dfoliojet.fuzzImage の画像は既定と同じ寸法でなければならない"
					+ "(シードが指す文書が変わってしまう): " + path + " は "
					+ replaced[0] + "x" + replaced[1] + "、既定の " + DEFAULT_FUZZ_IMAGE + " は "
					+ original[0] + "x" + original[1]);
		}
	}

	/** PNGのIHDRから寸法を読みます(読めなければnull)。 */
	private static int[] pngSize(final File file) throws Exception {
		if (!file.isFile()) {
			return null;
		}
		final byte[] head = new byte[24];
		try (java.io.InputStream in = new java.io.FileInputStream(file)) {
			if (in.readNBytes(head, 0, head.length) != head.length) {
				return null;
			}
		}
		// 8バイトの署名 + 長さ4 + "IHDR" + 幅4 + 高さ4
		if (head[0] != (byte) 0x89 || head[12] != 'I' || head[13] != 'H' || head[14] != 'D'
				|| head[15] != 'R') {
			return null;
		}
		return new int[] { readInt(head, 16), readInt(head, 20) };
	}

	private static int readInt(final byte[] b, final int offset) {
		return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16) | ((b[offset + 2] & 0xFF) << 8)
				| (b[offset + 3] & 0xFF);
	}

	/**
	 * 掃過を開始するシード({@code -Dfoliojet.fuzzFrom})。
	 *
	 * <p>
	 * <b>100年目標の3,000万文書は1回で回せない</b>(6,000万文書=28時間前後)。
	 * 生成器は決定的なので、100万件ずつ30回に分けても連続実行と同じ文書集合に
	 * なる。途中で落ちても、そこまでの結果は積算できる。
	 * </p>
	 */
	private static int seedFrom() {
		final String v = System.getProperty("foliojet.fuzzFrom");
		return v == null ? 0 : Integer.parseInt(v);
	}

	/** 途中経過を出す間隔。3,000万件で3,000行——落ちても到達点が残る。 */
	private static final int PROGRESS_EVERY = 10_000;

	private static int seedCount() {
		final String v = System.getProperty("foliojet.fuzzSeeds");
		return v == null ? DEFAULT_SEEDS : Integer.parseInt(v);
	}

	/**
	 * <b>既知の未解決</b>: 末尾に空ページが1枚余分に出るシード。
	 *
	 * <p>
	 * <b>2026-07-26に既定シードの範囲では解消したので空にした。</b>
	 * 原因は{@code BreakableBuilder.classifyFloatPlacement}が
	 * 「箱だけがはみ出しているのか、はみ出した先に描くものがあるのか」を
	 * 区別していなかったこと。区別を入れた
	 * ({@code paintsNothingBeyondPage})結果、6,000シードでの発生が
	 * <b>88件→47件</b>へ減り、既定60シードでは0件になった。
	 * </p>
	 *
	 * <p>
	 * <b>残り47件は別の機序</b>で、まだ縮小できていない。既定シードには
	 * 当たらないので回帰は緑のまま、掃過
	 * ({@code -Dfoliojet.fuzzReport=1 -Dfoliojet.fuzzSeeds=6000})でだけ
	 * 見える。**この集合へ安易に追加しないこと**——追加は「直せないと
	 * 判断した」という意思表示であり、既定の回帰から永久に隠れる。
	 * </p>
	 */
	private static final java.util.Set<Integer> KNOWN_TRAILING_BLANK_PAGE = java.util.Set.of();

	/**
	 * <b>既知の未解決</b>: 変換が例外で終わるシード(2026-07-26、
	 * 語彙を広げた掃過で発見)。2種類の不変条件違反がある。
	 *
	 * <ul>
	 * <li><b>textBuilderが開いたまま</b>ブロック境界を越える
	 * ({@code BlockBuilder.requireNoOpenTextBuilder})。strictで400文書に1件</li>
	 * <li><b>flowStackの深さと継続の深さが食い違う</b>
	 * ({@code RootBuilder.pageBreak}の"break flow failed")。strictで1,000文書に1件</li>
	 * </ul>
	 *
	 * <p>
	 * <b>どちらもfail closed済み</b>(2026-07-26に確認)。assertではなく
	 * {@code ContinuationInvariantViolationException}なので、<b>本番でも
	 * 変換が失敗する</b>——黙って壊れた出力を出すことはない。
	 * {@code -PnoAssertions}で掃過しても件数が変わらないことで確かめた。
	 * </p>
	 *
	 * <p>
	 * fail closed化する前は実際に内容が消えていた(seed 890で
	 * {@code column-count:3}のブロックが丸ごと落ちた)。どちらも改ページ・
	 * 継続機構の中枢なので、原因を特定してから直す。
	 * 再現手順は`copperpdf4/docs/PLAN.md`。
	 * </p>
	 */
	private static final java.util.Set<Integer> KNOWN_INVARIANT_VIOLATION = java.util.Set.of();

	/**
	 * 統計用の集計モード({@code -Dfoliojet.fuzzReport})。早期打ち切りを
	 * せず全シードを走らせ、<b>失敗の種別ごとの件数と初出シード</b>を
	 * 出力する。これが「あと何件残っているか」「次の失敗まで何回か」の
	 * 推定の入力になる(`copperpdf4/docs/REVIEW-STATISTICS.md` §8)。
	 */
	/**
	 * watchdogを超えて生き残ったスレッドの数。<b>止められないので数える</b>。
	 */
	private static final java.util.concurrent.atomic.AtomicInteger LEAKED_WORKERS =
			new java.util.concurrent.atomic.AtomicInteger();

	/**
	 * これを超えたら掃過ごと止める。漏れた1本はレイアウト1件分のヒープと
	 * 64MBのスタック予約を抱えるので、数本で測定条件が変わってしまう。
	 */
	private static final int MAX_LEAKED_WORKERS = 4;

	/**
	 * 規模に比例した漏れワーカーの上限(2026-07-28)。
	 *
	 * <p>
	 * 2万件で4本という値は、3,000万件では<b>確率的に必ず踏む</b>。
	 * ただし上限を撤廃してはいけない——漏れた1本はレイアウト1件分のヒープと
	 * 64MBのスタック予約を抱えるので、放置すると自己増幅する(§10.3)。
	 * 止める設計は維持し、閾値だけを規模に比例させる(10万件に1本)。
	 * </p>
	 */
	private static int maxLeakedWorkers() {
		return Math.max(MAX_LEAKED_WORKERS, seedCount() / 100_000);
	}

	private static boolean reportMode() {
		return System.getProperty("foliojet.fuzzReport") != null;
	}

	/**
	 * 既存の文書単位被覆と局所被覆で共通に使う機能語彙。
	 * 順番はビット番号でもあるため、既存項目の並べ替えや途中挿入をしない。
	 */
	private static final List<String> LOCAL_FEATURES = List.of("display:flex", "display:grid",
			"display:inline-block", "display:list-item", "display:table", "display:none", "position:absolute",
			"position:relative", "float:left", "float:right", "float:footnote", "float:top", "float:bottom",
			"writing-mode:vertical", "overflow:hidden", "<table", "<ul", "<ol", "<ruby", "<img", "<form",
			"<input", "<select", "<textarea", "<button", "page-break-before", "page-break-inside",
			"list-style-type", "clear:", "column-count:");

	private enum LocalScope {
		SAME_BOX("same-box"),
		PARENT_CHILD("parent-child"),
		ANCESTOR_2("ancestor-distance-2"),
		SIBLING("sibling");

		final String label;

		LocalScope(final String label) {
			this.label = label;
		}
	}

	private record CoverageKey(LocalScope scope, long features) {
		int t() {
			return Long.bitCount(this.features);
		}
	}

	private static final class StructureNode {
		final long features;
		final List<StructureNode> children = new ArrayList<>();

		StructureNode(final long features) {
			this.features = features;
		}
	}

	private record GenerationStructure(StructureNode body, int elements) {
		java.util.Set<CoverageKey> coverageKeys() {
			final java.util.Set<CoverageKey> keys = new java.util.HashSet<>();
			collectCoverage(this.body, null, null, keys);
			return keys;
		}

		String topologyHash() {
			final List<String> topology = new ArrayList<>();
			collectTopology(this.body, null, null, topology);
			java.util.Collections.sort(topology);
			final MessageDigest digest = sha256();
			for (final String relation : topology) {
				digest.update(relation.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
			return java.util.HexFormat.of().formatHex(digest.digest(), 0, 8);
		}
	}

	private static final ThreadLocal<javax.xml.stream.XMLInputFactory> STRUCTURE_XML =
			ThreadLocal.withInitial(() -> {
				final javax.xml.stream.XMLInputFactory factory =
						javax.xml.stream.XMLInputFactory.newFactory();
				factory.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
				factory.setProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
						Boolean.FALSE);
				return factory;
			});

	/**
	 * 生成HTMLをXMLイベントとして走査する。HTML文字列の正規表現近似ではなく、
	 * 実際の要素の親子・兄弟関係を使う。
	 */
	private static GenerationStructure inspectGeneratedStructure(final String html) {
		final int firstLf = html.indexOf('\n');
		final String xml = html.startsWith("<!DOCTYPE") && firstLf >= 0
				? html.substring(firstLf + 1) : html;
		final java.util.ArrayDeque<StructureNode> stack = new java.util.ArrayDeque<>();
		StructureNode body = null;
		int elements = 0;
		boolean inBody = false;
		javax.xml.stream.XMLStreamReader reader = null;
		try {
			reader = STRUCTURE_XML.get().createXMLStreamReader(new StringReader(xml));
			while (reader.hasNext()) {
				final int event = reader.next();
				if (event == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
					final String tag = reader.getLocalName().toLowerCase(java.util.Locale.ROOT);
					if ("body".equals(tag)) {
						body = new StructureNode(elementFeatures(tag,
								reader.getAttributeValue(null, "style")));
						stack.push(body);
						inBody = true;
						++elements;
					} else if (inBody) {
						final StructureNode node = new StructureNode(elementFeatures(tag,
								reader.getAttributeValue(null, "style")));
						stack.peek().children.add(node);
						stack.push(node);
						++elements;
					}
				} else if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT && inBody) {
					final String tag = reader.getLocalName().toLowerCase(java.util.Locale.ROOT);
					stack.pop();
					if ("body".equals(tag)) {
						inBody = false;
					}
				}
			}
		} catch (final javax.xml.stream.XMLStreamException e) {
			throw new IllegalStateException("生成HTMLの構造走査に失敗した", e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (final javax.xml.stream.XMLStreamException ignore) {
					// StringReaderなので解放対象はない
				}
			}
		}
		if (body == null) {
			throw new IllegalStateException("生成HTMLにbody要素がない");
		}
		return new GenerationStructure(body, elements);
	}

	private static long elementFeatures(final String tag, final String style) {
		long mask = switch (tag) {
		case "table" -> feature("<table");
		case "ul" -> feature("<ul");
		case "ol" -> feature("<ol");
		case "ruby" -> feature("<ruby");
		case "img" -> feature("<img");
		case "form" -> feature("<form");
		case "input" -> feature("<input");
		case "select" -> feature("<select");
		case "textarea" -> feature("<textarea");
		case "button" -> feature("<button");
		default -> 0;
		};
		if (style == null) {
			return mask;
		}
		for (int start = 0; start < style.length();) {
			int end = style.indexOf(';', start);
			if (end < 0) {
				end = style.length();
			}
			final String declaration = style.substring(start, end).trim();
			final int colon = declaration.indexOf(':');
			if (colon > 0) {
				final String property = declaration.substring(0, colon).trim();
				final String value = declaration.substring(colon + 1).trim();
				mask |= switch (property) {
				case "display" -> optionalFeature("display:" + value);
				case "position" -> optionalFeature("position:" + value);
				case "float" -> optionalFeature("float:" + value);
				case "writing-mode" -> value.startsWith("vertical")
						? feature("writing-mode:vertical") : 0;
				case "overflow" -> "hidden".equals(value) ? feature("overflow:hidden") : 0;
				case "page-break-before" -> feature("page-break-before");
				case "page-break-inside" -> feature("page-break-inside");
				case "list-style-type" -> feature("list-style-type");
				case "clear" -> feature("clear:");
				case "column-count" -> feature("column-count:");
				default -> 0;
				};
			}
			start = end + 1;
		}
		return mask;
	}

	private static long optionalFeature(final String name) {
		final int index = LOCAL_FEATURES.indexOf(name);
		return index < 0 ? 0 : 1L << index;
	}

	private static long feature(final String name) {
		final int index = LOCAL_FEATURES.indexOf(name);
		if (index < 0) {
			throw new IllegalArgumentException("未知の局所被覆機能: " + name);
		}
		return 1L << index;
	}

	private static void collectCoverage(final StructureNode node, final StructureNode parent,
			final StructureNode grandparent, final java.util.Set<CoverageKey> keys) {
		addCoverageCombos(keys, LocalScope.SAME_BOX, node.features);
		if (parent != null) {
			addCoverageCombos(keys, LocalScope.PARENT_CHILD, parent.features | node.features);
		}
		if (grandparent != null) {
			addCoverageCombos(keys, LocalScope.ANCESTOR_2, grandparent.features | node.features);
		}
		for (int i = 0; i < node.children.size(); ++i) {
			for (int j = i + 1; j < node.children.size(); ++j) {
				addCoverageCombos(keys, LocalScope.SIBLING,
						node.children.get(i).features | node.children.get(j).features);
			}
		}
		for (final StructureNode child : node.children) {
			collectCoverage(child, node, parent, keys);
		}
	}

	private static void collectTopology(final StructureNode node, final StructureNode parent,
			final StructureNode grandparent, final List<String> topology) {
		if (node.features != 0) {
			topology.add("B:" + Long.toUnsignedString(node.features, 16));
		}
		if (parent != null && (parent.features | node.features) != 0) {
			topology.add("P:" + Long.toUnsignedString(parent.features, 16) + ">"
					+ Long.toUnsignedString(node.features, 16));
		}
		if (grandparent != null && (grandparent.features | node.features) != 0) {
			topology.add("A2:" + Long.toUnsignedString(grandparent.features, 16) + ">"
					+ Long.toUnsignedString(node.features, 16));
		}
		for (int i = 0; i < node.children.size(); ++i) {
			for (int j = i + 1; j < node.children.size(); ++j) {
				final long a = node.children.get(i).features;
				final long b = node.children.get(j).features;
				if ((a | b) != 0) {
					topology.add("S:" + Long.toUnsignedString(Math.min(a, b), 16) + ","
							+ Long.toUnsignedString(Math.max(a, b), 16));
				}
			}
		}
		for (final StructureNode child : node.children) {
			collectTopology(child, node, parent, topology);
		}
	}

	private static void addCoverageCombos(final java.util.Set<CoverageKey> keys,
			final LocalScope scope, final long presentMask) {
		final int count = Long.bitCount(presentMask);
		final int[] present = new int[count];
		int at = 0;
		for (int bit = 0; bit < LOCAL_FEATURES.size(); ++bit) {
			if ((presentMask & (1L << bit)) != 0) {
				present[at++] = bit;
			}
		}
		for (int t = 2; t <= 5 && t <= count; ++t) {
			final int[] index = new int[t];
			for (int i = 0; i < t; ++i) {
				index[i] = i;
			}
			while (true) {
				long combination = 0;
				for (int i = 0; i < t; ++i) {
					combination |= 1L << present[index[i]];
				}
				keys.add(new CoverageKey(scope, combination));
				int i = t - 1;
				while (i >= 0 && index[i] == count - t + i) {
					--i;
				}
				if (i < 0) {
					break;
				}
				++index[i];
				for (int j = i + 1; j < t; ++j) {
					index[j] = index[j - 1] + 1;
				}
			}
		}
	}

	private static java.util.Set<Long> orProduct(final java.util.Set<Long> left,
			final long... right) {
		final java.util.Set<Long> product = new java.util.HashSet<>();
		for (final long a : left) {
			for (final long b : right) {
				product.add(a | b);
			}
		}
		return product;
	}

	/**
	 * サンプルからではなく、現在の生成スキーマの選択肢から到達可能分母を作る。
	 * 観測側と食い違った場合はreportのoutsideSchemaが非0になる。
	 */
	private static java.util.Set<CoverageKey> reachableLocalCombos(final boolean strict) {
		java.util.Set<Long> layout = java.util.Set.of(0L);
		layout = orProduct(layout, 0, feature("display:flex"), feature("display:grid"),
				feature("display:inline-block"), feature("display:list-item"), feature("display:table"),
				strict ? 0 : feature("display:none"));
		layout = orProduct(layout, 0, feature("position:relative"),
				strict ? 0 : feature("position:absolute"));
		layout = orProduct(layout, 0, feature("float:left"), feature("float:right"),
				strict ? 0 : feature("float:footnote"),
				strict ? 0 : feature("float:top"),
				strict ? 0 : feature("float:bottom"));
		layout = orProduct(layout, 0, feature("writing-mode:vertical"));
		if (!strict) {
			layout = orProduct(layout, 0, feature("overflow:hidden"));
		}

		final java.util.Set<Long> plain = new java.util.HashSet<>();
		plain.add(0L);
		plain.add(feature("float:left"));
		plain.add(feature("float:right"));
		plain.add(feature("<table"));
		plain.add(feature("column-count:"));
		plain.add(feature("writing-mode:vertical"));
		plain.add(feature("<ul") | feature("list-style-type"));
		plain.add(feature("<ol") | feature("list-style-type"));
		plain.add(feature("clear:"));
		plain.add(feature("page-break-inside"));
		plain.add(feature("<form"));
		if (!strict) {
			plain.add(feature("position:absolute"));
			plain.add(feature("page-break-before") | feature("overflow:hidden"));
		}

		final java.util.Set<Long> roots = new java.util.HashSet<>(plain);
		roots.addAll(layout);
		final java.util.Set<Long> controls = java.util.Set.of(feature("<input"), feature("<select"),
				feature("<textarea"), feature("<button"));
		final java.util.Set<Long> fixedChildren = new java.util.HashSet<>(controls);
		fixedChildren.add(feature("display:inline-block"));
		fixedChildren.add(feature("<ruby"));
		fixedChildren.add(feature("<img"));
		fixedChildren.add(0L);

		final java.util.Set<Long> recursiveParents = java.util.Set.of(0L, feature("float:left"),
				feature("float:right"), feature("column-count:"), feature("writing-mode:vertical"),
				feature("page-break-inside"));
		final java.util.Set<Long> bodyMasks =
				java.util.Set.of(0L, feature("writing-mode:vertical"));

		final java.util.Set<CoverageKey> reachable = new java.util.HashSet<>();
		final java.util.Set<Long> nodeMasks = new java.util.HashSet<>(roots);
		nodeMasks.addAll(fixedChildren);
		for (final long mask : nodeMasks) {
			addCoverageCombos(reachable, LocalScope.SAME_BOX, mask);
		}

		final java.util.Set<Long> ordinaryParents = new java.util.HashSet<>(recursiveParents);
		ordinaryParents.addAll(bodyMasks);
		for (final long parent : ordinaryParents) {
			for (final long child : roots) {
				addCoverageCombos(reachable, LocalScope.PARENT_CHILD, parent | child);
			}
		}
		for (final long wrapper : layout) {
			for (final long child : plain) {
				addCoverageCombos(reachable, LocalScope.PARENT_CHILD, wrapper | child);
			}
		}
		for (final long list : java.util.Set.of(feature("<ul") | feature("list-style-type"),
				feature("<ol") | feature("list-style-type"))) {
			addCoverageCombos(reachable, LocalScope.PARENT_CHILD, list);
		}
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<input"));
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<select"));
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<textarea"));
		addCoverageCombos(reachable, LocalScope.PARENT_CHILD,
				feature("<form") | feature("<button"));

		final java.util.Set<Long> possibleGrandparents = new java.util.HashSet<>(ordinaryParents);
		possibleGrandparents.addAll(layout);
		for (final long grandparent : possibleGrandparents) {
			for (final long descendant : roots) {
				addCoverageCombos(reachable, LocalScope.ANCESTOR_2,
						grandparent | descendant);
			}
		}
		for (final long wrapper : layout) {
			for (final long descendant : fixedChildren) {
				addCoverageCombos(reachable, LocalScope.ANCESTOR_2,
						wrapper | descendant);
			}
		}

		for (final long a : roots) {
			for (final long b : roots) {
				addCoverageCombos(reachable, LocalScope.SIBLING, a | b);
			}
		}
		for (final long a : controls) {
			for (final long b : controls) {
				addCoverageCombos(reachable, LocalScope.SIBLING, a | b);
			}
		}
		return reachable;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (final java.security.NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	private static final class Distribution {
		private final java.util.concurrent.ConcurrentSkipListMap<Integer,
				java.util.concurrent.atomic.LongAdder> histogram =
						new java.util.concurrent.ConcurrentSkipListMap<>();
		private final java.util.concurrent.atomic.LongAdder count =
				new java.util.concurrent.atomic.LongAdder();
		private final java.util.concurrent.atomic.LongAdder sum =
				new java.util.concurrent.atomic.LongAdder();

		void add(final int value) {
			this.histogram.computeIfAbsent(value,
					x -> new java.util.concurrent.atomic.LongAdder()).increment();
			this.count.increment();
			this.sum.add(value);
		}

		String summary() {
			final long n = this.count.sum();
			if (n == 0) {
				return "n=0";
			}
			return "n=" + n + " min=" + this.histogram.firstKey()
					+ " p50=" + quantile(0.50, n)
					+ " p95=" + quantile(0.95, n)
					+ " p99=" + quantile(0.99, n)
					+ " max=" + this.histogram.lastKey()
					+ " mean=" + String.format(java.util.Locale.ROOT, "%.2f",
							this.sum.sum() / (double) n);
		}

		private int quantile(final double q, final long n) {
			final long target = Math.max(1, (long) Math.ceil(q * n));
			long cumulative = 0;
			for (final var entry : this.histogram.entrySet()) {
				cumulative += entry.getValue().sum();
				if (cumulative >= target) {
					return entry.getKey();
				}
			}
			return this.histogram.lastKey();
		}
	}

	private static final class SweepMeasurements {
		final Distribution elements = new Distribution();
		final Distribution pages = new Distribution();
		final java.util.concurrent.ConcurrentHashMap<CoverageKey,
				java.util.concurrent.atomic.LongAdder> coverage =
						new java.util.concurrent.ConcurrentHashMap<>();

		void record(final GenerationStructure structure) {
			this.elements.add(structure.elements());
			for (final CoverageKey key : structure.coverageKeys()) {
				this.coverage.computeIfAbsent(key,
						x -> new java.util.concurrent.atomic.LongAdder()).increment();
			}
		}
	}

	/**
	 * <b>紙面に収まらない箱を含む文書</b>で白紙ページが出た、という印
	 * (2026-07-26新設)。失敗ではなく<b>除外</b>として扱う。
	 *
	 * <p>
	 * 2026-07-26のユーザー裁定: 「意図的にやらないとこうはならないと
	 * 言えるレアケースは、デザイナの責任として取りこぼしてよい」。
	 * 紙面より大きい不可分な箱を置いた文書は、エンジンがどう振る舞っても
	 * ——はみ出させるか、次ページへ送るか——版面が破綻している。
	 * </p>
	 *
	 * <p>
	 * <b>例外にしているのは、集計モードで件数を出すため。</b> 単に検査を
	 * 飛ばすと、除外が増えたことに気づけなくなる。除外の増加は
	 * 「生成器が変わった」か「本当の退行が除外に紛れ込んだ」かの
	 * どちらかであり、どちらも見逃してはならない。
	 * </p>
	 */
	private static final class ExcludedByOversizedBox extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByOversizedBox(final String message) {
			super(message);
		}
	}

	/**
	 * <b>直交フローが親の行軸へはみ出した</b>、という印(2026-07-28新設)。
	 * 失敗ではなく<b>除外</b>として扱う。
	 *
	 * <p>
	 * 縦書きの中の横書き(またはその逆)は、2026-07-22の改ページ契約で
	 * <b>原子的</b>と定めた({@code ContinuationCapability.ORTHOGONAL_FLOW}、
	 * {@code supportsPageSplitThrough}が{@code false})。その箱が親の
	 * <b>行軸</b>方向に紙面を超えると、エンジンには打つ手がない——
	 * 改ページが進むのは<b>ページ軸</b>で、新しい紙は行軸に新しい空間を
	 * 与えないからである(実測: 3ページ目へ送っても{@code y=0.00→100.80}
	 * のまま1ptも変わらない)。
	 * </p>
	 *
	 * <p>
	 * <b>CSS標準はこれを「自動段組化」で解こうとしている</b>
	 * (css-writing-modes-4 §7.3 auto-multicol: はみ出した内容を包含ブロックの
	 * 流れ方向へ段として折り返し、T字型ドキュメントを避ける)。しかし
	 * 当の仕様が<b>at-risk</b>(CR期間中に削除されうる)と認めており、
	 * 「この要件は<b>すべてのブロックコンテナ</b>に多段組フローを自動的に
	 * 発生させる」と自ら書いている。Blinkは旧実装の切り刻みを<b>やめて</b>
	 * 単体内容を溢れさせる方針へ移り、WPTにも「長い直交フローが分断される」
	 * ことを要求するテストは1件も無い(2026-07-28にコーパスを実査)。
	 * </p>
	 *
	 * <p>
	 * したがって<b>溢れさせるのは実ブラウザと同じ挙動</b>であり、
	 * 組版を指定した側の責任とする(2026-07-28のユーザー裁定)。
	 * ARCHITECTURE.md §5.13「仕様(=組版を指定した側の責任)」に連なる。
	 * </p>
	 */
	private static final class ExcludedByOrthogonalLineAxis extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByOrthogonalLineAxis(final String message) {
			super(message);
		}
	}

	/**
	 * <b>組版できない幅の浮動体または包含ブロック</b>から白紙ページ・
	 * 紙面外配置が出た、という印
	 * (2026-07-29新設)。失敗ではなく<b>除外</b>として扱う。
	 *
	 * <p>
	 * 判定は{@link #hasUntypesettableFloat}——明示した寸法が基準フォントの
	 * 8倍(=約8文字)未満の浮動体、同じ下限未満の祖先に入った浮動体、または
	 * 親・段幅より広い左右フロートがあること。欄が組版できない幅なら中身は
	 * 必ず溢れ、CSSの{@code overflow:visible}によりそれは<b>正しい挙動</b>で
	 * ある。{@code isTinyPage}(紙面が組版できない大きさ)と同じ物差しを
	 * 欄に当てたもの。
	 * </p>
	 */
	private static final class ExcludedByUntypesettableFloat extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByUntypesettableFloat(final String message) {
			super(message);
		}
	}

	/**
	 * <b>直交フローが3段以上入れ子になった</b>文書での紙面外配置、という印
	 * (2026-07-30新設)。失敗ではなく<b>除外</b>として扱う。
	 *
	 * <p>
	 * 2026-07-30のユーザー裁定(seed 5448946)。{@code body}が
	 * {@code vertical-rl}、その中が{@code horizontal-tb}、さらにその中が
	 * {@code vertical-lr}——のように軸が2回以上入れ替わると、内容は紙の
	 * 外へ出て<b>1文字も見えなくなる</b>。実測(60x60ptの紙):
	 * </p>
	 *
	 * <pre>
	 * T0 x= 60.50   T1 x= 93.66   T3 x=109.90   T4 x=126.14   (出力は1ページ)
	 * </pre>
	 *
	 * <p>
	 * <b>これは座標変換の誤りではない。</b>{@code vertical-rl}のblock開始辺は
	 * 紙の<b>右端</b>である。その中でblock方向が{@code +x}へ反転すれば、
	 * 右端から外向きに進む。だから先頭が{@code 紙幅+0.5pt}に来る——
	 * 向きの反転を素直に合成した結果であり、紙幅を変えるとずれも比例する
	 * (200pt幅なら{@code x=200.5})。
	 * </p>
	 *
	 * <p>
	 * <b>正解が定義できない。</b>直交フローの利用可能blockサイズは
	 * css-writing-modes-4 §7.3でも曖昧で、実ブラウザ間でも一致しない。
	 * ここが3段になると「どこで改ページすべきか」に定義が無い。
	 * 400万文書に1件、現実の帳票や書籍では起きない形である。
	 * </p>
	 *
	 * <p>
	 * <b>除外は狭く限定する。</b>判定は{@link #orthogonalAxisChanges}が2以上
	 * ——軸が2回入れ替わることだけを見る。「直交フローを含む」まで広げると、
	 * 普通の縦書き文書での本物の紙面外バグを見逃す。
	 * </p>
	 */
	private static final class ExcludedByNestedOrthogonalFlow extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByNestedOrthogonalFlow(final String message) {
			super(message);
		}
	}

	/**
	 * <b>組版できない幅の縦書きフローでページ進行方向を反転した</b>文書での
	 * 紙面外配置、
	 * という印(2026-08-15新設)。失敗ではなく<b>除外</b>として扱う。
	 *
	 * <p>
	 * seed 36607は{@code vertical-rl}の親の中に幅48ptの
	 * {@code vertical-lr}を置き、その子へ幅86pt、さらに幅125ptの箱を置く。
	 * 縮小した最小形は{@code vertical-rl}の親の中に
	 * {@code writing-mode:vertical-lr;width:0pt}を置き、その子を再び
	 * {@code vertical-rl}にする。幅0の辺が紙面右端にあり、そこでページ進行を
	 * {@code +x}へ反転するため、子は右端から紙面外へ伸びる。Chromeでも同じ
	 * 配置になるので、座標変換の欠陥ではなく、明示したゼロ幅と
	 * {@code overflow:visible}の帰結である。
	 * seed 82162の最小形は10pt文字に対して幅1ptの反転フローへ
	 * {@code list-item}と表を詰めたものだった。
	 * </p>
	 *
	 * <p>
	 * <b>除外はこの形だけに限定する。</b>{@link #hasUntypesettableOppositeProgression}
	 * は、同じ縦軸の親子で{@code vertical-rl}/{@code vertical-lr}が反転し、
	 * 反転した要素自身の幅が基準フォント8文字分未満、またはその幅より広い
	 * 明示幅の子孫がある場合だけ真になる。単なる同軸反転、組版できる幅、
	 * 直交フロー、別軸の{@code height:0}は除外しない。
	 * </p>
	 */
	private static final class ExcludedByUntypesettableOppositeProgression extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByUntypesettableOppositeProgression(final String message) {
			super(message);
		}
	}

	/** 軸を変えた子へ基準文字送り未満の幅を明示した組版不能入力。 */
	private static final class ExcludedByUntypesettableOrthogonalFlow extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByUntypesettableOrthogonalFlow(final String message) {
			super(message);
		}
	}

	/**
	 * flex項目の自動最小幅に、段組表のmin-content幅が効いた紙面外配置。
	 *
	 * <p>
	 * CSS Flexbox §4.5では非スクロールflex項目の自動最小幅をcontent-based
	 * minimumとする。seed 473924の最小形は単一flex項目の中に3段組と表を
	 * 入れたもので、表のmin-content幅を段数分確保するため版面より広くなる。
	 * {@code min-width:0}を指定しない作者側CSSの帰結なので専用除外にする。
	 * 判定は{@link #hasFlexMulticolTable}の実際の祖先関係だけに限定する。
	 * </p>
	 */
	private static final class ExcludedByFlexMulticolMinContent extends AssertionError {
		private static final long serialVersionUID = 1L;

		ExcludedByFlexMulticolMinContent(final String message) {
			super(message);
		}
	}

	/** 失敗メッセージから種別(defect class)を粗く取り出す。 */
	static String classify(final Throwable t) {
		for (Throwable c = t; c != null; c = c.getCause()) {
			if (c instanceof PageCountLimitExceeded) {
				return "ページ数過大";
			}
		}
		if (t instanceof ExcludedByUntypesettableFloat) {
			return "(除外)組版できない幅の浮動体";
		}
		if (t instanceof ExcludedByOrthogonalLineAxis) {
			return "(除外)直交フローの行軸はみ出し";
		}
		if (t instanceof ExcludedByNestedOrthogonalFlow) {
			return "(除外)直交フロー3段以上の入れ子";
		}
		if (t instanceof ExcludedByUntypesettableOppositeProgression) {
			return "(除外)同軸逆進行フローの組版不能幅";
		}
		if (t instanceof ExcludedByUntypesettableOrthogonalFlow) {
			return "(除外)直交フローの組版不能幅";
		}
		if (t instanceof ExcludedByFlexMulticolMinContent) {
			return "(除外)flex内の段組表によるmin-content幅";
		}
		// 捕捉するのはラッパ(AssertionError)なので、**cause鎖の全メッセージ**を
		// 連結して判定する。t.getMessage()だけを見ると常にラッパの文言に
		// なり、種別が1つに潰れる(2026-07-26に踏んだ)
		if (t instanceof ExcludedByOversizedBox) {
			// 除外の理由は同じ(版面が破綻した文書)だが、どちらの不変条件が
			// 引っかかったのかは残す——除外の内訳が変わったことに
			// 気づけなくなるため
			return String.valueOf(t.getMessage()).startsWith("白紙ページ") ? "(除外)版面が破綻した文書の白紙ページ"
					: "(除外)版面が破綻した文書の紙面外配置";
		}
		final StringBuilder chain = new StringBuilder();
		for (Throwable c = t; c != null; c = c.getCause()) {
			chain.append(String.valueOf(c.getMessage())).append('\0');
		}
		final String m = chain.toString();
		// 変換エラーは**メッセージの形**で種別を分ける。もとの
		// AssertionErrorのスタックはTranscoderExceptionへ包む段で
		// 失われており(causeにも入らない)、発生箇所では分けられない。
		// 種別を粗くすると「残り何件か」の推定が過小になる(2026-07-26)
		if (m.contains("auto page break repeated")) {
			// livelockガードが止めたもの。`Unexpected error.`を含むので、
			// 分けないとtextBuilderの枠に紛れて集計が嘘になる(2026-07-27)
			return "進捗のない自動改ページ(ガードが停止)";
		}
		if (m.contains("再生範囲") || m.contains("range is not intact")) {
			return "再生範囲が欠けている";
		}
		if (m.contains("break flow failed")) {
			return "不変条件: flowStack深さ≠継続深さ";
		}
		if (m.contains("text builder still open")) {
			return "不変条件: textBuilderが開いたまま";
		}
		// **ここで`Unexpected error.`を丸めない**(2026-07-28)。
		// これは TranscoderException の汎用ラッパ文言で、原因を問わず付く。
		// 以前はこれを「textBuilderが開いたまま」と決めつけており、
		// **まったく別の欠陥をその名前で報告していた**——実際に踏んだ:
		// assert が落ちたのは `textBuilder != null` の側、つまり
		// **開いていたのではなく null だった**のに、名前がそう言わないので
		// 私(と修正者)は誤った機序を追いかけた。
		// 名前の付いていない変換失敗は**発生箇所で分ける**(detailKey)。
		// 同じ文言でも別の場所で落ちていれば別の欠陥である。
		if (m.contains("白紙ページ")) {
			return "白紙ページ";
		}
		if (m.contains("内容が失われた")) {
			return "内容の消失";
		}
		if (m.contains("紙面外への配置")) {
			return "紙面外への配置";
		}
		if (m.contains("全描画が紙面外")) {
			return "全描画が紙面外";
		}
		if (m.contains("読み順が入れ替わった")) {
			return "読み順の逆転";
		}
		if (m.contains("内容が複製された")) {
			return "内容の複製";
		}
		if (m.contains("watchdogを超えたスレッドが")) {
			return "掃過が過負荷(測定不能)";
		}
		if (m.contains("watchdog超過")) {
			// **「停止しない」と言い切らない**。掃過が過負荷のときは正常な
			// 文書でも超える(2026-07-27に実証)。無限ループの証拠には
			// ならないので、種別名でそれを明示する
			return "watchdog超過(停止性は未確定)";
		}
		if (m.contains("ページ数が過大")) {
			return "ページ数過大";
		}
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		final String site = detailKey(t);
		return site != null ? site : c.getClass().getSimpleName();
	}

	/**
	 * 例外の**発生箇所**(スタックの最上位のfoliojetフレーム)まで見て
	 * 種別を分ける。同じ例外型でも別の欠陥なら別種として数えるため——
	 * ここを粗くすると「残り何件か」の推定が過小になる。
	 */
	private static String detailKey(final Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) {
			c = c.getCause();
		}
		for (final StackTraceElement e : c.getStackTrace()) {
			if (e.getClassName().startsWith("net.zamasoft.foliojet")) {
				final String cls = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1);
				return c.getClass().getSimpleName() + "@" + cls + "." + e.getMethodName() + ":" + e.getLineNumber();
			}
		}
		return null;
	}

	private static String normalizedDefectKey(final Throwable t, final int seed,
			final boolean strict) {
		Throwable deepest = t;
		while (deepest.getCause() != null) {
			deepest = deepest.getCause();
		}
		String site = "-";
		for (final StackTraceElement frame : deepest.getStackTrace()) {
			if (frame.getClassName().startsWith("net.zamasoft.foliojet")) {
				site = frame.getClassName() + "." + frame.getMethodName()
						+ ":" + frame.getLineNumber();
				break;
			}
		}
		String topology;
		try {
			topology = inspectGeneratedStructure(generate(seed, strict).html()).topologyHash();
		} catch (final Throwable unavailable) {
			topology = "unavailable";
		}
		return classify(t) + "|cause=" + deepest.getClass().getName()
				+ "|site=" + site + "|topology=" + topology;
	}

	private static void rememberSeed(
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf,
			final String key, final int seed) {
		final java.util.List<Integer> list = seedsOf.computeIfAbsent(key,
				x -> java.util.Collections.synchronizedList(new ArrayList<>()));
		synchronized (list) {
			if (list.size() < 8) {
				list.add(seed);
			}
		}
	}

	/**
	 * 集計モードの並列度({@code -Dfoliojet.fuzzThreads})。既定は
	 * 「コア数-2」。数百万文書の掃過は並列化しないと現実的な時間に
	 * 収まらない(2026-07-26に「20年間エラーなし」を目標化した際に追加)。
	 *
	 * <p>
	 * 変換は文書ごとに独立で、エンジン自身もサーバ用途で並行変換を
	 * 前提にしている(状態はThreadLocal)。1文書=1スレッドという
	 * 既存の構造をそのまま横に並べるだけで、判定内容は変えない。
	 * </p>
	 */
	private static int sweepThreads() {
		final String v = System.getProperty("foliojet.fuzzThreads");
		if (v != null) {
			return Math.max(1, Integer.parseInt(v));
		}
		return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
	}

	/**
	 * 大規模掃過では、入力ごとに発生しうる既知のWARNINGを標準エラーへ流さない。
	 * 旧ジョブは8万seedで20MBに達し、extremeは1文書の要素数が約1,000なので
	 * ログI/Oが探索を支配する。例外・オラクル違反は別経路で必ず分類される。
	 */
	private static void configureSweepLogging() {
		configureFuzzLogging(java.util.logging.Level.SEVERE);
	}

	private static void configureFuzzLogging(final java.util.logging.Level level) {
		if (System.getProperty("foliojet.fuzzVerboseLogging") == null) {
			final java.util.logging.Logger packageLogger = java.util.logging.Logger
					.getLogger("net.zamasoft.foliojet");
			packageLogger.setLevel(level);
			final java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
			for (final java.util.logging.Handler handler : root.getHandlers()) {
				handler.setLevel(level);
			}
			// 既に独自handlerを持つ子loggerも塞ぐ。以後生成されるloggerは
			// packageLoggerを継承し、root handlerでもWARNINGを捨てる。
			final java.util.logging.LogManager manager = java.util.logging.LogManager.getLogManager();
			final java.util.Enumeration<String> names = manager.getLoggerNames();
			while (names.hasMoreElements()) {
				final String name = names.nextElement();
				if (!name.startsWith("net.zamasoft.foliojet")) {
					continue;
				}
				final java.util.logging.Logger logger = manager.getLogger(name);
				logger.setLevel(level);
				for (final java.util.logging.Handler handler : logger.getHandlers()) {
					handler.setLevel(level);
				}
			}
		}
	}

	/** 集計モードの並列掃過。判定は{@link #checkOne}で共通。 */
	private void sweepParallel(final boolean strict, final int seeds) throws Exception {
		configureSweepLogging();
		final int threads = sweepThreads();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> classCount =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> defectCount =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> defectSeeds =
				new java.util.concurrent.ConcurrentHashMap<>();
		final SweepMeasurements measurements = new SweepMeasurements();
		final int from = seedFrom();
		System.out.println("[fuzzManifest] {\"generatorVersion\":" + GENERATOR_VERSION
				+ ",\"generatorProfile\":\"" + generatorProfile() + "\",\"mode\":\""
				+ (strict ? "strict" : "wild") + "\",\"from\":" + from + ",\"seeds\":" + seeds + "}");
		final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger(from + 1);
		final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
		final long began = System.currentTimeMillis();
		final java.util.function.BiConsumer<Integer, Throwable> recordFailure = (seed, t) -> {
			final String k = classify(t);
			classCount.computeIfAbsent(k, x -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
			rememberSeed(seedsOf, k, seed);
			final String normalized = normalizedDefectKey(t, seed, strict);
			defectCount.computeIfAbsent(normalized,
					x -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
			rememberSeed(defectSeeds, normalized, seed);
		};
		// 依存ライブラリの遅延初期化を並列に競わせると、冷間起動時だけ
		// DirectSessionの初期化が失敗することがある。最初の1件を直列に
		// 完了させてからワーカーを放つ(このシードも集計件数に含む)。
		if (seeds > 0) {
			try {
				checkSweepDocument(from, strict, measurements);
			} catch (final Throwable t) {
				recordFailure.accept(from, t);
			}
			done.set(1);
		}
		final Thread[] workers = new Thread[threads];
		for (int w = 0; w < threads; ++w) {
			workers[w] = new Thread(() -> {
				for (;;) {
					final int seed = next.getAndIncrement();
					if (seed >= from + seeds) {
						return;
					}
					try {
						checkSweepDocument(seed, strict, measurements);
					} catch (final Throwable t) {
						recordFailure.accept(seed, t);
					}
					final int n = done.incrementAndGet();
					if (n % PROGRESS_EVERY == 0) {
						System.out.println("[fuzzProgress] " + (strict ? "strict" : "wild") + " " + n + "/" + seeds
								+ " 経過" + ((System.currentTimeMillis() - began) / 1000) + "s "
								+ new java.util.TreeMap<>(classCount));
						try {
							writeFuzzManifest(strict, from, seeds, threads, n, false,
									classCount, seedsOf, defectCount, defectSeeds);
						} catch (final Exception e) {
							System.err.println("[fuzzProgress] manifestのcheckpointを書けない: " + e);
						}
					}
				}
			}, "fuzz-sweep-" + (strict ? "s" : "w") + w);
			workers[w].setDaemon(true);
			workers[w].start();
		}
		for (final Thread t : workers) {
			t.join();
		}
		final long ms = System.currentTimeMillis() - began;
		if (reportMode()) {
			reportFeatureCoverage(strict, seeds, from);
		}
		System.out.println("[fuzzReport] generator=" + generatorLabel() + " mode="
				+ (strict ? "strict" : "wild") + " seeds=" + seeds
				+ (from == 0 ? "" : " from=" + from) + " threads="
				+ threads + " elapsed=" + (ms / 1000) + "s (" + String.format("%.2f", ms / (double) seeds)
				+ " ms/文書)");
		if (classCount.isEmpty()) {
			System.out.println("[fuzzReport]   失敗なし");
		}
		for (final String k : new java.util.TreeSet<>(classCount.keySet())) {
			System.out.println("[fuzzReport]   " + k + " : " + classCount.get(k).get() + "件 seeds=" + seedsOf.get(k));
		}
		for (final String k : new java.util.TreeSet<>(defectCount.keySet())) {
			System.out.println("[fuzzReport]   正規化キー " + k + " : "
					+ defectCount.get(k).get() + "件 seeds=" + defectSeeds.get(k));
		}
		final String mode = strict ? "strict" : "wild";
		System.out.println("[fuzzReport] mode=" + mode + " 生成要素数分布 "
				+ measurements.elements.summary());
		System.out.println("[fuzzReport] mode=" + mode + " 出力ページ数分布 "
				+ measurements.pages.summary());
		reportLocalCoverage(strict, measurements.coverage);
		final List<Long> defects = new ArrayList<>();
		for (final var count : defectCount.values()) {
			defects.add((long) count.get());
		}
		reportDiscovery(mode, "defects", defects);
		for (final LocalScope scope : LocalScope.values()) {
			for (int t = 2; t <= 5; ++t) {
				final List<Long> occurrences = new ArrayList<>();
				for (final var entry : measurements.coverage.entrySet()) {
					if (entry.getKey().scope() == scope && entry.getKey().t() == t) {
						occurrences.add(entry.getValue().sum());
					}
				}
				reportDiscovery(mode, "coverage/" + scope.label + "/t" + t, occurrences);
			}
		}
		writeFuzzManifest(strict, from, seeds, threads, done.get(), true,
				classCount, seedsOf, defectCount, defectSeeds);
	}

	private void checkSweepDocument(final int seed, final boolean strict,
			final SweepMeasurements measurements) throws Exception {
		final Generated doc = generate(seed, strict);
		measurements.record(inspectGeneratedStructure(doc.html()));
		checkOne(seed, strict, doc, measurements.pages::add);
	}

	private static int minimumDocuments(final int t) {
		return switch (t) {
		case 2 -> 100;
		case 3 -> 50;
		case 4 -> 20;
		case 5 -> 5;
		default -> throw new IllegalArgumentException("t=" + t);
		};
	}

	private static void reportLocalCoverage(final boolean strict,
			final java.util.concurrent.ConcurrentHashMap<CoverageKey,
					java.util.concurrent.atomic.LongAdder> counts) {
		final java.util.Set<CoverageKey> reachable = reachableLocalCombos(strict);
		final String mode = strict ? "strict" : "wild";
		for (final LocalScope scope : LocalScope.values()) {
			for (int t = 2; t <= 5; ++t) {
				final int minDocuments = minimumDocuments(t);
				long denominator = 0;
				long observed = 0;
				long qualified = 0;
				for (final CoverageKey key : reachable) {
					if (key.scope() != scope || key.t() != t) {
						continue;
					}
					++denominator;
					final var count = counts.get(key);
					final long documents = count == null ? 0 : count.sum();
					if (documents > 0) {
						++observed;
					}
					if (documents >= minDocuments) {
						++qualified;
					}
				}
				long outsideSchema = 0;
				for (final CoverageKey key : counts.keySet()) {
					if (key.scope() == scope && key.t() == t && !reachable.contains(key)) {
						++outsideSchema;
					}
				}
				final String coverage = denominator == 0 ? "n/a"
						: String.format(java.util.Locale.ROOT, "%.3f%%",
								qualified * 100.0 / denominator);
				System.out.println("[fuzzReport] mode=" + mode + " 局所機能被覆 scope="
						+ scope.label + " t=" + t + " reachable=" + denominator
						+ " observed=" + observed + " qualified=" + qualified
						+ " minDocs=" + minDocuments + " coverage=" + coverage
						+ " outsideSchema=" + outsideSchema);
			}
		}
	}

	private static void reportDiscovery(final String mode, final String label,
			final List<Long> occurrences) {
		long m = 0;
		long f1 = 0;
		long f2 = 0;
		for (final long count : occurrences) {
			m += count;
			if (count == 1) {
				++f1;
			} else if (count == 2) {
				++f2;
			}
		}
		final double p0 = m == 0 ? 0 : f1 / (double) m;
		final double chao1 = occurrences.size()
				+ f1 * (f1 - 1.0) / (2.0 * (f2 + 1.0));
		System.out.println("[fuzzReport] mode=" + mode + " discovery=" + label
				+ " M=" + m + " Sobs=" + occurrences.size()
				+ " f1=" + f1 + " f2=" + f2
				+ " GoodTuring-p0="
				+ String.format(java.util.Locale.ROOT, "%.8g", p0)
				+ " Chao1="
				+ String.format(java.util.Locale.ROOT, "%.6f", chao1));
	}

	private static String buildIdentifier() throws Exception {
		final String supplied = System.getProperty("foliojet.fuzzBuild");
		if (supplied != null && !supplied.isBlank()) {
			return supplied.trim();
		}
		final MessageDigest digest = sha256();
		for (final Class<?> type : List.of(RandomDocumentFuzzTest.class, DirectSession.class)) {
			final String resource = "/" + type.getName().replace('.', '/') + ".class";
			try (java.io.InputStream in = type.getResourceAsStream(resource)) {
				if (in == null) {
					throw new IllegalStateException("build識別用classを読めない: " + resource);
				}
				final byte[] buffer = new byte[8192];
				for (int len; (len = in.read(buffer)) >= 0;) {
					digest.update(buffer, 0, len);
				}
			}
		}
		return "classes-" + java.util.HexFormat.of().formatHex(digest.digest(), 0, 12);
	}

	private static String jsonEscape(final String value) {
		final StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < value.length(); ++i) {
			final char c = value.charAt(i);
			switch (c) {
			case '"' -> escaped.append("\\\"");
			case '\\' -> escaped.append("\\\\");
			case '\b' -> escaped.append("\\b");
			case '\f' -> escaped.append("\\f");
			case '\n' -> escaped.append("\\n");
			case '\r' -> escaped.append("\\r");
			case '\t' -> escaped.append("\\t");
			default -> {
				if (c < 0x20) {
					escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
				} else {
					escaped.append(c);
				}
			}
			}
		}
		return escaped.toString();
	}

	private static synchronized void writeFuzzManifest(final boolean strict, final int from,
			final int seeds, final int threads, final int processed, final boolean completed,
			final java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger> classCount,
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf,
			final java.util.concurrent.ConcurrentHashMap<String,
					java.util.concurrent.atomic.AtomicInteger> defectCount,
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> defectSeeds)
			throws Exception {
		final String manifestProperty = System.getProperty("foliojet.fuzzManifest");
		if (manifestProperty == null || manifestProperty.isBlank()) {
			return;
		}
		final String mode = strict ? "strict" : "wild";
		final StringBuilder json = new StringBuilder();
		json.append("{\"from\":").append(from)
				.append(",\"seeds\":").append(seeds)
				.append(",\"processed\":").append(processed)
				.append(",\"completed\":").append(completed)
				.append(",\"generatorVersion\":").append(GENERATOR_VERSION)
				.append(",\"generatorProfile\":\"").append(generatorProfile())
				.append("\",\"mode\":\"").append(mode)
				.append("\",\"build\":\"").append(jsonEscape(buildIdentifier()))
				.append("\",\"threads\":").append(threads)
				.append(",\"updatedAt\":\"").append(Instant.now()).append('"');
		if (completed) {
			json.append(",\"completedAt\":\"").append(Instant.now()).append('"');
		}
		json.append(",\"classificationCounts\":{");
		boolean first = true;
		for (final String key : new java.util.TreeSet<>(classCount.keySet())) {
			if (!first) {
				json.append(',');
			}
			first = false;
			json.append('"').append(jsonEscape(key)).append("\":")
					.append(classCount.get(key).get());
		}
		json.append("},\"classificationSeeds\":");
		appendSeedMap(json, seedsOf);
		json.append(",\"normalizedDefectCounts\":{");
		first = true;
		for (final String key : new java.util.TreeSet<>(defectCount.keySet())) {
			if (!first) {
				json.append(',');
			}
			first = false;
			json.append('"').append(jsonEscape(key)).append("\":")
					.append(defectCount.get(key).get());
		}
		json.append("},\"normalizedDefectSeeds\":");
		appendSeedMap(json, defectSeeds);
		json.append("}\n");

		final Path target = Path.of(manifestProperty).toAbsolutePath();
		Files.createDirectories(target.getParent());
		final Path temporary = target.resolveSibling(target.getFileName()
				+ ".tmp-" + ProcessHandle.current().pid() + "-" + mode);
		Files.writeString(temporary, json, StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
				java.nio.file.StandardOpenOption.WRITE);
		try {
			Files.move(temporary, target,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (final java.nio.file.AtomicMoveNotSupportedException e) {
			Files.move(temporary, target,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		System.out.println("[fuzzReport] manifest=" + target);
	}

	private static void appendSeedMap(final StringBuilder json,
			final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsByKey) {
		json.append('{');
		boolean firstKey = true;
		for (final String key : new java.util.TreeSet<>(seedsByKey.keySet())) {
			if (!firstKey) {
				json.append(',');
			}
			firstKey = false;
			json.append('"').append(jsonEscape(key)).append("\":[");
			final java.util.List<Integer> values = seedsByKey.get(key);
			synchronized (values) {
				for (int i = 0; i < values.size(); ++i) {
					if (i > 0) {
						json.append(',');
					}
					json.append(values.get(i));
				}
			}
			json.append(']');
		}
		json.append('}');
	}

	/**
	 * <b>1文書あたり何機能を網羅したか</b>を測ります(2026-08-02、ユーザー
	 * 指摘)。「N文書・0失敗」は単機能の信頼度しか語らない——欠陥は機能の
	 * <b>組み合わせ</b>に居るので、指標は<b>ペア被覆率</b>(全機能ペアのうち
	 * 実際に同居したペアの割合)にする。
	 *
	 * <p>
	 * 文書の生成は決定的なので、掃過とは別に作り直して数えても同じ集合に
	 * なる(変換はしないため安い)。標本は先頭2,000シードまで。
	 * </p>
	 */
	private static void reportFeatureCoverage(final boolean strict, final int seeds, final int from) {
		final int sample = Math.min(seeds, 2000);
		final java.util.List<String> features = LOCAL_FEATURES;
		final int n = features.size();
		final java.util.Map<Integer, java.util.Set<Long>> combos = new java.util.HashMap<>();
		long featureTotal = 0;
		for (int i = 0; i < sample; ++i) {
			final String doc = generate(from + i, strict).html();
			final boolean[] has = new boolean[n];
			int count = 0;
			for (int f = 0; f < n; ++f) {
				has[f] = doc.contains(features.get(f));
				if (has[f]) {
					++count;
				}
			}
			featureTotal += count;
			final int[] present = new int[count];
			int at = 0;
			for (int f = 0; f < n; ++f) {
				if (has[f]) {
					present[at++] = f;
				}
			}
			recordCombos(present, count, combos);
		}
		final StringBuilder tways = new StringBuilder();
		for (int t = 2; t <= 5; ++t) {
			final long total = binomial(n, t);
			final java.util.Set<Long> set = combos.get(t);
			final long got = set == null ? 0 : set.size();
			if (tways.length() > 0) {
				tways.append(' ');
			}
			tways.append(t).append("組 ").append(String.format("%.1f", got * 100.0 / total)).append('%');
		}
		System.out.println("[fuzzReport] generator=" + generatorLabel() + " mode="
				+ (strict ? "strict" : "wild") + " 機能被覆: 1文書あたり平均"
				+ String.format("%.1f", featureTotal / (double) sample) + "機能 / " + n + "機能中、"
				+ tways + " (標本" + sample + "文書)");
	}

	/** {@code n}個から{@code t}個を選ぶ組の数です。 */
	private static long binomial(final int n, final int t) {
		long v = 1;
		for (int i = 0; i < t; ++i) {
			v = v * (n - i) / (i + 1);
		}
		return v;
	}

	/**
	 * 文書が持つ機能の集合から、t個の組(t=2..5)を全て列挙して記録します
	 * (2026-08-02、ユーザー指摘——組み合わせはペアで止めない)。機能は
	 * 63種以下なので、組は{@code long}のビットマスクで一意に表せる。
	 */
	private static void recordCombos(final int[] present, final int count,
			final java.util.Map<Integer, java.util.Set<Long>> combos) {
		for (int t = 2; t <= 5; ++t) {
			if (count < t) {
				break;
			}
			final int[] idx = new int[t];
			for (int i = 0; i < t; ++i) {
				idx[i] = i;
			}
			final java.util.Set<Long> set = combos.computeIfAbsent(t, k -> new java.util.HashSet<>());
			while (true) {
				long mask = 0;
				for (int i = 0; i < t; ++i) {
					mask |= 1L << present[idx[i]];
				}
				set.add(mask);
				int i = t - 1;
				while (i >= 0 && idx[i] == count - t + i) {
					--i;
				}
				if (i < 0) {
					break;
				}
				++idx[i];
				for (int j = i + 1; j < t; ++j) {
					idx[j] = idx[j - 1] + 1;
				}
			}
		}
	}

	private void sweep(final boolean strict) throws Exception {
		// **失敗したシードを自動で最小化する入口**(2026-07-28新設)。
		// 掃過は数分で終わるのに1件の診断に数時間かかるので、縮小は
		// 機械にやらせる。述語の作り方は {@link FuzzShrinker} を参照
		// ——ここを雑に書くと偽の最小形が出る(LESSONS.md §3.15)
		final String shrinkSeed = System.getProperty("foliojet.fuzzShrink");
		if (shrinkSeed != null) {
			// 同じAssertionErrorを数千候補で再現するため、DirectSessionの
			// SEVEREスタックを毎回流さない。縮小器の分類・進捗は標準出力。
			configureFuzzLogging(java.util.logging.Level.OFF);
			final String mode = System.getProperty("foliojet.fuzzShrinkMode", "strict");
			if ("both".equals(mode) || strict == "strict".equals(mode)) {
				FuzzShrinker.shrink(Integer.parseInt(shrinkSeed.trim()), strict);
			}
			return;
		}
		// **ファイルを縮小する入口**(2026-07-28新設)。手で書いた再現文書や、
		// 縮小器自身の検算(答えの分かっている文書を水増ししてから縮小させる)に使う
		final String shrinkFile = System.getProperty("foliojet.fuzzShrinkFile");
		if (shrinkFile != null) {
			if (strict) {
				FuzzShrinker.shrinkFile(new File(shrinkFile));
			}
			return;
		}
		// **任意のHTMLを同じ検査にかける入口**(2026-07-28新設)。縮小した
		// 文書が「生成器を通さない普通の経路」でも同じ種別で落ちることを
		// 確かめるために使う
		final String checkFile = System.getProperty("foliojet.fuzzCheckFile");
		if (checkFile != null) {
			if (strict) {
				FuzzShrinker.checkFile(new File(checkFile));
			}
			return;
		}
		// 修正後に既知の分類seedだけを一括再検査する入口。1seedずつGradleを
		// 起動するとコンパイル・JVM起動が支配するため、カンマ区切りを同じ
		// テストJVMで最後まで走らせ、実失敗だけをまとめて落とす。
		final String selected = System.getProperty("foliojet.fuzzOnlySeeds");
		if (selected != null) {
			final boolean v1 = System.getProperty("foliojet.fuzzV1") != null;
			final List<String> failures = new ArrayList<>();
			for (final String part : selected.split(",")) {
				final int seed = Integer.parseInt(part.trim());
				System.out.println("[fuzzOnly] generator=" + (v1 ? "1" : generatorLabel()) + " mode="
						+ (strict ? "strict" : "wild") + " seed=" + seed);
				try {
					if (v1) {
						checkOneV1(seed, strict);
					} else {
						checkOne(seed, strict);
					}
					System.out.println("[fuzzOnly]   通った");
				} catch (final ExcludedByOversizedBox | ExcludedByUntypesettableFloat excluded) {
					System.out.println("[fuzzOnly]   " + classify(excluded) + " : " + excluded);
				} catch (final Throwable t) {
					System.out.println("[fuzzOnly]   " + classify(t) + " : " + t);
					failures.add("seed " + seed + ": " + t);
				}
			}
			assertTrue(String.join("\n", failures), failures.isEmpty());
			return;
		}
		// **特定のシードだけを走らせる入口**(2026-07-27新設)。
		// 大規模な掃過では成果物を使い回して捨てるので、後から
		// 「seed 27648で内容が消えた」と分かっても再現できなかった。
		// 生成器は決定的なので、シードを指定すれば必ず同じ文書になる。
		final String only = System.getProperty("foliojet.fuzzOnlySeed");
		if (only != null) {
			final int seed = Integer.parseInt(only);
			// -Dfoliojet.fuzzV1=1 でv1分布の再現(旧掲過seedの検証用)
			final boolean v1 = System.getProperty("foliojet.fuzzV1") != null;
			System.out.println("[fuzzOnly] generator=" + (v1 ? "1" : generatorLabel()) + " mode="
					+ (strict ? "strict" : "wild") + " seed=" + seed);
			try {
				if (v1) {
					checkOneV1(seed, strict);
				} else {
					checkOne(seed, strict);
				}
				System.out.println("[fuzzOnly]   通った");
			} catch (final Throwable t) {
				System.out.println("[fuzzOnly]   " + classify(t) + " : " + t);
				// **必ず落とすこと**(2026-07-29)。以前はここで握り潰して
				// いたため、このモードは失敗しても常に exit=0・failures=0 を
				// 返した。「修正できた」と誤認する事故が実際に起きた
				// (seed 213026。掃過は落ち続けていたのに、この入口で
				// 確認して直ったと報告した)。表示だけの確認装置は嘘をつく。
				throw new AssertionError("seed " + seed + " (" + (strict ? "strict" : "wild") + ") が失敗した: " + t, t);
			}
			return;
		}
		final int seeds = seedCount();
		final boolean report = reportMode();
		if (report) {
			this.sweepParallel(strict, seeds);
			return;
		}
		final List<String> failures = new ArrayList<>();
		final List<Integer> knownStillFailing = new ArrayList<>();
		final java.util.TreeMap<String, int[]> classCount = new java.util.TreeMap<>();
		final java.util.TreeMap<String, java.util.List<Integer>> seedsOf = new java.util.TreeMap<>();
		for (int seed = seedFrom(), end = seedFrom() + seeds; seed < end; ++seed) {
			final boolean known = !report && strict
					&& (KNOWN_TRAILING_BLANK_PAGE.contains(seed) || KNOWN_INVARIANT_VIOLATION.contains(seed));
			try {
				checkOne(seed, strict);
				if (known) {
					failures.add("seed=" + seed + ": 既知の未解決だったが通った。"
							+ "KNOWN_TRAILING_BLANK_PAGE から外すこと");
				}
			} catch (final ExcludedByOversizedBox excluded) {
				// 除外。集計モードでのみ数える(sweepParallel側で拾う)
				continue;
			} catch (final Throwable t) {
				if (report) {
					final String k = classify(t);
					classCount.computeIfAbsent(k, x -> new int[1])[0]++;
					final var lst = seedsOf.computeIfAbsent(k, x -> new ArrayList<>());
					if (lst.size() < 8) {
						lst.add(seed);
					}
					continue;
				}
				if (known) {
					knownStillFailing.add(seed);
					continue;
				}
				failures.add("seed=" + seed + " (" + (strict ? "strict" : "wild") + "): " + t);
				if (failures.size() >= 5) {
					break; // 最初の数件で十分。全件走らせても情報が増えない
				}
			}
		}
		if (report) {
			System.out.println("[fuzzReport] generator=" + generatorLabel() + " mode="
					+ (strict ? "strict" : "wild") + " seeds=" + seeds);
			if (classCount.isEmpty()) {
				System.out.println("[fuzzReport]   失敗なし");
			}
			for (final var e : classCount.entrySet()) {
				System.out.println("[fuzzReport]   " + e.getKey() + " : " + e.getValue()[0] + "件 seeds="
						+ seedsOf.get(e.getKey()));
			}
			return;
		}
		if (!knownStillFailing.isEmpty()) {
			System.out.println("[fuzz] 既知の未解決(末尾の空ページ): " + knownStillFailing);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	private int checkOne(final int seed, final boolean strict) throws Exception {
		return checkOne(seed, strict, generate(seed, strict), null);
	}

	/** 歴史的seedの検査はv1の入力分布で行う(期待値がv1文書で記録済み)。 */
	private int checkOneV1(final int seed, final boolean strict) throws Exception {
		return checkOne(seed, strict, generate(seed, strict, true), null);
	}

	private int checkOne(final int seed, final boolean strict, final Generated doc,
			final java.util.function.IntConsumer pageObserver) throws Exception {
		// 長時間掃過(数百万文書)では、シードごとの成果物を残すとディスクが
		// 保たない。失敗したシードは同じシードで再実行すれば必ず再現する
		// (生成器は決定的)ので、成功したシードの成果物は捨ててよい。
		// 保存ディレクトリもシードで分けず使い回す(2026-07-26)
		// 集計モードは規模にかかわらずワーカー単位のスロットを使い回す。
		// 生成器は決定的なので、報告されたシードは単独再実行で復元できる。
		final boolean keep = System.getProperty("foliojet.fuzzOnlySeed") != null || !reportMode();
		final String slot = keep ? String.valueOf(seed) : Thread.currentThread().getName();
		final File html = new File(workDir(), (strict ? "strict" : "wild") + "-" + slot + ".html");
		final File outDir = new File(workDir(), "dl-" + (strict ? "strict" : "wild") + "-" + slot);
		return checkDocument(doc, html, outDir, strict, "fuzz-" + seed, pageObserver);
	}

	/**
	 * <b>生成済みの文書</b>を1件検査する(2026-07-28に{@code checkOne}から分離)。
	 *
	 * <p>
	 * 分離したのは{@link FuzzShrinker}の<b>述語</b>として使うため。縮小器が
	 * 検査するのは<b>生成器が作った文書ではない</b>ので、{@link Generated}を
	 * 外から渡せなければならない——ここを{@code seed}から作り直す設計にすると、
	 * 縮小後の文書に<b>元の文書のトークン表</b>を当ててしまい、「削ったから
	 * 消えた」を「内容の消失」と誤判定する(`LESSONS.md` §3.15 の6例目に
	 * なるところだった)。
	 * </p>
	 */
	static int checkDocument(final Generated doc, final File html, final File outDir, final boolean strict,
			final String workerName) throws Exception {
		return checkDocument(doc, html, outDir, strict, workerName, null);
	}

	private static int checkDocument(final Generated doc, final File html, final File outDir,
			final boolean strict, final String workerName,
			final java.util.function.IntConsumer pageObserver) throws Exception {
		final int pageLimit = pageLimit(doc);
		html.getParentFile().mkdirs();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
			w.write(doc.html);
		}

		outDir.mkdirs();
		final File[] old = outDir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}

		final Throwable[] failure = new Throwable[1];
		final DirectSession[] session = new DirectSession[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(html, outDir, session, pageLimit);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, workerName);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		// 不変条件2: 停止する
		if (worker.isAlive()) {
			// **まず実際に止めにいく**(2026-07-27)。エンジンに協調的な
			// 中断点(`UserAgent.checkAbort`)を入れたので、ページの途中でも
			// 止まる。放置すると**レイアウト1件分のヒープと64MBのスタック
			// 予約を抱えたまま**残り、掃過が自己増幅的に詰まる。
			final DirectSession s = session[0];
			if (s != null) {
				try {
					s.abort(jp.cssj.cti2.CTISession.ABORT_FORCE);
					worker.join(5_000L);
				} catch (final Exception ignore) {
					// 中断要求が通らなくても以下の封じ込めへ進む
				}
			}
		}
		if (worker.isAlive()) {
			// 中断要求を出しても止まらなかった。Thread.stop()は現代のJavaでは
			// 使えないので、ここから先は**数えて封じ込める**しかない。
			//
			// これは自己増幅する(2026-07-27に10万文書の掃過が7時間停止して
			// 発覚): ヒープ逼迫 → GCが回り続けて全体が遅くなる → watchdogを
			// 超える文書が増える → さらに漏れる。**2万シードでは0件、
			// 5万シードでは頻発**という、規模に依存した測定になっていた。
			//
			// 止められない以上、せめて(a)優先度を落として掃過の足を
			// 引っ張らせない (b)一定数を超えたら掃過ごと止める。
			// **黙って遅くなるより、大きな音を立てて止まるほうがよい。**
			try {
				worker.setPriority(Thread.MIN_PRIORITY);
			} catch (final RuntimeException ignore) {
				// 優先度を下げられなくても続行する
			}
			final int leaked = LEAKED_WORKERS.incrementAndGet();
			if (leaked > maxLeakedWorkers()) {
				throw new AssertionError("watchdogを超えたスレッドが" + leaked + "本たまった。"
						+ "掃過が過負荷になっており、以後の測定は信用できない"
						+ "(-Dfoliojet.fuzzThreads を減らすか -PtestHeap を増やすこと)。最後の文書: " + html);
			}
			fail("watchdog超過 " + (WATCHDOG_MS / 1000) + "秒 (" + html + ")");
		}
		// 不変条件1: 例外で中断しない
		if (failure[0] != null) {
			throw new AssertionError("変換が例外で終わった (" + html + ")", failure[0]);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("ページが1枚も出ていない (" + html + ")", pages);
		assertTrue("ページが1枚も出ていない (" + html + ")", pages.length > 0);
		// 不変条件3: ページ数が有界
		assertTrue("ページ数が過大 " + pages.length + " (上限" + pageLimit + ", " + html + ")",
				pages.length <= pageLimit);
		if (pageObserver != null) {
			pageObserver.accept(pages.length);
		}

		// **WILDはここまで**(2026-07-28)。不変条件4〜8はSTRICT限定なので、
		// 以下の読み込み・解析はWILDでは結果を一切使わない——従来は全ページを
		// 読んで解析してから捨てていた(早期returnは解析の**後**にあった)。
		// 実測では誤差程度の差しか出なかったが、捨てる仕事を残す理由もない
		if (!strict) {
			return pages.length;
		}

		java.util.Arrays.sort(pages);
		final List<ObservedText> observedText = new ArrayList<>();
		// トークンが**最初に現れたページ**(不変条件7)。ページ内の描画順は
		// 実装の都合(rowspanのセルは跨ぐ行が確定してから描かれる)なので、
		// ページ粒度でしか順序を問えない
		final java.util.Map<String, Integer> firstPage = new java.util.HashMap<String, Integer>();
		// トークンごとの「1ページ内での最大描画回数」(不変条件8)
		final java.util.Map<String, int[]> drawn = new java.util.HashMap<String, int[]>();
		final List<Integer> blanks = new ArrayList<>();
		for (int i = 0; i < pages.length; ++i) {
			final String dump = Files.readString(Path.of(pages[i].toURI()), StandardCharsets.UTF_8);
			boolean drew = false;
			for (final String line : dump.split("\n")) {
				final String t = line.trim();
				if (!t.isEmpty() && !t.startsWith("drawer")) {
					drew = true;
				}
			}
			if (!drew) {
				blanks.add(i + 1);
			}
			// **同一ページ内**で数える。字がページ境界を物理的に跨ぐと、
			// 同じ字が前後のページに分けて描かれる——これは正当なので、
			// ページを跨いだ合計で数えると誤検出になる(50,000文書で603件)
			final java.util.Map<String, int[]> onThisPage = new java.util.HashMap<String, int[]>();
			for (final String raw : dump.split("\n")) {
				final Matcher m = TEXT_IN_DUMP.matcher(raw);
				// **artifact 印の描画は数えない**(不変条件8、2026-07-28)。
				// タグ付きPDFの artifact は「論理構造に属さない描画」で、
				// 救済分割などでエンジンが**意図的に**重複させたものである。
				// これを複製として数えると50,000文書中7,227件(14.5%)が
				// 誤検出になった(§12の素朴な「紙面内」11.9%と同型)。
				final boolean artifact = raw.contains(" artifact ");
				while (m.find()) {
					observedText.add(new ObservedText(m.group(1), i, artifact));
					if (!artifact) {
						countTokens(m.group(1), onThisPage);
					}
					if (m.group(2) != null) {
						// ルビのふりがな側
						observedText.add(new ObservedText(m.group(2), i, artifact));
						if (!artifact) {
							countTokens(m.group(2), onThisPage);
						}
					}
				}
			}
			for (final var e : onThisPage.entrySet()) {
				final int[] max = drawn.computeIfAbsent(e.getKey(), x -> new int[1]);
				max[0] = Math.max(max[0], e.getValue()[0]);
			}
		}

		// 不変条件5: 意図しない白紙ページがない
		//
		// **紙面に収まらない箱を含む文書は除外する**(2026-07-26のユーザー裁定)。
		// エンジンがどう振る舞っても版面は破綻しており——はみ出させるか、
		// 次ページへ送るか——寸法を直すのは組版を指定した側の責任である。
		// 除外は「見なかったことにする」ではなく**別の種別として数える**:
		// 除外が増えたことに気づけなくなると、本当の退行を見落とす。
		if (!blanks.isEmpty()) {
			if (doc.beyondEngineControl()) {
				throw new ExcludedByOversizedBox("白紙ページ " + blanks + " (" + html + ")");
			}
			if (hasUntypesettableFloat(doc.html())) {
				throw new ExcludedByUntypesettableFloat(
						"白紙ページ " + blanks + " (" + html + ") [組版できない幅の浮動体]");
			}
			fail("白紙ページ " + blanks + " (" + html + ")");
		}
		// 不変条件4: 内容が失われない
		final List<String> lost = new ArrayList<>();
		final Set<String> expectedTokens = Set.copyOf(doc.tokens);
		for (final String token : doc.tokens) {
			final int page = firstObservedTokenPage(token, observedText, expectedTokens);
			if (page < 0) {
				lost.add(token);
			} else {
				firstPage.put(token, page);
			}
		}
		assertTrue("内容が失われた " + lost + " (" + html + ")", lost.isEmpty());
		// 不変条件9: PDFの宛先(id断片)が失われない
		checkFragments(doc, outDir, html);
		// 不変条件8: 内容が複製されない(まだ報告のみ)
		checkNoDuplication(doc, drawn, html);
		// 不変条件6: 説明のつかない紙面外への配置がない
		assertNoUnexplainedOffPage(doc, pages, html);
		// 不変条件10: 少なくとも1つの本文描画が実際の紙面と交差する
		assertSomeDrawingOnPage(doc, pages, html);
		// 不変条件7: 読み順が保たれる(まだ報告のみ)
		checkReadingOrder(doc, firstPage, html);
		return pages.length;
	}

	/**
	 * <b>不変条件9: PDFの宛先(id断片)が失われない</b>(2026-08-03新設)。
	 *
	 * <p>
	 * 生成器は段落に{@code id="pN"}を振る({@code id}属性は版面に影響しない
	 * ——生成する文書はidセレクタを使わないので、既存のシードの結果は
	 * 変わらない)。名前付き宛先は{@code output.pdf.hyperlinks.fragment}の
	 * 既定onで出るので、出力PDFには同じ名前の宛先が並ぶはずである。
	 * <b>仮に組んだページを捨てる経路</b>で
	 * 宛先の登録が取り消されないと、捨てたページの中で完結していた要素の
	 * 宛先が失われる——表示リストには宛先が出てこないため、既存の検出器は
	 * どれも素通りする(PLAN §3で「検出器未実装」としていた穴)。
	 * </p>
	 *
	 * <p>
	 * 宛先は描画命令ではないので、ここだけは<b>出力PDFを実際に読む</b>
	 * (PDFBox)。読めない場合は検査を飛ばす——PDFの健全性は他の検査の
	 * 担当で、ここで二重に落とす意味がない。
	 * </p>
	 */
	private static void checkFragments(final Generated doc, final File outDir, final File html) throws Exception {
		final java.util.Set<String> expected = new java.util.LinkedHashSet<>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("id=\"(p[0-9]+)\"").matcher(doc.html());
		while (m.find()) {
			expected.add(m.group(1));
		}
		if (System.getProperty("foliojet.debug.noFragments") != null) {
			// 検出器自身の検算用。この名前の宛先は決して出力されないので、
			// これを付けて落ちなければ**検出器が空振りしている**と分かる
			expected.add("p-not-emitted");
		}
		if (expected.isEmpty()) {
			return;
		}
		final File pdf = new File(outDir, "out.pdf");
		if (System.getProperty("foliojet.debug.fragTrace") != null) {
			System.err.println("[frag] 期待=" + expected.size() + " pdf=" + pdf + " 存在=" + pdf.isFile());
		}
		if (!pdf.isFile() || pdf.length() == 0) {
			return;
		}
		final java.util.Set<String> found = new java.util.LinkedHashSet<>();
		try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			final org.apache.pdfbox.pdmodel.PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
			if (names == null || names.getDests() == null) {
				// 宛先が1つも無い = 全部失われている
				fail("PDFの宛先が1つも無い(期待 " + expected.size() + " 件): " + html);
				return;
			}
			collectDestinationNamesRaw(names.getDests(), found);
		} catch (final java.io.IOException e) {
			// PDFとして読めない場合はここでは問わない
			return;
		}
		final java.util.List<String> lost = new ArrayList<>();
		for (final String id : expected) {
			if (!found.contains(id)) {
				lost.add(id);
			}
		}
		assertTrue("PDFの宛先が失われた " + lost + " / 期待" + expected.size() + "件 (" + html + ")", lost.isEmpty());
	}

	/** 宛先の名前ツリーを再帰的に集めます。 */
	private static void collectDestinationNamesRaw(final org.apache.pdfbox.pdmodel.common.PDNameTreeNode<?> node,
			final java.util.Set<String> out) throws java.io.IOException {
		if (node.getNames() != null) {
			out.addAll(node.getNames().keySet());
		}
		if (node.getKids() != null) {
			for (final org.apache.pdfbox.pdmodel.common.PDNameTreeNode<?> kid : node.getKids()) {
				collectDestinationNamesRaw(kid, out);
			}
		}
	}

	/**
	 * トークンが最初に描かれたページを返します。単一の文字実行内だけでなく、
	 * {@code overflow-wrap:anywhere}等で複数の実行・ページへ分かれた場合も復元します。
	 * floatやマーカーの描画順が分割片の間へ割り込むことがあるため、artifact、ordered listの数字マーカー、
	 * 「別の完全なfuzzトークン」だけは飛ばします。普通の本文を越えた接続は、実損失を隠すので認めません。
	 */
	private static int firstObservedTokenPage(final String token, final List<ObservedText> runs,
			final Set<String> expectedTokens) {
		// 完全なトークンがどこかにあるなら、別トークンの短いprefixと数字を
		// 繋いでより早いページを捏造しない(seed 5776のT1+1 => T11)。
		for (final ObservedText observed : runs) {
			if (!observed.artifact() && containsWholeToken(observed.text(), token)) {
				return observed.page();
			}
		}
		int bestPage = -1;
		int bestSpan = Integer.MAX_VALUE;
		int bestSkipped = Integer.MAX_VALUE;
		for (int i = 0; i < runs.size(); ++i) {
			if (runs.get(i).artifact()) {
				continue;
			}
			final String first = runs.get(i).text();
			for (int split = 1; split < token.length(); ++split) {
				if (!first.endsWith(token.substring(0, split))) {
					continue;
				}
				int matched = split;
				int skipped = 0;
				int lastPage = runs.get(i).page();
				for (int j = i + 1; j < runs.size() && matched < token.length(); ++j) {
					final ObservedText observed = runs.get(j);
					final String next = observed.text();
					// 「T9」とordered-listの「1.」を接続してT91と誤認しない。
					// マーカーはトークン断片ではなく、割り込みとして必ず飛ばす。
					if (ORDERED_LIST_MARKER.matcher(next).matches()) {
						++skipped;
						continue;
					}
					final int take = Math.min(next.length(), token.length() - matched);
					if (take == 0 || !next.regionMatches(0, token, matched, take)) {
						if (observed.artifact() || isIgnorableFormattingText(next) || isGeneratedControlText(next)
								|| containsOnlyExpectedTokens(next, expectedTokens)) {
							++skipped;
							continue;
						}
						break;
					}
					matched += take;
					lastPage = observed.page();
					if (matched == token.length()) {
						final int page = runs.get(i).page();
						final int span = lastPage - page;
						if (span < bestSpan || span == bestSpan && skipped < bestSkipped
								|| span == bestSpan && skipped == bestSkipped && (bestPage < 0 || page < bestPage)) {
							bestPage = page;
							bestSpan = span;
							bestSkipped = skipped;
						}
						break;
					}
					if (take < next.length()) {
						break;
					}
				}
			}
		}
		return bestPage;
	}

	/** {@code T57}を{@code T575}の部分文字列として数えない完全一致検索。 */
	private static boolean containsWholeToken(final String run, final String token) {
		int from = 0;
		while (from <= run.length() - token.length()) {
			final int at = run.indexOf(token, from);
			if (at < 0) {
				return false;
			}
			final int end = at + token.length();
			final boolean startsClean = at == 0 || !Character.isLetterOrDigit(run.charAt(at - 1));
			final boolean endsClean = end == run.length() || !Character.isDigit(run.charAt(end));
			if (startsClean && endsClean) {
				return true;
			}
			from = at + 1;
		}
		return false;
	}

	/** 描画順に割り込んだ、別の完全な fuzz トークンだけの実行か。 */
	private static boolean containsOnlyExpectedTokens(final String run, final Set<String> expectedTokens) {
		final Matcher matcher = TOKEN.matcher(run);
		int end = 0;
		boolean found = false;
		while (matcher.find()) {
			if (!run.substring(end, matcher.start()).isBlank() || !expectedTokens.contains(matcher.group())) {
				return false;
			}
			found = true;
			end = matcher.end();
		}
		return found && run.substring(end).isBlank();
	}

	/**
	 * トークン断片の間に描かれても文字内容を持たない空白・Unicode書式文字か。
	 * 空のフォーム部品はゼロ幅スペース(U+200B)を描画することがあり、
	 * ページ境界で分かれた{@code "T" + "417"}の間へ入っても内容の消失では
	 * ない(extreme strict seed 3797)。普通の本文文字は決して飛ばさない。
	 */
	private static boolean isIgnorableFormattingText(final String run) {
		return run.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
				|| Character.getType(c) == Character.FORMAT);
	}

	/** トークン断片の間へ割り込む、生成器固有のフォーム表示か。 */
	private static boolean isGeneratedControlText(final String run) {
		final StringBuilder visible = new StringBuilder(run.length());
		run.codePoints().filter(c -> Character.getType(c) != Character.FORMAT).forEach(visible::appendCodePoint);
		return GENERATED_CONTROL_TEXT.contains(visible.toString().strip());
	}

	/**
	 * 表示リストの1つのテキスト実行からトークンを数えます。
	 *
	 * <p>
	 * 実行は{@code Text["T0 T1 T2"]}のように<b>複数のトークンを含みうる</b>
	 * ので、空白で割ってから数えます。部分一致で数えてはいけません——
	 * {@code T1}は{@code T10}の部分文字列です(不変条件4の
	 * {@code contains}はこの弱さを持っており、ここでは繰り返さない)。
	 * </p>
	 */
	private static void countTokens(final String run, final java.util.Map<String, int[]> drawn) {
		for (final String piece : run.split("[ \t]+")) {
			if (TOKEN.matcher(piece).matches()) {
				drawn.computeIfAbsent(piece, x -> new int[1])[0]++;
			}
		}
	}

	/** 生成器が埋めるトークンの形。 */
	private static final Pattern TOKEN = Pattern.compile("T[0-9]+");

	/**
	 * <b>不変条件8(まだ報告のみ)</b>: 内容が複製されないこと
	 * (2026-07-28新設)。
	 *
	 * <p>
	 * <b>埋める穴。</b> 既存の不変条件はどれも「同じ内容が2回描かれる」ことを
	 * 捕まえません——内容は失われず(4合格)、白紙もなく(5合格)、紙面内
	 * (6合格)だからです。seed 118665 の最小形では入れ子段組が同じソース範囲を
	 * 二重に再生し、ページ2に{@code T0, T2, T3, T4, T3, T4}——
	 * <b>T3/T4が別の段へ二重に</b>——が出ていました。不変条件7(読み順)が
	 * <b>偶然</b>引っかかっただけで、複製そのものは誰も見ていませんでした。
	 * </p>
	 *
	 * <p>
	 * <b>帳票では逆転より重い。</b> 金額の行が2回出る出力は、
	 * 黙って壊れた出力の中でも最悪の部類です。
	 * </p>
	 *
	 * <p>
	 * 生成器のトークンは一意なので、<b>正しい出力では各トークンが1ページ内で
	 * ちょうど1回</b>描かれます。表ヘッダの繰り返し({@code thead})を生成する
	 * ようにしたら、そのトークンはここから除外すること——繰り返しは正当です。
	 * </p>
	 *
	 * <p>
	 * <b>定式化を2回やり直した</b>(§6.9j)。50,000文書での誤検出:
	 * </p>
	 *
	 * <ol>
	 * <li>素朴に「全ページ合計で2回以上」→ <b>7,227件(14.5%)</b>。
	 * {@code artifact}印の描画を数えていた——タグ付きPDFの artifact は
	 * 「論理構造に属さない描画」で、救済分割などでエンジンが<b>意図的に</b>
	 * 重複させたものである</li>
	 * <li>artifactを除いて合計で数える→ <b>603件(1.2%)</b>。字がページ境界を
	 * <b>物理的に跨ぐ</b>と同じ字が前後のページに分けて描かれる。これは正当</li>
	 * <li><b>同一ページ内</b>で数える→ <b>160件(0.32%)</b>。標本を確認した
	 * ところ真陽性で、機序は<b>入れ子段組の二重再生</b>だった
	 * (最小形: {@code column-count:2}の中の{@code column-count:2})</li>
	 * </ol>
	 */
	private static void checkNoDuplication(final Generated doc, final java.util.Map<String, int[]> drawn,
			final File html) {
		final List<String> dup = new ArrayList<String>();
		for (final String token : doc.tokens()) {
			final int[] n = drawn.get(token);
			if (n != null && n[0] > 1) {
				dup.add(token + "×" + n[0]);
			}
		}
		if (!dup.isEmpty()) {
			throw new AssertionError("内容が複製された " + dup + " (" + html + ")");
		}
	}

	/**
	 * <b>不変条件7(まだ報告のみ)</b>: 内容の読み順が保たれること
	 * (2026-07-27新設)。
	 *
	 * <p>
	 * <b>埋める穴。</b> 不変条件4はトークンが<b>どこかに</b>現れるかしか
	 * 見ないので、{@code T5}が{@code T3}より前に描かれても通ります。
	 * 帳票で表の行が入れ替わったら致命的ですが、この壊れ方を検出する手段が
	 * 現在ひとつもありません。不変条件6が「現れるが<b>場所</b>が異常」を
	 * 埋めたのと同じ構図で、ここは「現れるが<b>順序</b>が異常」を埋めます。
	 * </p>
	 *
	 * <p>
	 * <b>素朴な定義は使えません。</b> フロートは後続の宣言が前の行の横へ
	 * 持ち上がり、絶対配置はどこへでも置けるので、全トークンで順序を
	 * 要求すると正当な文書が軒並み落ちます(§12で素朴な「紙面内」が
	 * 3000文書中356文書=11.9%を誤検出したのと同型)。そこで<b>生成器に
	 * 記録させ</b>、フロート・絶対配置の部分木を除きます。段組・表・改ページは
	 * 内容を順に流すだけなので対象に残します。
	 * </p>
	 *
	 * <p>
	 * <b>再出現は初出だけを見ます。</b> {@code seen}は描画順の
	 * {@code LinkedHashSet}なので、同じトークンが複数ページに出ても
	 * (将来{@code thead}の繰り返しを生成する場合など)最初の1回で判定します。
	 * </p>
	 *
	 * <p>
	 * <b>固定する前に測りました</b>(§6.9j)。報告のみのモードで20,000文書を
	 * 掃過して<b>12件</b>——狙いの帯です。5件を個別に確認したところ
	 * <b>例外なく単一の機序</b>で、いずれも真陽性でした。他の種別の件数は
	 * 導入前と完全に一致しており、生成器の乱数列を乱していません。
	 * </p>
	 *
	 * <p>
	 * <b>最初の定式化は捨てました</b>(1回目)。描画順で比べたところ
	 * seed 0 で即発火し、原因は{@code rowspan}のセルが「跨ぐ行が確定してから
	 * 描かれる」ことでした——<b>表示リストは描画順であって読み順ではない</b>。
	 * ページ粒度に変えたのが現行(2回目)です。
	 * </p>
	 *
	 * <p>
	 * <b>見つけた欠陥</b>: 分割された{@code rowspan}セルの内容が、先頭の断片
	 * ではなく<b>継続断片</b>に描かれる。seed 130では同じ行が2ページに出て、
	 * 前ページが{@code [ ][ ][T12]}、次ページが{@code [T10][T11][ ]}になった
	 * ——枠は両方にあるのに文字が片方にしかない。既存の検出器は全て素通り
	 * する(トークンは現れる・白紙でない・紙面内)。
	 * </p>
	 */
	private static void checkReadingOrder(final Generated doc, final java.util.Map<String, Integer> firstPage,
			final File html) {
		// 表のセルは並列フロー(2026-08-23): 分割された表では各セルの内容が
		// 同一切断線で独立にkept/移送へ分かれるため、セル間・(縦書き表では)
		// 行間のページ前後は正当。v1生成器はセル=1トークンでほぼ顕在化
		// しなかったが、v2の複雑セルで多発した(seed 30の実測: 幅8emの
		// セルの内容が次ページ、隣の小さいセルが前ページ——Chromeと同じ
		// 正しい組版)。そこで:
		// (a) 全体走査は「最外の表に属するトークンのページ」を表全体の
		//     maxページへ畳んで単調性を見る(表の後の内容が表の途中の
		//     ページへ遡らないことは引き続き検査される)
		// (b) 同一セル内のトークン列はページ単調でなければならない
		// この緩和で「rowspanセルの内容が継続断片側に描かれる」(seed 130)
		// 型のセル間比較による検出は失われる——別の検出器が要る場合は
		// 断片ごとのセル内容の有無を直接見ること
		final java.util.Map<String, int[]> tokenTable = tokenTableAndCell(doc.html());
		final java.util.Map<Integer, Integer> tableMaxPage = new java.util.HashMap<>();
		for (final String t : doc.orderedTokens()) {
			final Integer at = firstPage.get(t);
			final int[] tc = tokenTable.get(t);
			if (at == null || tc == null) {
				continue;
			}
			tableMaxPage.merge(tc[0], at, Integer::max);
		}
		int prev = -1;
		String prevToken = null;
		final java.util.Map<Integer, Integer> cellPrev = new java.util.HashMap<>();
		for (final String t : doc.orderedTokens()) {
			final Integer at = firstPage.get(t);
			if (at == null) {
				continue; // 消失は不変条件4の担当
			}
			final int[] tc = tokenTable.get(t);
			final int effective = tc != null ? tableMaxPage.get(tc[0]).intValue() : at.intValue();
			if (effective < prev) {
				throw new AssertionError("読み順が入れ替わった: 文書順では" + prevToken + "→" + t + " だが、" + t
						+ "はページ" + (effective + 1) + "、" + prevToken + "はページ" + (prev + 1) + " (" + html + ")");
			}
			prev = effective;
			prevToken = t;
			if (tc != null) {
				// (b) セル内の単調性
				final Integer cp = cellPrev.get(tc[1]);
				if (cp != null && at.intValue() < cp.intValue()) {
					throw new AssertionError("読み順が入れ替わった(セル内): " + t + "はページ" + (at.intValue() + 1)
							+ "だが、同セルの先行トークンはページ" + (cp.intValue() + 1) + " (" + html + ")");
				}
				cellPrev.put(tc[1], at);
			}
		}
	}

	/**
	 * 各トークンの[最外の表id, セルid]です(表の外のトークンは含まない)。
	 * 構造はHTMLの簡易パース({@link FuzzShrinker#parseBody})から導く——
	 * 生成器と縮小器の両方の出力に対して同じ真実源になる。
	 */
	private static java.util.Map<String, int[]> tokenTableAndCell(final String html) {
		final java.util.Map<String, int[]> result = new java.util.HashMap<>();
		final int[] tableSeq = { 0 };
		final int[] cellSeq = { 0 };
		collectTableTokens(FuzzShrinker.parseBody(html), -1, -1, result, tableSeq, cellSeq);
		return result;
	}

	private static void collectTableTokens(final java.util.List<FuzzShrinker.Node> nodes, final int tableId,
			final int cellId, final java.util.Map<String, int[]> result, final int[] tableSeq, final int[] cellSeq) {
		for (final FuzzShrinker.Node n : nodes) {
			if (n.tag == null) {
				if (tableId >= 0 && n.text != null) {
					final java.util.regex.Matcher m = java.util.regex.Pattern.compile("T\\d+").matcher(n.text);
					// 直下の裸テキスト(匿名item相当)はそれ自身で1枝
					final int branch = m.find() ? cellSeq[0]++ : cellId;
					m.reset();
					while (m.find()) {
						result.put(m.group(), new int[] { tableId, branch });
					}
				}
				continue;
			}
			int nextTable = tableId;
			int nextCell = cellId;
			final String style = n.attr("style");
			final boolean parallelContainer = "table".equals(n.tag)
					|| (style != null && (style.contains("display:flex") || style.contains("display:grid")));
			if (parallelContainer && tableId < 0) {
				// 最外の並列コンテナ(表・flex・grid)だけを単位にする
				// (入れ子は外の並列性に包含)
				nextTable = tableSeq[0]++;
			}
			if ("td".equals(n.tag) || "th".equals(n.tag)) {
				// セルごとに新しい枝(入れ子は最内のセル単位)
				nextCell = cellSeq[0]++;
			} else if (tableId >= 0 && cellId < 0) {
				// 並列コンテナ直下の子要素(flex/gridのitem、表のtbody/tr等の
				// 中間要素は後でtdが上書き)ごとに枝を切る
				nextCell = cellSeq[0]++;
			}
			collectTableTokens(n.children, nextTable, nextCell, result, tableSeq, cellSeq);
		}
	}

	/**
	 * <b>不変条件6</b>: 内容が、作者の指定では説明できないほど紙面の外へ
	 * 出ていないこと(2026-07-26新設)。
	 *
	 * <p>
	 * <b>なぜ「紙面内」では駄目か。</b>CSSの{@code overflow}の既定値は
	 * {@code visible}で、<b>箱からはみ出した内容を紙の外に描くのは正しい
	 * 挙動</b>である。生成器は60×60ptの紙に250ptの箱を置くような病的な
	 * 文書を作るので、素朴に「紙面内」を要求すると3000文書中356文書
	 * (11.9%)が引っかかり、そのほとんどが正当だった。
	 * </p>
	 *
	 * <p>
	 * <b>そこで「作者が指定した大きさで説明できるか」で切る。</b> はみ出し量が
	 * 文書中の最大の明示サイズの2倍以内なら、指定の帰結として説明できる
	 * ものとして見逃す。この基準で切ると<b>横書きは1件も残らず、縦書きだけ
	 * 19件残った</b>(3000文書)。詳細は
	 * `copperpdf4/docs/REVIEW-STATISTICS.md` §12。
	 * </p>
	 *
	 * <p>
	 * <b>【訂正 2026-07-28】この非対称から「原因は実装側の縦書き固有の
	 * 欠陥」と推論したのは誤りだった。生成器の語彙が作った偽の信号である。</b>
	 * 下の case 2 を見れば分かるとおり、生成器はフロートに<b>{@code width}
	 * しか指定しない</b>。縦書きでは{@code width}がページ軸(欠陥が出る)、
	 * 横書きでは行軸(出ない)——非対称は書字方向ではなく<b>どちらの軸に
	 * 明示寸法が乗るか</b>で決まっていた。実際、横書きで
	 * {@code float:right;height:0pt}(=ページ軸を明示)にすると同一に再現する。
	 * </p>
	 *
	 * <p>
	 * <b>教訓</b>: 生成器が特定の軸・特定の属性しか出さないとき、その偏りは
	 * 掃過の統計に<b>実装の性質に見える偏り</b>として現れる。
	 * 「A では出るが B では出ない」を実装の証拠として使う前に、
	 * <b>生成器が A と B を対称に作っているか</b>を確かめること。
	 * </p>
	 *
	 * <p>
	 * 検査4「内容が失われない」はトークンが表示リストに<b>現れるか</b>しか
	 * 見ないので、紙面外に描かれた内容を合格させる。ここはその穴を埋める。
	 * </p>
	 */
	private static void assertNoUnexplainedOffPage(final Generated doc, final File[] pages, final File html)
			throws Exception {
		final double intrinsicControlSize = defaultTextControlWidth(doc.html());
		final double slack = 2 * Math.max(doc.maxExplicitSize(), intrinsicControlSize);
		double worst = 0;
		String worstAt = null;
		boolean worstIsY = false;
		for (final File page : pages) {
			final String dump = Files.readString(Path.of(page.toURI()), StandardCharsets.UTF_8);
			for (final String raw : dump.split("\n")) {
				// **artifact 印の描画は数えない**(2026-07-29、不変条件8が
				// 2026-07-28に同じ理由で入れたものと同型)。
				//
				// 紙面より大きい不可分な箱は、各ページが**同じ箱を平行移動
				// して**描くことで表現される——3ページに跨る箱なら、
				// 3ページ目の原点は2ページ分**上**(縦書きなら左)にある。
				// その座標は「紙面外への配置」ではなく、**箱が跨いでいる
				// ことの正しい表現**である。
				//
				// 実測(seed 422410、最小形611バイト): 121.72ptの表が60ptの
				// 紙に3ページで描かれ、2ページ目が y=-60、3ページ目が
				// y=-120。どちらも artifact 印つきで、これを数えると
				// 「紙面外60pt」と報告される。
				if (raw.contains(" artifact ")) {
					continue;
				}
				final Matcher m = DRAWING_GEOMETRY_IN_DUMP.matcher(raw);
				while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				final double width = Double.parseDouble(m.group(3)), height = Double.parseDouble(m.group(4));
				// 紙面をまるごと1枚分はみ出して初めて数える(端の1ptは論外に
				// してよいが、そこを厳しくすると罫線の丸めで揺れる)。原点だけ
				// ではなく外接矩形の近い辺で測る。seed 473636の入力欄は
				// x=-70でも幅124ptあり、60pt紙面へ54pt分掛かっていた。
				final double overX = distanceBeyondWholePage(x, width, doc.pageWidth());
				final double overY = distanceBeyondWholePage(y, height, doc.pageHeight());
				final double over = Math.max(overX, overY);
				if (over > worst) {
					worst = over;
					worstIsY = overY >= overX;
					worstAt = "x=" + x + " y=" + y + " w=" + width + " h=" + height + " "
							+ page.getName();
				}
				}
			}
		}
		if (worst <= slack) {
			return;
		}
		final String detail = "紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(doc.pageWidth()) + "x"
				+ Math.round(doc.pageHeight()) + "pt, 最大指定/UA既定サイズ"
				+ Math.round(Math.max(doc.maxExplicitSize(), intrinsicControlSize)) + "pt, " + worstAt
				+ ") (" + html + ")";
		// 白紙ページと同じ除外基準を適用する(2026-07-26)。版面が破綻して
		// いる文書ではエンジンの振る舞いを問えない
		if (doc.beyondEngineControl()) {
			throw new ExcludedByOversizedBox(detail);
		}
		// 複数条件に該当するときも、より狭い同軸逆進行の分類を安定して残す。
		if (hasUntypesettableOppositeProgression(doc.html())) {
			throw new ExcludedByUntypesettableOppositeProgression(detail + " [同軸逆進行フローの組版不能幅]");
		}
		if (hasUntypesettableOrthogonalFlow(doc.html())) {
			throw new ExcludedByUntypesettableOrthogonalFlow(detail + " [直交フローの組版不能幅]");
		}
		// 組版できない幅の浮動体からの溢れも除外(2026-07-29)。
		// {@link ExcludedByUntypesettableFloat}に理由を書いた
		if (hasUntypesettableFloat(doc.html())) {
			throw new ExcludedByUntypesettableFloat(detail + " [組版できない幅の浮動体]");
		}
		if (hasFlexMulticolTable(doc.html())) {
			throw new ExcludedByFlexMulticolMinContent(detail + " [flex内の段組表によるmin-content幅]");
		}
		// 直交フローが親の**行軸**へ溢れた場合も除外(2026-07-28のユーザー
		// 裁定。{@link ExcludedByOrthogonalLineAxis}に理由を書いた)。
		// **ページ軸への溢れは除外しない**——そちらは改ページで直せるので、
		// 直らなければエンジンの欠陥である。この区別を落とすと、
		// 直交フローを含む文書のはみ出しを何でも見逃すことになる
		final boolean overflowInLineAxis = worstIsY != pageAxisIsY(doc.html());
		if (overflowInLineAxis && hasOrthogonalFlow(doc.html())) {
			throw new ExcludedByOrthogonalLineAxis(detail + " [直交フローの行軸]");
		}
		// 直交フローが3段以上入れ子になった文書も除外(2026-07-30のユーザー
		// 裁定。{@link ExcludedByNestedOrthogonalFlow}に理由を書いた)。
		// **2段は除外しない**——普通の縦書き中の横書きでの紙面外は
		// 本物の欠陥である
		if (orthogonalAxisChanges(doc.html()) >= 2) {
			throw new ExcludedByNestedOrthogonalFlow(detail + " [直交フロー3段以上]");
		}
		fail(detail);
	}

	/** 外接矩形全体が紙面からさらに1枚分離れている距離。0以下なら許容範囲。 */
	static double distanceBeyondWholePage(final double origin, final double extent, final double pageExtent) {
		return Math.max(-(origin + extent) - pageExtent, origin - 2 * pageExtent);
	}

	private static final Pattern TEXT_CONTROL_TAG = Pattern.compile("<(input|textarea)\\b([^>]*)>",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern INPUT_TYPE = Pattern.compile("\\btype\\s*=\\s*[\"']?([a-z]+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern INPUT_SIZE = Pattern.compile("\\bsize\\s*=", Pattern.CASE_INSENSITIVE);
	/** 試験用UA CSSの一行入力欄/textarea既定20exに外枠を加えた実寸。 */
	private static final double DEFAULT_TEXT_CONTROL_WIDTH_PT = 124;

	/**
	 * 幅指定のない一行入力欄またはtextareaが持つUA既定幅。
	 *
	 * <p>
	 * {@code html-ua.css}はこれらを20exにする。固定試験フォントでは外枠込み
	 * 124ptで、seed 473636/526411/651439の負座標・後続インライン位置を
	 * そのまま説明する。checkbox/radio等と{@code size}指定付き入力へ広げない。
	 * </p>
	 */
	static double defaultTextControlWidth(final String html) {
		final Matcher tag = TEXT_CONTROL_TAG.matcher(html);
		while (tag.find()) {
			if (tag.group(1).equalsIgnoreCase("textarea")) {
				return DEFAULT_TEXT_CONTROL_WIDTH_PT;
			}
			final String attrs = tag.group(2);
			if (INPUT_SIZE.matcher(attrs).find()) {
				continue;
			}
			final Matcher typeMatcher = INPUT_TYPE.matcher(attrs);
			if (!typeMatcher.find()) {
				return DEFAULT_TEXT_CONTROL_WIDTH_PT;
			}
			final String type = typeMatcher.group(1).toLowerCase(java.util.Locale.ROOT);
			if (!Set.of("button", "submit", "reset", "checkbox", "radio", "image", "hidden").contains(type)) {
				return DEFAULT_TEXT_CONTROL_WIDTH_PT;
			}
		}
		return 0;
	}

	/**
	 * <b>不変条件10</b>: 文書中のトークンを含む描画が、少なくとも1つは
	 * 実際の紙面と交差すること。
	 *
	 * <p>
	 * 不変条件6は、作者が明示した寸法で説明できる小さなはみ出しを許す。
	 * そのため全トークンが紙面のすぐ外に並んでも、消失検査には現れ、
	 * 紙面外検査の閾値には届かず合格できた。この検査は量を問わず、
	 * <b>全内容が見えない</b>場合だけを別に捕捉する。
	 * </p>
	 *
	 * <p>
	 * 座標点だけでは判定しない。原点が紙面外でも字形の矩形が紙面へ
	 * かかる正当な描画があるため、掃過時だけ詳細ダンプへ付けた幅・高さとの
	 * 矩形交差で判定する。
	 * </p>
	 */
	static void assertSomeDrawingOnPage(final Generated doc, final File[] pages, final File html)
			throws Exception {
		double nearest = Double.POSITIVE_INFINITY;
		boolean nearestIsY = false;
		String nearestAt = null;
		for (final File page : pages) {
			final String dump = Files.readString(Path.of(page.toURI()), StandardCharsets.UTF_8);
			for (final String raw : dump.split("\n")) {
				final Matcher m = DRAWING_GEOMETRY_IN_DUMP.matcher(raw);
				if (!m.find()) {
					continue;
				}
				final double x = Double.parseDouble(m.group(1));
				final double y = Double.parseDouble(m.group(2));
				final double width = Double.parseDouble(m.group(3) != null ? m.group(3) : m.group(5));
				final double height = Double.parseDouble(m.group(4) != null ? m.group(4) : m.group(6));
				if (rectangleIntersectsPage(x, y, width, height, doc.pageWidth(), doc.pageHeight())) {
					return;
				}
				final double dx = Math.max(Math.max(-x - width, x - doc.pageWidth()), 0);
				final double dy = Math.max(Math.max(-y - height, y - doc.pageHeight()), 0);
				final double distance = Math.hypot(dx, dy);
				if (distance < nearest) {
					nearest = distance;
					nearestIsY = dy >= dx;
					nearestAt = "x=" + x + " y=" + y + " w=" + width + " h=" + height + " "
							+ page.getName();
				}
			}
		}
		final String detail = "全描画が紙面外 (紙面" + Math.round(doc.pageWidth()) + "x"
				+ Math.round(doc.pageHeight()) + "pt, 最短=" + Math.round(nearest) + "pt, " + nearestAt + ") (" + html
				+ ")";
		if (doc.beyondEngineControl()) {
			throw new ExcludedByOversizedBox(detail);
		}
		// 複数条件に該当するときも、より狭い同軸逆進行の分類を安定して残す。
		if (hasUntypesettableOppositeProgression(doc.html())) {
			throw new ExcludedByUntypesettableOppositeProgression(detail + " [同軸逆進行フローの組版不能幅]");
		}
		if (hasUntypesettableOrthogonalFlow(doc.html())) {
			throw new ExcludedByUntypesettableOrthogonalFlow(detail + " [直交フローの組版不能幅]");
		}
		if (hasUntypesettableFloat(doc.html())) {
			throw new ExcludedByUntypesettableFloat(detail + " [組版できない幅の浮動体]");
		}
		if (hasFlexMulticolTable(doc.html())) {
			throw new ExcludedByFlexMulticolMinContent(detail + " [flex内の段組表によるmin-content幅]");
		}
		final boolean outsideInLineAxis = nearestIsY != pageAxisIsY(doc.html());
		if (outsideInLineAxis && hasOrthogonalFlow(doc.html())) {
			throw new ExcludedByOrthogonalLineAxis(detail + " [直交フローの行軸]");
		}
		if (orthogonalAxisChanges(doc.html()) >= 2) {
			throw new ExcludedByNestedOrthogonalFlow(detail + " [直交フロー3段以上]");
		}
		fail(detail);
	}

	/** 正の面積を持つ2矩形として、紙面と交差するか。境界への接触だけは含めない。 */
	static boolean rectangleIntersectsPage(final double x, final double y, final double width, final double height,
			final double pageWidth, final double pageHeight) {
		return width > 0 && height > 0 && x < pageWidth && y < pageHeight && x + width > 0 && y + height > 0;
	}

	private static void convert(final File html, final File outDir) throws Exception {
		convert(html, outDir, new DirectSession[1], MAX_PAGES);
	}

	/**
	 * @param sessionOut watchdogが中断要求を出せるよう、生成したセッションを
	 *                   ここへ書き出す。<b>放置ではなく実際に止めるため</b>
	 *                   (2026-07-27)——止められないスレッドはレイアウト1件分の
	 *                   ヒープと64MBのスタック予約を抱えたまま残り、掃過が
	 *                   自己増幅的に詰まる
	 */
	private static void convert(final File html, final File outDir, final DirectSession[] sessionOut,
			final int pageLimit)
			throws Exception {
		// 出力先はスレッド単位。システムプロパティだとプロセス全体で共有され、
		// 並列掃過でダンプ先が互いに上書きされる(2026-07-26)
		try (AutoCloseable scope = DisplayListDumper.scopedDir(outDir.getPath());
				AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(true)) {
			final File pdf = new File(outDir, "out.pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				final AtomicBoolean pageLimitExceeded = new AtomicBoolean();
				sessionOut[0] = session;
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					// 数百万文書の掃過で全ページのINFOを標準エラーへ流すと、
					// I/Oが測定時間とログ容量を支配する。ページ上限だけは型付きで
					// 捕捉し、必要なときだけ-Dfoliojet.fuzzMessagesで全メッセージを出す。
					session.setMessageHandler((code, args, mes) -> {
						if (code == net.zamasoft.foliojet.message.MessageCodes.ERROR_OUT_OF_PAGE_LIMIT) {
							pageLimitExceeded.set(true);
						}
						if (System.getProperty("foliojet.fuzzMessages") != null) {
							System.err.println(mes);
						}
					});
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					// オラクルが許す最大枚数を超えた時点で変換自体を止める。
					// 従来は完了後にファイル数を数えていたため、ページ爆発が
					// 数千枚を生成してwatchdogとワーカー漏れを起こし、縮小器も
					// 1候補30秒かかっていた(seed 7662)。上限+1枚目で停止すれば
					// 不変条件3の意味を変えず、異常入力だけを早く封じ込められる。
					session.property("output.page-limit", String.valueOf(pageLimit));
					// 名前付き宛先(id断片)は `output.pdf.hyperlinks.fragment`
					// の既定onで出るので、ここでの指定は要らない(2026-08-03に
					// 確認。不変条件9=checkFragments が読む)
					try {
						CTISessionHelper.transcodeFile(session, html, "text/html", null);
					} catch (final jp.cssj.cti2.TranscoderException e) {
						if (pageLimitExceeded.get()) {
							throw new PageCountLimitExceeded(pageLimit, e);
						}
						throw e;
					}
				} finally {
					session.close();
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// 生成器
	// ------------------------------------------------------------------

	/**
	 * @param pageWidth       紙面の幅(pt)
	 * @param pageHeight      紙面の高さ(pt)
	 * @param maxExplicitSize この文書が指定した{@code width}/{@code height}の
	 *                        最大値(pt)。不変条件6で「作者が指定した大きさの
	 *                        帰結として説明できるはみ出しか」を判定するのに使う
	 */
	record Generated(String html, List<String> tokens, Set<String> reorderable, double pageWidth,
			double pageHeight, double maxExplicitSize, boolean oversized, boolean tinyPage) {

		/** 版面が破綻していて、エンジンの振る舞いを問えない文書か。 */
		boolean beyondEngineControl() {
			return this.oversized || this.tinyPage;
		}

		/**
		 * 読み順が保たれるべきトークンを<b>文書順に</b>返します。
		 *
		 * <p>
		 * フロートと絶対配置の部分木は除きます——どちらも<b>正当に</b>
		 * 読み順を変えるからです。段組・表・改ページは内容を順に流すだけ
		 * なので対象に残します。
		 * </p>
		 */
		List<String> orderedTokens() {
			final List<String> ordered = new ArrayList<String>();
			for (final String t : this.tokens) {
				if (!this.reorderable.contains(t)) {
					ordered.add(t);
				}
			}
			return ordered;
		}
	}

	/** 生成器が出す明示サイズ({@code width:120pt}等)。 */
	static final Pattern EXPLICIT_SIZE = Pattern.compile("(?:width|height):(\\d+)pt");

	private static final Pattern EXPLICIT_WIDTH = Pattern.compile("width:(\\d+)pt");
	private static final Pattern EXPLICIT_HEIGHT = Pattern.compile("height:(\\d+)pt");
	private static final Pattern FONT_SIZE = Pattern.compile("(?:font-size:|font:normal )(\\d+)pt");
	private static final Pattern PAGE_MARGIN = Pattern.compile("@page\\{margin:(\\d+)pt");
	private static final Pattern PAGE_WIDTH_PROPERTY = Pattern
			.compile("name=\"output\\.page-width\"\\s+value=\"([\\d.]+)pt\"");

	/**
	 * 生成する文書が参照する画像の<b>絶対URI</b>(2026-07-27)。
	 *
	 * <p>
	 * <b>相対パスにしてはいけない。</b>従来は
	 * {@code ../../files/unittest/red.png}を埋めていたため、生成された文書は
	 * <b>`local/fuzz/`に置いたときだけ画像が解決した</b>。縮小や再現のために
	 * 他所へコピーすると<b>画像が黙って消え、別の文書になる</b>——失敗が
	 * 再現しなくなり、「パスによって挙動が変わる」という誤った結論を招いた
	 * (2026-07-27に実際に踏んだ)。
	 * </p>
	 *
	 * <p>
	 * 画像が入るかどうかでページ数が変わる(実測で2ページ↔3ページ)ので、
	 * <b>再現性の前提そのもの</b>である。同型の脆さが{@code EnduranceTest}
	 * にもある({@code ../../../}を決め打ち)。
	 * </p>
	 */
	/**
	 * 画像の位置は{@code -Dfoliojet.fuzzImage}で差し替えられます(2026-07-29)。
	 *
	 * <p>
	 * 既定の{@code files/unittest/red.png}は{@code /mnt/f}(DrvFs)にあり、
	 * <b>文書ごとに開いて復号し直す</b>ため掃過の主要な費用になっていた
	 * (スレッドダンプで、働いているレイアウトスレッドのほとんどが
	 * {@code PNGImageReader.readMetadata}だった)。tmpfsへ複製して
	 * ここを指せば大きく落ちる。
	 * </p>
	 *
	 * <p>
	 * <b>再現性は損なわれない。</b> 失敗の再現は保存されたHTMLではなく
	 * <b>シード番号</b>から行う({@code -Dfoliojet.fuzzOnlySeed})ので、
	 * 生成時に有効な画像パスが使われれば足りる。絶対URIにする理由
	 * (相対パスだと置き場所で文書が変わる)は従来どおり。
	 * </p>
	 */
	/** 掃過に使う既定の画像です(差し替え時の寸法照合の原本)。 */
	private static final String DEFAULT_FUZZ_IMAGE = "files/unittest/red.png";

	private static final String RED_PNG_URI = new File(
			System.getProperty("foliojet.fuzzImage", DEFAULT_FUZZ_IMAGE)).getAbsoluteFile().toURI().toString();

	/**
	 * 掃過の作業ディレクトリ({@code -Dfoliojet.fuzzWorkDir})。既定は
	 * {@code local/fuzz}。
	 *
	 * <p>
	 * <b>掃過の律速はCPUではなくI/Oである</b>(2026-07-28に実測)。
	 * 1文書ごとに文書HTML・表示リストのダンプ・PDFを書くため、既定の
	 * {@code local/}が{@code /mnt/f}(WSLのDrvFs=Windowsファイルシステム)に
	 * あると、12スレッドでもワーカーのCPU時間が経過時間の1.2倍にしかならず、
	 * 残りはI/O待ちで積まれる。tmpfs({@code /dev/shm})やWSL側のext4
	 * ({@code /}配下)を指すと大きく変わる。
	 * </p>
	 *
	 * <p>
	 * <b>失敗の再現性は損なわれない。</b> 生成器は決定的なので、
	 * シード番号さえ分かれば{@code -Dfoliojet.fuzzOnlySeed}でいつでも
	 * 同じ文書を作り直せる。文書が参照する画像は既に絶対URIなので
	 * (下記{@link #RED_PNG_URI})、作業ディレクトリを移しても
	 * 同じ文書になる。
	 * </p>
	 */
	static File workDir() {
		final File dir = new File(System.getProperty("foliojet.fuzzWorkDir", "local/fuzz"));
		dir.mkdirs();
		return dir;
	}

	/** ページ寸法の候補(極端に小さいものを含む)。 */
	private static final int[][] PAGE_SIZES = { { 200, 200 }, { 300, 150 }, { 120, 400 }, { 595, 842 }, { 60, 60 } };

	private static final String[] WRITING_MODES = { "horizontal-tb", "vertical-rl", "vertical-lr" };

	static Generated generate(final int seed, final boolean strict) {
		return generate(seed, strict, false);
	}

	/**
	 * @param legacyV1 v1の入力分布で生成する(拡張を全て無効化)。
	 *                 固定seed回帰の期待値はv1文書で記録されているため、
	 *                 歴史的seedの検査はこちらを使う(2026-08-23)
	 */
	static Generated generate(final int seed, final boolean strict, final boolean legacyV1) {
		return generate(seed, strict, legacyV1, !legacyV1 && extremeProfile());
	}

	/**
	 * @param extreme v2本文へextreme-v1の制約付き高密度シナリオを追加する。
	 *                独立乱数列を使うのでfalse時のv2文書は一切変わらない。
	 */
	static Generated generate(final int seed, final boolean strict, final boolean legacyV1,
			final boolean extreme) {
		final long randomSeed = seed * 7919L + (strict ? 1 : 2);
		final Random r = new Random(randomSeed);
		// v1の乱数消費順を変えない。追加機能はこの独立系列だけを消費する。
		// legacyV1のときnull=全拡張ゲートが不発火
		final Random extensionRandom = legacyV1 ? null
				: new Random(randomSeed ^ 0x6A09E667F3BCC909L
						^ ((long) GENERATOR_VERSION << 32));
		// extremeはv2の乱数消費順を変えない第三系列。プロファイル版をseedへ
		// 混ぜ、将来extreme-v2を作っても同じseedの意味を黙って変えない。
		final Random extremeRandom = extreme
				? new Random(randomSeed ^ 0xBB67AE8584CAA73BL
						^ ((long) EXTREME_PROFILE_VERSION << 40))
				: null;
		final List<String> tokens = new ArrayList<>();
		// 並べ替えが正当なトークン(フロート・絶対配置の部分木の中)。
		// 事後に表示リストから推定せず、**知っている側**に記録させる
		final Set<String> reorderable = new LinkedHashSet<String>();
		final StringBuilder body = new StringBuilder();
		final int[] counter = { 0 };
		// **1文書あたりの機能数を指標にする**(2026-08-02、ユーザー指摘)。
		// t個の機能の組は、機能k個の文書がC(k,t)個を一度に被覆する——
		// つまり密度はtの次数で効く。「小さい文書×多数」では、文書数を
		// いくら積んでも3組・4組は被覆されない。
		// 3/4の文書は全種別を一巡半〜二巡ぶん詰め込み、1/4は小さいまま
		// 残す(欠陥が出たときの切り分けを速く保つため)
		final boolean dense = r.nextInt(4) != 0;
		if (dense) {
			final int kinds = nodeKinds(strict);
			final int[] order = new int[kinds];
			for (int i = 0; i < kinds; ++i) {
				order[i] = i;
			}
			for (int i = kinds - 1; i > 0; --i) {
				final int j = r.nextInt(i + 1);
				final int t = order[i];
				order[i] = order[j];
				order[j] = t;
			}
			final int nodes = kinds + kinds / 2 + r.nextInt(kinds);
			for (int i = 0; i < nodes; ++i) {
				appendNode(body, r, extensionRandom, 3, strict, tokens, counter, reorderable, false,
						order[i % kinds]);
			}
		} else {
			final int roots = 1 + r.nextInt(4);
			for (int i = 0; i < roots; ++i) {
				appendNode(body, r, extensionRandom, 3, strict, tokens, counter, reorderable, false);
			}
		}
		if (extremeRandom != null) {
			appendExtremeDocument(body, extremeRandom, strict, tokens, counter, reorderable);
		}
		final int[] size = PAGE_SIZES[r.nextInt(PAGE_SIZES.length)];
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"").append(size[0]).append("pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"").append(size[1]).append("pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<title>fuzz v").append(GENERATOR_VERSION);
		if (extreme) {
			s.append("/extreme-v").append(EXTREME_PROFILE_VERSION);
		}
		s.append(' ').append(seed).append("</title>\n")
				.append("<meta name=\"foliojet-fuzz-generator\" content=\"").append(GENERATOR_VERSION)
				.append("\" />\n");
		if (extreme) {
			s.append("<meta name=\"foliojet-fuzz-profile\" content=\"extreme-v")
					.append(EXTREME_PROFILE_VERSION).append("\" />\n");
		}
		s.append("<style>\n");
		s.append("@page{margin:").append(r.nextInt(3) * 5).append("pt}\n");
		s.append("body{margin:0;font:normal ").append(6 + r.nextInt(8)).append("pt/1.2 serif;writing-mode:")
				.append(WRITING_MODES[r.nextInt(WRITING_MODES.length)]).append("}\n");
		s.append("p,div,td{margin:0;padding:0}\n");
		s.append("table{border-collapse:").append(r.nextBoolean() ? "collapse" : "separate")
				.append(";table-layout:").append(r.nextBoolean() ? "auto" : "fixed").append("}\n");
		s.append("td{border:1pt solid black}\n");
		s.append("</style></head><body data-fuzz-generator=\"").append(GENERATOR_VERSION).append('"');
		if (extreme) {
			s.append(" data-fuzz-profile=\"extreme-v").append(EXTREME_PROFILE_VERSION).append('"');
		}
		s.append(">\n");
		s.append(body);
		s.append("\n</body></html>\n");
		final String out = s.toString();
		// 生成器が出した明示サイズの最大値。生成箇所が複数に散っているので、
		// 引数で持ち回るより出来上がった文書から拾うほうが取りこぼさない
		double maxExplicit = 0;
		final Matcher em = EXPLICIT_SIZE.matcher(out);
		while (em.find()) {
			maxExplicit = Math.max(maxExplicit, Double.parseDouble(em.group(1)));
		}
		return new Generated(out, tokens, reorderable, size[0], size[1], maxExplicit, isOversized(out, size),
				isTinyPage(out, size));
	}

	/**
	 * extreme-v1の制約付きシナリオ。単に乱数の深さを上げるとDOMが指数的に
	 * 膨らみ、ほぼ全件がwatchdog/OOMという無意味な分布になる。そこで、過去の
	 * 構造的な穴を毎文書へ有界に組み込む。v2本文とは独立乱数列なので標準
	 * プロファイルのseed再現性を保つ。
	 */
	private static void appendExtremeDocument(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-document\" style=\"min-width:0;max-width:100%\">\n");
		appendExtremeTable(s, r, strict, tokens, counter, reorderable);
		appendExtremeLayout(s, r, strict, tokens, counter, reorderable);
		appendExtremeList(s, r, strict, tokens, counter, reorderable);
		appendExtremeText(s, r, tokens, counter, reorderable);
		appendExtremeFragmentation(s, r, strict, tokens, counter, reorderable);
		if (!strict) {
			appendExtremeWildBreak(s, r, tokens, counter, reorderable);
		}
		s.append("</div>\n");
	}

	/** 有効なspanを保ったthead/tbody/tfoot・複数複雑セルの表。 */
	private static void appendExtremeTable(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<table data-fuzz-role=\"extreme-table\" style=\"width:90%;min-width:8em;max-width:100%;")
				.append("break-inside:auto\">\n<caption>")
				.append(token(tokens, counter, reorderable, false)).append("</caption>\n")
				.append("<colgroup><col style=\"width:22%\" /><col span=\"2\" style=\"width:24%\" />")
				.append("<col style=\"width:30%\" /></colgroup>\n<thead><tr>");
		for (int i = 0; i < 4; ++i) {
			s.append("<th>").append(token(tokens, counter, reorderable, false)).append("</th>");
		}
		s.append("</tr></thead>\n<tbody>\n<tr><td rowspan=\"2\">")
				.append(token(tokens, counter, reorderable, false));
		appendRichCellChild(s, r, 3, strict, tokens, counter, reorderable, false);
		s.append("</td><td colspan=\"2\">").append(token(tokens, counter, reorderable, false));
		appendLayoutContainer(s, r, 3, strict, tokens, counter, reorderable, false, r.nextBoolean());
		s.append("</td><td>").append(token(tokens, counter, reorderable, false)).append("</td></tr>\n")
				.append("<tr><td>").append(token(tokens, counter, reorderable, false)).append("</td><td>")
				.append(token(tokens, counter, reorderable, false)).append("</td><td>")
				.append(token(tokens, counter, reorderable, false)).append("</td></tr>\n");
		for (int row = 0; row < 2; ++row) {
			s.append("<tr>");
			for (int col = 0; col < 4; ++col) {
				s.append("<td>").append(token(tokens, counter, reorderable, false));
				if (row == 0 && col == 2) {
					appendRichCellChild(s, r, 2, strict, tokens, counter, reorderable, false);
				}
				s.append("</td>");
			}
			s.append("</tr>\n");
		}
		s.append("</tbody>\n<tfoot><tr><td colspan=\"4\">")
				.append(token(tokens, counter, reorderable, false))
				.append("</td></tr></tfoot>\n</table>\n");
	}

	/** 6 itemのflexと明示配置gridを重ね、幅交渉・折返し・順序を必ず踏む。 */
	private static void appendExtremeLayout(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		final boolean reversed = !strict && r.nextBoolean();
		s.append("<div data-fuzz-role=\"extreme-layout\" style=\"display:flex;flex-direction:")
				.append(reversed ? "row-reverse" : "row")
				.append(";flex-wrap:wrap;align-items:stretch;align-content:space-between;")
				.append("justify-content:space-around;gap:calc(1pt + .25em);min-width:0;max-width:100%\">\n");
		for (int item = 0; item < 6; ++item) {
			s.append("<div data-fuzz-role=\"extreme-flex-item\" style=\"order:").append((item * 5) % 7)
					.append(";flex:").append(item % 3).append(' ').append(1 + item % 2)
					.append(" calc(18% + ").append(item).append("pt);min-width:0;align-self:")
					.append(item % 2 == 0 ? "stretch" : "center").append("\">\n")
					.append("<div style=\"display:grid;grid-template-columns:repeat(3,minmax(0,1fr));")
					.append("grid-auto-flow:dense;gap:.25em;align-items:center;justify-content:space-between\">\n");
			for (int grid = 0; grid < 4; ++grid) {
				s.append("<div data-fuzz-role=\"extreme-grid-item\" style=\"grid-column:")
						.append(grid == 0 ? "span 2" : String.valueOf(1 + grid % 3))
						.append(";grid-row:span 1;min-width:0\">\n");
				appendNode(s, r, r, 3, strict, tokens, counter, reorderable, reversed,
						(item + grid) % nodeKinds(strict));
				s.append("</div>\n");
			}
			s.append("</div></div>\n");
		}
		s.append("</div>\n");
	}

	/** list itemの中へruby・画像・form・段組を同居させる。 */
	private static void appendExtremeList(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<ol data-fuzz-role=\"extreme-list\" style=\"list-style-position:outside;break-inside:auto\">\n")
				.append("<li><ruby>").append(token(tokens, counter, reorderable, false)).append("<rt>")
				.append(token(tokens, counter, reorderable, false)).append("</rt></ruby> ");
		for (int i = 0; i < 6; ++i) {
			s.append("<ruby>").append(token(tokens, counter, reorderable, false)).append("<rt>")
					.append(token(tokens, counter, reorderable, false)).append("</rt></ruby> ");
		}
		s.append("</li>\n<li><span>").append(token(tokens, counter, reorderable, false))
				.append("</span><img src=\"").append(RED_PNG_URI)
				.append("\" alt=\"img\" style=\"display:inline;width:3em;height:2em\" /><span>")
				.append(token(tokens, counter, reorderable, false)).append("</span></li>\n")
				.append("<li><form style=\"display:grid;grid-template-columns:1fr 1fr;gap:2pt\">")
				.append("<input type=\"text\" value=\"mixed\" size=\"8\" />")
				.append("<textarea rows=\"2\">日本語 العربية</textarea><select><option>甲</option>")
				.append("<option>乙</option></select><button type=\"button\">")
				.append(token(tokens, counter, reorderable, false)).append("</button></form></li>\n<li>");
		appendPlainNode(s, r, r, 4, strict, tokens, counter, reorderable, false, 4);
		s.append("</li>\n</ol>\n");
	}

	/** 長語・連続空白・CJK・RTL/bidiを、多数の追跡トークンと同じ行へ置く。 */
	private static void appendExtremeText(final StringBuilder s, final Random r,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-text\" style=\"min-width:0;max-width:100%;")
				.append("overflow-wrap:anywhere;word-break:normal;widows:4;orphans:4\">\n<p id=\"p")
				.append(counter[0]).append("\">");
		for (int i = 0; i < 64; ++i) {
			s.append(token(tokens, counter, reorderable, false)).append(' ');
			s.append(switch (i % 5) {
			case 0 -> "日本語の禁則、句読点。 ";
			case 1 -> "Latin-hyphenation/fragmentation ";
			case 2 -> "<span dir=\"rtl\" style=\"unicode-bidi:isolate\">العربية עברית</span> ";
			case 3 -> "漢字かな交じり文　連続　全角空白 ";
			default -> "emoji&#x1F642;&#x1F469;&#x200D;&#x1F4BB; ";
			});
		}
		s.append("<span class=\"fuzzUnbreakable\">Supercalifragilisticexpialidocious")
				.append("_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz</span></p>\n")
				.append("<p style=\"white-space:pre-wrap\">")
				.append(token(tokens, counter, reorderable, false))
				.append("    spaces\nline-break\t tab ")
				.append(token(tokens, counter, reorderable, false)).append("</p></div>\n");
	}

	/** 段バランス・avoid・深い入れ子の切断境界を有界に5枝作る。 */
	private static void appendExtremeFragmentation(final StringBuilder s, final Random r, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-fragmentation\" style=\"column-count:3;column-fill:balance;")
				.append("column-gap:calc(1pt + .5em);max-width:100%\">\n");
		final int[] kinds = { 11, 5, 3, 8, 1 };
		for (int i = 0; i < kinds.length; ++i) {
			s.append("<div style=\"page-break-inside:avoid;break-inside:avoid-column;min-height:")
					.append(2 + i).append("em;max-height:none\">\n");
			appendPlainNode(s, r, r, 4, strict, tokens, counter, reorderable, false, kinds[i]);
			s.append("</div>\n");
		}
		s.append("</div>\n");
	}

	/** WILDでだけ、強制改ページ・非表示・overflowの内側を複雑化する。 */
	private static void appendExtremeWildBreak(final StringBuilder s, final Random r,
			final List<String> tokens, final int[] counter, final Set<String> reorderable) {
		s.append("<div data-fuzz-role=\"extreme-wild-break\" style=\"page-break-before:always;")
				.append("overflow:hidden;visibility:visible;max-height:80vh\">\n");
		for (int i = 0; i < 6; ++i) {
			appendNode(s, r, r, 4, false, tokens, counter, reorderable, false, i % nodeKinds(false));
		}
		s.append("<div style=\"visibility:hidden\">");
		appendLayoutContainer(s, r, 3, false, tokens, counter, reorderable, true, true);
		s.append("</div></div>\n");
	}

	/**
	 * この文書が<b>紙面の内容領域に収まらない箱</b>を含むかを返します
	 * (2026-07-26新設)。
	 *
	 * <p>
	 * <b>収まらないものを置いた文書は、白紙ページの検査から除外します。</b>
	 * エンジンがどう振る舞っても版面は破綻しており(はみ出させるか、
	 * 次ページへ送るか)、寸法を直すのは組版を指定した側の責任だからです
	 * ——2026-07-26のユーザー裁定。
	 * </p>
	 *
	 * <p>
	 * 判定は生成器が出した値だけで行います。実測では、白紙ページを出す
	 * 33文書のうち<b>28文書</b>がこれに該当しました。残り5件のうち2件は
	 * 50x50pt(1.7cm角=切手より小さい)の紙で、これも実質同じ性質です。
	 * 追うべきは残り3件——190x190pt・110x390pt・280x130pt という
	 * <b>実在する寸法で、かつ全要素が紙面に収まる</b>文書です。
	 * </p>
	 *
	 * <p>
	 * <b>除外は「見なかったことにする」ではありません。</b> 集計モードでは
	 * 除外分も件数として出します——除外が増えたことに気づけなくなると、
	 * 本当の退行を見落とすからです。
	 * </p>
	 */
	static boolean isOversized(final String html, final int[] pageSize) {
		final Matcher pm = PAGE_MARGIN.matcher(html);
		final double margin = pm.find() ? Double.parseDouble(pm.group(1)) : 0;
		final double contentWidth = pageSize[0] - 2 * margin;
		final double contentHeight = pageSize[1] - 2 * margin;
		if (maxOf(EXPLICIT_WIDTH, html) > contentWidth) {
			return true;
		}
		if (maxOf(EXPLICIT_HEIGHT, html) > contentHeight) {
			return true;
		}
		// フォントサイズは行の高さになるので、ページ方向の寸法と比べる
		return maxOf(FONT_SIZE, html) > contentHeight;
	}

	/**
	 * この文書の紙面が<b>そもそも組版できない大きさ</b>かを返します
	 * (2026-07-26新設)。
	 *
	 * <p>
	 * 基準は「内容領域が基準フォントサイズの{@value #MIN_PAGE_CHARS}倍
	 * (=約{@value #MIN_PAGE_CHARS}文字)に満たない軸がある」。生成器は
	 * 60x60ptの紙に13ptのフォントという文書を作る——<b>1行4文字</b>で、
	 * 行分割も浮動体も段組も意味のある版面にならず、エンジンがどう
	 * 振る舞っても正解がない。実在する最小の印刷物(ラベル・値札)でも
	 * この比率にはならない。
	 * </p>
	 *
	 * <p>
	 * 除外されるのは実質「60x60ptかつフォント8pt以上」だけで、
	 * 他の紙面(120x400・200x200・300x150・A4)は生成器が出す
	 * フォントサイズの範囲(6〜13pt)では該当しない。
	 * </p>
	 */
	static boolean isTinyPage(final String html, final int[] pageSize) {
		final Matcher pm = PAGE_MARGIN.matcher(html);
		final double margin = pm.find() ? Double.parseDouble(pm.group(1)) : 0;
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double font = Double.parseDouble(fm.group(1));
		final double least = font * MIN_PAGE_CHARS;
		return pageSize[0] - 2 * margin < least || pageSize[1] - 2 * margin < least;
	}

	/** 「組版できる紙面」の下限を文字数で表したもの。 */
	private static final int MIN_PAGE_CHARS = 8;

	/** 生成器が書く浮動体の明示寸法({@code float:left;width:64pt}等)。 */
	private static final Pattern FLOAT_EXPLICIT_SIZE = Pattern
			.compile("float:[a-z]+;(?:width|height):(\\d+)pt");

	/**
	 * <b>組版できない幅の浮動体</b>を含むかを返します(2026-07-29新設)。
	 *
	 * <p>
	 * 基準は2つ。明示した寸法が基準フォントサイズの
	 * {@value #MIN_PAGE_CHARS}倍(=約{@value #MIN_PAGE_CHARS}文字)に
	 * 満たないか、明示した親幅・段幅より左右フロートが広いこと。
	 * 紙面が組版できない大きさなら除外する、と決めたのと同じ理由で、
	 * <b>欄が組版できない幅なら中身は必ず溢れる</b>。
	 * </p>
	 *
	 * <p>
	 * 実測(2026-07-29): 掃過に残っていた紙面外3件は、いずれも
	 * <b>3文字未満</b>の幅の浮動体に表や段組を詰め込んでいた——
	 * 596520が29pt/13pt(2.2文字)、1412230が10pt/11pt(0.9文字)、
	 * 1928901が15pt/10pt(1.5文字)。CSSの{@code overflow}既定は
	 * {@code visible}なので、この中身が箱の外へ描かれるのは<b>正しい挙動</b>で
	 * あり、エンジンの振る舞いを問えない。
	 * </p>
	 *
	 * <p>
	 * <b>紙面外の検査にだけ使う。</b> 内容の消失・読み順・複製・変換の失敗には
	 * 適用しない({@code ARCHITECTURE.md} §5.13が白紙ページと紙面外にだけ
	 * 除外を認めているのと同じ線引き)。
	 * </p>
	 */
	static boolean hasUntypesettableFloat(final String html) {
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double least = Double.parseDouble(fm.group(1)) * MIN_PAGE_CHARS;
		final Matcher m = FLOAT_EXPLICIT_SIZE.matcher(html);
		while (m.find()) {
			if (Double.parseDouble(m.group(1)) < least) {
				return true;
			}
		}
		return hasFloatInsideNarrowContainer(html, least) || hasOverwideFloat(html);
	}

	/** 明示幅が組版下限未満の祖先に、左右フロートが実際に入っているか。 */
	static boolean hasFloatInsideNarrowContainer(final String html, final double least) {
		final java.util.ArrayDeque<Boolean> narrow = new java.util.ArrayDeque<>();
		narrow.push(Boolean.FALSE);
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (narrow.size() > 1) {
					narrow.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			final boolean insideNarrow = narrow.peek().booleanValue();
			if (insideNarrow && STYLE_FLOAT.matcher(attrs).find()) {
				return true;
			}
			if (attrs.endsWith("/")) {
				continue;
			}
			final Matcher widthMatcher = STYLE_WIDTH.matcher(attrs);
			final boolean thisNarrow = insideNarrow
					|| (widthMatcher.find() && Double.parseDouble(widthMatcher.group(1)) < least);
			narrow.push(Boolean.valueOf(thisNarrow));
		}
		return false;
	}

	private static final Pattern STYLE_FLOAT = Pattern
			.compile("(?:^|[;\\s\"])float\\s*:\\s*(left|right)\\s*(?:;|\"|'|$)");
	private static final Pattern STYLE_COLUMN_COUNT = Pattern.compile("column-count\\s*:\\s*(\\d+)");
	private static final Pattern STYLE_COLUMN_GAP = Pattern.compile("column-gap\\s*:\\s*([\\d.]+)pt");
	private static final Pattern STYLE_FLEX = Pattern
			.compile("(?:^|[;\\s\"])display\\s*:\\s*(?:inline-)?flex\\s*(?:;|\"|'|$)");

	/**
	 * 明示した包含幅より広い左右フロートを含むかを返す。
	 *
	 * <p>
	 * 単に文書中の最大・最小を比べない。開始・終了タグをスタックでたどり、
	 * 別の枝にある幅を結び付けない。段組はページ内容幅または親の明示幅から
	 * {@code (幅 - 段間*(段数-1))/段数}を計算する。これでstrict seed
	 * 132786(99pt中の126ptフロート)と143513(約25.3pt段中の89pt
	 * フロート)だけを、作者指定による組版不能幅として識別できる。
	 * </p>
	 */
	static boolean hasOverwideFloat(final String html) {
		final Matcher pageWidthMatcher = PAGE_WIDTH_PROPERTY.matcher(html);
		if (!pageWidthMatcher.find()) {
			return false;
		}
		final Matcher marginMatcher = PAGE_MARGIN.matcher(html);
		final double margin = marginMatcher.find() ? Double.parseDouble(marginMatcher.group(1)) : 0;
		final double pageContentWidth = Double.parseDouble(pageWidthMatcher.group(1)) - 2 * margin;
		if (!(pageContentWidth > 0)) {
			return false;
		}

		final java.util.ArrayDeque<Double> childWidths = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Double> floatLimits = new java.util.ArrayDeque<>();
		childWidths.push(Double.valueOf(pageContentWidth));
		floatLimits.push(Double.valueOf(Double.POSITIVE_INFINITY));
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (childWidths.size() > 1) {
					childWidths.pop();
					floatLimits.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			final double containingWidth = childWidths.peek().doubleValue();
			final Matcher widthMatcher = STYLE_WIDTH.matcher(attrs);
			final Double explicitWidth = widthMatcher.find() ? Double.valueOf(widthMatcher.group(1)) : null;
			final boolean floating = STYLE_FLOAT.matcher(attrs).find();
			if (explicitWidth != null && explicitWidth.doubleValue() > floatLimits.peek().doubleValue()) {
				// 自動幅floatのshrink-to-fit幅を、子孫の明示幅が包含幅より
				// 大きくしている(seed 865035)。別枝の幅はstack上に無い。
				return true;
			}
			if (explicitWidth != null && floating
					&& explicitWidth.doubleValue() > containingWidth) {
				return true;
			}

			double availableForChildren = explicitWidth == null ? containingWidth : explicitWidth.doubleValue();
			final Matcher countMatcher = STYLE_COLUMN_COUNT.matcher(attrs);
			if (countMatcher.find()) {
				final int count = Integer.parseInt(countMatcher.group(1));
				final Matcher gapMatcher = STYLE_COLUMN_GAP.matcher(attrs);
				final double gap = gapMatcher.find() ? Double.parseDouble(gapMatcher.group(1)) : 0;
				if (count > 1) {
					availableForChildren = (availableForChildren - gap * (count - 1)) / count;
				}
			}
			childWidths.push(Double.valueOf(availableForChildren));
			floatLimits.push(Double.valueOf(floating
					? (explicitWidth == null ? containingWidth : explicitWidth.doubleValue())
					: floatLimits.peek().doubleValue()));
		}
		return false;
	}

	/** flex祖先の中で、2段以上の段組の子孫にtableがあるか。 */
	static boolean hasFlexMulticolTable(final String html) {
		final java.util.ArrayDeque<Boolean> flex = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Boolean> multicolInFlex = new java.util.ArrayDeque<>();
		flex.push(Boolean.FALSE);
		multicolInFlex.push(Boolean.FALSE);
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (flex.size() > 1) {
					flex.pop();
					multicolInFlex.pop();
				}
				continue;
			}
			final String name = tag.group(2);
			final String attrs = String.valueOf(tag.group(3));
			final boolean inFlex = flex.peek().booleanValue() || STYLE_FLEX.matcher(attrs).find();
			boolean inMulticol = multicolInFlex.peek().booleanValue();
			final Matcher count = STYLE_COLUMN_COUNT.matcher(attrs);
			if (inFlex && count.find() && Integer.parseInt(count.group(1)) >= 2) {
				inMulticol = true;
			}
			if (inMulticol && name.equalsIgnoreCase("table")) {
				return true;
			}
			if (!attrs.endsWith("/")) {
				flex.push(Boolean.valueOf(inFlex));
				multicolInFlex.push(Boolean.valueOf(inMulticol));
			}
		}
		return false;
	}

	/** {@code writing-mode} の宣言(body・style属性のどちらも拾う)。 */
	private static final Pattern WRITING_MODE = Pattern.compile("writing-mode\\s*:\\s*([a-z-]+)");

	/**
	 * この文書が<b>直交フロー</b>(軸の異なる{@code writing-mode}の入れ子)を
	 * 含むかを返します(2026-07-28新設)。
	 *
	 * <p>
	 * <b>述語は文書の字面だけから計算する</b>——3,000万文書の掃過では
	 * 1件ずつ人が判断できない(ARCHITECTURE.md §5.13)。生成器は
	 * {@code writing-mode}を{@code body}のスタイル規則と要素の
	 * {@code style}属性にしか書かないので、宣言を全部拾って
	 * <b>縦横の軸が2種類以上現れるか</b>だけを見れば足りる。
	 * </p>
	 *
	 * <p>
	 * 入れ子関係(どちらが祖先か)は見ない。軸が2種類ある時点で、
	 * どこかに必ず直交する境界があるからである。生成器が同じ軸の
	 * 方向違い({@code vertical-rl}と{@code vertical-lr})しか出さない
	 * 文書は、ここでは直交とみなさない——そちらは
	 * {@code SAME_AXIS_DIRECTION_CHANGE}であり、行軸の長さは変わらない。
	 * </p>
	 */
	static boolean hasOrthogonalFlow(final String html) {
		boolean vertical = false, horizontal = false;
		final Matcher m = WRITING_MODE.matcher(html);
		while (m.find()) {
			if (m.group(1).startsWith("vertical")) {
				vertical = true;
			} else {
				horizontal = true;
			}
		}
		// bodyに宣言が無ければ既定(horizontal-tb)が効いている
		return vertical && (horizontal || !BODY_WRITING_MODE.matcher(html).find());
	}

	/** 開始タグ・終了タグと、その{@code style}の{@code writing-mode}。 */
	private static final Pattern TAG_OR_WM = Pattern
			.compile("</(\\w+)>|<(\\w+)([^>]*)>");
	private static final Pattern STYLE_WRITING_MODE = Pattern
			.compile("writing-mode\\s*:\\s*([a-z-]+)");
	/** 縦書きのページ軸に対する明示寸法。 */
	private static final Pattern STYLE_WIDTH = Pattern
			.compile("(?:^|[;\\s\"])width\\s*:\\s*([\\d.]+)(?:pt)?\\s*(?:;|\"|'|$)");

	/**
	 * 軸を変えた子に、基準文字送りの{@value #MIN_PAGE_CHARS}倍未満の
	 * {@code width}を明示した箇所があるか。
	 *
	 * <p>タグの入れ子をたどるため、別の枝にある狭幅とwriting-modeを
	 * 結び付けない。単なる直交フロー、同軸の方向変更、{@code height:0}、
	 * 幅指定のない自動サイズは対象外とする。</p>
	 */
	static boolean hasUntypesettableOrthogonalFlow(final String html) {
		final Matcher bm = BODY_WRITING_MODE.matcher(html);
		final String rootMode = bm.find() ? bm.group(1) : "horizontal-tb";
		final Matcher fm = FONT_SIZE.matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double least = Double.parseDouble(fm.group(1)) * MIN_PAGE_CHARS;
		final java.util.ArrayDeque<String> modes = new java.util.ArrayDeque<>();
		modes.push(rootMode);
		final int bodyAt = html.indexOf("<body");
		final Matcher tag = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			tag.region(bodyAt, html.length());
		}
		while (tag.find()) {
			if (tag.group(1) != null) {
				if (modes.size() > 1) {
					modes.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(tag.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			final String parent = modes.peek();
			String mode = parent;
			final Matcher wm = STYLE_WRITING_MODE.matcher(attrs);
			if (wm.find()) {
				mode = wm.group(1);
			}
			final Matcher width = STYLE_WIDTH.matcher(attrs);
			if (mode.startsWith("vertical-") != parent.startsWith("vertical-") && width.find()
					&& Double.parseDouble(width.group(1)) < least) {
				return true;
			}
			modes.push(mode);
		}
		return false;
	}

	/**
	 * 同じ縦軸の親子でページ進行方向を反転し、反転要素の明示幅が基準文字の
	 * {@value #MIN_PAGE_CHARS}倍未満か、その明示幅を子孫の明示幅が超える箇所を
	 * 含むかを返します。物差しは{@link #isTinyPage}と
	 * {@link #hasUntypesettableFloat}と同じです。
	 *
	 * <p>
	 * 文字列の出現だけではなく、生成器が作るHTMLの入れ子をスタックでたどる。
	 * これにより、別々の枝にある{@code vertical-rl}・{@code vertical-lr}・
	 * {@code width:0}を誤って一つの除外条件に結び付けない。
	 * </p>
	 */
	static boolean hasUntypesettableOppositeProgression(final String html) {
		final Matcher bm = BODY_WRITING_MODE.matcher(html);
		final String rootMode = bm.find() ? bm.group(1) : "horizontal-tb";
		final Matcher fm = FONT_SIZE.matcher(html);
		final double least = fm.find() ? Double.parseDouble(fm.group(1)) * MIN_PAGE_CHARS : 0;
		final java.util.ArrayDeque<String> modes = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Double> reverseLimits = new java.util.ArrayDeque<>();
		modes.push(rootMode);
		reverseLimits.push(Double.valueOf(Double.POSITIVE_INFINITY));
		final int bodyAt = html.indexOf("<body");
		final Matcher m = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			m.region(bodyAt, html.length());
		}
		while (m.find()) {
			if (m.group(1) != null) {
				if (modes.size() > 1) {
					modes.pop();
					reverseLimits.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(m.group(3));
			if (attrs.endsWith("/")) {
				continue;
			}
			final String parent = modes.peek();
			String mode = parent;
			final Matcher wm = STYLE_WRITING_MODE.matcher(attrs);
			if (wm.find()) {
				mode = wm.group(1);
			}
			final Matcher widthMatcher = STYLE_WIDTH.matcher(attrs);
			final Double width = widthMatcher.find() ? Double.valueOf(widthMatcher.group(1)) : null;
			final double inheritedLimit = reverseLimits.peek().doubleValue();
			if (width != null && width.doubleValue() > inheritedLimit) {
				return true;
			}
			double reverseLimit = inheritedLimit;
			if (parent.startsWith("vertical-") && mode.startsWith("vertical-") && !parent.equals(mode)
					&& width != null) {
				if (width.doubleValue() < least) {
					return true;
				}
				reverseLimit = Math.min(reverseLimit, width.doubleValue());
			}
			modes.push(mode);
			reverseLimits.push(Double.valueOf(reverseLimit));
		}
		return false;
	}

	/**
	 * 入れ子をたどって、<b>軸(縦/横)が何回入れ替わるか</b>の最大値を返します。
	 *
	 * <p>
	 * {@link #hasOrthogonalFlow}は「縦と横が同居するか」しか見ないので、
	 * <b>2段</b>(普通の縦書き中の横書き)と<b>3段</b>
	 * ({@code vertical-rl}→{@code horizontal-tb}→{@code vertical-lr})を
	 * 区別できない。除外を後者だけに絞るために深さを数える。
	 * </p>
	 *
	 * <p>
	 * 属性を持たないタグは軸を変えないので、スタックには積むだけでよい。
	 * 自己終了タグ({@code <br/>})は入れ子を作らないため、深さに影響しない
	 * ——{@code style}に{@code writing-mode}を持たないので積んでも同じ値が
	 * 続き、入れ替わり回数は増えない。
	 * </p>
	 */
	static int orthogonalAxisChanges(final String html) {
		final Matcher bm = BODY_WRITING_MODE.matcher(html);
		final boolean rootVertical = bm.find() && bm.group(1).startsWith("vertical");
		final java.util.ArrayDeque<Boolean> axis = new java.util.ArrayDeque<>();
		final java.util.ArrayDeque<Integer> changes = new java.util.ArrayDeque<>();
		axis.push(Boolean.valueOf(rootVertical));
		changes.push(Integer.valueOf(0));
		int worst = 0;
		final int bodyAt = html.indexOf("<body");
		final Matcher m = TAG_OR_WM.matcher(html);
		if (bodyAt >= 0) {
			m.region(bodyAt, html.length());
		}
		while (m.find()) {
			if (m.group(1) != null) {          // 終了タグ
				if (axis.size() > 1) {
					axis.pop();
					changes.pop();
				}
				continue;
			}
			final String attrs = String.valueOf(m.group(3));
			if (attrs.endsWith("/")) {         // 自己終了タグは入れ子を作らない
				continue;
			}
			boolean vertical = axis.peek().booleanValue();
			int n = changes.peek().intValue();
			final Matcher wm = STYLE_WRITING_MODE.matcher(attrs);
			if (wm.find()) {
				final boolean v = wm.group(1).startsWith("vertical");
				if (v != vertical) {
					vertical = v;
					++n;
					worst = Math.max(worst, n);
				}
			}
			axis.push(Boolean.valueOf(vertical));
			changes.push(Integer.valueOf(n));
		}
		return worst;
	}

	/** {@code body}規則の{@code writing-mode}(紙面の軸を決める)。 */
	private static final Pattern BODY_WRITING_MODE = Pattern
			.compile("body\\s*\\{[^}]*writing-mode\\s*:\\s*([a-z-]+)");

	/**
	 * 紙面のページ軸が縦(y)かを返します。{@code body}が縦書きなら
	 * ページ軸は<b>横(x)</b>、行軸が縦(y)になる。
	 */
	static boolean pageAxisIsY(final String html) {
		final Matcher m = BODY_WRITING_MODE.matcher(html);
		return !(m.find() && m.group(1).startsWith("vertical"));
	}

	private static double maxOf(final Pattern pattern, final String html) {
		double max = 0;
		final Matcher m = pattern.matcher(html);
		while (m.find()) {
			max = Math.max(max, Double.parseDouble(m.group(1)));
		}
		return max;
	}

	/** 一意なトークン(行分割で割れないよう空白を含めない)。 */
	private static String token(final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable) {
		final String t = "T" + (counter[0]++);
		tokens.add(t);
		if (inReorderable) {
			reorderable.add(t);
		}
		return t;
	}

	/**
	 * <b>レイアウトを決めるプロパティを直交に振る修飾</b>です(2026-08-02)。
	 *
	 * <p>
	 * 生成器は「パターンごとに書かれたHTML」の集まりで、絶対配置の箱は
	 * 常に素の{@code div}、フロートは常に{@code div}……とパターン内に
	 * プロパティが焼き込まれていた。そのため<b>パターンをまたぐ組み合わせ
	 * (position × display × float × writing-mode)が一度も生成されず</b>、
	 * 200万文書を通しても`position:absolute`かつ`display:flex`が
	 * クラッシュする欠陥に到達できなかった(2026-08-02、yahoo.co.jpで発覚)。
	 * ここで独立に振ることで、パターン数を増やさずに直積を作る。
	 * </p>
	 *
	 * <p>
	 * 内容を消す値(`display:none`・`visibility:hidden`)と紙面外へ飛ばす
	 * 値はSTRICT(内容保存を検査する)では引かない。
	 * </p>
	 */
	private static String layoutMods(final Random r, final Random extensionRandom, final boolean strict) {
		// 半分は素のまま(素朴な文書も出続けるようにする)
		if (r.nextBoolean()) {
			return "";
		}
		final StringBuilder mods = new StringBuilder();
		final String[] displays = strict
				? new String[] { "block", "inline-block", "flex", "grid", "list-item", "table", "inline" }
				: new String[] { "block", "inline-block", "flex", "grid", "list-item", "table", "table-row",
						"table-cell", "inline", "none" };
		if (r.nextBoolean()) {
			mods.append("display:").append(displays[r.nextInt(displays.length)]).append(';');
		}
		if (r.nextBoolean()) {
			// **内容を紙面順から動かす値はSTRICTでは引かない**。絶対配置・
			// ページfloat・脚注は「文書順に1度だけ現れる」というSTRICTの
			// 前提(読み順・複製・消失の検査)を仕様どおりに壊すため、
			// これらはWILD(クラッシュ・停止性・ページ数だけを見る)専用
			final String[] positions = strict ? new String[] { "static", "relative" }
					: new String[] { "static", "relative", "absolute" };
			final String position = positions[r.nextInt(positions.length)];
			mods.append("position:").append(position).append(';');
			// STRICTは「作者が内容を紙面外へ動かす意図を持ち得ない」集合。
			// relative自体は残すが、top/leftを付けると縦書きの開始辺から
			// 全内容を紙面外へ動かせるため、変位はWILDだけで生成する。
			if (!position.equals("static")) {
				final int top = r.nextInt(40) - 10;
				final int left = r.nextInt(40) - 10;
				if (!strict) {
					mods.append("top:").append(top).append("pt;left:").append(left).append("pt;");
				}
			}
		}
		if (r.nextBoolean()) {
			// footnote/top/bottomはページ単位のfloat(2026-07-31・08-02に追加)
			final String[] floats = strict ? new String[] { "none", "left", "right" }
					: new String[] { "none", "left", "right", "footnote", "top", "bottom", "start", "end" };
			mods.append("float:").append(floats[r.nextInt(floats.length)]).append(';');
		}
		if (r.nextBoolean()) {
			mods.append("writing-mode:").append(WRITING_MODES[r.nextInt(WRITING_MODES.length)]).append(';');
		}
		if (!strict && r.nextBoolean()) {
			mods.append("overflow:hidden;visibility:")
					.append(r.nextBoolean() ? "hidden" : "visible").append(';');
		}
		if (mods.length() == 0) {
			return "";
		}
		// 寸法を与えないとflex/gridの経路が痩せるため、たまに付ける
		if (r.nextBoolean()) {
			mods.append("width:").append(20 + r.nextInt(120)).append("pt;");
		}
		final boolean flex = mods.indexOf("display:flex;") >= 0;
		final boolean grid = mods.indexOf("display:grid;") >= 0;
		if ((flex || grid) && extensionRandom != null) {
			appendLayoutProperties(mods, extensionRandom, grid);
		}
		if (extensionRandom != null && extensionRandom.nextInt(4) == 0) {
			mods.append(sizeMods(extensionRandom));
		}
		return mods.toString();
	}

	/** v2のサイズ語彙。8emの床で組版不能な狭幅へ寄せない。 */
	private static String sizeMods(final Random r) {
		return switch (r.nextInt(6)) {
		case 0 -> "width:" + (30 + r.nextInt(51)) + "%;min-width:8em;max-width:90%;";
		case 1 -> "width:8em;min-width:8em;max-width:90%;";
		case 2 -> "width:min-content;min-width:8em;max-width:90%;";
		case 3 -> "width:max-content;min-width:8em;max-width:90%;";
		case 4 -> "width:fit-content(70%);min-width:8em;max-width:90%;";
		default -> "width:calc(35% + 8em);min-width:8em;max-width:90%;";
		};
	}

	/** flex/gridコンテナ固有の方向・折返し・track・gap。 */
	private static void appendLayoutProperties(final StringBuilder mods, final Random r, final boolean grid) {
		if (grid) {
			final String[] tracks = { "repeat(2,minmax(12pt,1fr))", "24pt 1fr",
					"min-content max-content", "fit-content(48pt) minmax(12pt,1fr)" };
			mods.append("grid-template-columns:").append(tracks[r.nextInt(tracks.length)]).append(';');
		} else {
			final String[] directions = { "row", "row-reverse", "column", "column-reverse" };
			final String[] wraps = { "nowrap", "wrap", "wrap-reverse" };
			mods.append("flex-direction:").append(directions[r.nextInt(directions.length)]).append(';')
					.append("flex-wrap:").append(wraps[r.nextInt(wraps.length)]).append(';');
		}
		final String[] gaps = { "0pt", "2pt", ".5em", "calc(1pt + .25em)" };
		mods.append("gap:").append(gaps[r.nextInt(gaps.length)]).append(';');
	}

	/** 生成できるノード種別の数です(STRICTは内容を動かす種別を含まない)。 */
	private static int nodeKinds(final boolean strict) {
		return strict ? 13 : 15;
	}

	private static void appendNode(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict, final List<String> tokens, final int[] counter,
			final Set<String> reorderable, final boolean inReorderable) {
		appendNode(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable, -1);
	}

	private static void appendNode(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict, final List<String> tokens, final int[] counter,
			final Set<String> reorderable, final boolean inReorderable, final int forcedKind) {
		final String mods = layoutMods(r, extensionRandom, strict);
		if (!mods.isEmpty()) {
			// 修飾は包む(パターン側の記述を壊さずに組み合わせを作る)
			//
			// **浮動体にしたなら、その中身は読み順の検査から外す**
			// (2026-08-03)。フロートは入りきらなければ次のページへ送られ、
			// 後続の本文はこのページに残る——文書順とページ順が食い違うのは
			// CSSの仕様どおりで、欠陥ではない。専用の生成経路
			// (`<div style="float:left;width:..">`)は既に
			// {@code inReorderable=true}で子を作っていたが、こちらの修飾
			// 経由でフロートになった場合が漏れていた。実際に strict の
			// seed 6/9/12/15 が「読み順が入れ替わった」と誤検出していた
			// (T28が`float:right`の中にあった)。
			final boolean floated = mods.contains("float:") && !mods.contains("float:none");
			final boolean positioned = mods.contains("position:absolute");
			final boolean flex = mods.contains("display:flex;");
			final boolean grid = mods.contains("display:grid;");
			// reverse系flexは読み順を正当に変える(row/column-reverseは主軸、
			// wrap-reverseは交差軸の行順)——float/absoluteと同じくreorderableへ
			final boolean reversedFlex = mods.contains("-reverse");
			final int items = (flex || grid) && extensionRandom != null ? 2 + extensionRandom.nextInt(3) : 1;
			s.append("<div style=\"").append(mods).append("\">\n");
			// 第1 itemはv1と同じrで生成し、旧系列の消費順を維持する。
			appendPlainNode(s, r, extensionRandom, depth, strict, tokens, counter, reorderable,
					inReorderable || floated || positioned || reversedFlex, forcedKind);
			for (int i = 1; i < items; ++i) {
				appendLayoutItem(s, extensionRandom, depth, strict, tokens, counter, reorderable,
						inReorderable || floated || positioned || reversedFlex, flex, grid);
			}
			s.append("</div>\n");
			return;
		}
		appendPlainNode(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable,
				forcedKind);
	}

	/** flex/gridの追加item。追加系列だけを使い、直接の子を2〜4個にする。 */
	private static void appendLayoutItem(final StringBuilder s, final Random r, final int depth,
			final boolean strict, final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final boolean flex, final boolean grid) {
		s.append("<div data-fuzz-role=\"layout-item\" style=\"");
		if (flex) {
			final String[] bases = { "auto", "content", "24pt", "35%", "8em", "calc(25% + 8pt)" };
			s.append("flex:").append(r.nextInt(3)).append(' ').append(r.nextInt(3)).append(' ')
					.append(bases[r.nextInt(bases.length)]).append(';');
		} else if (grid && r.nextInt(4) == 0) {
			s.append("grid-column:span 2;");
		}
		if (r.nextBoolean()) {
			s.append(sizeMods(r));
		}
		s.append("\">\n");
		appendNode(s, r, r, depth - 1, strict, tokens, counter, reorderable, inReorderable);
		s.append("</div>\n");
	}

	/** 表セルから明示的に生成する複数itemのflex/grid。 */
	private static void appendLayoutContainer(final StringBuilder s, final Random r, final int depth,
			final boolean strict, final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final boolean grid) {
		final StringBuilder mods = new StringBuilder(grid ? "display:grid;" : "display:flex;");
		appendLayoutProperties(mods, r, grid);
		if (r.nextBoolean()) {
			mods.append(sizeMods(r));
		}
		final boolean reversedFlex = !grid && mods.indexOf("-reverse") >= 0;
		s.append("<div style=\"").append(mods).append("\">\n");
		final int items = 2 + r.nextInt(3);
		for (int i = 0; i < items; ++i) {
			appendLayoutItem(s, r, depth, strict, tokens, counter, reorderable,
					inReorderable || reversedFlex, !grid, grid);
		}
		s.append("</div>\n");
	}

	private static void appendPlainNode(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final int forcedKind) {
		if (depth <= 0) {
			s.append("<p id=\"p").append(counter[0]).append("\">")
					.append(token(tokens, counter, reorderable, inReorderable)).append("</p>\n");
			return;
		}
		final int kind = forcedKind >= 0 ? forcedKind : r.nextInt(nodeKinds(strict));
		switch (kind) {
		case 0 -> { // 段落(複数トークン)
			s.append("<p id=\"p").append(counter[0]).append("\">");
			final int n = 1 + r.nextInt(6);
			for (int i = 0; i < n; ++i) {
				s.append(token(tokens, counter, reorderable, inReorderable)).append(' ');
			}
			s.append("</p>\n");
		}
		case 1 -> { // 入れ子ブロック
			s.append("<div style=\"margin:").append(r.nextInt(8)).append("pt;padding:").append(r.nextInt(6))
					.append("pt;border:").append(r.nextInt(3)).append("pt solid black\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 2 -> { // フロート
			s.append("<div style=\"float:").append(r.nextBoolean() ? "left" : "right").append(";width:")
					.append(10 + r.nextInt(120)).append("pt\">\n");
			// フロートの中身は**正当に**読み順が変わる(前の行の横へ持ち上がる)
			// ので、不変条件7の対象から外す
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, true);
			s.append("</div>\n");
		}
		case 3 -> { // 表(rowspan/colspanつき。最大1セルに再帰的な子)
			final int rows = 1 + r.nextInt(4);
			final int cols = 1 + r.nextInt(4);
			// 1表1セルまでにして、入れ子表でもDOM数が指数的に増えないようにする。
			final int richCell = extensionRandom != null && depth > 1 && extensionRandom.nextInt(3) == 0
					? extensionRandom.nextInt(rows * cols) : -1;
			int cell = 0;
			s.append("<table><tbody>\n");
			for (int y = 0; y < rows; ++y) {
				s.append("<tr>");
				for (int x = 0; x < cols; ++x) {
					s.append("<td");
					if (r.nextInt(4) == 0) {
						s.append(" colspan=\"").append(1 + r.nextInt(3)).append('"');
					}
					if (r.nextInt(4) == 0) {
						s.append(" rowspan=\"").append(1 + r.nextInt(3)).append('"');
					}
					s.append('>').append(token(tokens, counter, reorderable, inReorderable));
					if (cell++ == richCell) {
						appendRichCellChild(s, extensionRandom, depth - 1, strict, tokens, counter, reorderable,
								inReorderable);
					}
					s.append("</td>");
				}
				s.append("</tr>\n");
			}
			s.append("</tbody></table>\n");
		}
		case 4 -> { // 段組
			s.append("<div style=\"column-count:").append(2 + r.nextInt(3)).append(";column-gap:")
					.append(r.nextInt(20)).append("pt\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 5 -> { // 書字方向の入れ子
			s.append("<div style=\"writing-mode:").append(WRITING_MODES[r.nextInt(WRITING_MODES.length)])
					.append("\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 6 -> { // インラインブロック・大きいフォント(救済分割の入口)
			s.append("<p><span style=\"display:inline-block;width:").append(10 + r.nextInt(200)).append("pt;height:")
					.append(10 + r.nextInt(200)).append("pt\">").append(token(tokens, counter, reorderable, inReorderable))
					.append("</span></p>\n");
		}
		case 7 -> { // リスト(マーカー・list-style)
			final String tag = r.nextBoolean() ? "ul" : "ol";
			s.append('<').append(tag).append(" style=\"list-style-position:")
					.append(r.nextBoolean() ? "inside" : "outside").append(";list-style-type:")
					.append(new String[] { "disc", "decimal", "lower-roman", "cjk-ideographic", "none" }[r.nextInt(5)])
					.append("\">\n");
			final int n = 1 + r.nextInt(4);
			for (int i = 0; i < n; ++i) {
				s.append("<li>").append(token(tokens, counter, reorderable, inReorderable)).append("</li>\n");
			}
			s.append("</").append(tag).append(">\n");
		}
		case 8 -> { // ルビ(短い単位と、行分割を要する長い単位)
			s.append("<p>");
			final int n = 1 + r.nextInt(3);
			for (int i = 0; i < n; ++i) {
				s.append("<ruby>").append(token(tokens, counter, reorderable, inReorderable)).append("<rt>")
						.append(token(tokens, counter, reorderable, inReorderable)).append("</rt></ruby>");
			}
			if (extensionRandom != null && extensionRandom.nextInt(3) == 0) {
				s.append("<ruby class=\"fuzz-long-ruby\">");
				final int baseLength = 8 + extensionRandom.nextInt(9);
				for (int i = 0; i < baseLength; ++i) {
					if (i > 0) {
						s.append(' ');
					}
					s.append(token(tokens, counter, reorderable, inReorderable));
				}
				s.append("<rt>");
				final int rubyLength = 8 + extensionRandom.nextInt(9);
				for (int i = 0; i < rubyLength; ++i) {
					if (i > 0) {
						s.append(' ');
					}
					s.append(token(tokens, counter, reorderable, inReorderable));
				}
				s.append("</rt></ruby>");
			}
			s.append("</p>\n");
		}
		case 9 -> { // 置換要素(画像。救済分割の本来の動機)
			// altは描かれないのでトークンにしない(オラクルが誤検出する)
			s.append("<p><img src=\"").append(RED_PNG_URI).append("\" alt=\"img\"")
					.append(" style=\"display:").append(r.nextBoolean() ? "block" : "inline")
					.append(";width:").append(10 + r.nextInt(250)).append("pt;height:")
					.append(10 + r.nextInt(250)).append("pt\" /></p>\n");
		}
		case 10 -> { // clear と極端なフォントサイズ(floatと絡ませる)
			s.append("<div style=\"clear:")
					.append(new String[] { "left", "right", "both" }[r.nextInt(3)]).append("\">")
					.append("<span style=\"font-size:").append(6 + r.nextInt(40)).append("pt\">")
					.append(token(tokens, counter, reorderable, inReorderable)).append("</span></div>\n");
		}
		case 11 -> { // avoid ヒント(改ページ判定を揺さぶる)
			s.append("<div style=\"page-break-inside:avoid;margin:").append(r.nextInt(6)).append("pt\">\n");
			appendChildren(s, r, extensionRandom, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 12 -> { // フォーム部品(値テキストは描画されないためトークンにしない)
			s.append("<form>");
			final int n = 1 + r.nextInt(3);
			for (int i = 0; i < n; ++i) {
				switch (r.nextInt(6)) {
				case 0 -> s.append("<input type=\"text\" value=\"x\" size=\"")
						.append(1 + r.nextInt(20)).append("\" />");
				case 1 -> s.append("<input type=\"checkbox\" checked=\"checked\" />");
				case 2 -> s.append("<input type=\"radio\" />");
				case 3 -> s.append("<textarea rows=\"").append(1 + r.nextInt(4)).append("\">x</textarea>");
				case 4 -> s.append("<select><option>x</option><option>y</option></select>");
				default -> s.append("<button type=\"button\">")
						.append(token(tokens, counter, reorderable, inReorderable)).append("</button>");
				}
			}
			s.append("</form>\n");
		}
		case 13 -> { // WILDのみ: 絶対配置
			s.append("<div style=\"position:absolute;top:").append(r.nextInt(300) - 50).append("pt;left:")
					.append(r.nextInt(300) - 50).append("pt\">").append("X").append("</div>\n");
		}
		default -> { // WILDのみ: 強制改ページ・非表示・overflowと複雑な部分木
			if (extensionRandom == null) {
				// v1互換: 内容は固定のX
				s.append("<div style=\"page-break-before:always;visibility:")
						.append(r.nextBoolean() ? "hidden" : "visible").append(";overflow:hidden\">X</div>\n");
			} else {
				s.append("<div data-fuzz-role=\"wild-complex\" style=\"page-break-before:always;visibility:")
						.append(r.nextBoolean() ? "hidden" : "visible").append(";overflow:hidden\">\n");
				final int n = 2 + extensionRandom.nextInt(2);
				for (int i = 0; i < n; ++i) {
					appendNode(s, extensionRandom, extensionRandom, Math.max(1, depth - 1), strict, tokens, counter,
							reorderable, inReorderable);
				}
				s.append("</div>\n");
			}
		}
		}
	}

	/** 表セル内に段組・float・flex/grid・list・image・入れ子表を1つ生成する。 */
	private static void appendRichCellChild(final StringBuilder s, final Random r, final int depth,
			final boolean strict, final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable) {
		s.append("<div data-fuzz-role=\"cell-child\">\n");
		switch (r.nextInt(7)) {
		case 0 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 4);
		case 1 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 2);
		case 2 -> appendLayoutContainer(s, r, depth, strict, tokens, counter, reorderable, inReorderable, false);
		case 3 -> appendLayoutContainer(s, r, depth, strict, tokens, counter, reorderable, inReorderable, true);
		case 4 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 7);
		case 5 -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 9);
		default -> appendPlainNode(s, r, r, depth, strict, tokens, counter, reorderable, inReorderable, 3);
		}
		s.append("</div>\n");
	}

	private static void appendChildren(final StringBuilder s, final Random r, final Random extensionRandom,
			final int depth, final boolean strict, final List<String> tokens, final int[] counter,
			final Set<String> reorderable, final boolean inReorderable) {
		final int n = 1 + r.nextInt(3);
		for (int i = 0; i < n; ++i) {
			appendNode(s, r, extensionRandom, depth - 1, strict, tokens, counter, reorderable, inReorderable);
		}
	}
}
