package net.zamasoft.foliojet.layout.balance;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * M6c-2(排除域P2、2026-07-24)の隔離性テストです。
 *
 * <p>
 * M6cバランスプローブの核心契約——「losing candidateを何個作っても
 * live側(owner container identity・寸法・{@code ContinuationStats}
 * カウンタ)が一切変わらない」「候補は完全な新品で互いに独立」
 * 「{@code takeContainer()}は一回だけ」「Barrier/フロート含みは
 * プローブ不適格」——を、実セッションなしで構築できる合成ソースログと
 * 凍結スナップショットで固定する(codex設計§1.8の隔離性条件)。
 * あわせて探索ドライバ({@code BalanceProbe.search})の反復上限・
 * 非単調フォールバック(無限ループの構造的排除——2026-07-24のユーザー
 * 較正で絶対要件)をstub候補で検証する。
 * </p>
 */
public class BalanceProbeSessionTest extends TestCase {

	/** {@code BoxRecipeBoxFactoryTest}と同じダミーパターン(値は不問、non-nullのみ要求)。 */
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

	/**
	 * プロパティ参照だけに応答するダミーUAです。プローブ候補構築がUAの
	 * それ以外の機能(ソース解決・ページ出力等)へ触れた場合は
	 * {@code UnsupportedOperationException}で騒がしく落ちる——隔離性の
	 * 検査を兼ねる。
	 */
	private static UserAgent dummyUserAgent() {
		return (UserAgent) Proxy.newProxyInstance(BalanceProbeSessionTest.class.getClassLoader(),
				new Class<?>[] { UserAgent.class }, new InvocationHandler() {
					@Override
					public Object invoke(final Object proxy, final Method method, final Object[] args) {
						switch (method.getName()) {
						case "getProperty":
							return null;
						case "message":
							return null;
						case "equals":
							return proxy == args[0];
						case "hashCode":
							return System.identityHashCode(proxy);
						case "toString":
							return "dummy balance-probe UA";
						default:
							throw new UnsupportedOperationException(
									"balance probe candidate must not use UserAgent." + method.getName());
						}
					}
				});
	}

	private static BlockParams multicolParams(final int columnCount) {
		final BlockParams params = new BlockParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		params.columns = new Columns((byte) columnCount, net.zamasoft.foliojet.layout.util.LayoutUtils.NONE, 0,
				Border.NONE_BORDER, Columns.FILL_BALANCE);
		return params;
	}

	/** 固定高さ{@code height}ptの空ブロックのパラメータです(テキスト整形不要)。 */
	private static BlockParams childParams(final double height) {
		final BlockParams params = new BlockParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		params.size = Dimension.create(0, height, LengthType.AUTO, LengthType.ABSOLUTE);
		return params;
	}

	/** 行方向内寸300pt・3段・横書きのownerスナップショットです。 */
	private static BalanceBoxSnapshot ownerGeometry(final BlockParams ownerParams, final FlowPos ownerPos) {
		return new BalanceBoxSnapshot(ownerParams, ownerPos, ownerParams.size, ownerParams.minSize,
				RectFrame.NULL_FRAME, new AbsoluteInsets(), new AbsoluteInsets(), 300, 0, 0, false, Align.START);
	}

	/**
	 * 「MULTICOL(FLOW×10、各30pt)」の合成ソースログからプローブ入力を
	 * 凍結します。
	 */
	private static BalanceProbeInput frozenInput(final MulticolumnBlockBox owner, final long selfId,
			final LayoutSource log) {
		final Optional<BalanceProbeInput> input = BalanceProbeInput.capture(log, selfId, owner, 3, dummyUserAgent());
		assertTrue("固定高さブロックのみの内容はプローブ適格のはずです", input.isPresent());
		return input.get();
	}

	private BlockParams ownerParams;
	private FlowPos ownerPos;
	private MulticolumnBlockBox owner;
	private LayoutSource log;
	private long selfId;

	@Override
	protected void setUp() {
		this.ownerParams = multicolParams(3);
		this.ownerPos = new FlowPos();
		// ownerは「解決済み物理形状を持つlive owner」の代役——candidateと
		// 同じ工場から作るが、以降プローブは一切これへ触れないはずである
		this.owner = MulticolumnBlockBox.newBalanceProbeShell(ownerGeometry(this.ownerParams, this.ownerPos), 240);
		this.log = new LayoutSource();
		this.selfId = this.log.append(new LayoutSource.Start(LayoutSource.BoxKind.MULTICOL, this.ownerParams,
				this.ownerPos));
		this.owner.setSourceAnchor(this.selfId);
		for (int i = 0; i < 10; ++i) {
			this.log.append(new LayoutSource.Start(LayoutSource.BoxKind.FLOW, childParams(30), new FlowPos()));
			this.log.append(new LayoutSource.EndBlock());
		}
		this.log.append(new LayoutSource.EndBlock());
	}

