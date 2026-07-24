package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * {@link LayoutSource.Event}を{@link SegmentEvent}へ変換するアダプタ
 * です(2026-07-22新設、M6d-A3c)。
 *
 * <p>
 * イベント数は1:1を維持する(M6d-A2のordinal対応・将来のshadow比較を
 * 壊さないため、codex設計相談で確認)。{@code Start}はE-6増分3b-4
 * (2026-07-24)で記録時({@code StyleBuilder.startBox})freeze済みの
 * {@link BoxRecipe}保持となったため、そのまま
 * {@link SegmentEvent.BeginBox}へ包む(旧{@code convertStart}の変換時
 * freezeは{@link BoxRecipe#freeze}として記録時へ前倒しされた)。
 * {@code Replaced}も同様に記録時freeze済みの{@link ReplacedRecipe}を
 * {@link SegmentEvent.Replaced}へ包むだけ(E-6増分3b-3)。記録時に
 * freezeできなかった{@code ReplacedLive}(不安全な{@code image}=
 * {@code ReplacedBoxImage}実装、または未知の
 * {@code AbstractReplacedBox}サブクラス)は従来どおりfail closedで
 * {@link SegmentEvent.Barrier}へ落とす。{@code Opaque}は常に
 * {@link SegmentEvent.Barrier}へ変換する——silent fallbackを避けるため、
 * 理由(常に{@link BarrierReason#NOT_YET_SUPPORTED}、未知の型は
 * {@link BarrierReason#UNKNOWN_TYPE})を明示する。
 * </p>
 *
 * <p>
 * {@code LayoutSource}本体・{@code SourceReplayer}には一切関与しない
 * ——読み取り専用の変換のみを行う。
 * </p>
 */
public final class LayoutSourceEventConverter {
	private LayoutSourceEventConverter() {
	}

	/**
	 * {@code slice}の全イベントを、対応する{@link SegmentEvent}へ1:1変換する。
	 * E-6増分3a(2026-07-24): {@code slice}はstreamingビュー(consume-once)
	 * のため、この変換が{@code slice}を消費する——呼び出し側は同じsliceを
	 * 再度読めない(凍結結果のListだけを使うこと)。
	 */
	public static List<SegmentEvent> convert(final LayoutSource.ReplaySlice slice) {
		// イベント数 == 範囲長(EventIdは連番、captureが検証済み)
		final List<SegmentEvent> result = new ArrayList<>((int) (slice.toId() - slice.fromId() + 1));
		slice.replay(event -> result.add(convert(event)));
		return result;
	}

	/** 単一の{@link LayoutSource.Event}を対応する{@link SegmentEvent}へ変換する。 */
	public static SegmentEvent convert(final LayoutSource.Event event) {
		return switch (event) {
		// E-6増分3b-4: 記録時(StyleBuilder.startBox)にfreeze済みの
		// recipeをそのまま包む(変換時freezeは記録時freezeへ前倒しされた)
		case LayoutSource.Start(final BoxRecipe recipe) -> new SegmentEvent.BeginBox(recipe);
		case LayoutSource.EndBlock endBlock -> new SegmentEvent.EndBox();
		case LayoutSource.Chars(final int charOffset, final LayoutSource.TextPayload payload, final boolean fixed) ->
			// freshChars()はInline=clone、Spilled=storeからのdecode(E-6増分3b-2)
			new SegmentEvent.Text(charOffset, new String(payload.freshChars()), fixed);
		// E-6増分3b-3: 記録時(StyleBuilder.addReplacedBox)にfreeze済みの
		// recipeをそのまま包む(変換時freezeは記録時freezeへ前倒しされた)
		case LayoutSource.Replaced(final ReplacedRecipe recipe) -> new SegmentEvent.Replaced(recipe);
		case LayoutSource.ReplacedLive(final AbstractReplacedBox box) ->
			// 記録時にfreezeできなかったもの: 従来と同じ分類でBarrier化する
			// (unsafe image=NOT_YET_SUPPORTED、未知のサブクラス=UNKNOWN_TYPE
			// ——旧convertReplacedの判定順を維持)
			new SegmentEvent.Barrier(Optional.empty(),
					box.getReplacedParams().image instanceof net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage
							? BarrierReason.NOT_YET_SUPPORTED
							: BarrierReason.UNKNOWN_TYPE);
		// Opaqueはそもそも種別情報を保持しない(フィールドなしの位置占有
		// マーカー)ため常にBarrier化する
		case LayoutSource.Opaque opaque ->
			new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED);
		};
	}
}
