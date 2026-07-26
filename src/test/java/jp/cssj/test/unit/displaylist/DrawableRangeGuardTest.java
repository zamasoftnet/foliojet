package jp.cssj.test.unit.displaylist;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;

/**
 * <b>表示リストへ載る座標の範囲ガード</b>が実際に効いていることを固定します
 * (2026-07-26新設)。
 *
 * <p>
 * このガードは3000文書のランダム掃過で<b>一度も発火しませんでした</b>。
 * 休眠している検出器は「守っている」のか「そもそも到達しない死んだコード」
 * なのか区別がつかないので、ここで<b>発火することそのもの</b>を回帰として
 * 固定します。
 * </p>
 *
 * <p>
 * <b>何を守っているか。</b>{@link LayoutUtils#isNone(double)}は番兵値
 * そのものとしか一致しないため、{@code NONE + 10}のように一度でも算術を
 * 通った番兵も、{@code NaN}も素通りします。どちらも例外にはならず、
 * <b>内容が紙面のどこにも現れないまま静かに欠落する</b>形で出ます。
 * 帳票用途では最悪の壊れ方なので、範囲で弾いています。
 * </p>
 *
 * <p>
 * <b>なぜ今これを置くか。</b>次の機能であるCSS Gridは{@code fr}単位の
 * 割り算を持ち込みます。利用可能量が0のときの{@code 0/0}はNaNなので、
 * この検出器はGridの実装前に置いておく価値があります。
 * </p>
 */
public class DrawableRangeGuardTest extends TestCase {
	public DrawableRangeGuardTest(String name) {
		super(name);
	}

	/** 何も描かないダミー。ガードは座標だけを見るので中身は要らない。 */
	private static final Drawable NOOP = new Drawable() {
		public void draw(GC gc, double x, double y) {
			// 描かない
		}

		public String describe() {
			return "Noop";
		}
	};

	/** そもそもassertが有効でないとこのテストは無意味なので、先に確かめる。 */
	public void testAssertionsAreEnabled() {
		boolean enabled = false;
		assert enabled = true;
		assertTrue("assertが無効。-ea なしではガードの回帰は検証できない", enabled);
	}

	public void testRejectsNaN() {
		assertRejected(Double.NaN, 0);
		assertRejected(0, Double.NaN);
	}

	public void testRejectsInfinity() {
		assertRejected(Double.POSITIVE_INFINITY, 0);
		assertRejected(0, Double.NEGATIVE_INFINITY);
	}

	/** 番兵そのもの。従来の{@code isNone}でも弾けていた。 */
	public void testRejectsSentinel() {
		assertRejected(LayoutUtils.NONE, 0);
		assertRejected(0, LayoutUtils.NONE);
	}

	/**
	 * <b>本題。</b>番兵に乗除算を施した値は{@code isNone}を素通りするが、
	 * 10<sup>307</sup>級のゴミ座標であることに変わりはない。
	 *
	 * <p>
	 * <b>加算では逃げられない</b>ことも同時に固定する。10<sup>308</sup>付近の
	 * doubleの刻み幅(ULP)は10<sup>292</sup>程度あるので、{@code NONE + 10}は
	 * <b>値が1ビットも変わらず</b>、番兵のまま残る。逃がすのは倍率が
	 * 変わる演算(スケール・折半・符号反転)だけ——つまり実際に危ないのは
	 * 「番兵に座標変換を掛けた」経路である。
	 * </p>
	 */
	public void testRejectsSentinelAfterArithmetic() {
		assertTrue("前提が崩れた: この規模のdoubleでは加算は値を変えないはず",
				LayoutUtils.isNone(LayoutUtils.NONE + 10));

		final double halved = LayoutUtils.NONE / 2;
		assertFalse("前提が崩れた: 番兵の折半がまだ番兵と等しい", LayoutUtils.isNone(halved));
		assertRejected(halved, 0);
		assertRejected(0, -LayoutUtils.NONE);
		assertRejected(LayoutUtils.NONE * 0.5, 0);
	}

	/** 「上限なし」の制約を位置へ漏らした場合。 */
	public void testRejectsMaxValue() {
		assertRejected(Double.MAX_VALUE, 0);
	}

	/** 正当な座標は当然通る。負の座標も正当(はみ出し・裁ち落とし)。 */
	public void testAcceptsRealisticCoordinates() {
		final Drawer drawer = new Drawer(0);
		drawer.visitDrawable(NOOP, 0, 0);
		drawer.visitDrawable(NOOP, 595.276, 841.89);
		drawer.visitDrawable(NOOP, -100, -100);
		// PDFのページ寸法の上限(200インチ)。これは通らなければならない
		drawer.visitDrawable(NOOP, 14400, 14400);
	}

	/**
	 * <b>このガードで守れない穴を明示する。</b>{@code NONE - NONE}は0になる。
	 * 番兵同士の差(「未定義の位置 − 未定義の原点」)は<b>もっともらしい
	 * 座標</b>に化けるので、範囲では絶対に検出できない。
	 *
	 * <p>
	 * これを検出するには番兵を{@code NaN}にする(NaNは伝播するので差も
	 * NaNになる)しかないが、{@code NONE}は{@code ==}比較で使われている
	 * ため型を変えるのに広い改修が要る。ここでは<b>穴の所在を記録する</b>
	 * に留める。ガードの守備範囲を過大評価しないこと。
	 * </p>
	 */
	public void testKnownBlindSpotSentinelDifference() {
		final double difference = LayoutUtils.NONE - LayoutUtils.NONE;
		assertEquals("前提が崩れた", 0.0, difference, 0.0);
		// 通ってしまう。これは既知の穴であって、テストの失敗ではない
		new Drawer(0).visitDrawable(NOOP, difference, difference);
	}

	private static void assertRejected(double x, double y) {
		final Drawer drawer = new Drawer(0);
		try {
			drawer.visitDrawable(NOOP, x, y);
		} catch (final AssertionError expected) {
			return;
		}
		fail("異常な描画位置が素通りした: x=" + x + " y=" + y);
	}
}