	/**
	 * 隔離性(codex設計§1.8): losing candidateを複数構築しても、
	 * live owner(container identity・寸法)と{@code ContinuationStats}が
	 * 一切変わらない。候補同士も完全に独立した新品である。
	 */
	public void testLosingCandidatesLeaveLiveStateUntouched() {
		final BalanceProbeInput input = frozenInput(this.owner, this.selfId, this.log);
		final BalanceProbeSession session = new BalanceProbeSession(input);

		final Container ownerContainer = this.owner.getContainer();
		final double ownerWidth = this.owner.getWidth();
		final double ownerHeight = this.owner.getHeight();

		ContinuationStats.reset();
		final BalanceCandidate c1 = session.build(40); // 収まらないはず(10×30pt/3段)
		final BalanceCandidate c2 = session.build(100);
		final BalanceCandidate c3 = session.build(400); // 全量が単一カラムに収まる

		// live owner不変
		assertSame("owner container identityが変わってはいけません", ownerContainer, this.owner.getContainer());
		assertEquals("owner幅が変わってはいけません", ownerWidth, this.owner.getWidth(), 0);
		assertEquals("owner高さが変わってはいけません", ownerHeight, this.owner.getHeight(), 0);

		// production診断カウンタ不変(BALANCE_PROBEスコープが全て抑制する)
		assertEquals(0, ContinuationStats.COLUMNS_SPLIT_ATTEMPTS.get());
		assertEquals(0, ContinuationStats.MAX_COLUMN_OPEN_TAIL_DEPTH.get());
		assertEquals(0, ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
		assertEquals(-1, ContinuationStats.LAST_COLUMN_OWNER_COLUMN_COUNT.get());
		assertEquals(0, ContinuationStats.BALANCE_PROBE_SESSIONS.get());

		// 候補は互いに独立した新品
		assertNotSame(c1.candidateBox(), c2.candidateBox());
		assertNotSame(c2.candidateBox(), c3.candidateBox());
		assertNotSame(this.owner, c1.candidateBox());
		assertNotSame(ownerContainer, c1.candidateBox().getContainer());
		assertNotSame(c1.candidateBox().getContainer(), c2.candidateBox().getContainer());

		// 観測の妥当性: 大容量は単一カラムで全量、他は実測extentを持つ
		assertEquals(1, c3.actualColumns());
		assertEquals(300.0, c3.committedCapacity(), 0.5);
		assertTrue(c3.fits(3));
		assertFalse("40ptに10×30ptは収まらないはずです", c1.fits(3));
		assertEquals(c1.actualColumns(), c1.usedExtents().size());
		assertEquals(c2.actualColumns(), c2.usedExtents().size());
	}

	/** 同一容量の再構築は決定的(同じカラム数・同じ実測extent)である。 */
	public void testSameCapacityRebuildsDeterministically() {
		final BalanceProbeSession session = new BalanceProbeSession(frozenInput(this.owner, this.selfId, this.log));
		final BalanceCandidate a = session.build(100);
		final BalanceCandidate b = session.build(100);
		assertEquals(a.actualColumns(), b.actualColumns());
		assertEquals(a.committedCapacity(), b.committedCapacity(), 0);
		assertEquals(a.usedExtents(), b.usedExtents());
		assertNotSame(a.candidateBox(), b.candidateBox());
	}

	/** {@code takeContainer()}は一回だけ——二回目は例外(commit一回制の基盤)。 */
	public void testTakeContainerIsOneShot() {
		final BalanceProbeSession session = new BalanceProbeSession(frozenInput(this.owner, this.selfId, this.log));
		final BalanceCandidate candidate = session.build(400);
		final Container container = candidate.takeContainer();
		assertNotNull(container);
		try {
			candidate.takeContainer();
			fail("二回目のtakeContainer()は例外になるはず");
		} catch (IllegalStateException expected) {
			// OK
		}
	}

	/**
	 * 破棄された候補はGC可能である(メモリリーク排除——2026-07-24の
	 * ユーザー較正で絶対要件)。live側が候補への参照を保持しないことの
	 * 実証。
	 */
	public void testDiscardedCandidateIsGarbageCollectable() throws Exception {
		final BalanceProbeSession session = new BalanceProbeSession(frozenInput(this.owner, this.selfId, this.log));
		BalanceCandidate candidate = session.build(100);
		final WeakReference<MulticolumnBlockBox> ref = new WeakReference<>(candidate.candidateBox());
		candidate = null;
		for (int i = 0; i < 50 && ref.get() != null; ++i) {
			System.gc();
			Thread.sleep(10);
		}
		assertNull("破棄された候補shellはGCされるはずです", ref.get());
	}

	/** フロートを含む内容はプローブ不適格(M6c-5まで)。 */
	public void testFloatContentIsIneligible() {
		final LayoutSource floatLog = new LayoutSource();
		final long floatSelfId = floatLog.append(new LayoutSource.Start(LayoutSource.BoxKind.MULTICOL,
				this.ownerParams, this.ownerPos));
		floatLog.append(new LayoutSource.Start(LayoutSource.BoxKind.FLOAT_BLOCK, childParams(30), new FloatPos()));
		floatLog.append(new LayoutSource.EndBlock());
		floatLog.append(new LayoutSource.EndBlock());
		assertTrue(BalanceProbeInput.capture(floatLog, floatSelfId, this.owner, 3, dummyUserAgent()).isEmpty());
	}

	/** Opaque(replay不能イベント)を含む内容はプローブ不適格。 */
	public void testOpaqueContentIsIneligible() {
		final LayoutSource opaqueLog = new LayoutSource();
		final long opaqueSelfId = opaqueLog.append(new LayoutSource.Start(LayoutSource.BoxKind.MULTICOL,
				this.ownerParams, this.ownerPos));
		opaqueLog.append(new LayoutSource.Opaque());
		opaqueLog.append(new LayoutSource.EndBlock());
		opaqueLog.append(new LayoutSource.EndBlock());
		assertTrue(BalanceProbeInput.capture(opaqueLog, opaqueSelfId, this.owner, 3, dummyUserAgent()).isEmpty());
	}

	/** 凍結イベント列はordinal 1:1で、Barrierを含まない。 */
	public void testFrozenEventsKeepOrdinalParity() {
		final BalanceProbeInput input = frozenInput(this.owner, this.selfId, this.log);
		assertEquals(input.source().events().size(), input.frozenEvents().size());
		for (final SegmentEvent event : input.frozenEvents()) {
			assertFalse(event instanceof SegmentEvent.Barrier);
		}
		// 変換自体もordinal 1:1(LayoutSourceEventConverterの既存契約の再確認)
		assertEquals(input.frozenEvents().size(), LayoutSourceEventConverter.convert(input.source()).size());
	}

	// ---- 探索ドライバ(BalanceProbe.search)の構造的検証 ----

	private static BalanceCandidate stubCandidate(final double capacity, final double maxUsed, final int columns) {
		return new BalanceCandidate(capacity, maxUsed, columns, List.of(maxUsed), null);
	}

	/**
	 * 決定的な理想モデル(30pt刻み・総量300pt・3段)で、探索が
	 * 「指定段数へ収まる最小の実測容量」ちょうど(120pt)へスナップする。
	 */
	public void testSearchFindsMinimalMeasuredCapacity() {
		final BalanceProbe.CandidateSource model = capacity -> {
			final double perColumn = Math.max(1, Math.floor(capacity / 30)) * 30;
			final int columns = (int) Math.ceil(300 / perColumn);
			return stubCandidate(capacity, Math.min(perColumn, 300), columns);
		};
		final BalanceProbe.Result result = BalanceProbe.search(model, 3, 100);
		assertTrue(String.valueOf(result), result instanceof BalanceProbe.Result.Winner);
		final BalanceProbe.Result.Winner winner = (BalanceProbe.Result.Winner) result;
		assertEquals(120.0, winner.winner().committedCapacity(), 0);
		assertEquals(3, winner.winner().actualColumns());
		assertTrue("反復上限内で収束するはずです", winner.builds() <= BalanceProbe.MAX_PROBES);
	}

	/**
	 * どの容量でも収まらないstubに対し、反復上限で必ず打ち切って
	 * フォールバックする(無限ループの構造的排除)。
	 */
	public void testSearchFallsBackAtProbeCap() {
		final BalanceProbe.CandidateSource neverFits = capacity -> stubCandidate(capacity, capacity * 2 + 100, 1);
		final BalanceProbe.Result result = BalanceProbe.search(neverFits, 3, 100);
		assertTrue(String.valueOf(result), result instanceof BalanceProbe.Result.Fallback);
		assertTrue(((BalanceProbe.Result.Fallback) result).builds() <= BalanceProbe.MAX_PROBES);
	}

	/** 非単調観測(成功候補のmaxUsedが既知の失敗容量を下回る)はフォールバック。 */
	public void testSearchFallsBackOnNonMonotonicObservation() {
		final BalanceProbe.CandidateSource nonMonotonic = capacity -> capacity < 100
				? stubCandidate(capacity, capacity + 10, 1)
				: stubCandidate(capacity, 1, 1);
		final BalanceProbe.Result result = BalanceProbe.search(nonMonotonic, 3, 50);
		assertTrue(String.valueOf(result), result instanceof BalanceProbe.Result.Fallback);
	}
}
