package net.zamasoft.foliojet.layout;

import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.segment.FlexParamsTemplate;
import net.zamasoft.foliojet.layout.segment.GridParamsTemplate;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.layout.segment.SegmentExecutor;

/**
 * レイアウトソースの再生ドライバです(M6b v3)。
 *
 * <p>
 * 改ページ残余のうち「丸ごと次ページへ移動した閉じた部分木」を、
 * LayoutSource の記録から再スタイルなしで再レイアウトします。
 * ライブの StyleBuilder/DocumentBuilder の状態には一切触れず、
 * 新品の DocumentBuilder を既存のルートビルダーへ向けて駆動します
 * (doc プロトコルの対称性 pop→open→push が新品の unitizer 上で
 * 完結するため、v1 の再入クラッシュは構造的に起きません)。
 * ボックスは記録済みの params/pos から再インスタンス化されるため、
 * 新しいページ文脈(利用可能幅・フロート)で完全に再レイアウトされます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SourceReplayer {
	/** 再生の全期間にわたりRootの平行移動を禁止する例外安全なスコープです。 */
	private static final class TranslateBlockScope implements AutoCloseable {
		private final RootBuilder root;

		TranslateBlockScope(final BlockBuilder target) {
			this.root = target instanceof RootBuilder r ? r : target.getPageContext();
			if (this.root != null) {
				this.root.enterTranslateBlockScope();
			}
		}

		@Override
		public void close() {
			if (this.root != null) {
				this.root.exitTranslateBlockScope();
			}
		}
	}

	/**
	 * 閉部分木のソース再生の発火計測です(移行カバレッジの証明・診断用)。
	 */
	public static final AtomicLong SUBTREE_REPLAYS = new AtomicLong();

	/**
	 * 切断段落の尾部再生の発火計測です(実験フラグ制)。
	 */
	public static final AtomicLong TEXT_TAIL_REPLAYS = new AtomicLong();

	/**
	 * 吸収済み再生範囲(C1c prefixItems)経由の発火計測です
	 * (SUBTREE_REPLAYS の内数。ボックス運搬なしの経路が実際に
	 * 通っていることの移行カバレッジ)。
	 */
	public static final AtomicLong PREFIX_REPLAYS = new AtomicLong();

	/**
	 * カラムバランスのソース再生の発火計測です(M6c)。
	 */
	public static final AtomicLong BALANCE_REPLAYS = new AtomicLong();

	/**
	 * 現在のスレッドで駆動中の部分木範囲です(2026-08-23)。
	 *
	 * <p>
	 * 再生した部分木が駆動完了前に改ページすると、継続側に同じ
	 * SourceAnchorが再付与された新品の箱が現れ、その範囲がもう一度
	 * 刻印・再生される。再開位置が部分木のStartまで巻き戻り、同じ内容を
	 * ページごとに作り直す(v2生成器 seed 30: 匿名セル入りの縦書き表が
	 * 34ページに複製された)。同じ範囲への再入だけを拒否し、呼び出し側の
	 * box-restyleフォールバック(replayFromSource==false)へ戻す。
	 * </p>
	 */
	private record ActiveReplay(LayoutSource source, long fromId, long toId) {
	}

	private static final ThreadLocal<java.util.ArrayDeque<ActiveReplay>> ACTIVE_REPLAYS = new ThreadLocal<>();

	private SourceReplayer() {
		// driver
	}

	/**
	 * capture 済み範囲のイベントを doc へそのまま再駆動します
	 * (共有ドライバ)。slice は検証済みの streaming ビュー(リース付き。
	 * E-6増分3a)のため、駆動中の入れ子改ページによる compact の影響を
	 * 受けません(未読範囲はリースが守る)。
	 *
	 * <p>
	 * E-6増分3b-1(2026-07-24): 駆動本体(ボックス構築・SourceAnchor
	 * 再付与・Chars の fresh copy 駆動)は
	 * {@link SegmentExecutor} へ一元化した。E-6増分3b-6: live型
	 * ({@code ReplacedLive})の撤去により過渡の {@code executeLive}
	 * 経路も撤去され、{@code LayoutSource.Event} をオンザフライで
	 * {@link SegmentEvent} へ変換して単一の
	 * {@link SegmentExecutor#execute(SegmentEvent)} で駆動する。
	 * 範囲に {@code Opaque} が
	 * 含まれる場合は Barrier 変換 → execute が即時失敗する——適格判定
	 * ({@code containsOpaque})は呼び出し側の契約。
	 * </p>
	 */
	private static void drive(final DocumentBuilder doc, final LayoutSource.ReplaySlice slice) {
		// slice の EventId は fromId からの連番(capture が検証済み)。
		// 再生インスタンスにはイベントIDから SourceAnchor を再付与する
		// (P0: アンカーはボックス個体に属する — 次の破断で再び
		// 再生可能になるための系譜)
		final SegmentExecutor executor = new SegmentExecutor(doc, slice.fromId());
		slice.replay(event -> executor.execute(LayoutSourceEventConverter.convert(event)));
	}

	/**
	 * ログ範囲を scratch ページへ再生し、実レイアウトで計測します(M2c)。
	 * ライブの状態には一切触れず、新品のボックス木を作って測るため、
	 * 何度でも・任意の寸法で呼べます。
	 *
	 * @param log      ソースログ
	 * @param fromId   範囲の先頭 EventId
	 * @param toId     範囲の末尾 EventId
	 * @param template 書体等を引き継ぐ計算済みパラメータ
	 * @param ua       ユーザーエージェント
	 * @param width    scratch ページ幅(max-content 測定は十分大きな値)
	 * @param height   scratch ページ高さ
	 * @param paginate 破断を許すか(収まりのプローブは true、寸法測定は false)
	 * @return 測定結果を保持する生成器(最終ページ・ページ数)
	 */
	public static MeasurePageGenerator measure(final LayoutSource log, final long fromId, final long toId,
			final BlockParams template, final net.zamasoft.foliojet.ua.UserAgent ua, final double width,
			final double height, final boolean paginate) {
		try (ReplayIntent.Scope replay = ReplayIntent.MEASURE.enter();
				ScratchReplayScope scratch = new ScratchReplayScope();
				ContinuationStats.TwoPassMeasurement measurement = ContinuationStats.twoPassMeasurement(ReplayIntent.MEASURE)) {
			return measureRange(log, fromId, toId, template, ua, width, height, paginate);
		}
	}

	private static MeasurePageGenerator measureRange(final LayoutSource log, final long fromId, final long toId,
			final BlockParams template, final net.zamasoft.foliojet.ua.UserAgent ua, final double width,
			final double height, final boolean paginate) {
		final MeasurePageGenerator pg = new MeasurePageGenerator(ua, template, width, height, log);
		final DocumentBuilder doc = new DocumentBuilder(pg);
		if (!paginate) {
			doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
		}
		// 子範囲を裸のまま scratch ページ直下へ流すと、フロート等が
		// ページボックスに係留されようとして壊れる。元のブロックに相当する
		// ラッパーブロックで包んで、係留文脈を通常構築と同型にする
		doc.startBox(createMeasureWrapper(template));
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			// 計測はフォールバック経路を持たない(範囲は呼び出し側が
			// 生きているうちに確定させる契約)
			throw new IllegalStateException("measure range is not intact: [" + fromId + ", " + toId + "]");
		}
		try (slice) {
			drive(doc, slice);
			doc.endBox();
			doc.end();
		}
		return pg;
	}

	/** 子範囲の外にあるGrid/FlexのStartに相当する配置文脈も復元します。 */
	private static FlowBlockBox createMeasureWrapper(final BlockParams template) {
		final BlockParams common = createMeasureWrapperParams(template);
		if (template instanceof GridParams grid) {
			final GridParams params = GridParamsTemplate.freeze(grid).materialize();
			BlockParamsTemplate.freeze(common).materializeInto(params);
			return new GridBox(params, new FlowPos());
		}
		if (template instanceof FlexParams flex) {
			final FlexParams params = FlexParamsTemplate.freeze(flex).materialize();
			BlockParamsTemplate.freeze(common).materializeInto(params);
			// columnの適格性と配置に必要な主軸寸法は保持する。行寸法・枠は中立。
			params.size = net.zamasoft.foliojet.layout.box.params.Dimension.create(
					flex.flow.isVertical() ? flex.size.getWidth() : 0,
					flex.flow.isVertical() ? 0 : flex.size.getHeight(),
					flex.flow.isVertical() ? flex.size.getWidthType() : net.zamasoft.foliojet.layout.box.params.LengthType.AUTO,
					flex.flow.isVertical() ? net.zamasoft.foliojet.layout.box.params.LengthType.AUTO : flex.size.getHeightType());
			if (!flex.flexDirection.isRow() && flex.flexWrap.isWrap()) {
				// column wrapは両軸definiteが適格条件。中立化でcoordinatorを失わない。
				params.size = flex.size;
			}
			return new FlexBox(params, new FlowPos());
		}
		return new FlowBlockBox(common, new FlowPos());
	}

	/** scratch 再生を包む匿名ブロックへ、元のテキスト文脈を写します。 */
	static BlockParams createMeasureWrapperParams(final BlockParams template) {
		final BlockParams wrapperParams = new BlockParams();
		wrapperParams.fontStyle = template.fontStyle;
		wrapperParams.fontManager = template.fontManager;
		wrapperParams.lineBreakRules = template.lineBreakRules;
		wrapperParams.flow = template.flow;
		wrapperParams.writingModeVariant = template.writingModeVariant;
		wrapperParams.direction = template.direction;
		wrapperParams.unicodeBidi = template.unicodeBidi;
		wrapperParams.paragraphBidi = template.paragraphBidi;
		wrapperParams.bidiSemanticAlias = template.bidiSemanticAlias;
		// ラッパー直下へ裸のテキストが流れると、そのテキスト状態は行頭で
		// ラッパーの params から取り直される(BuilderGlyphHandler)。元
		// ブロックのテキスト組版パラメータを写さないと autospace や
		// letter-spacing が脱落し、実測が模倣計測より痩せて折返しを誤る
		// (kabutan「2,980.0円」2026-08-08)。組版に効く継承フィールドを写す
		wrapperParams.letterSpacing = template.letterSpacing;
		wrapperParams.wordSpacing = template.wordSpacing;
		wrapperParams.textTransform = template.textTransform;
		wrapperParams.whiteSpace = template.whiteSpace;
		wrapperParams.wordWrap = template.wordWrap;
		wrapperParams.textWrapStyle = template.textWrapStyle;
		// T5b(2026-09-06): strut 規約も写す。写さないとラッパー直下の atomic 行が MAIN と違う高さで実測される(codex レビュー P2)
		wrapperParams.strictLineBox = template.strictLineBox;
		wrapperParams.tabSize = template.tabSize;
		wrapperParams.tabSizeIsMultiple = template.tabSizeIsMultiple;
		wrapperParams.hyphens = template.hyphens;
		wrapperParams.hyphenateCharacter = template.hyphenateCharacter;
		wrapperParams.hyphenator = template.hyphenator;
		wrapperParams.textAutospace = template.textAutospace;
		wrapperParams.textSpacingTrimOff = template.textSpacingTrimOff;
		wrapperParams.textSpacingTrimStart = template.textSpacingTrimStart;
		wrapperParams.textSpacingTrimEnd = template.textSpacingTrimEnd;
		wrapperParams.textSpacingSpaceFirst = template.textSpacingSpaceFirst;
		wrapperParams.rubyAlign = template.rubyAlign;
		wrapperParams.rubyMerge = template.rubyMerge;
		wrapperParams.rubyOverhang = template.rubyOverhang;
		wrapperParams.rubyPosition = template.rubyPosition;
		wrapperParams.warichu = template.warichu;
		wrapperParams.textCombine = template.textCombine;
		wrapperParams.hangingPunctuationEnd = template.hangingPunctuationEnd;
		wrapperParams.hangingPunctuationFirst = template.hangingPunctuationFirst;
		wrapperParams.hangingPunctuationForceEnd = template.hangingPunctuationForceEnd;
		wrapperParams.textAlign = template.textAlign;
		wrapperParams.textAlignLast = template.textAlignLast;
		wrapperParams.textIndent = template.textIndent;
		wrapperParams.lineHeight = template.lineHeight;
		wrapperParams.firstLineStyle = template.firstLineStyle;
		return wrapperParams;
	}

	/**
	 * 切断段落の尾部(charOffset 以降)を再駆動します(M6b v3)。
	 * ログから該当 Chars を charOffset の単調性で直接探索するため、
	 * 分割でアンカーを失ったチェーンにも依存しません。
	 *
	 * @param log            ソースログ
	 * @param charOffset     再開位置のソース文字オフセット
	 * @param endIdExclusive 尾部の終端(次の兄弟の EventId。負ならログ末尾まで)
	 * @param keepTextOpen   再生後もテキストブロックを開いたままにする
	 *                       (続く SAX ストリームが流れ込む場合)
	 * @param rootBuilder    再生先のルートビルダー
	 * @param pageGenerator  ページ生成器
	 * @return 再開位置を特定し再駆動した場合 true
	 */
	public static boolean replayTextTail(final LayoutSource log, final int charOffset, final long endIdExclusive,
			final boolean keepTextOpen, final BlockBuilder rootBuilder, final PageGenerator pageGenerator) {
		final long fromId = log.findCharsAt(charOffset);
		if (fromId < 0) {
			return false;
		}
		// 尾部は囲みブロックの EndBlock(=このテキストの終わり)または
		// 次の兄弟アイテムの手前まで
		final long cap = endIdExclusive < 0 ? log.nextId() : endIdExclusive;
		final long toId = log.tailBound(fromId, cap) - 1;
		if (toId < fromId || log.containsOpaque(fromId, toId) || log.containsTable(fromId, toId)
				|| log.containsFloat(fromId, toId) || log.containsAbsolute(fromId, toId)) {
			// フロート・絶対配置を含む尾部の再生は係留の再実行(二重化)の
			// 危険があるためフォールバック(replayChildren と同じゲート。
			// 絶対配置は増分4e以前はOpaque記録でcontainsOpaqueが捕捉していた。
			// 表は表セット——2026-07-30——のrecipe記録化以前はOpaque記録で
			// 同様に捕捉されていた——尾部内での表再構築は未検証のため維持)
			return false;
		}
		// live パイプライン(shaper)が未配達のまま保留している文字は
		// break 後に live 側から供給されるため、再生はそこで打ち切る
		final int charEndExclusive = pageGenerator.getDeliveredCharEnd();
		if (charEndExclusive <= charOffset) {
			// 再開位置全体が live 保留中: 再生不要(live が全て供給する)
			return false;
		}
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			// 範囲が欠けていれば box-restyle へフォールバック
			return false;
		}
		try (TranslateBlockScope scope = new TranslateBlockScope(rootBuilder)) {
			final DocumentBuilder doc = new DocumentBuilder(pageGenerator, rootBuilder);
			// E-6増分3b-1: 駆動本体は共有の SegmentExecutor へ。Chars だけは
			// 尾部特有のトリミング(先頭 skip・配達済み終端での打ち切り)を
			// ここで計算し、部分範囲プリミティブで駆動する(それ以外は
			// 3b-6以降 drive と同じオンザフライ変換の単一 execute)
			final SegmentExecutor executor = new SegmentExecutor(doc, slice.fromId());
			final boolean[] first = { true };
			slice.replay(event -> {
				switch (event) {
				case LayoutSource.Chars(final int off, final LayoutSource.TextPayload payload,
						final boolean fixed) -> {
					// E-6増分3b-2: payloadはspill済みのことがある。freshChars()は
					// 毎回freshな配列(Inline=clone、Spilled=decode)を返す
					final char[] ch = payload.freshChars();
					int skip = 0;
					if (first[0]) {
						first[0] = false;
						skip = charOffset - off;
					}
					int len = ch.length - skip;
					if (off >= 0 && off + skip + len > charEndExclusive) {
						// 配達済み終端で打ち切り(以降は live が供給)
						len = charEndExclusive - off - skip;
					}
					executor.executeCharsRange(off + skip, ch, skip, len, fixed);
				}
				default -> executor.execute(LayoutSourceEventConverter.convert(event));
				}
			});
			if (keepTextOpen) {
				doc.finishReplayKeepText();
			} else {
				doc.finishReplay();
			}
			TEXT_TAIL_REPLAYS.incrementAndGet();
			return true;
		}
	}

	/**
	 * 子範囲の再駆動({@link #replayChildren})が可能かを判定します
	 * (2026-07-24分離、排除域P2のM6c-2——バランスの実プローブが反復の前に
	 * 一度だけ検査するため。判定条件は従来の{@code replayChildren}冒頭と
	 * 完全に同一)。
	 *
	 * @param log    ソースログ
	 * @param selfId 親ボックスの Start の EventId
	 * @param flow   再生先の書字方向
	 * @return 再駆動可能なら true
	 */
	public static boolean canReplayChildren(final LayoutSource log, final long selfId,
			final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		if (log == null || selfId < 0) {
			return false;
		}
		if (log.get(selfId) instanceof LayoutSource.Start start
				&& start.recipe() instanceof net.zamasoft.foliojet.layout.segment.BoxRecipe.PlacedTable) {
			// 配置宿主の子は表構造。TABLE Startを含めて表ビルダーを開く必要がある。
			return false;
		}
		final long endId = log.endOf(selfId);
		if (endId < 0 || endId <= selfId + 1) {
			return false;
		}
		// フロート・絶対配置の係留、入れ子段組・縦横混在の再現は未検証の
		// ためフォールバック(絶対配置は増分4e以前はOpaque記録で
		// containsOpaqueが捕捉していた——挙動維持のゲート分離)。
		// 表(表セット、2026-07-30): recipe記録化以前はOpaque記録で
		// containsOpaqueが捕捉していた。バランス再駆動での表全体再構築
		// (auto列幅の再確定を含む)は未検証のため、表専用のゲートで
		// 従来挙動を維持する(MeasuredIntrinsicsと同型)。
		// containsCaption(caption recipe化C1): 表根(restyleItem case TABLEの
		// 直接replay)の内容にキャプションが現れうる——containsTableは入れ子
		// 表しか見ないため、recipe化後はここで明示的に弾く(C2の
		// context-complete検証で解禁するまでrouting不変)
		return !(log.containsOpaque(selfId + 1, endId - 1) || log.observeCaptionGate(selfId + 1, endId - 1)
				|| log.containsTable(selfId + 1, endId - 1)
				|| log.containsFloat(selfId + 1, endId - 1) || log.containsAbsolute(selfId + 1, endId - 1)
				|| log.containsMulticol(selfId + 1, endId - 1) || log.containsMixedFlow(selfId + 1, endId - 1, flow));
	}

	/**
	 * 閉じたブロックの「子イベント範囲」を指定ビルダーへ再駆動します
	 * (M6c: カラムバランス。multicol は endFlowBlock 時点で閉部分木
	 * なので、その内容をソースから ColumnBuilder へ再構築できる)。
	 *
	 * @param log        ソースログ
	 * @param selfId     ブロック自身の StartBlock の EventId
	 * @param target     再生先ビルダー(ColumnBuilder 等)
	 * @param pageGenerator ページ生成器
	 * @return 再駆動できた場合 true(範囲不明・Opaque 含みは false)
	 */
	public static boolean replayChildren(final LayoutSource log, final long selfId, final BlockBuilder target,
			final PageGenerator pageGenerator) {
		if (!canReplayChildren(log, selfId, target.getRootBox().getBlockParams().flow)) {
			return false;
		}
		final long endId = log.endOf(selfId);
		final LayoutSource.ReplaySlice slice = log.capture(selfId + 1, endId - 1);
		if (slice == null) {
			// 範囲が欠けていればボックス再生へフォールバック
			return false;
		}
		try (TranslateBlockScope scope = new TranslateBlockScope(target)) {
			final DocumentBuilder doc = new DocumentBuilder(pageGenerator, target);
			drive(doc, slice);
			doc.finishReplay();
		}
		BALANCE_REPLAYS.incrementAndGet();
		return true;
	}

	/** 子範囲を直接指定する既存の再生入口。リースの所有・終端は呼び出し側が担います。 */
	public static void bindTwoPassRange(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder target, final PageGenerator pageGenerator) {
		bindTwoPassRange(log, fromId, toId, target, pageGenerator, ReplayIntent.current());
	}

	/**
	 * MEASUREでは新品のDocumentBuilderを駆動し、その間に取得した一時リースを
	 * finallyで破棄します。呼び出し元のセルハンドルのリースは所有しません。
	 */
	public static void bindTwoPassRange(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder target, final PageGenerator pageGenerator, final ReplayIntent intent) {
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			throw new IllegalStateException(
					"seal済みTwoPass範囲が失われました(リースが守っているはずの範囲): [" + fromId + ", " + toId + "]");
		}
		bindTwoPassRange(slice, target, pageGenerator, intent);
	}

	/** 本体のリース付きビューと、セルの不変文字sliceを同じドライバで再生する。 */
	public static void bindTwoPassRange(final LayoutSource.ReplaySlice slice,
			final BlockBuilder target, final PageGenerator pageGenerator, final ReplayIntent intent) {
		// -Dfoliojet.debug.floatTrace=1 で浮動体の一生を追う(2026-08-03新設)。
		// 「入れ子の浮動体で内容が消える」
		// (files/fuzz-repro/nested-float-content-loss.html)の切り分けに使った
		// ——受理(BlockBuilder)・配置・再生(Floatings)と対で読むこと。
		if (System.getProperty("foliojet.debug.floatTrace") != null) {
			final StringBuilder where = new StringBuilder();
			final StackTraceElement[] st = new Throwable().getStackTrace();
			for (int k = 1; k < Math.min(st.length, 9); ++k) {
				where.append(' ').append(st[k].getMethodName()).append(':').append(st[k].getLineNumber());
			}
			System.err.println("[float] === 2パス再駆動 intent=" + intent + " 範囲=[" + slice.fromId() + "," + slice.toId() + "]"
					+ where);
		}
		try (slice;
				ReplayIntent.Scope replay = intent.enter();
				ScratchReplayScope scratch = ReplayIntent.current() == ReplayIntent.MEASURE ? new ScratchReplayScope() : null;
				TranslateBlockScope scope = new TranslateBlockScope(target);
				ContinuationStats.TwoPassMeasurement measurement = ContinuationStats.twoPassMeasurement(intent)) {
			final DocumentBuilder doc = new DocumentBuilder(pageGenerator, target, ReplayIntent.current());
			drive(doc, slice);
			doc.finishReplay();
		}
	}

	/**
	 * [fromId, toId] の閉じた部分木列を再駆動します。
	 * 範囲は Opaque を含まないこと(呼び出し側が containsOpaque で検査)。
	 *
	 * @param log           ソースログ
	 * @param fromId        先頭 StartBlock の EventId
	 * @param toId          対応する EndBlock の EventId
	 * @param rootBuilder   再生先のルートビルダー(現在のページ文脈)
	 * @param pageGenerator ページ生成器
	 * @return 再駆動した場合 true。範囲が欠けていれば駆動前に false
	 *         (呼び出し側の契約: box フォールバックがある経路は false を
	 *         フォールバックへ、ない経路(C1c prefix)は失敗にする)
	 */
	public static boolean replay(final LayoutSource log, final long fromId, final long toId,
			final BlockBuilder rootBuilder, final PageGenerator pageGenerator) {
		java.util.ArrayDeque<ActiveReplay> active = ACTIVE_REPLAYS.get();
		if (active != null) {
			for (final ActiveReplay replay : active) {
				if (replay.source() == log && replay.fromId() == fromId && replay.toId() == toId) {
					// 駆動中の範囲への再入(ACTIVE_REPLAYSのコメント参照)。
					// 呼び出し側が保持している箱をrestyle/addBoundする
					return false;
				}
			}
		}
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			return false;
		}
		// 段組ゲートの再確認は capture の後(2026-07-27)。
		// containsMulticol は「範囲が保持されていない」を判定不能として
		// true に倒す(indexOf<0 で即 true)ため、compact で範囲が失われた
		// だけの正常なフォールバックを invariant 違反と誤認していた
		// (刻印〜消費の間に入れ子改ページが compact を走らせる。
		// stampRanges 時点では段組なし・intact と確認済みで、範囲を失う
		// ことは呼び出し側が想定している——RootBuilder.replayFromSource は
		// ボックスを残しており box-restyle へ落ちる)。capture が成功した
		// 後なら intact が保証されるので、ここでの true は本当に段組を
		// 含んでいることを意味する
		assert !log.containsMulticol(fromId, toId) : "段組を含む範囲がソース再生されようとしました: [" + fromId + ", " + toId + "]";
		if (active == null) {
			active = new java.util.ArrayDeque<>();
			ACTIVE_REPLAYS.set(active);
		}
		final ActiveReplay replay = new ActiveReplay(log, fromId, toId);
		active.push(replay);
		try {
			try (TranslateBlockScope scope = new TranslateBlockScope(rootBuilder)) {
				final DocumentBuilder doc = new DocumentBuilder(pageGenerator, rootBuilder);
				drive(doc, slice);
				doc.finishReplay();
			}
			SUBTREE_REPLAYS.incrementAndGet();
			return true;
		} finally {
			assert active.peek() == replay;
			active.pop();
			if (active.isEmpty()) {
				ACTIVE_REPLAYS.remove();
			}
		}
	}
}
