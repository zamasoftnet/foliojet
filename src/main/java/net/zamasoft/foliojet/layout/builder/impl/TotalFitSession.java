package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.zamasoft.foliojet.layout.box.content.BreakToken;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit;

/**
 * Knuth-Plass行分割(CSS {@code text-wrap-style: pretty})のオプトイン
 * セッションです(2026-07-23新設、M3c増分3。2026-07-25に独自プロパティ
 * {@code text.line-breaker}からCSSへ一本化)。
 *
 * <p>
 * {@code requireTextBlock()}〜{@code endTextBlock()}のTextBuilder構築
 * セッション(段落相当)の整形済みイベントを配達せずに蓄積し、セッション
 * 終了時に{@link TotalFitProjection}でbreakpoint列を選択してから、既存の
 * {@link TextBuilder}へ蓄積イベントを再生する。物理的な行生成は従来
 * どおりTextBuilderが唯一の担当で、本セッションは「どのflushで改行
 * するか」({@link TotalFitProjection.Plan})を供給するだけ。
 * </p>
 *
 * <p>
 * 適格条件は保守的で、1つでも満たさなければ計画なし(plan=null)の
 * verbatim再生=legacyと完全一致の出力にフォールバックする:
 * </p>
 * <ul>
 * <li>開始時: 段落の算出値が{@code text-wrap-style: pretty}である・
 * 改ページ再開({@code BreakToken.midFlow/midLine})でない・
 * 横書き({@code WritingMode.TB}。縦書きは天付き調整
 * {@code locateLine()}が行ごとに実効幅を変えるため初期版では除外)・
 * {@code ::first-line}なし・段落開始Y以降に影響しうるfloat排除域が
 * ない・{@code white-space: pre/pre-wrap}でない・
 * {@code word-wrap: break-word}でない・行幅が正で有限。</li>
 * <li>記録中(即時中断→蓄積分をlegacyで再生し、以降は素通し):
 * タブ・インライン置換要素・インラインブロック(ルビを含む)・
 * インライン絶対配置・advance(枠幅)を持つインライン境界・
 * {@code addBound}(段落内float/絶対配置——TextBuilderの実状態を読む
 * ため、読む前に確定させる)・イベント数上限(10000)超過。</li>
 * </ul>
 *
 * <p>
 * 最適化再生中に行間改ページ({@code BreakableBuilder.flush()})が
 * TextBuilderを差し替えた場合、planは元のTextBuilderインスタンスに
 * 束縛されているため、残りのイベントは自然にlegacyの貪欲法で組まれる
 * (計画の座標系と改ページ後の再生機構が混線しない)。
 * </p>
 */
final class TotalFitSession {

	/** 蓄積イベント数の上限です(超えたらlegacyへフォールバック)。 */
	private static final int EVENT_LIMIT = 10000;

	private enum State {
		RECORDING, REPLAYING, DONE
	}

	/** 記録される配達イベントです(再生時に同順で配達する)。 */
	private sealed interface Recorded {
		record Run(FontStyle fontStyle, FontMetrics fontMetrics) implements Recorded {
		}

		record Glyph(int charOffset, char[] cluster, byte clen, int gid) implements Recorded {
		}

		record RunEnd() implements Recorded {
			static final RunEnd INSTANCE = new RunEnd();
		}

		record Ctrl(TextControl quad) implements Recorded {
		}

		record Flush() implements Recorded {
			static final Flush INSTANCE = new Flush();
		}
	}

	private final BlockBuilder builder;

	/** セッション開始時のTextBuilder(planはこのインスタンスに束縛)。 */
	private final TextBuilder textBuilder;

	private final double lineSize;

	private final double textIndent;

	private final TotalFit.LastLinePolicy lastLine;

	private final List<Recorded> events = new ArrayList<>();

	private final List<TotalFitProjection.Piece> pieces = new ArrayList<>();

	private State state = State.RECORDING;

