package jp.cssj.test.unit.fragment;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * LayoutSource(レイアウトソースプロトコルログ)のテストです(M6b v3)。
 * recipeの中身はログ機構に無関係のため、デフォルト値の最小recipeで
 * 代用します(E-6増分3b-4でStartは記録時freezeのrecipe保持になった)。
 */
public class LayoutSourceTest extends TestCase {
	private static LayoutSource.Event start() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Flow(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event tableStart() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Table(
				net.zamasoft.foliojet.layout.segment.TableParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event captionStart() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Caption(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.TableCaptionPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableCaptionPos())));
	}

	/**
	 * caption recipe化C1/C2のプロトコルテストです
	 * (consult-codex-2026-08-01-caption-recipe.txt Q1の範囲別扱い表)。
	 */
	public void testCaptionContextCompleteRange() {
		final LayoutSource log = new LayoutSource();
		final long block = log.append(start()); // BLOCK
		final long table = log.append(tableStart()); // TABLE
		final long caption = log.append(captionStart()); // CAPTION
		log.append(new LayoutSource.Chars(0, "c".toCharArray(), false));
		final long captionEnd = log.append(new LayoutSource.EndBlock());
		final long tableEnd = log.append(new LayoutSource.EndBlock());
		final long blockEnd = log.append(new LayoutSource.EndBlock());

		// containsCaption: 含有検出
		assertTrue(log.containsCaption(block, blockEnd));
		assertTrue(log.containsCaption(caption, caption));
		assertFalse(log.containsCaption(block, table));

		// TABLE→CAPTION / BLOCK→TABLE→CAPTION: 適格
		assertTrue(log.isContextCompleteRange(table, tableEnd));
		assertTrue(log.isContextCompleteRange(block, blockEnd));
		// CAPTION単独範囲: 不適格(G-1の直接原因)
		assertFalse(log.isContextCompleteRange(caption, captionEnd));
		// tableStart+1..tableEnd-1(先頭がCAPTION、TABLE Startなし): 不適格
		assertFalse(log.isContextCompleteRange(table + 1, tableEnd - 1));
		// CAPTIONを途中で切る範囲(開いたまま終わる): 不適格
		assertFalse(log.isContextCompleteRange(table, caption));
		// CAPTION Startの直前で終わる範囲: 適格(caption非含有——
		// 開いたTABLEが残るため不適格が正しい)
		assertFalse(log.isContextCompleteRange(block, table));
	}

	public void testEventIdStableAcrossCompaction() {
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start()); // 開いたまま
		final long p1 = log.append(start());
		log.append(new LayoutSource.Chars(0, "aaa".toCharArray(), false));
		final long p1end = log.append(new LayoutSource.EndBlock());
		final long p2 = log.append(start());
		log.append(new LayoutSource.Chars(3, "bbb".toCharArray(), false));
		log.append(new LayoutSource.EndBlock());

		assertEquals(p1end, log.endOf(p1));

		// p2 より前を破棄(開いている body は残る)
		log.compact(p2);
		assertNotNull(log.get(body));
		assertNull(log.get(p1));
		assertNotNull(log.get(p2));
		// id は不変
		assertTrue(log.get(p2) instanceof LayoutSource.Start);
		assertEquals(log.endOf(p2), p2 + 2);
	}

	public void testReplayRange() {
		final LayoutSource log = new LayoutSource();
		log.append(start());
		final long from = log.append(new LayoutSource.Chars(0, "xy".toCharArray(), false));
		final long to = log.append(new LayoutSource.EndBlock());
		log.append(start());

		final List<LayoutSource.Event> seen = new ArrayList<LayoutSource.Event>();
		log.replay(from, to, seen::add);
		assertEquals(2, seen.size());
		assertTrue(seen.get(0) instanceof LayoutSource.Chars);
		assertTrue(seen.get(1) instanceof LayoutSource.EndBlock);
	}

	public void testOpenSubtreeNotClosed() {
		final LayoutSource log = new LayoutSource();
		final long open = log.append(start());
		log.append(new LayoutSource.Chars(0, "a".toCharArray(), false));
		assertEquals(-1, log.endOf(open));
	}

	public void testOpaquePairsWithEndBlock() {
		// Opaque は EndBlock と対を成す開始イベント。対称に扱わないと
		// (1) endOf が Opaque の対の EndBlock を親の終端と誤認し、
		// (2) compact が祖先の Start を誤って pop して開チェーンが崩壊する
		final LayoutSource log = new LayoutSource();
		final long body = log.append(start());
		final long div = log.append(start());
		log.append(new LayoutSource.Opaque()); // 絶対配置などの非対応ボックス
		log.append(new LayoutSource.Chars(0, "a".toCharArray(), false));
		log.append(new LayoutSource.EndBlock()); // /opaque
		log.append(new LayoutSource.Chars(1, "b".toCharArray(), false));
		final long divEnd = log.append(new LayoutSource.EndBlock()); // /div
		final long tail = log.nextId();
		log.append(new LayoutSource.Chars(2, "c".toCharArray(), false));

		// endOf: Opaque の対の EndBlock を div の終端と誤認しないこと
		assertEquals(divEnd, log.endOf(div));

		// compact: 破棄範囲内で完結する opaque 対が、開いている祖先
		// (body)の Start を pop してしまわないこと
		log.compact(tail);
		assertNotNull(log.get(body));
		assertEquals(-1, log.endOf(body));
	}

	public void testReplaySurvivesNestedCompaction() {
		// 再生の visitor 内で入れ子の改ページが compact を呼ぶケース
		// (再生した内容が新ページを溢れさせる)。backing list が
		// clear+addAll で作り直されても、再生中の範囲は完走しなければ
		// ならない(外部レビュー指摘: 数値 index の走査では silent skip する)
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[7];
		for (int i = 0; i < 7; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final List<Integer> seen = new ArrayList<Integer>();
		log.replay(ids[2], ids[5], event -> {
			final int off = ((LayoutSource.Chars) event).charOffset();
			seen.add(off);
			if (off == 2) {
				// 水位 pin(fromId=ids[2])で clamp された compact を模擬
				log.compact(ids[2]);
			}
		});
		assertEquals(List.of(2, 3, 4, 5), seen);
	}

	public void testReplaySurvivesNestedCompactionBeyondRange() {
		// E-6増分3a: 再生中の入れ子 compact の水位が再生範囲の先(範囲全体
		// より後ろ)を指しても、slice 自身のリースが fromId で clamp する
		// ため streaming 走査は完走する(従来は全量コピーが隔離していた
		// 保証の置き換え)
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[7];
		for (int i = 0; i < 7; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final List<Integer> seen = new ArrayList<Integer>();
		log.replay(ids[2], ids[5], event -> {
			final int off = ((LayoutSource.Chars) event).charOffset();
			seen.add(off);
			if (off == 3) {
				// 範囲の終端より先の水位(リースなしなら未読の 4,5 が消える)
				log.compact(ids[6]);
			}
		});
		assertEquals(List.of(2, 3, 4, 5), seen);
		// 再生完了でリースは解放済み——次の compact は普通に破棄できる
		// (取り残すと永久 clamp = 保持リーク)
		log.compact(ids[6]);
		assertNull(log.get(ids[2]));
		assertNotNull(log.get(ids[6]));
	}

	public void testReplaySliceIsConsumeOnce() {
		final LayoutSource log = new LayoutSource();
		final long from = log.append(new LayoutSource.Chars(0, "a".toCharArray(), false));
		final long to = log.append(new LayoutSource.Chars(1, "b".toCharArray(), false));
		final LayoutSource.ReplaySlice slice = log.capture(from, to);
		slice.replay(event -> {
		});
		try {
			slice.replay(event -> {
			});
			fail("ReplaySlice は consume-once のはず");
		} catch (IllegalStateException expected) {
			// OK
		}
	}

	public void testAbandonedReplaySliceReleasesLeaseOnClose() {
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[3];
		for (int i = 0; i < 3; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.ReplaySlice slice = log.capture(ids[0], ids[1]);
		// capture 中はリースが compact を clamp する
		log.compact(ids[2]);
		assertNotNull(log.get(ids[0]));
		// 放棄(close は冪等)でリースが解放され、破棄できる
		slice.close();
		slice.close();
		log.compact(ids[2]);
		assertNull(log.get(ids[0]));
		assertNotNull(log.get(ids[2]));
	}

	public void testRetentionLeaseClampsCompaction() {
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[5];
		for (int i = 0; i < 5; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.RetentionLease lease = log.retainFrom(ids[1]);
		// リースが生きている間、fromId より前は破棄されない
		log.compact(ids[4]);
		assertNotNull(log.get(ids[1]));
		assertNotNull(log.get(ids[3]));
		assertNull(log.get(ids[0]));
		// 解放後は破棄できる
		lease.close();
		log.compact(ids[4]);
		assertNull(log.get(ids[1]));
		assertNotNull(log.get(ids[4]));
	}

	public void testRetentionLeaseIsRefCounted() {
		// 同じ fromId を複数の継続が独立に所有した場合、一方の解放で
		// もう一方の保持が消えてはならない(参照カウント)
		final LayoutSource log = new LayoutSource();
		final long[] ids = new long[3];
		for (int i = 0; i < 3; ++i) {
			ids[i] = log.append(new LayoutSource.Chars(i, new char[] { (char) ('A' + i) }, false));
		}
		final LayoutSource.RetentionLease outer = log.retainFrom(ids[0]);
		final LayoutSource.RetentionLease inner = log.retainFrom(ids[0]);
		inner.close();
		inner.close(); // 冪等
		log.compact(ids[2]);
		assertNotNull(log.get(ids[0]));
		outer.close();
		log.compact(ids[2]);
		assertNull(log.get(ids[0]));
	}

	public void testCompactKeepsNestedOpenStarts() {
		final LayoutSource log = new LayoutSource();
		final long html = log.append(start());
		final long body = log.append(start());
		final long p = log.append(start());
		log.append(new LayoutSource.EndBlock()); // /p
		final long div = log.append(start()); // 開いたまま
		final long tail = log.nextId();
		log.append(new LayoutSource.Chars(0, "t".toCharArray(), false));

		log.compact(tail);
		assertNotNull(log.get(html));
		assertNotNull(log.get(body));
		assertNotNull(log.get(div));
		assertNull(log.get(p));
		assertEquals(4, log.size());
	}
	/**
	 * RangeSummary(疎索引によるcontains*のO(log k)化、2026-08-01)の
	 * 線形走査との等価性プロパティテストです。テスト自身がイベント列を
	 * 構築するため、カテゴリの正解台帳を構築時に記録し、乱数の範囲照会
	 * (シード固定)で全述語を突き合わせる。compact後は「全域が水位以降の
	 * 範囲」(エントリが全て生存)について再検証する。
	 * Replaced(FLOAT)分岐のみここでは構築困難のため対象外——実コーパスの
	 * float画像文書を通るtier1/goldenが実効カバレッジ。
	 */
	public void testRangeSummaryMatchesLinearScan() {
		final java.util.Random random = new java.util.Random(20260801L);
		final LayoutSource log = new LayoutSource();
		final int kinds = 9;
		final java.util.List<long[]> truth = new java.util.ArrayList<>(); // {id, kindOrdinal}
		final int OPAQUE = 0, CAPTION = 1, TABLE = 2, MULTICOL = 3, GRID = 4, ABSOLUTE = 5, FLOATB = 6, VFLOW = 7,
				FLEX = 8;
		final java.util.ArrayDeque<Long> open = new java.util.ArrayDeque<>();
		for (int i = 0; i < 400; ++i) {
			final int roll = random.nextInt(13);
			final long id;
			switch (roll) {
			case 0 -> {
				id = log.append(new LayoutSource.Opaque());
				truth.add(new long[] { id, OPAQUE });
				open.push(id);
			}
			case 1 -> {
				id = log.append(caption());
				truth.add(new long[] { id, CAPTION });
				open.push(id);
			}
			case 2 -> {
				id = log.append(table());
				truth.add(new long[] { id, TABLE });
				open.push(id);
			}
			case 3 -> {
				id = log.append(multicol());
				truth.add(new long[] { id, MULTICOL });
				open.push(id);
			}
			case 4 -> {
				id = log.append(grid());
				truth.add(new long[] { id, GRID });
				open.push(id);
			}
			case 5 -> {
				id = log.append(absolute());
				truth.add(new long[] { id, ABSOLUTE });
				open.push(id);
			}
			case 6 -> {
				id = log.append(floatBlock());
				truth.add(new long[] { id, FLOATB });
				open.push(id);
			}
			case 7 -> {
				id = log.append(verticalStart());
				truth.add(new long[] { id, VFLOW });
				open.push(id);
			}
			case 12 -> {
				id = log.append(flex());
				truth.add(new long[] { id, FLEX });
				open.push(id);
			}
			case 8, 9 -> {
				id = log.append(start()); // 横flowのFLOW
				open.push(id);
			}
			case 10 -> {
				if (open.isEmpty()) {
					id = log.append(start());
					open.push(id);
				} else {
					open.pop();
					id = log.append(new LayoutSource.EndBlock());
				}
			}
			default -> id = log.append(new LayoutSource.Chars(i, "x".toCharArray(), false));
			}
		}
		final long maxId = log.nextId() - 1;
		verifyAgainstTruth(log, truth, 0, maxId, random);
		// compact後: 全域が水位以降の範囲で再検証
		final long watermark = maxId / 2;
		log.compact(watermark);
		verifyAgainstTruth(log, truth, watermark, maxId, random);
	}

	private static void verifyAgainstTruth(final LayoutSource log, final java.util.List<long[]> truth,
			final long minFrom, final long maxId, final java.util.Random random) {
		final int OPAQUE = 0, CAPTION = 1, TABLE = 2, MULTICOL = 3, GRID = 4, ABSOLUTE = 5, FLOATB = 6, VFLOW = 7,
				FLEX = 8;
		final net.zamasoft.foliojet.layout.box.params.WritingMode horizontal = net.zamasoft.foliojet.layout.box.params.WritingMode.TB;
		for (int q = 0; q < 500; ++q) {
			final long from = minFrom + (long) (random.nextDouble() * (maxId - minFrom + 1));
			final long to = from + (long) (random.nextDouble() * (maxId - from + 1));
			final boolean[] expect = new boolean[9];
			for (final long[] t : truth) {
				if (t[0] >= from && t[0] <= to) {
					expect[(int) t[1]] = true;
				}
			}
			final String at = " [" + from + "," + to + "]";
			assertEquals("opaque" + at, expect[OPAQUE], log.containsOpaque(from, to));
			assertEquals("caption" + at, expect[CAPTION], log.containsCaption(from, to));
			assertEquals("table" + at, expect[TABLE], log.containsTable(from, to));
			assertEquals("multicol" + at, expect[MULTICOL], log.containsMulticol(from, to));
			assertEquals("grid" + at, expect[GRID], log.containsGrid(from, to));
			assertEquals("flex" + at, expect[FLEX], log.containsFlex(from, to));
			assertEquals("absolute" + at, expect[ABSOLUTE], log.containsAbsolute(from, to));
			assertEquals("float" + at, expect[FLOATB], log.containsFloat(from, to));
			// 横rootに対するmixedFlow=縦flow開始の存在
			assertEquals("mixedFlow" + at, expect[VFLOW], log.containsMixedFlow(from, to, horizontal));
		}
	}

	private static LayoutSource.Event caption() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Caption(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.TableCaptionPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableCaptionPos())));
	}

	private static LayoutSource.Event table() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Table(
				net.zamasoft.foliojet.layout.segment.TableParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.TableParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event multicol() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Multicol(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event grid() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Grid(
				net.zamasoft.foliojet.layout.segment.GridParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.GridParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event flex() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Flex(
				net.zamasoft.foliojet.layout.segment.FlexParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlexParams()),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

	private static LayoutSource.Event absolute() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Absolute(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.AbsolutePosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.AbsolutePos())));
	}

	private static LayoutSource.Event floatBlock() {
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.FloatBlock(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.BlockParams()),
				net.zamasoft.foliojet.layout.segment.FloatPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FloatPos())));
	}

	private static LayoutSource.Event verticalStart() {
		final net.zamasoft.foliojet.layout.box.params.BlockParams params = new net.zamasoft.foliojet.layout.box.params.BlockParams();
		params.flow = net.zamasoft.foliojet.layout.box.params.WritingMode.RL;
		return new LayoutSource.Start(new net.zamasoft.foliojet.layout.segment.BoxRecipe.Flow(
				net.zamasoft.foliojet.layout.segment.BlockParamsTemplate.freeze(params),
				net.zamasoft.foliojet.layout.segment.FlowPosTemplate
						.freeze(new net.zamasoft.foliojet.layout.box.params.FlowPos())));
	}

}
