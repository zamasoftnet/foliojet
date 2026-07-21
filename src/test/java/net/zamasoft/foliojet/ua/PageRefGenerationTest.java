package net.zamasoft.foliojet.ua;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import junit.framework.TestCase;

/**
 * {@link PageRef}の世代混在バグ(重複idの出現数がパスをまたいで
 * 減った際、stale化した古いFragmentが{@link PageRef#getFragments(URI)}
 * に残り続ける)の修正の検証。target-counter()/target-counters()の
 * 実装にあたり、PageRefを再利用するにあたって修正した。
 */
public class PageRefGenerationTest extends TestCase {
	private static Counter[] counters(int page) {
		return new Counter[] { new Counter("page", page) };
	}

	/** 1パス前(意図的なforward-reference)のFragmentは読める。 */
	public void testOnePassBehindFragmentIsKept() {
		PageRef pageRef = new PageRef();
		URI uri = URI.create("#x");

		pageRef.reset(); // 1パス目開始
		pageRef.addFragment(uri, counters(3));

		pageRef.reset(); // 2パス目開始、まだ#xは未訪問
		PageRef.Fragment frag = pageRef.getFragment(uri);
		assertNotNull(frag);
		assertEquals(3, frag.getCounterValue("page"));
	}

	/** 2パス以上前のFragmentはstaleとしてプルーニングされる。 */
	public void testTwoPassesStaleFragmentIsPruned() {
		PageRef pageRef = new PageRef();
		URI uri = URI.create("#x");

		pageRef.reset(); // 1パス目
		pageRef.addFragment(uri, counters(3));

		pageRef.reset(); // 2パス目、#xは未訪問のまま(1パス目の値のみ)
		pageRef.reset(); // 3パス目、依然未訪問 -> 1パス目の値は2世代前でstale

		PageRef.Fragment frag = pageRef.getFragment(uri);
		assertNull(frag);
	}

	/**
	 * 重複idの出現数がパスをまたいで減った場合、はみ出た古いFragmentが
	 * {@link PageRef#getFragments(URI)}(target-counters()のdedupパス)に
	 * 混入しないことを確認する。
	 */
	public void testShrinkingDuplicateIdCountPrunesOrphans() {
		PageRef pageRef = new PageRef();
		URI uri = URI.create("#d");

		pageRef.reset(); // 1パス目: #dが5回出現
		for (int i = 1; i <= 5; ++i) {
			pageRef.addFragment(uri, counters(i));
		}

		pageRef.reset(); // 2パス目: #dが3回しか出現しない(構造が変わったケースを模擬)
		for (int i = 1; i <= 3; ++i) {
			pageRef.addFragment(uri, counters(i * 10));
		}
		// この時点ではuid4,5は「今回パスでまだ未訪問」と区別できないため
		// (意図的なforward-referenceと同じ形)残っている。3パス目に入って
		// 初めて2世代前と確定し、プルーニング対象になる。
		pageRef.reset(); // 3パス目

		Collection<?> frags = pageRef.getFragments(uri);
		assertNotNull(frags);
		assertEquals("2世代以上前のuid4,5は除去され、今回の3件のみ残るはず", 3, frags.size());
		for (Iterator<?> i = frags.iterator(); i.hasNext();) {
			PageRef.Fragment f = (PageRef.Fragment) i.next();
			assertTrue("stale(1桁台)な値が残っていないこと", f.getCounterValue("page") >= 10);
		}
	}
}
