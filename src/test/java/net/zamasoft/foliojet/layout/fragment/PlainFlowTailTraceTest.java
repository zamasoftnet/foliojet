package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.BoxSubtype;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * {@link PlainFlowTailTrace}の単体テストです(2026-07-22新設、B6a0)。
 * 実際の{@code FlowContainer}/{@code RootBuilder}配線を経由せず、
 * {@code observeChainBox}/{@code observeOpenText}を直接呼んで
 * shadow比較の契約(一致/不一致/allPlainFlowによる強制の有無)を固定する。
 */
public class PlainFlowTailTraceTest extends TestCase {

	/**
	 * {@code AbstractBlockBox}のコンストラクタは{@code assert params
	 * .fontStyle != null}を要求する(通常はCSSスタイル解決の産物)。
	 * このテストはshadow比較のみを見るため、値そのものはどうでもよく
	 * non-nullでさえあればよい({@link ContinuationCapabilityTest}と
	 * 同じ理由・同じパターン)。
	 */
	private static final FontStyle DUMMY_FONT_STYLE = new FontStyle() {
		public Direction getDirection() {
			return Direction.LTR;
		}

		public Weight getWeight() {
			return Weight.W_400;
		}

		public Style getStyle() {
			return Style.NORMAL;
		}

		public net.zamasoft.pdfg2d.gc.font.FontFamilyList getFamily() {
			return null;
		}

		public double getSize() {
			return 10;
		}

		public net.zamasoft.pdfg2d.gc.font.FontPolicyList getPolicy() {
			return null;
		}
	};

	private static BlockParams blockParams() {
		final BlockParams params = new BlockParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		return params;
	}

	private static FlowBlockBox plainFlowBox() {
		return new FlowBlockBox(blockParams(), new FlowPos());
	}

	private static OpenPathSnapshot.OpenLevelDescriptor levelFor(final int index,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox box,
			final OpenPathSnapshot.OpenLevelRole role) {
		final OpenPathSnapshot.FragmentSignature sig = OpenPathSnapshot.FragmentSignature.from(box);
		return new OpenPathSnapshot.OpenLevelDescriptor(index, sig.boxClass(), sig.subtype(), sig.flow(),
				sig.columnCount(), index, role);
	}

	private static final OpenPathSnapshot.OpenLevelRole PLAIN_FLOW_ROLE = new OpenPathSnapshot.OpenLevelRole.Ancestor(
			ContinuationCapability.PLAIN_FLOW);