	/**
	 * 再生済みイベント数です(記録中は0)。{@link #clampDeliveredCharEnd}
	 * が「物理的にTextBuilderへ届いた配達境界」を求めるのに使う。
	 */
	private int replayIndex = 0;

	/**
	 * 再生で配達済みのグリフのソース文字終端の最大値です(未配達なら-1)。
	 * legacyの{@code deliveredCharEnd}(グリフでのみ前進)と同じ意味論。
	 */
	private int maxDeliveredGlyphEnd = -1;

	// ---- 幅計測ミラー(TextBuilderと同じ計算で投影用の幅を得る) ----

	/** インラインのTextParamsスタック(TextBuilder.textParamStack相当)。 */
	private final List<AbstractTextParams> paramsStack = new ArrayList<>();

	private final AbstractTextParams baseParams;

	private double letterSpacing;

	private boolean collapseSpaces;

	private FontStyle fontStyle;

	private FontMetrics fontMetrics;

	private TextImpl mirrorText;

	/**
	 * 和文詰めT1a: 同一run内の約物詰めの追跡(autospace有効段落は
	 * pretty対象外のためflagsは常に0=trim判定のみ使用)。
	 */
	private final net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker spacing = //
			new net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker();

	private double pendingBoxWidth = 0;

	private int flushOrdinal = 0;

	private TotalFitSession(final BlockBuilder builder, final TextBuilder textBuilder, final BlockParams params,
			final double lineSize, final double textIndent) {
		this.builder = builder;
		this.textBuilder = textBuilder;
		this.lineSize = lineSize;
		this.textIndent = textIndent;
		this.lastLine = params.textAlignLast == AbstractLineParams.TEXT_ALIGN_JUSTIFY
				? TotalFit.LastLinePolicy.JUSTIFY
				: TotalFit.LastLinePolicy.RAGGED;
		this.baseParams = params;
		this.applyTextState(params);
		// 和文詰めT1b: trim policy(autospace有効段落はpretty対象外のため
		// flagsは0のまま)
		this.spacing.setTrimOff(params.textSpacingTrimOff);
	}

	/**
	 * セッション開始時の適格条件を検査し、適格なら新しいセッションを
	 * 返します(不適格ならnull=従来の直接配達)。
	 */
	static TotalFitSession tryBegin(final BlockBuilder builder, final TextBuilder textBuilder,
			final BreakToken breakToken) {
		if (breakToken.midFlow()) {
			// 改ページ・改段・同一フロー内の再開(midLineを含む)
			return null;
		}
		final LayoutContext.Flow flow = builder.getFlow();
		final BlockParams params = flow.box.getBlockParams();
		if (params.textWrapStyle != AbstractTextParams.TEXT_WRAP_STYLE_PRETTY) {
			// CSS text-wrap-style: pretty のオプトイン(2026-07-25)。
			// 既定(auto)は貪欲法——K-Pの適用単位は段落なので、段落を
			// 確立するブロックの算出値だけを見る
			return null;
		}
		if (params.flow != WritingMode.TB) {
			// 縦書きは天付き(locateLineのCL01調整)が行ごとの実効幅を
			// 変えるため初期版では除外する
			return null;
		}
		if (params.firstLineStyle != null) {
			// ::first-line は先頭行のみスタイルが変わる
			return null;
		}
		if (!textStateSupported(params)) {
			return null;
		}
		// 段落開始Y以降に影響しうるfloat排除域がないこと(codexレビュー:
		// locateLine()は幅照会ではなく4状態を更新する副作用持ちで、下方
		// floatのmaxPageSize設定など「幅が同じでもfloat関与」がある)
		if (builder.toAddFloatings != null && !builder.toAddFloatings.isEmpty()) {
			return null;
		}
		if (builder.floatings != null && !builder.floatings.isEmpty()) {
			// floatingsはpageEnd昇順ソート済み——末尾が最大pageEnd
			final LayoutContext.Floating last = builder.floatings.get(builder.floatings.size() - 1);
			if (LayoutUtils.compare(last.pageEnd, builder.pageAxis) > 0) {
				return null;
			}
		}
		final double lineSize = flow.box.getLineSize();
		final double indent = flow.box.getTextIndent();
		if (!(lineSize > 0) || Double.isInfinite(lineSize) || !(lineSize - indent > 0)) {
			return null;
		}
		final TotalFitSession session = new TotalFitSession(builder, textBuilder, params, lineSize, indent);
		if (LayoutUtils.isNone(session.letterSpacing)) {
			return null;
		}
		return session;
	}

