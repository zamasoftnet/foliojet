package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * {@link LayoutSource}のtext payload spill(E-6増分3b-2、2026-07-24
 * 新設)の単体テストです。決定的なbytes予算制(inline保持量+新規分が
 * 予算内ならinline、超過ならspill)・Spilledのfresh decode・
 * findCharsAtの挙動不変(utf16Lengthのheapメタデータ)・compactによる
 * 予算解放・closeの冪等な一時ファイル削除を固定する。
 */
public class LayoutSourceTextSpillTest extends TestCase {
	/** 予算内はinline、超過後の新規追記はspillされる(イベント境界は不変)。 */
	public void testBudgetSpillDecision() throws Exception {
		try (LayoutSource log = new LayoutSource(8)) {
			// 4 chars = 8 bytes → ちょうど予算内(inline)
			final long first = log.appendChars(0, "abcd".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, first) instanceof LayoutSource.TextPayload.Inline);
			assertNull("予算内ではspillストアは生成されないはず", log.textSpillForTest());

			// さらに4 chars → 8 + 8 > 8 でspill
			final long second = log.appendChars(4, "efgh".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, second) instanceof LayoutSource.TextPayload.Spilled);
			assertNotNull(log.textSpillForTest());

			// 1 Chars = 1 payload(分割・結合されない)
			assertEquals(4, payloadOf(log, first).utf16Length());
			assertEquals(4, payloadOf(log, second).utf16Length());

			// decodeは常にfresh(内容一致・毎回別インスタンス)
			final LayoutSource.TextPayload spilled = payloadOf(log, second);
			final char[] a = spilled.freshChars();
			final char[] b = spilled.freshChars();
			assertEquals("efgh", new String(a));
			assertEquals("efgh", new String(b));
			assertNotSame(a, b);
			final LayoutSource.TextPayload inline = payloadOf(log, first);
			assertNotSame(inline.freshChars(), inline.freshChars());

			// findCharsAtはSpilled側にもheapメタデータ(utf16Length)で効く
			assertEquals(first, log.findCharsAt(2));
			assertEquals(second, log.findCharsAt(5));
			assertEquals(-1, log.findCharsAt(8));
		}
	}

	/** 予算より大きい単一イベントは(inline保持量ゼロでも)spillされる。 */
	public void testOversizeSingleEventSpills() throws Exception {
		try (LayoutSource log = new LayoutSource(8)) {
			final long id = log.appendChars(0, "abcdefgh".toCharArray(), 0, 8, false);
			assertTrue(payloadOf(log, id) instanceof LayoutSource.TextPayload.Spilled);
			assertEquals("abcdefgh", new String(payloadOf(log, id).freshChars()));
		}
	}

	/** compactがinline分を予算会計から解放し、以降の追記がinlineへ戻る。 */
	public void testCompactReleasesInlineBudget() throws Exception {
		try (LayoutSource log = new LayoutSource(8)) {
			log.appendChars(0, "abcd".toCharArray(), 0, 4, false);
			final long spilledId = log.appendChars(4, "efgh".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, spilledId) instanceof LayoutSource.TextPayload.Spilled);

			// 全イベントを破棄 → inline会計が0へ戻る
			log.compact(log.nextId());
			assertEquals(0, log.size());

			// 予算が空いたので再びinline(Spilled recordはストア上に残るが
			// close時に一時ファイルごと削除される——リークではない)
			final long third = log.appendChars(8, "ijkl".toCharArray(), 0, 4, false);
			assertTrue(payloadOf(log, third) instanceof LayoutSource.TextPayload.Inline);
		}
	}

	/** replay(streamingビュー)がspill済みイベントを正しく復元する。 */
	public void testReplayDecodesSpilledEvents() throws Exception {
		try (LayoutSource log = new LayoutSource(0)) {
			// 予算0 → 全追記がspill
			final long from = log.appendChars(0, "hello".toCharArray(), 0, 5, false);
			final long to = log.appendChars(5, "world".toCharArray(), 0, 5, true);
			assertTrue(payloadOf(log, from) instanceof LayoutSource.TextPayload.Spilled);
			assertTrue(payloadOf(log, to) instanceof LayoutSource.TextPayload.Spilled);

			final List<String> texts = new ArrayList<>();
			log.replay(from, to, event -> {
				final LayoutSource.Chars chars = (LayoutSource.Chars) event;
				texts.add(new String(chars.payload().freshChars()));
			});
			assertEquals(List.of("hello", "world"), texts);
		}
	}

	/** closeが一時ファイルを削除し、冪等である。 */
	public void testCloseDeletesTempFilesIdempotently() throws Exception {
		final LayoutSource log = new LayoutSource(0);
		log.appendChars(0, "x".toCharArray(), 0, 1, false);
		final TextSpill spill = log.textSpillForTest();
		assertNotNull(spill);
		assertTrue(spill.dataFileForTest().exists());
		assertTrue(spill.indexFileForTest().exists());
		log.close();
		assertTrue("close後に一時ファイルが残っています", spill.tempFilesDeletedForTest());
		// 冪等
		log.close();
		assertTrue(spill.tempFilesDeletedForTest());
	}

	/** spillが不要だった場合、close(冪等)は何もしない。 */
	public void testCloseWithoutSpillIsNoop() throws Exception {
		final LayoutSource log = new LayoutSource();
		log.appendChars(0, "abc".toCharArray(), 0, 3, false);
		assertNull(log.textSpillForTest());
		log.close();
		log.close();
	}

	private static LayoutSource.TextPayload payloadOf(final LayoutSource log, final long id) {
		return ((LayoutSource.Chars) log.get(id)).payload();
	}
}
