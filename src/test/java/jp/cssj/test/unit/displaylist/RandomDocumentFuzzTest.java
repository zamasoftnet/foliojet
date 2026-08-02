package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	/** 既定のシード数(回帰用。掃過は -Dfoliojet.fuzzSeeds で増やす)。 */
	private static final int DEFAULT_SEEDS = 60;

	/** 1文書あたりの上限時間。通常は1秒未満で終わる。 */
	private static final long WATCHDOG_MS = Long.getLong("foliojet.fuzzWatchdogMs", 30_000L);

	/** ページ数の上限。生成する内容量から見て明らかに過大な値。 */
	private static final int MAX_PAGES = 300;

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
	 * このシード数までは、シードごとに成果物(HTML・表示リスト)を残す。
	 * これを超える掃過では使い回して捨てる——数百万文書を残すと
	 * ディスクが保たないため。生成器は決定的なので、失敗したシードは
	 * 同じシードで再実行すれば再現できる。
	 */
	private static final int KEEP_ARTIFACTS_BELOW = 20_000;

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
	 * <b>組版できない幅の浮動体</b>から紙面外配置が出た、という印
	 * (2026-07-29新設)。失敗ではなく<b>除外</b>として扱う。
	 *
	 * <p>
	 * 判定は{@link #hasUntypesettableFloat}——明示した寸法が基準フォントの
	 * 8倍(=約8文字)未満の浮動体があること。欄が組版できない幅なら中身は
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

	/** 失敗メッセージから種別(defect class)を粗く取り出す。 */
	static String classify(final Throwable t) {
		if (t instanceof ExcludedByUntypesettableFloat) {
			return "(除外)組版できない幅の浮動体";
		}
		if (t instanceof ExcludedByOrthogonalLineAxis) {
			return "(除外)直交フローの行軸はみ出し";
		}
		if (t instanceof ExcludedByNestedOrthogonalFlow) {
			return "(除外)直交フロー3段以上の入れ子";
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
			chain.append(String.valueOf(c.getMessage())).append(' ');
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

	/** 集計モードの並列掃過。判定は{@link #checkOne}で共通。 */
	private void sweepParallel(final boolean strict, final int seeds) throws Exception {
		final int threads = sweepThreads();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> classCount =
				new java.util.concurrent.ConcurrentHashMap<>();
		final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Integer>> seedsOf =
				new java.util.concurrent.ConcurrentHashMap<>();
		final int from = seedFrom();
		final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger(from);
		final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
		final long began = System.currentTimeMillis();
		final Thread[] workers = new Thread[threads];
		for (int w = 0; w < threads; ++w) {
			workers[w] = new Thread(() -> {
				for (;;) {
					final int seed = next.getAndIncrement();
					if (seed >= from + seeds) {
						return;
					}
					final int n = done.incrementAndGet();
					if (n % PROGRESS_EVERY == 0) {
						System.out.println("[fuzzProgress] " + (strict ? "strict" : "wild") + " " + n + "/" + seeds
								+ " 経過" + ((System.currentTimeMillis() - began) / 1000) + "s "
								+ new java.util.TreeMap<>(classCount));
					}
					try {
						checkOne(seed, strict);
					} catch (final Throwable t) {
						final String k = classify(t);
						classCount.computeIfAbsent(k, x -> new java.util.concurrent.atomic.AtomicInteger())
								.incrementAndGet();
						final java.util.List<Integer> lst = seedsOf.computeIfAbsent(k,
								x -> java.util.Collections.synchronizedList(new ArrayList<>()));
						synchronized (lst) {
							if (lst.size() < 8) {
								lst.add(seed);
							}
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
		System.out.println("[fuzzReport] mode=" + (strict ? "strict" : "wild") + " seeds=" + seeds
				+ (from == 0 ? "" : " from=" + from) + " threads="
				+ threads + " elapsed=" + (ms / 1000) + "s (" + String.format("%.2f", ms / (double) seeds)
				+ " ms/文書)");
		if (classCount.isEmpty()) {
			System.out.println("[fuzzReport]   失敗なし");
		}
		for (final String k : new java.util.TreeSet<>(classCount.keySet())) {
			System.out.println("[fuzzReport]   " + k + " : " + classCount.get(k).get() + "件 seeds=" + seedsOf.get(k));
		}
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
		final java.util.List<String> features = java.util.List.of("display:flex", "display:grid",
				"display:inline-block", "display:list-item", "display:table", "display:none", "position:absolute",
				"position:relative", "float:left", "float:right", "float:footnote", "float:top", "float:bottom",
				"writing-mode:vertical", "overflow:hidden", "<table", "<ul", "<ol", "<ruby", "<img", "<form",
				"<input", "<select", "<textarea", "<button", "page-break-before", "page-break-inside",
				"list-style-type", "clear:", "columns");
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
		System.out.println("[fuzzReport] mode=" + (strict ? "strict" : "wild") + " 機能被覆: 1文書あたり平均"
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
		// **特定のシードだけを走らせる入口**(2026-07-27新設)。
		// 大規模な掃過では成果物を使い回して捨てるので、後から
		// 「seed 27648で内容が消えた」と分かっても再現できなかった。
		// 生成器は決定的なので、シードを指定すれば必ず同じ文書になる。
		final String only = System.getProperty("foliojet.fuzzOnlySeed");
		if (only != null) {
			final int seed = Integer.parseInt(only);
			System.out.println("[fuzzOnly] mode=" + (strict ? "strict" : "wild") + " seed=" + seed);
			try {
				checkOne(seed, strict);
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
			System.out.println("[fuzzReport] mode=" + (strict ? "strict" : "wild") + " seeds=" + seeds);
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

	private void checkOne(final int seed, final boolean strict) throws Exception {
		final Generated doc = generate(seed, strict);
		// 長時間掃過(数百万文書)では、シードごとの成果物を残すとディスクが
		// 保たない。失敗したシードは同じシードで再実行すれば必ず再現する
		// (生成器は決定的)ので、成功したシードの成果物は捨ててよい。
		// 保存ディレクトリもシードで分けず使い回す(2026-07-26)
		final boolean keep = System.getProperty("foliojet.fuzzOnlySeed") != null || !reportMode()
				|| seedCount() <= KEEP_ARTIFACTS_BELOW;
		final String slot = keep ? String.valueOf(seed) : Thread.currentThread().getName();
		final File html = new File(workDir(), (strict ? "strict" : "wild") + "-" + slot + ".html");
		final File outDir = new File(workDir(), "dl-" + (strict ? "strict" : "wild") + "-" + slot);
		checkDocument(doc, html, outDir, strict, "fuzz-" + seed);
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
	static void checkDocument(final Generated doc, final File html, final File outDir, final boolean strict,
			final String workerName) throws Exception {
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
				convert(html, outDir, session);
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
		assertTrue("ページ数が過大 " + pages.length + " (" + html + ")", pages.length <= MAX_PAGES);

		// **WILDはここまで**(2026-07-28)。不変条件4〜8はSTRICT限定なので、
		// 以下の読み込み・解析はWILDでは結果を一切使わない——従来は全ページを
		// 読んで解析してから捨てていた(早期returnは解析の**後**にあった)。
		// 実測では誤差程度の差しか出なかったが、捨てる仕事を残す理由もない
		if (!strict) {
			return;
		}

		java.util.Arrays.sort(pages);
		final Set<String> seen = new LinkedHashSet<>();
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
					seen.add(m.group(1));
					firstPage.putIfAbsent(m.group(1), i);
					if (!artifact) {
						countTokens(m.group(1), onThisPage);
					}
					if (m.group(2) != null) {
						// ルビのふりがな側
						seen.add(m.group(2));
						firstPage.putIfAbsent(m.group(2), i);
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
			fail("白紙ページ " + blanks + " (" + html + ")");
		}
		// 不変条件4: 内容が失われない
		final String all = String.join("", seen);
		final List<String> lost = new ArrayList<>();
		for (final String token : doc.tokens) {
			if (!all.contains(token)) {
				lost.add(token);
			}
		}
		assertTrue("内容が失われた " + lost + " (" + html + ")", lost.isEmpty());
		// 不変条件8: 内容が複製されない(まだ報告のみ)
		checkNoDuplication(doc, drawn, html);
		// 不変条件6: 説明のつかない紙面外への配置がない
		assertNoUnexplainedOffPage(doc, pages, html);
		// 不変条件7: 読み順が保たれる(まだ報告のみ)
		checkReadingOrder(doc, firstPage, html);
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
		int prev = -1;
		String prevToken = null;
		for (final String t : doc.orderedTokens()) {
			final Integer at = firstPage.get(t);
			if (at == null) {
				continue; // 消失は不変条件4の担当
			}
			if (at.intValue() < prev) {
				throw new AssertionError("読み順が入れ替わった: 文書順では" + prevToken + "→" + t + " だが、" + t
						+ "はページ" + (at.intValue() + 1) + "、" + prevToken + "はページ" + (prev + 1) + " (" + html + ")");
			}
			prev = at.intValue();
			prevToken = t;
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
		final double slack = 2 * doc.maxExplicitSize();
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
				final Matcher m = POS_IN_DUMP.matcher(raw);
				while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				// 紙面をまるごと1枚分はみ出して初めて数える(端の1ptは論外に
				// してよいが、そこを厳しくすると罫線の丸めで揺れる)
				final double overX = Math.max(-x - doc.pageWidth(), x - 2 * doc.pageWidth());
				final double overY = Math.max(-y - doc.pageHeight(), y - 2 * doc.pageHeight());
				final double over = Math.max(overX, overY);
				if (over > worst) {
					worst = over;
					worstIsY = overY >= overX;
					worstAt = "x=" + x + " y=" + y + " " + page.getName();
				}
				}
			}
		}
		if (worst <= slack) {
			return;
		}
		final String detail = "紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(doc.pageWidth()) + "x"
				+ Math.round(doc.pageHeight()) + "pt, 最大明示サイズ" + Math.round(doc.maxExplicitSize()) + "pt, " + worstAt
				+ ") (" + html + ")";
		// 白紙ページと同じ除外基準を適用する(2026-07-26)。版面が破綻して
		// いる文書ではエンジンの振る舞いを問えない
		if (doc.beyondEngineControl()) {
			throw new ExcludedByOversizedBox(detail);
		}
		// 組版できない幅の浮動体からの溢れも除外(2026-07-29)。
		// {@link ExcludedByUntypesettableFloat}に理由を書いた
		if (hasUntypesettableFloat(doc.html())) {
			throw new ExcludedByUntypesettableFloat(detail + " [組版できない幅の浮動体]");
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

	private static void convert(final File html, final File outDir) throws Exception {
		convert(html, outDir, new DirectSession[1]);
	}

	/**
	 * @param sessionOut watchdogが中断要求を出せるよう、生成したセッションを
	 *                   ここへ書き出す。<b>放置ではなく実際に止めるため</b>
	 *                   (2026-07-27)——止められないスレッドはレイアウト1件分の
	 *                   ヒープと64MBのスタック予約を抱えたまま残り、掃過が
	 *                   自己増幅的に詰まる
	 */
	private static void convert(final File html, final File outDir, final DirectSession[] sessionOut)
			throws Exception {
		// 出力先はスレッド単位。システムプロパティだとプロセス全体で共有され、
		// 並列掃過でダンプ先が互いに上書きされる(2026-07-26)
		try (AutoCloseable scope = DisplayListDumper.scopedDir(outDir.getPath())) {
			final File pdf = new File(outDir, "out.pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				sessionOut[0] = session;
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, html, "text/html", null);
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

	/** 表示リストの1行から描画位置を拾う。 */
	private static final Pattern POS_IN_DUMP = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+)");

	/** 生成器が出す明示サイズ({@code width:120pt}等)。 */
	static final Pattern EXPLICIT_SIZE = Pattern.compile("(?:width|height):(\\d+)pt");

	private static final Pattern EXPLICIT_WIDTH = Pattern.compile("width:(\\d+)pt");
	private static final Pattern EXPLICIT_HEIGHT = Pattern.compile("height:(\\d+)pt");
	private static final Pattern FONT_SIZE = Pattern.compile("(?:font-size:|font:normal )(\\d+)pt");
	private static final Pattern PAGE_MARGIN = Pattern.compile("@page\\{margin:(\\d+)pt");

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
		final Random r = new Random(seed * 7919L + (strict ? 1 : 2));
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
				appendNode(body, r, 3, strict, tokens, counter, reorderable, false, order[i % kinds]);
			}
		} else {
			final int roots = 1 + r.nextInt(4);
			for (int i = 0; i < roots; ++i) {
				appendNode(body, r, 3, strict, tokens, counter, reorderable, false);
			}
		}
		final int[] size = PAGE_SIZES[r.nextInt(PAGE_SIZES.length)];
		final StringBuilder s = new StringBuilder();
		s.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
		s.append("<?jp.cssj.property name=\"output.page-width\" value=\"").append(size[0]).append("pt\"?>\n");
		s.append("<?jp.cssj.property name=\"output.page-height\" value=\"").append(size[1]).append("pt\"?>\n");
		s.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
		s.append("<title>fuzz ").append(seed).append("</title>\n<style>\n");
		s.append("@page{margin:").append(r.nextInt(3) * 5).append("pt}\n");
		s.append("body{margin:0;font:normal ").append(6 + r.nextInt(8)).append("pt/1.2 serif;writing-mode:")
				.append(WRITING_MODES[r.nextInt(WRITING_MODES.length)]).append("}\n");
		s.append("p,div,td{margin:0;padding:0}\n");
		s.append("table{border-collapse:").append(r.nextBoolean() ? "collapse" : "separate")
				.append(";table-layout:").append(r.nextBoolean() ? "auto" : "fixed").append("}\n");
		s.append("td{border:1pt solid black}\n");
		s.append("</style></head><body>\n");
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
	 * 基準は{@link #isTinyPage}と同じ——明示した寸法が基準フォントサイズの
	 * {@value #MIN_PAGE_CHARS}倍(=約{@value #MIN_PAGE_CHARS}文字)に
	 * 満たない浮動体があること。紙面が組版できない大きさなら除外する、と
     * 決めたのと同じ理由で、<b>欄が組版できない幅なら中身は必ず溢れる</b>。
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
	private static String layoutMods(final Random r, final boolean strict) {
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
			if (!position.equals("static")) {
				mods.append("top:").append(r.nextInt(40) - 10).append("pt;left:")
						.append(r.nextInt(40) - 10).append("pt;");
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
		return mods.toString();
	}

	/** 生成できるノード種別の数です(STRICTは内容を動かす種別を含まない)。 */
	private static int nodeKinds(final boolean strict) {
		return strict ? 13 : 15;
	}

	private static void appendNode(final StringBuilder s, final Random r, final int depth, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable) {
		appendNode(s, r, depth, strict, tokens, counter, reorderable, inReorderable, -1);
	}

	private static void appendNode(final StringBuilder s, final Random r, final int depth, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final int forcedKind) {
		final String mods = layoutMods(r, strict);
		if (!mods.isEmpty()) {
			// 修飾は包む(パターン側の記述を壊さずに組み合わせを作る)
			s.append("<div style=\"").append(mods).append("\">\n");
			appendPlainNode(s, r, depth, strict, tokens, counter, reorderable, inReorderable, forcedKind);
			s.append("</div>\n");
			return;
		}
		appendPlainNode(s, r, depth, strict, tokens, counter, reorderable, inReorderable, forcedKind);
	}

	private static void appendPlainNode(final StringBuilder s, final Random r, final int depth, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable, final int forcedKind) {
		if (depth <= 0) {
			s.append("<p>").append(token(tokens, counter, reorderable, inReorderable)).append("</p>\n");
			return;
		}
		final int kind = forcedKind >= 0 ? forcedKind : r.nextInt(nodeKinds(strict));
		switch (kind) {
		case 0 -> { // 段落(複数トークン)
			s.append("<p>");
			final int n = 1 + r.nextInt(6);
			for (int i = 0; i < n; ++i) {
				s.append(token(tokens, counter, reorderable, inReorderable)).append(' ');
			}
			s.append("</p>\n");
		}
		case 1 -> { // 入れ子ブロック
			s.append("<div style=\"margin:").append(r.nextInt(8)).append("pt;padding:").append(r.nextInt(6))
					.append("pt;border:").append(r.nextInt(3)).append("pt solid black\">\n");
			appendChildren(s, r, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 2 -> { // フロート
			s.append("<div style=\"float:").append(r.nextBoolean() ? "left" : "right").append(";width:")
					.append(10 + r.nextInt(120)).append("pt\">\n");
			// フロートの中身は**正当に**読み順が変わる(前の行の横へ持ち上がる)
			// ので、不変条件7の対象から外す
			appendChildren(s, r, depth, strict, tokens, counter, reorderable, true);
			s.append("</div>\n");
		}
		case 3 -> { // 表(rowspan/colspanつき)
			final int rows = 1 + r.nextInt(4);
			final int cols = 1 + r.nextInt(4);
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
					s.append('>').append(token(tokens, counter, reorderable, inReorderable)).append("</td>");
				}
				s.append("</tr>\n");
			}
			s.append("</tbody></table>\n");
		}
		case 4 -> { // 段組
			s.append("<div style=\"column-count:").append(2 + r.nextInt(3)).append(";column-gap:")
					.append(r.nextInt(20)).append("pt\">\n");
			appendChildren(s, r, depth, strict, tokens, counter, reorderable, inReorderable);
			s.append("</div>\n");
		}
		case 5 -> { // 書字方向の入れ子
			s.append("<div style=\"writing-mode:").append(WRITING_MODES[r.nextInt(WRITING_MODES.length)])
					.append("\">\n");
			appendChildren(s, r, depth, strict, tokens, counter, reorderable, inReorderable);
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
		case 8 -> { // ルビ(2026-07-25に行内の注釈付きテキストへ仕様変更)
			s.append("<p>");
			final int n = 1 + r.nextInt(3);
			for (int i = 0; i < n; ++i) {
				s.append("<ruby>").append(token(tokens, counter, reorderable, inReorderable)).append("<rt>")
						.append(token(tokens, counter, reorderable, inReorderable)).append("</rt></ruby>");
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
			appendChildren(s, r, depth, strict, tokens, counter, reorderable, inReorderable);
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
		default -> { // WILDのみ: 強制改ページ・非表示・overflow
			s.append("<div style=\"page-break-before:always;visibility:")
					.append(r.nextBoolean() ? "hidden" : "visible").append(";overflow:hidden\">X</div>\n");
		}
		}
	}

	private static void appendChildren(final StringBuilder s, final Random r, final int depth, final boolean strict,
			final List<String> tokens, final int[] counter, final Set<String> reorderable,
			final boolean inReorderable) {
		final int n = 1 + r.nextInt(3);
		for (int i = 0; i < n; ++i) {
			appendNode(s, r, depth - 1, strict, tokens, counter, reorderable, inReorderable);
		}
	}
}
