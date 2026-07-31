package net.zamasoft.foliojet.layout.segment;

import java.util.Arrays;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;

/**
 * 共有Segment executorです(2026-07-24新設、E-6増分3b-1——
 * {@code docs/consultations/consult-e6b-remaining-increments-codex.md}
 * §2.3増分1)。
 *
 * <p>
 * {@code SourceReplayer.drive}に重複していた
 * replay switchの「駆動部分」——{@code DocumentBuilder}への駆動・
 * ordinal({@code EventId})→{@code SourceAnchor}再付与・テキストの
 * fresh copy化——をここへ一元化する。入力はstreamingカーソル
 * (1イベントずつの呼び出し)で、Listを要求しない。
 * </p>
 *
 * <p>
 * <b>単一switch(E-6増分3b-6で統合完了)</b>: 過渡の
 * {@code executeLive(LayoutSource.Event)}経路は、置換要素のfreeze総関数化
 * ({@code ReplacedBoxImage}のduplicateベースfreeze——
 * {@code ReplacedParamsTemplate})によりlive変種({@code ReplacedLive})が
 * 撤去されたため、{@link #execute(SegmentEvent)}へ一本化された。
 * 呼び出し側({@code SourceReplayer})は
 * {@code LayoutSource.Event}を{@code LayoutSourceEventConverter.convert}で
 * オンザフライ変換して駆動する。唯一の例外は切断段落の尾部再生の
 * 部分範囲プリミティブ({@link #executeCharsRange})で、これは
 * イベント境界内のトリミング(先頭skip・配達済み終端での打ち切り)という
 * 尾部特有の駆動であり、SegmentEventの語彙には含めない。
 * </p>
 *
 * <p>
 * <b>StructureTokenのintern(E-6増分3b-4)</b>: recipeのmaterializeは
 * イベントごとに独立した{@code Params}を作るため、同じ論理要素が複数の
 * Startを持つケース(例: {@code <li>}のprincipal boxとmarker box)では
 * {@code Params.element}のtokenも別インスタンスになる。Tagged PDFの
 * 構造タグ二重開き防止({@code PageBox.beginStruct}のidentity set)は
 * 「同じ論理要素=同じインスタンス」を要求するため、この executor
 * (=1再生セッション)内で{@code elementKey}によりinternし、live経路の
 * {@code CSSElement}共有と同じidentityを再現する(codex裁定——
 * {@code docs/history/2026-07-24-e6-remaining-design-decision.md})。
 * </p>
 *
 * <p>
 * <b>Charsのfresh copy化(3b-1で是正)</b>: 記録済み{@code char[]}を
 * そのまま{@code doc.characters}へ渡すと、
 * {@code StyledTextUnitizer.characters}が配列内容をその場で書き換える
 * ため、同一範囲の再replayで変換済みテキストへの再変換が起きうる
 * (記録時は防御コピー済みだがreplay時は生渡しだった)。
 * {@link SegmentEvent.Text}経由の駆動は{@code String#toCharArray()}が
 * 毎回freshな配列を返すため構造的に安全。
 * </p>
 */
public final class SegmentExecutor {

	private final DocumentBuilder doc;

	/**
	 * 次に駆動するイベントのordinal(= EventId)。再生インスタンスへ
	 * {@code SourceAnchor}として再付与する(P0: アンカーはボックス個体に
	 * 属する——次の破断で再び再生可能になるための系譜。保存すべきは
	 * 「ordinal→SourceAnchor」の対応のみで、元params/pos/boxの
	 * identityではない——codex設計§1.3)。
	 */
	private long eventId;

	/**
	 * 再生セッション内のStructureToken internです(E-6増分3b-4、クラス
	 * javadoc「StructureTokenのintern」参照)。key({@code elementKey})→
	 * 最初にmaterializeされたtokenインスタンス。
	 */
	private final java.util.HashMap<Long, StructureToken> structureTokens = new java.util.HashMap<>();

	/**
	 * @param doc    駆動先(新品の{@code DocumentBuilder})
	 * @param fromId 範囲先頭のEventId(sliceのordinalと1:1)
	 */
	public SegmentExecutor(final DocumentBuilder doc, final long fromId) {
		this.doc = doc;
		this.eventId = fromId;
	}

	/**
	 * materialize済みparamsの{@code element}をこの再生セッションの正準
	 * tokenへ差し替えます(E-6増分3b-4)。tokenは{@code StructureToken
	 * .freeze}によりelementKey&gt;=0の実要素のみ({@code elementKey<0}は
	 * static singletonの{@code CSSElement}がそのまま入るため対象外——
	 * identityは共有singletonが既に保っている)。package-privateは
	 * 単体テスト({@code StructureTokenTest})からの直接検証のため。
	 */
	void internStructureToken(final net.zamasoft.foliojet.layout.box.params.Params params) {
		if (params.element instanceof StructureToken token && token.elementKey() >= 0) {
			params.element = this.structureTokens.computeIfAbsent(token.elementKey(), key -> token);
		}
	}

	/**
	 * 正規イベント({@link SegmentEvent})を1件駆動します。
	 * {@link SegmentEvent.Barrier}は駆動不能——範囲の適格性は呼び出し側が
	 * 事前に検証している契約のため、ここでは失敗にする。
	 */
	public void execute(final SegmentEvent event) {
		switch (event) {
		case SegmentEvent.BeginBox(final BoxRecipe recipe) -> {
			final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
			this.internStructureToken(box.getParams());
			box.setSourceAnchor(this.eventId);
			this.doc.startBox(box);
		}
		case SegmentEvent.EndBox end -> this.doc.endBox();
		case SegmentEvent.Text(final int sourceOffset, final String text, final boolean fixed) -> {
			// toCharArray()は毎回freshな配列(下流のin-place変換に安全)
			final char[] ch = text.toCharArray();
			this.doc.characters(sourceOffset, ch, 0, ch.length, fixed);
		}
		case SegmentEvent.Replaced(final ReplacedRecipe recipe) -> {
			final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
			this.internStructureToken(box.getParams());
			box.setSourceAnchor(this.eventId);
			this.doc.addReplacedBox(box);
		}
		case SegmentEvent.Barrier barrier -> throw new IllegalStateException("barrier event in replay range: " + barrier);
		// leader() L1: 駆動のたびにshape・割り付けし直す(可変状態を再生間で
		// 共有しない——LeaderQuadはaddLeaderが新規生成する)
		case SegmentEvent.Leader(final String pattern) -> this.doc.addLeader(pattern);
		}
		++this.eventId;
	}

	/**
	 * Charsイベントを部分範囲だけ駆動します(切断段落の尾部再生
	 * {@code SourceReplayer.replayTextTail}専用——先頭のskip・配達済み
	 * 終端での打ち切りで範囲が空になることがあり、その場合も
	 * ordinalは1イベントぶん進める)。駆動する場合はfreshなコピーを渡す。
	 *
	 * @param charOffset 駆動範囲のソース文字オフセット
	 * @param ch         記録済み配列(変更しない)
	 * @param off        配列内の開始位置
	 * @param len        駆動する文字数(0以下なら駆動なしでordinalだけ進める)
	 * @param fixed      docプロトコルの固定テキストフラグ
	 */
	public void executeCharsRange(final int charOffset, final char[] ch, final int off, final int len,
			final boolean fixed) {
		if (len > 0) {
			final char[] fresh = Arrays.copyOfRange(ch, off, off + len);
			this.doc.characters(charOffset, fresh, 0, len, fixed);
		}
		++this.eventId;
	}
}
