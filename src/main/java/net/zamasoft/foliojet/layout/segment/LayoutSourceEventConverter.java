package net.zamasoft.foliojet.layout.segment;

import java.util.Optional;

import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * {@link LayoutSource.Event}を{@link SegmentEvent}へ変換するアダプタ
 * です(2026-07-22新設、M6d-A3c)。
 *
 * <p>
 * イベント数は1:1を維持する(M6d-A2のordinal対応・shadow比較を
 * 壊さないため、codex設計相談で確認)。{@code Start}/{@code Replaced}は
 * 記録時({@code StyleBuilder})にfreeze済みのrecipeを対応する
 * {@link SegmentEvent.BeginBox}/{@link SegmentEvent.Replaced}へ包むだけ
 * (E-6増分3b-3/3b-4。live box保持の過渡変種{@code ReplacedLive}は
 * 3b-6で撤去された——{@code ReplacedBoxImage}もduplicateベースで
 * freezeされる)。{@code Opaque}(範囲replay不能マーカー)は常に
 * {@link SegmentEvent.Barrier}へ変換する——silent fallbackを避けるため、
 * 理由({@link BarrierReason#NOT_YET_SUPPORTED})を明示する。
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
	 * {@code event}が{@link #convert}で{@link SegmentEvent.Barrier}になる
	 * (=replay不能)かを、payloadのdecodeや変換オブジェクトの生成なしで
	 * 判定します(E-6増分3b-5——範囲の適格判定の1パスstreaming走査用。
	 * {@link #convert}の分類と常に同期していること)。3b-6のlive型撤去後、
	 * Barrier源は{@code Opaque}のみ。
	 */
	public static boolean convertsToBarrier(final LayoutSource.Event event) {
		return event instanceof LayoutSource.Opaque;
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
		// Opaqueはそもそも種別情報を保持しない(フィールドなしの位置占有
		// マーカー)ため常にBarrier化する
		case LayoutSource.Opaque opaque ->
			new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED);
		};
	}
}
