package net.zamasoft.foliojet.layout.fragment;

/**
 * {@link FragmentationTrace}に記録される1件の観測です(2026-07-21新設、
 * M6d-0.5)。live boxやcontainerへの参照は保持しない——識別には
 * {@code System.identityHashCode}由来の軽量fingerprintのみ使う
 * (M6d設計の材料として「何が変異したか」を後から追えれば十分で、
 * 実際のオブジェクトを握り続ける必要はない)。
 */
public sealed interface FragmentationEvent {
	/** この観測がPAGE経路かCOLUMN経路かを返します。 */
	boolean column();

	/**
	 * {@code RootBuilder.pageBreak()}呼び出しの入口です(2026-07-21新設)。
	 * 1回のPAGE改ページ判定全体の開始点。
	 *
	 * @param forced        {@code BreakMode.ForceBreakMode}かどうか
	 * @param flags         {@code IPageBreakableBox.FLAGS_*}
	 * @param flowStackDepth 呼び出し時点のflowStackの深さ
	 */
	record PageBreakEntry(boolean forced, byte flags, int flowStackDepth) implements FragmentationEvent {
		public boolean column() {
			return false;
		}
	}

	/**
	 * {@code BreakableBuilder.columnBreak()}呼び出しの入口です
	 * (2026-07-21新設)。1回のCOLUMN改段判定全体の開始点。
	 *
	 * @param forced      {@code BreakMode.ForceBreakMode}かどうか
	 * @param flags       {@code IPageBreakableBox.FLAGS_*}
	 * @param depth       owner(段組)から数えた相対open pathの深さ
	 * @param columnCount owner自身の設定段数
	 */
	record ColumnBreakEntry(boolean forced, byte flags, int depth, int columnCount) implements FragmentationEvent {
		public boolean column() {
			return true;
		}
	}

	/**
	 * {@code FlowContainer.splitPageAxis}の自動改ページ主ループが
	 * インデックス{@code i}のflowアイテムを検分した記録です。
	 *
	 * @param containerFingerprint 検分中のFlowContainer自身の識別子
	 *                             ({@code System.identityHashCode})
	 * @param i                    現在検分中のflowインデックス
	 * @param lastOrphan           このpageLimitでの最初の孤立行インデックス
	 * @param ignoreAvoid          avoid抑止を無視するモードに入っているか
	 * @param isChainMember        plan選択済みチェーンメンバーかどうか
	 */
	record LoopExamine(boolean column, int containerFingerprint, int i, int lastOrphan, boolean ignoreAvoid,
			boolean isChainMember) implements FragmentationEvent {
	}

	/**
	 * avoid-break押し戻し(pushback)により{@code i}が巻き戻された記録です。
	 *
	 * @param fromIndex   巻き戻し前のインデックス
	 * @param toIndex     巻き戻し後のインデックス({@code
	 *                    FlowCutter.AvoidPushback.resumeIndex()})
	 * @param newPageLimit 巻き戻し後の新しいpageLimit
	 */
	record Pushback(boolean column, int fromIndex, int toIndex, double newPageLimit) implements FragmentationEvent {
	}

	/**
	 * plan選択済みチェーンメンバー(またはTABLE/TEXT_BLOCK等の通常アイテム)
	 * の{@code split}/{@code splitForContinuation}が返した結果です。
	 *
	 * @param i           結果を返したflowアイテムのインデックス
	 * @param isChainMember チェーンメンバーの結果かどうか
	 * @param resultKind  {@code "Keep"}/{@code "Move"}/{@code "Split"}/{@code "Frame"}
	 */
	record ChildResult(boolean column, int i, boolean isChainMember, String resultKind) implements FragmentationEvent {
	}

	/**
	 * {@code splitPageAxis}呼び出し全体の最終決定です(どの{@code return}
	 * 文が実行されたか)。
	 *
	 * @param containerFingerprint 対象FlowContainer自身の識別子
	 * @param exitPath             決定を返した箇所を表す短いラベル
	 *                             (例: {@code "moveAll:plain(this)"}・
	 *                             {@code "nextBox+splitFloatings"})
	 * @param remainingFlows       決定確定時点でのthis側flow件数
	 */
	record Decision(boolean column, int containerFingerprint, String exitPath, int remainingFlows)
			implements FragmentationEvent {
	}
}