	/** 予測どおりの列(box1本+終端テキスト)を観測すると一致・例外なし。 */
	public void testConsistentWhenObservationsMatchPrediction() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		trace.observeOpenText();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertTrue(result.consistent());
		assertTrue(result.allPlainFlow());
	}

	/**
	 * allPlainFlow=trueでも不一致(signature違い)はverifyComplete()を
	 * 例外化しない(2026-07-22、配線試行での実回帰を受けて非例外化に
	 * 変更——docs/history/2026-07-22-b6a0-wiring-attempt-and-revert.md
	 * 参照)——Result.consistent()=falseとして返るだけ。
	 */
	public void testDoesNotThrowOnSignatureMismatchEvenWhenAllPlainFlow() {
		final FlowBlockBox expectedBox = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(
				List.of(levelFor(1, expectedBox, PLAIN_FLOW_ROLE)), true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		// 実際にはcolumnCountが違う(段組)boxが来た、という食い違いを模擬
		final FlowBlockBox actualBox = new FlowBlockBox(blockParams(), new FlowPos()) {
			public int getColumnCount() {
				return 2;
			}
		};
		trace.observeChainBox(actualBox);
		trace.observeOpenText();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertFalse(result.consistent());
		assertTrue(result.allPlainFlow());
		assertNotNull(result.mismatch());
	}

	/** allPlainFlow=falseなら同じ不一致でも例外化せず、Result.consistent()=falseを返すだけ。 */
	public void testDoesNotThrowOnMismatchWhenNotAllPlainFlow() {
		final FlowBlockBox expectedBox = plainFlowBox();
		final OpenPathSnapshot.OpenLevelRole flowSubtypeRole = new OpenPathSnapshot.OpenLevelRole.Ancestor(
				ContinuationCapability.FLOW_SUBTYPE);
		final PlainFlowTailProgram program = new PlainFlowTailProgram(
				List.of(levelFor(1, expectedBox, flowSubtypeRole)), false);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		// 何も観測しない(未到達)——それでも例外は投げない
		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertFalse(result.consistent());
		assertFalse(result.allPlainFlow());
	}

	/** 予告された深さを超えてchain boxを観測すると不一致として記録される(例外化はしない)。 */
	public void testExtraChainBoxBeyondPredictedDepthIsMismatch() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		trace.observeChainBox(box); // 予告より1段多い
		trace.observeOpenText();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertFalse(result.consistent());
		assertNotNull(result.mismatch());
	}

	/** 終端の開きテキストへ到達しないまま検証すると不一致として記録される(例外化はしない)。 */
	public void testMissingTerminalOpenTextIsMismatch() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		// observeOpenText()を呼ばない

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertFalse(result.consistent());
		assertEquals(PlainFlowTailTrace.TerminalKind.NONE, result.terminalKind());
	}

	/** levelsが空(残存tailが終端テキストのみ)の場合も正しく扱える。 */
	public void testEmptyLevelsWithImmediateTerminal() {
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(), true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeOpenText();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertTrue(result.consistent());
	}

	/**
	 * 終端がTextBlockBoxのopenではなくBLOCK型のCLOSEDへ落ちるケースも、
	 * 正当な「終端到達」として一致扱いになる(2026-07-22、
	 * AvoidBeforeBlockTest等の実回帰から判明した既存の正当な挙動を
	 * モデルへ組み込んだ——docs/history/2026-07-22-b6a0-wiring-attempt
	 * -and-revert.md参照)。
	 */
	public void testTerminalClosedAsBlockIsConsistentNotMismatch() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		trace.observeTerminalClosedAsBlock();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertTrue(result.consistent());
		assertEquals(PlainFlowTailTrace.TerminalKind.CLOSED_AS_BLOCK, result.terminalKind());
	}

	/** 終端がTABLE型で既存の無条件addBound経路を通るケースも正当な一致扱いになる(2026-07-22、591文書コーパス実測で発見)。 */
	public void testTerminalBoundAsTableIsConsistentNotMismatch() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		trace.observeTerminalBoundAsTable();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertTrue(result.consistent());
		assertEquals(PlainFlowTailTrace.TerminalKind.BOUND_AS_TABLE, result.terminalKind());
	}

	/**
	 * 終端が書字方向不一致のBLOCKで既存のaddBound経路を通るケースも
	 * 正当な一致扱いになる(2026-07-22、codex設計相談で「3分類は
	 * 閉じていない」と指摘され発見)。
	 */
	public void testTerminalBoundAsWritingModeMismatchBlockIsConsistentNotMismatch() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		trace.observeTerminalBoundAsWritingModeMismatchBlock();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertTrue(result.consistent());
		assertEquals(PlainFlowTailTrace.TerminalKind.BOUND_AS_WRITING_MODE_MISMATCH_BLOCK, result.terminalKind());
	}

	/**
	 * 終端がREPLACED型で既存の無条件addBound経路を通るケースも正当な
	 * 一致扱いになる(2026-07-22、codex設計相談で「明示的な第4候補」と
	 * 指摘され発見——FlowReplacedBoxはIFlowBoxを実装するためlastFlowに
	 * なりうる)。
	 */
	public void testTerminalBoundAsReplacedIsConsistentNotMismatch() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		trace.observeTerminalBoundAsReplaced();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertTrue(result.consistent());
		assertEquals(PlainFlowTailTrace.TerminalKind.BOUND_AS_REPLACED, result.terminalKind());
	}

	/**
	 * 終端がfloatで既存の無条件{@code ((Floating) holder).restyle(builder)}
	 * 経路を通るケースも正当な一致扱いになる(2026-07-22、414文書
	 * コーパス実測`0110-clear/avoid-before-image.html`・
	 * `0110-clear/avoid-before-br.html`で発見——floatは構造的に
	 * lastFlowになりえない)。
	 */
	public void testTerminalBoundAsFloatIsConsistentNotMismatch() {
		final FlowBlockBox box = plainFlowBox();
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(levelFor(1, box, PLAIN_FLOW_ROLE)),
				true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeChainBox(box);
		trace.observeTerminalBoundAsFloat();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertTrue(result.consistent());
		assertEquals(PlainFlowTailTrace.TerminalKind.BOUND_AS_FLOAT, result.terminalKind());
	}

	/** observeOpenText()とobserveTerminalClosedAsBlock()を両方呼ぶと不一致(排他のはず)。 */
	public void testBothTerminalObservationsIsMismatch() {
		final PlainFlowTailProgram program = new PlainFlowTailProgram(List.of(), true);
		final PlainFlowTailTrace trace = PlainFlowTailTrace.of(program);

		trace.observeOpenText();
		trace.observeTerminalClosedAsBlock();

		final PlainFlowTailTrace.Result result = trace.verifyComplete();
		assertFalse(result.consistent());
		assertNotNull(result.mismatch());
	}
}
