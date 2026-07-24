package net.zamasoft.foliojet.layout.segment;

import java.util.Optional;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;

/**
 * {@link SegmentEventCodec}の単体テストです(E-6増分2、2026-07-24新設)。
 * 全variantのround-trip、side table契約(参照variantは同一インスタンス
 * 復元)、不正データ(版・tag・切詰め・余剰・参照範囲外)の検査を
 * 検証する。production経路は未配線。
 */
public class SegmentEventCodecTest extends TestCase {
	/** EndBoxは完全バイト化でround-tripする。 */
	public void testEndBoxRoundTrip() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		final SegmentEvent original = new SegmentEvent.EndBox();
		assertEquals(original, codec.decode(codec.encode(original)));
		assertEquals("EndBoxはside tableを使わない", 0, codec.sideTableSizeForTest());
	}

	/** Textは完全バイト化でround-tripする(和文・生成内容-1・fixed含む)。 */
	public void testTextRoundTrip() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		for (final SegmentEvent original : new SegmentEvent[] { //
				new SegmentEvent.Text(42, "hello", false), //
				new SegmentEvent.Text(-1, "日本語の生成内容テキスト", true), //
				new SegmentEvent.Text(0, "", false) }) {
			assertEquals(original, codec.decode(codec.encode(original)));
		}
		assertEquals("Textはside tableを使わない", 0, codec.sideTableSizeForTest());
	}

	/** Barrierは完全バイト化でround-tripする(kindあり・なし両方)。 */
	public void testBarrierRoundTrip() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		for (final SegmentEvent original : new SegmentEvent[] { //
				new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED), //
				new SegmentEvent.Barrier(Optional.of(BoxKind.TABLE), BarrierReason.UNKNOWN_TYPE), //
				new SegmentEvent.Barrier(Optional.of(BoxKind.TABLE_COLUMN), BarrierReason.UNCLOSED_SUBTREE) }) {
			assertEquals(original, codec.decode(codec.encode(original)));
		}
		assertEquals("Barrierはside tableを使わない", 0, codec.sideTableSizeForTest());
	}

	/** BeginBoxのBoxRecipeはside table経由で「同一インスタンス」が復元される。 */
	public void testBeginBoxRestoresSameRecipeInstance() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		final BoxRecipe recipe = new BoxRecipe.Flow(BlockParamsTemplate.freeze(new BlockParams()),
				FlowPosTemplate.freeze(new FlowPos()));
		final SegmentEvent original = new SegmentEvent.BeginBox(recipe);

		final SegmentEvent decoded = codec.decode(codec.encode(original));

		assertEquals(original, decoded);
		assertSame("side table契約: 参照は同一インスタンス復元", recipe, ((SegmentEvent.BeginBox) decoded).recipe());
		assertEquals(1, codec.sideTableSizeForTest());
	}

	/** ReplacedのReplacedRecipeもside table経由で同一インスタンスが復元される。 */
	public void testReplacedRestoresSameRecipeInstance() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		final ReplacedRecipe recipe = new ReplacedRecipe.Flow(
				ReplacedParamsTemplate.freeze(new ReplacedParams()).get(), FlowPosTemplate.freeze(new FlowPos()));
		final SegmentEvent original = new SegmentEvent.Replaced(recipe);

		final SegmentEvent decoded = codec.decode(codec.encode(original));

		assertEquals(original, decoded);
		assertSame(recipe, ((SegmentEvent.Replaced) decoded).recipe());
	}

	/** codec版の不一致はdecodeで失敗する。 */
	public void testVersionMismatchFails() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		final byte[] record = codec.encode(new SegmentEvent.Text(0, "x", false));
		record[0] = (byte) (SegmentEventCodec.CODEC_VERSION + 1);
		try {
			codec.decode(record);
			fail("版不一致は失敗するはず");
		} catch (final IllegalStateException e) {
		}
	}

	/** 未知のtagはdecodeで失敗する。 */
	public void testUnknownTagFails() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		final byte[] record = codec.encode(new SegmentEvent.EndBox());
		record[1] = 99;
		try {
			codec.decode(record);
			fail("未知tagは失敗するはず");
		} catch (final IllegalStateException e) {
		}
	}

	/** 切り詰められたレコード・余剰バイトのあるレコードはdecodeで失敗する。 */
	public void testTruncatedAndTrailingBytesFail() {
		final SegmentEventCodec codec = new SegmentEventCodec();
		final byte[] record = codec.encode(new SegmentEvent.Text(7, "hello", true));
		try {
			codec.decode(java.util.Arrays.copyOf(record, record.length - 1));
			fail("切詰めは失敗するはず");
		} catch (final IllegalStateException e) {
		}
		final byte[] padded = java.util.Arrays.copyOf(record, record.length + 1);
		try {
			codec.decode(padded);
			fail("余剰バイトは失敗するはず");
		} catch (final IllegalStateException e) {
		}
	}

	/** side tableにない参照・型の合わない参照はdecodeで失敗する。 */
	public void testSideTableRefValidation() {
		final SegmentEventCodec encoder = new SegmentEventCodec();
		final byte[] beginBox = encoder.encode(new SegmentEvent.BeginBox(new BoxRecipe.Flow(
				BlockParamsTemplate.freeze(new BlockParams()), FlowPosTemplate.freeze(new FlowPos()))));

		// 別インスタンス(空のside table)でのdecodeは範囲外で失敗する
		// ——encodeとdecodeは同じcodecインスタンスで行うのが契約
		try {
			new SegmentEventCodec().decode(beginBox);
			fail("空side tableへの参照は失敗するはず");
		} catch (final IllegalStateException e) {
		}

		// tagをREPLACEDへ改竄するとside tableの型検査で失敗する
		final byte[] tampered = beginBox.clone();
		tampered[1] = SegmentEventCodec.TAG_REPLACED;
		try {
			encoder.decode(tampered);
			fail("参照の型不一致は失敗するはず");
		} catch (final IllegalStateException e) {
		}
	}
}