	/**
	 * white-space/word-wrapが初期版の対応範囲かを検査します。
	 */
	private static boolean textStateSupported(final AbstractTextParams params) {
		// 和文詰めA2→既定on化(2026-08-01): autospaceはプロパティでは蹴らない。
		// text-autospace既定normal化でプロパティ検査は全段落を蹴ってしまい、
		// 純英文・純和文のprettyまで死ぬため、実際にギャップが発生した時点で
		// recordGlyphがabortToLegacyする内容ベースの判定へ精密化した
		// (ギャップを含む段落がK-P対象外である点はP1——答申Q5——のまま不変)
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			// スペースをつぶさない経路は行頭・行末のつぶし規則が異なる
			return false;
		case AbstractTextParams.WHITE_SPACE_NORMAL:
		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			// 折り返しあり: break-wordはTextBuilder.glyph()の途中分割を
			// 使うため対応しない
			if (params.wordWrap == AbstractTextParams.WORD_WRAP_BREAK_WORD) {
				return false;
			}
			return true;
		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			return true;
		default:
			return false;
		}
	}

	private void applyTextState(final AbstractTextParams params) {
		this.collapseSpaces = params.whiteSpace != AbstractTextParams.WHITE_SPACE_PRE
				&& params.whiteSpace != AbstractTextParams.WHITE_SPACE_PRE_WRAP;
		this.letterSpacing = LayoutUtils.computeLength(params.letterSpacing,
				this.builder.getFlowBox().getLineSize());
		// 既定on化(2026-08-01): ミラーtrackerにもautospaceフラグを載せ、
		// recordGlyphのgapBefore検査(ギャップ実発生でabortToLegacy)を
		// TextBuilder側(changeTextState)と同じ粒度で追随させる
		this.spacing.setFlags(params.textAutospace);
	}

	/** 記録中であればtrueを返します。 */
	boolean recording() {
		return this.state == State.RECORDING;
	}

	/**
	 * 「物理的にTextBuilderへ届いたソース文字の配達境界」を返します。
	 *
	 * <p>
	 * {@code BuilderGlyphHandler.deliveredCharEnd}は上流(shaper)からの
	 * 配達で前進するため、本セッションが蓄積している間も進んでしまう。
	 * しかし切断段落の尾部再生({@code SourceReplayer.replayTextTail})は
	 * この値を「これ以降はliveが供給する」境界として使うため、蓄積中・
	 * 再生中の未配達イベントの先頭ソース位置でclampしないと、再生中の
	 * 行間改ページで尾部再生と本セッションの残イベント配達が二重供給に
	 * なる。未配達の最初のグリフのソース位置がその境界である(それより
	 * 前はセッション開始前かreplayで配達済み)。
	 * </p>
	 */
	int clampDeliveredCharEnd(final int deliveredCharEnd) {
		if (this.state == State.DONE) {
			return deliveredCharEnd;
		}
		for (int i = this.replayIndex; i < this.events.size(); ++i) {
			if (this.events.get(i) instanceof Recorded.Glyph glyph && glyph.charOffset() >= 0) {
				// 未配達のグリフが残っている: legacyと同じく「配達済み
				// グリフの終端」が境界。まだ1グリフも再生していなければ
				// 最初の未配達グリフの開始で抑える(セッション開始前の
				// 値の上界)
				return Math.min(deliveredCharEnd,
						this.maxDeliveredGlyphEnd >= 0 ? this.maxDeliveredGlyphEnd : glyph.charOffset());
			}
		}
		return deliveredCharEnd;
	}

	/**
	 * イベント数の上限を検査し、超過していればlegacyへ中断します。
	 *
	 * @return 記録を継続できればtrue
	 */
	private boolean checkCapacity() {
		if (this.events.size() < EVENT_LIMIT) {
			return true;
		}
		this.abortToLegacy();
		return false;
	}

	/**
	 * テキストランの開始を記録します。
	 *
	 * @return 記録した場合true(falseなら呼び出し側が直接配達する)
	 */
	boolean recordRun(final FontStyle fontStyle, final FontMetrics fontMetrics) {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		this.events.add(new Recorded.Run(fontStyle, fontMetrics));
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
		this.mirrorText = null;
		return true;
	}

	/**
	 * グリフを記録します(クラスタ文字は防御コピー——上流のバッファは
	 * 再利用される)。幅はTextBuilder.glyph()と同じ計算のミラー
	 * TextImplで求める。
	 */
	boolean recordGlyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		if (this.fontStyle == null || this.fontMetrics == null) {
			this.abortToLegacy();
			return false;
		}
		if (this.mirrorText == null) {
			this.mirrorText = new TextImpl(charOffset, this.fontStyle, this.fontMetrics);
			this.mirrorText.setLetterSpacing(this.letterSpacing);
		}
		final char[] cluster = Arrays.copyOfRange(ch, coff, coff + clen);
		// 既定on化(2026-08-01): autospaceギャップが実際に発生する段落のみ
		// K-P対象外(K-P側のpair gap discount対応はP1——答申Q5——のまま)。
		// プロパティ検査ではなく実発生で判定することで、text-autospace既定
		// normal下でも純英文・純和文の段落はprettyを保つ
		if (this.spacing.gapBefore(cluster, 0, this.fontStyle.getSize()) != 0) {
			this.abortToLegacy();
			return false;
		}
		// T1a: 同一run内の約物詰め(font層から移管)を候補幅へ反映
		// (ギャップ発生段落はpretty対象外のためtrimのみ。最終bindは
		// TextBuilder側trackerがxadvanceで適用する——旧font層kern時代と
		// 同じく分割点の復元はモデル化しない)
		final double trim = this.spacing.trimBefore(cluster, 0, gid, this.mirrorText, this.fontMetrics,
				this.fontStyle.getSize());
		final double advance = this.mirrorText.appendGlyph(cluster, 0, clen, gid) + this.letterSpacing - trim;
		this.pendingBoxWidth += advance;
		this.spacing.glyphAdded(this.mirrorText, this.fontStyle.getSize(), cluster, 0, clen, gid);
		this.events.add(new Recorded.Glyph(charOffset, cluster, clen, gid));
		if (this.mirrorText.getGlyphCount() > 10000) {
			// TextBuilder.glyph()の長大ラン分割と同じ地点でミラーも切る
			// (分割点のカーニング打ち切りを一致させる)
			this.mirrorText = null;
		}
		return true;
	}

	/** テキストランの終了を記録します。 */
	boolean recordRunEnd() {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		this.events.add(Recorded.RunEnd.INSTANCE);
		this.mirrorText = null;
		return true;
	}

	/**
	 * 制御(空白・改行・ソフトハイフン・インライン境界)を記録します。
	 * 初期版で対応しないもの(タブ・置換要素・インラインブロック・ルビ・
	 * インライン絶対配置・枠幅付きインライン境界)はlegacyへ中断し
	 * falseを返します(呼び出し側が直接配達する)。
	 */
	boolean recordControl(final TextControl quad) {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		// 既定on化(2026-08-01): ミラーtrackerのpair状態をTextBuilder.control
		// と同じ規約で断つ(幅0のインライン開始/終了だけはpairを維持)。
		// これが無いと「あ text」のような空白を挟む和欧の並びで
		// gapBefore検査が偽のギャップを検出し、K-Pが不要に中断される
		if (!(quad instanceof InlineQuad inlineQuad
				&& (inlineQuad.getType() == InlineQuad.INLINE_START
						|| inlineQuad.getType() == InlineQuad.INLINE_END)
				&& inlineQuad.getAdvance() == 0)) {
			this.spacing.reset();
		}
		if (quad instanceof Control control) {
			switch (control.getControlChar()) {
			case SoftHyphen.CHAR: {
				final SoftHyphen softHyphen = (SoftHyphen) control;
				this.emitPendingBox();
				this.pieces.add(new TotalFitProjection.Piece.Hyphen(softHyphen.getText().getAdvance()));
				break;
			}

			case '\n':
				this.emitPendingBox();
				this.pieces.add(new TotalFitProjection.Piece.LineFeed());
				break;

			case ' ': {
				if (!this.collapseSpaces) {
					// 適格条件で除外済みのはずだが防御的に中断する
					this.abortToLegacy();
					return false;
				}
				final WhiteSpace whiteSpace = (WhiteSpace) control;
				this.emitPendingBox();
				this.pieces.add(new TotalFitProjection.Piece.Space(whiteSpace.getAdvance()));
				break;
			}

			case '\t':
			default:
				// タブは行位置依存幅(TextBuilder.control()のlineAxis%24)
				this.abortToLegacy();
				return false;
			}
		} else if (quad instanceof InlineQuad inlineQuad) {
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				final InlineQuad.InlineStartQuad startQuad = (InlineQuad.InlineStartQuad) inlineQuad;
				final AbstractTextParams params = startQuad.box.getTextParams();
				if (inlineQuad.getAdvance() != 0 || !textStateSupported(params)) {
					// 枠幅付きインラインは行分割時の再生成で枠幅が変わる
					this.abortToLegacy();
					return false;
				}
				this.paramsStack.add(params);
				this.applyTextState(params);
				if (LayoutUtils.isNone(this.letterSpacing)) {
					this.abortToLegacy();
					return false;
				}
				break;
			}

			case InlineQuad.INLINE_END: {
				if (inlineQuad.getAdvance() != 0 || this.paramsStack.isEmpty()) {
					this.abortToLegacy();
					return false;
				}
				this.paramsStack.remove(this.paramsStack.size() - 1);
				final AbstractTextParams params = this.paramsStack.isEmpty() ? this.baseParams
						: this.paramsStack.get(this.paramsStack.size() - 1);
				this.applyTextState(params);
				break;
			}

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_ABSOLUTE:
			default:
				// 置換要素・インラインブロック(ルビ含む)・絶対配置
				this.abortToLegacy();
				return false;
			}
		} else {
			this.abortToLegacy();
			return false;
		}
		this.events.add(new Recorded.Ctrl(quad));
		return true;
	}

	/** flush(breakpoint候補)を記録します。 */
	boolean recordFlush() {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		this.emitPendingBox();
		final double stretch = this.fontStyle != null ? this.fontStyle.getSize() * 0.5 : 0;
		this.pieces.add(new TotalFitProjection.Piece.Flush(this.flushOrdinal, stretch));
		this.events.add(Recorded.Flush.INSTANCE);
		++this.flushOrdinal;
		return true;
	}

	private void emitPendingBox() {
		if (this.pendingBoxWidth != 0) {
			this.pieces.add(new TotalFitProjection.Piece.Box(this.pendingBoxWidth));
			this.pendingBoxWidth = 0;
		}
	}

	/**
	 * 蓄積を中止し、蓄積済みイベントをlegacyの経路で再生します。以降の
	 * イベントは呼び出し側が直接配達する(完全にlegacyと同じ挙動)。
	 * float・絶対配置の{@code addBound}のように「TextBuilderの実状態を
	 * 読む」外部処理の前に呼ぶこと。
	 */
	void abortToLegacy() {
		if (!this.recording()) {
			return;
		}
		this.replay(null);
	}

	/**
	 * セッション終了({@code endTextBlock()})。適格に完走していれば
	 * breakpoint列を選択して再生し、選択できない場合はverbatim再生
	 * (=legacyと一致)します。再生中の再入(行間改ページによる
	 * {@code endTextBlock()})では何もしません。
	 */
	void finishSession() {
		if (!this.recording()) {
			return;
		}
		this.emitPendingBox();
		final TotalFitProjection.Plan plan = TotalFitProjection.plan(this.pieces, this.lineSize - this.textIndent,
				this.lineSize, parameters(this.lastLine));
		this.replay(plan);
	}

	/**
	 * CSS向けの{@link TotalFit.Parameters}です(TeXのplain第2パスの
	 * 慣用値)。toleranceを緩めすぎると「ほぼ全てのbreakpointが実行
	 * 可能」になり、activeの除去を持たない{@code TotalFit}では候補集合が
	 * 爆発する({@link TotalFitProjection}の伸縮モデルの注記参照)。
	 */
	private static TotalFit.Parameters parameters(final TotalFit.LastLinePolicy lastLine) {
		return new TotalFit.Parameters(200, 10, 10000, 10000, 5000, lastLine);
	}

	/**
	 * 蓄積イベントを配達します。planがnullならverbatim(legacyと同一の
	 * 呼び出し列)、非nullなら元のTextBuilderへplanを束縛して配達する
	 * (flushの改行判定だけがplanに従い、行の物理生成はすべて既存
	 * コード)。行間改ページでTextBuilderが差し替わった場合、planは
	 * 死んだインスタンスに残るため残りは自然にlegacyで組まれる。
	 */
	private void replay(final TotalFitProjection.Plan plan) {
		// 注意: this.builder.textSessionは再生完了までnullにしない——
		// 再生中の改ページ処理が配達境界(clampDeliveredCharEnd)を
		// 参照するため。record系・abort・finishはすべてstateガードで
		// 再入しない
		this.state = State.REPLAYING;
		if (plan != null) {
			this.textBuilder.totalFitPlan = plan;
		}
		try {
			int ordinal = 0;
			for (int i = 0; i < this.events.size(); ++i) {
				this.replayIndex = i;
				// 行間改ページがTextBuilderを差し替えるため毎回読み直す
				switch (this.events.get(i)) {
				case Recorded.Run run -> this.builder.textBuilder.startTextRun(run.fontStyle(), run.fontMetrics());
				case Recorded.Glyph glyph -> {
					if (glyph.charOffset() >= 0) {
						this.maxDeliveredGlyphEnd = Math.max(this.maxDeliveredGlyphEnd,
								glyph.charOffset() + glyph.clen());
					}
					this.builder.textBuilder.glyph(glyph.charOffset(), glyph.cluster(), 0, glyph.clen(), glyph.gid());
				}
				case Recorded.RunEnd runEnd -> this.builder.textBuilder.endTextRun();
				case Recorded.Ctrl ctrl -> this.builder.textBuilder.control(ctrl.quad());
				case Recorded.Flush flush -> {
					if (plan != null) {
						plan.arriveFlush(ordinal);
					}
					++ordinal;
					// BreakableBuilderの行間改ページ機構を通す(legacyの
					// live配達と同じ経路)
					this.builder.flush();
				}
				}
			}
			this.replayIndex = this.events.size();
		} finally {
			if (plan != null && this.textBuilder.totalFitPlan == plan) {
				this.textBuilder.totalFitPlan = null;
			}
			this.state = State.DONE;
			this.events.clear();
			this.pieces.clear();
			this.builder.textSession = null;
		}
	}
}
