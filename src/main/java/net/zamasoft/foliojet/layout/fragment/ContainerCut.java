package net.zamasoft.foliojet.layout.fragment;

/**
 * コンテナのページ方向切断の結果です(C1d-C)。
 *
 * <p>
 * 従来の {@code Container} 返し(null=KEEP / this=MOVE / 他=残余)の
 * 契約に、継続フレームの伝播({@link WithFrame})を加えた形。
 * BreakPlan なしの legacy 呼び出しでは {@link Plain} のみが返ります。
 * </p>
 *
 * <p>
 * <b>{@link SplitResult}(box粒度)との対応表</b>——本型はコンテナ粒度:
 * {@code Plain(null)}=Keep、{@code Plain(構築元コンテナ自身)}=Move、
 * {@code Plain(他)}=Split(残余コンテナ)、{@code WithFrame}=Frame。
 * </p>
 */
public sealed interface ContainerCut {
	/**
	 * 従来契約の結果です(container: null=KEEP / 自身=MOVE / 他=残余)。
	 *
	 * <p>
	 * <b>sentinelの意味と解釈規約(2026-07-24 E-4で明文化)</b>:
	 * 「自身」とはこのPlainを構築した {@code FlowContainer} 自身であり、
	 * 消費側は<b>自分がsplitPageAxisを呼んだ対象コンテナ</b>との
	 * identity比較で解釈する。{@code ColumnsContainer} は切断を最終列へ
	 * 委譲してその結果をそのまま返すため、MOVEのsentinelは
	 * <b>最終列(lastColumn)</b>になる——段組ownerのcontainerと比較する
	 * 消費側({@code AbstractBlockBox.splitForContinuation}等)では一致
	 * せず残余経路へ落ち、列を基準に比較する消費側
	 * ({@code AbstractContainerBox.prepareColumnCut}のactiveColumn比較)
	 * では正しくMOVEと判定される。前者の現行挙動は
	 * {@code ContinuationStats.recordLastColumnMoveCandidate} で観測中で
	 * あり、sentinelを{@code Keep}/{@code Move}バリアントへ型化すると
	 * この層ごとの解釈差(何とidentity比較するか)を一つの意味へ潰して
	 * しまい挙動が変わりうる。型化はlegacy 3引数
	 * {@code Container.splitPageAxis} 契約の撤去(B6系)と同時に行うこと。
	 * </p>
	 */
	record Plain(net.zamasoft.foliojet.layout.box.content.Container container) implements ContainerCut {
	}

	/**
	 * plan選択済みチェーンメンバー自身が{@code Keep}/{@code Move}を返した
	 * ことを明示的に運ぶ結果です(2026-07-21新設、M6b Phase B5c-2)。
	 * 強制改ページ分岐に加え、自動改ページ主ループでも「nextBoxが
	 * チェーンメンバー単体だけを含む」(force-branchと同型でcontainer-
	 * identity比較では判別できない)場合に限って付与される(2026-07-22
	 * のB5c-2 Step3再挑戦で適用済み——{@code FlowContainer.splitPageAxis}
	 * の最終return参照)。
	 *
	 * <p>
	 * <b>改ページ契約§5.10との関係(2026-07-24 E-4で明文化)</b>:
	 * §5.10ルール2が「不要」と裁定したのは<b>MOVE専用</b>の継続型
	 * ({@code OpenTail.MovedOpen}/{@code ResumeTail.MovedOpen}——
	 * 2026-07-22に撤去済み)。本型はMOVE専用ではなくKEEP/MOVE両方の
	 * 「チェーンメンバー自身の判定結果」を運ぶマーカーであり、段組を
	 * またぐ改ページ(§5.10ルール4)でcontainer-identity比較では判別
	 * できない結果を親へ伝えるために恒久的に必要な型。削除予定はない。
	 * MOVEで生じたremainderの継続はルール2どおり専用型を新設せず既存の
	 * {@code OpenTailShape}/{@code ContinuationFrame}機構へ合流させて
	 * いる(コンテナに実内容がある場合の合流分岐——
	 * {@code AbstractBlockBox.splitForContinuation}等参照)。
	 * </p>
	 */
	record PlainWithChainStop(net.zamasoft.foliojet.layout.box.content.Container container, ChainStopReason reason)
			implements ContainerCut {
	}

	/**
	 * 切断がチェーンを貫通し、残余コンテナに加えて継続フレームが
	 * 返されました(チェーン子はボックスとして運搬されない)。
	 */
	record WithFrame(net.zamasoft.foliojet.layout.box.content.Container container,
			Continuation.ContinuationFrame frame) implements ContainerCut {
	}
}
