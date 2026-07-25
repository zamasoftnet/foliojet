package net.zamasoft.foliojet.layout.segment;

/**
 * 正規Segmentモデルにおける再生イベントです(2026-07-22新設、
 * M6d-A3a。2026-07-25時点で{@link LayoutSourceEventConverter}・
 * {@code SegmentExecutor}・{@code SourceReplayer}が使っている)。
 *
 * <p>
 * 旧{@code net.zamasoft.foliojet.layout.fragment.LayoutSource.Event}
 * (5variant: {@code Start}/{@code Replaced}/{@code Chars}/
 * {@code EndBlock}/{@code Opaque})と1:1のイベント数を維持する
 * (M6d-A2のordinal対応・将来のshadow比較を壊さないため、codex設計
 * 相談で確認)が、意味を正規化する:
 * </p>
 * <ul>
 * <li>{@code Start} → {@link BeginBox}(recipeのみ、子構造は持たない
 * ——recipeは「箱の生成方法」であって「構造」ではなく、混ぜない)</li>
 * <li>{@code EndBlock} → {@link EndBox}(実態に合わせて改名)</li>
 * <li>{@code Chars} → {@link Text}({@code char[]}を{@code String}へ
 * ——mutableな配列をrecordから取得して変更できてしまう問題を解消)</li>
 * <li>{@code Replaced} → {@link Replaced}(live boxではなく
 * {@link ReplacedRecipe}を持つ)</li>
 * <li>{@code Opaque} → {@link Barrier}(理由なしの位置占有マーカー
 * ではなく、{@link BarrierReason}で理由を必ず明示——silent fallback
 * を避ける)</li>
 * </ul>
 */
public sealed interface SegmentEvent {
	/**
	 * ボックスの開始です。子コンテンツの構造(子への参照)はここには
	 * 含めない——構造は{@link EndBox}までの並びが表現する。
	 */
	record BeginBox(BoxRecipe recipe) implements SegmentEvent {
	}

	/** ボックスの終了です(旧{@code EndBlock}、実態に合わせて改名)。 */
	record EndBox() implements SegmentEvent {
	}

	/**
	 * テキストです。
	 *
	 * @param sourceOffset ソース文字オフセット(生成内容は{@code -1}、
	 *                     旧{@code Chars.charOffset}と同じ規約)
	 * @param text         テキスト内容(不変の{@code String}——旧
	 *                     {@code char[]}をrecordから取得して変更
	 *                     できてしまう問題を解消)
	 * @param fixed        docプロトコルの固定テキストフラグ
	 */
	record Text(int sourceOffset, String text, boolean fixed) implements SegmentEvent {
	}

	/** 置換要素です。live boxではなく{@link ReplacedRecipe}を持つ。 */
	record Replaced(ReplacedRecipe recipe) implements SegmentEvent {
	}

	/**
	 * まだ正規化・再生に対応していない内容の位置占有マーカーです
	 * (旧{@code Opaque}相当。A3c実装時に判明: 旧{@code Replaced}も
	 * {@code ReplacedRecipe}の中身が未設計な間はこちらへ変換される)。
	 * 理由({@link BarrierReason})を必ず持ち、silent fallbackを避ける。
	 *
	 * @param kind   元のボックス種別(判明していれば——旧{@code Start}が
	 *               未対応の{@code BoxKind}を持っていた場合はここに
	 *               入る)。旧{@code Opaque}・{@code Replaced}はそもそも
	 *               種別情報を保持しないため空({@link
	 *               java.util.Optional#empty()})。
	 * @param reason 理由
	 */
	record Barrier(java.util.Optional<BoxKind> kind, BarrierReason reason) implements SegmentEvent {
	}
}
