package net.zamasoft.foliojet.layout.rescue;

import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 救済分割(visual rescue split)の<b>入口</b>——判定を一手に引き受ける
 * 純関数と、機能全体の仕様・設計判断の集約点です(2026-07-25新設。
 * {@code docs/history/2026-07-25-rescue-split-spec.md}(合意仕様)、
 * {@code docs/consultations/consult-rescue-split-codex.md}(設計答申))。
 *
 * <h2>1. 何をする機能か</h2>
 *
 * <p>
 * ページからはみ出す分割不能な要素の<b>情報のロスを防ぐ</b>ため、枠線
 * ボックスを<b>幾何学的に(機械的に)切断</b>して断片を次フラグメンテナへ
 * 送ります。行間やブロックの継ぎ目は一切考慮しません——通常の改ページ
 * 分割とは別の考えです。実装はクリップ+座標移動で、内容はベクターのまま
 * (画像化しない)。切断面には装飾を付けず、上マージン・上枠線は先頭断片
 * だけ、下枠線・下マージンは最終断片だけがclip内に入ります(CSS
 * {@code box-decoration-break: slice}と同じ結果が、辺を落とす特別処理
 * <b>なしに</b>得られます)。継続断片はPDFの{@code /Artifact}として出力し、
 * テキスト抽出・読み上げ・構造タグの二重化を防ぎます。
 * </p>
 *
 * <h2>2. なぜ「この一点だけ」を置き換えるのか</h2>
 *
 * <p>
 * 発動するのは「一度次のフラグメンテナへ送っても、その先頭でなお収まらない」
 * ——現在「はみ出したまま描画」に落ちる<b>唯一の非進行点</b>だけです
 * (通常フローは{@code FlowContainer.rescueSplit}、浮動体は
 * {@code FloatSplitPlan.rescue}の二か所)。この選択には3つの理由があります。
 * </p>
 *
 * <ul>
 * <li><b>通常経路が完全に不変になる</b>。収まる場合も一度の延期で収まる
 * 場合もこの地点には来ないため、既存goldenは一切変わりません。</li>
 * <li><b>置き換える対象が「情報が失われる場所」そのもの</b>。ここは
 * すでに敗北が確定している地点で、救済は劣化ではなく回復です。</li>
 * <li><b>分類器を作らなくてよい</b>。「何が分割不能か」をクラス列挙で
 * 定義すると必ず漏れます。「エンジン自身の通常分割が前進しなかった」と
 * いう<b>結果</b>を正本にすれば、置換要素・巨大な行・インラインブロック・
 * インラインテーブル・ルビ・書字方向不一致ボックスがすべて同じ一本の
 * 経路で捕捉されます。</li>
 * </ul>
 *
 * <h2>3. なぜ絶対配置は対象外か</h2>
 *
 * <p>
 * {@link #isRescuablePos(PosType)}が{@code PosType.ABSOLUTE}を明示的に
 * 拒否します(合意仕様)。
 * </p>
 *
 * <ul>
 * <li>絶対配置は<b>意図的に</b>はみ出させる用途が多い(透かし・装飾・
 * 裁ち落とし)。自動で切るのは明らかな誤りです。</li>
 * <li>基準ボックスが別ページにあり得るため「どこから続きか」の帳簿が
 * 曖昧になります。</li>
 * <li>回避策がある——通常ブロックで包めば対象になります。逆に、救済対象の
 * 通常ブロックの中にある絶対配置子は、そのブロックのclipに従って切れます。
 * </li>
 * </ul>
 *
 * <h2>4. なぜ表全体は見送りか</h2>
 *
 * <p>
 * {@code BoxType.TABLE}を幾何学的に切る経路は<b>作っていません</b>。表は
 * 行・行グループ・セルの分割機構を自前で持ち、{@code Keep}/{@code Move}が
 * 「内部機構が処理した」の意なのか「本当に前進できない」のかを現状の
 * 戻り値からは区別できません。区別できないまま切ると、正常に行分割できて
 * いる表まで帯状に刻む明確な劣化になります。<b>セルの中身</b>は
 * フラグメンテナがセルになるだけで同一経路が効くため、実害のある形
 * (セルに収まらない巨大画像)は既にカバー済みです。コーパスにも表全体の
 * 候補がないため、段階的拡大の方針に従って見送ります。
 * </p>
 *
 * <h2>5. 白紙対策の2つの下限</h2>
 *
 * <p>
 * 「意図しない白紙ページを作らない」は無限ループの不在と並ぶ絶対要件です。
 * 作者が意図した白紙(非表示オブジェクト・強制改ページ)は正常で、
 * <b>エンジンの都合で生じる白紙・実質白紙</b>だけが不具合です。救済分割は
 * 放っておくと「数ptの断片ページ」を量産しうるため、
 * {@link #planInFragmentainer}が<b>両側</b>に下限を課します。この2つは
 * どちらも{@link #MIN_RESCUE_ADVANCE}(1pt)とは別物です——1ptは
 * 「無限ループしないか」の下限であって「1ptずつ切ってよい」ではありません。
 * </p>
 *
 * <ul>
 * <li><b>先頭側</b>({@link #minUsefulSlice(double)}): <b>今</b>使える量が
 * 小さすぎるなら救済しない。浮動体の排除域などでフラグメンテナの一部しか
 * 空いていない状況で切り始めると、数十ptの断片ページが延々と続きます。
 * 絶対値20ptと容量の1/4の大きい方を使い、下回れば従来の終端へ落として
 * 外側のフラグメンテナへ委譲します。</li>
 * <li><b>末尾側</b>({@link #MIN_RESCUE_SLICE}): <b>はみ出し量</b>が
 * 小さすぎるなら救済しない。数ptのはみ出しを救うために丸ごと1ページ
 * 増やすと、そのページが実質白紙になります。こちらに割合の下限を課さない
 * のは、「A4に貼られた少しだけ背の高い画像」という<b>本来の用途</b>まで
 * 拒否してしまうためです。</li>
 * </ul>
 *
 * <p>
 * どちらも<b>救済を始めるかどうか</b>({@code offset == 0})にだけ効きます。
 * 切り始めた後に「小さすぎるからやめる」を選ぶと残りの内容が失われるため、
 * 開始後は前進保証だけを守って必ず切り進めます。
 * </p>
 *
 * <p>
 * <b>ただし例外が一つあります</b>(2026-07-25、独立レビュー指摘)。継続断片
 * ({@code offset > 0})であっても、送り先のフラグメンテナに
 * {@link #MIN_RESCUE_ADVANCE}未満しか空きがなければ
 * {@link RescueDecision.Reason#INSUFFICIENT_CAPACITY}を返し、救済の連鎖は
 * そこで終わります(残りは従来どおりはみ出したまま描かれる)。
 * <b>これは意図した終端です。</b>ここで「外側のフラグメンテナへ送る」を
 * 選ぶと、フラグメンテナの容量はページごとに変わらないため、送っても
 * 送っても同じ判定になり<b>無限ループ</b>になります——絶対要件の
 * 「無限ループの不在」は、絶対要件の「情報を失わない」より優先します。
 * なお{@link #minUsefulSlice(double)}(20pt以上)を満たさないと救済は
 * 始まらないので、この終端に到達するのは容量1pt未満という縮退した
 * フラグメンテナだけです。
 * </p>
 *
 * <h2>6. 前進保証(無限ループの不在)</h2>
 *
 * <p>
 * 再試行回数カウンタには一切依存しません。次の構造で保証します(答申§1)。
 * </p>
 *
 * <ul>
 * <li>tailを作る断片は必ず{@link #MIN_RESCUE_ADVANCE}以上を消費する。</li>
 * <li>{@code nextOffset > offset}が厳密に成り立つときしか断片を作らない
 * (NaN・Infinity・極大doubleの丸めで停滞する場合は救済しない)。</li>
 * <li>残余が容量に収まったら{@code lastFragment}となり、tailを作らない。</li>
 * </ul>
 *
 * <p>
 * したがって救済が改ページを起こしたフラグメントは必ず正の量を消費し、
 * 残余は真に減少します。判定が{@link RescueDecision.None}を返した場合は
 * 従来どおりの終端(ページ先頭ならはみ出したまま描画)へ落ち、再試行は
 * しません。実行時にも二重に検査します(配線側の{@code tailOffset > offset}
 * 検査)——不変条件の重複ですが、絶対要件なので冗長でも守ります。
 * </p>
 *
 * <h2>7. 作らないもの(将来の誘惑への歯止め)</h2>
 *
 * <p>
 * 答申§7で「作らない」と決めたものです。2026-07-25の増分8時点で、
 * 以下はいずれも<b>実装されていません</b>。増やしたくなったときは、まず
 * ここに反例が来ていないかを疑ってください——救済は「敗北確定地点の
 * 単調な幾何スライス」以上のものになってはいけません。
 * </p>
 *
 * <ul>
 * <li><b>切断位置の最適化</b>——グリフ境界や行境界を探す「賢い」切断は
 * しません。切るのは常に「使える量ちょうど」です。内容を見て位置を選び
 * 始めると、通常の改ページアルゴリズムの劣化コピーが生えます。</li>
 * <li><b>切断面の装飾</b>——線・マーク・余白・重複装飾を足しません。
 * {@code AbsoluteRectFrame}には辺を落とす{@code cut()}がありますが救済では
 * 使わず、元の幾何を保ったclipだけで済ませます(背景画像やtransformを
 * 含めて正確なため)。</li>
 * <li><b>ラスタライズ</b>——ページ画像化も救済専用Form XObjectも作らず、
 * 内容はベクターのまま。元箱の寸法変更・scale-to-fit・画像再圧縮も
 * しません。</li>
 * <li><b>救済専用のページ跨ぎ上限</b>——「10ページまで」のような専用の
 * 打ち切りは持ちません。前進保証が構造的なので不要で、持てば内容が
 * 黙って失われます。全体の{@code output.page-limit}はそのまま尊重します。
 * </li>
 * <li><b>全断片の事前生成</b>——各改ページでhead一個とtail一個だけを
 * 作ります。断片リストもThreadLocalもundo logも持ちません
 * (ストリーミング要件)。</li>
 * <li><b>失敗後のrollback・別アルゴリズム再試行</b>——救済しないと決めたら
 * 従来の終端へ一度落ちるだけで、やり直しません。</li>
 * <li><b>ParamsやLayoutSource recipeへの救済offset追加</b>——救済箱は
 * ソースイベントではなく、レイアウト済み箱から派生する短命なページング
 * 状態です({@code sourceAnchor = -1}のreplay barrier)。</li>
 * <li><b>絶対配置/fixed自身の救済</b>(上記§3)。</li>
 * <li><b>注釈(リンク・イメージマップ・フォーム)の断片への追従</b>——
 * 救済された要素の注釈矩形は<b>元ボックスの寸法のまま</b>で、継続断片は
 * artifact扱いなので注釈自体が出ません(2026-07-25、独立レビュー指摘。
 * {@code AbstractVisitor}はclipではなく箱の幾何から矩形を作る)。
 * <b>既知の制限として受け入れます</b>——救済が働くのは「ページに収まらない
 * 巨大な画像・行」であり、そこにリンクやフォームが載っている実例は
 * 想定しにくい一方、断片ごとに注釈を複製すると「同じリンクが複数ページに
 * ある」というPDF構造上の別問題を招きます。実例に当たったら、
 * 「先頭断片へクリップ」を第一候補として再検討します。</li>
 * </ul>
 *
 * <h2>8. この型の責務</h2>
 *
 * <p>
 * 判定は散らばらせずここへ集約します(答申§4)。入力は
 * </p>
 *
 * <ul>
 * <li>フラグメント先頭か({@code atFragmentStart})</li>
 * <li>利用可能なページ方向の量({@code available})</li>
 * <li>元ボックスのページ方向の占有量({@code sourcePageExtent})</li>
 * <li>すでに消費した量({@code offset})</li>
 * </ul>
 *
 * <p>
 * の4つ(+配置方法と容量)だけで、ボックスにもコンテナにも触れません。
 * 断片の表現は{@link VisualRescueBox}、通常フローへの配線は
 * {@code FlowContainer.rescueSplit}、浮動体への配線は
 * {@code FloatSplitPlan.rescue}、観測は{@link RescueStats}、テスト専用の
 * 切替は{@link RescuePolicy}です。
 * </p>
 */
public final class VisualRescuePlanner {

	private VisualRescuePlanner() {
		// unused
	}

	/**
	 * 救済分割が1ステップで消費しなければならない最小量です
	 * ({@code 2 * LayoutUtils.THRESHOLD} = 1pt相当)。
	 *
	 * <p>
	 * {@link LayoutUtils#compare(double, double)}は{@code THRESHOLD}未満の
	 * 差を「同一」とみなすため、これを下回る前進は「進んでいない」と
	 * 区別できません。したがってtailを作る断片の下限をこの値に取ります。
	 * </p>
	 */
	public static final double MIN_RESCUE_ADVANCE = 2 * LayoutUtils.THRESHOLD;

	/**
	 * 救済分割が1ステップで消費しなければならない<b>実用上の</b>最小量です
	 * (2026-07-25、増分4で追加。「意図しない白紙(実質白紙)ページを作らない」
	 * という絶対要件のうち、<b>極小断片ページ</b>を防ぐ側)。
	 *
	 * <p>
	 * {@link #MIN_RESCUE_ADVANCE}(1pt)は「無限ループしないか」の下限
	 * であって、「1ptずつ切って何十ページも作ってよい」という意味では
	 * ありません。値20ptは
	 * {@code BreakableBuilder.MIN_PAGE_LIMIT}と同じで、エンジン自身が
	 * 「これより小さいページ方向容量は縮退として無視する」と決めている
	 * 唯一既存の閾値です。新しい魔法数を増やさず、既存の判断基準に
	 * そろえます(定数の重複定義を避けて参照しないのは、
	 * {@code layout.rescue}が{@code layout.builder.impl}に依存しない
	 * ためです)。
	 * </p>
	 */
	public static final double MIN_RESCUE_SLICE = 20;

	/**
	 * 救済分割が1ステップで消費しなければならない、フラグメンテナ容量に
	 * 対する最小の割合です(2026-07-25、増分4)。
	 *
	 * <p>
	 * 絶対値の下限({@link #MIN_RESCUE_SLICE})だけでは、大きなページで
	 * フロートの排除域などにより利用可能量が極端に小さくなった場合に、
	 * 数十ptの断片ページが延々と続く危険が残ります。「フラグメンテナの
	 * 1/4も使えないなら救済しない(=従来どおりの終端へ落ちる)」という
	 * 割合の下限を併せて課します。
	 * </p>
	 */
	public static final double MIN_RESCUE_FRACTION = 0.25;

	/**
	 * 与えられたフラグメンテナ容量に対して、救済を始めてよい利用可能量の
	 * 下限です。
	 *
	 * @param capacity フラグメンテナ(ページ・段・セル)のページ方向内寸
	 * @return 下限
	 */
	public static double minUsefulSlice(final double capacity) {
		if (!isDefined(capacity) || !(capacity > 0)) {
			return MIN_RESCUE_SLICE;
		}
		return Math.max(MIN_RESCUE_SLICE, capacity * MIN_RESCUE_FRACTION);
	}

	/**
	 * 配置方法が救済分割の対象になり得るかを返します。
	 *
	 * <p>
	 * 絶対配置は対象外です——理由はこの型のクラス説明§3に集約しています。
	 * なお{@code BreakableBuilder.addBound()}も{@code PosType.ABSOLUTE}を
	 * 即座に通常配置へ送っており、除外は二重になります(答申§4)。
	 * </p>
	 *
	 * @param posType 配置方法({@code null}可——不明なら対象とみなす)
	 * @return 救済分割の対象になり得ればtrue
	 */
	public static boolean isRescuablePos(final PosType posType) {
		return posType != PosType.ABSOLUTE;
	}

	/**
	 * 配置方法の除外を含めて次の断片を決めます。
	 *
	 * @param posType          対象ボックスの配置方法
	 * @param atFragmentStart  フラグメント(ページ・段・セル)の先頭か
	 * @param available        利用可能なページ方向の量
	 * @param sourcePageExtent 元ボックスのページ方向の占有量(不変)
	 * @param offset           すでに消費したページ方向の量
	 * @return 判定結果
	 */
	public static RescueDecision plan(final PosType posType, final boolean atFragmentStart, final double available,
			final double sourcePageExtent, final double offset) {
		if (!isRescuablePos(posType)) {
			return new RescueDecision.None(RescueDecision.Reason.ABSOLUTE);
		}
		return plan(atFragmentStart, available, sourcePageExtent, offset);
	}

	/**
	 * フラグメンテナ容量を考慮して次の断片を決めます(<b>配線はこれを
	 * 使います</b>)。
	 *
	 * <p>
	 * {@link #plan(boolean, double, double, double)}の「前進保証」(無限
	 * ループの不在)に加えて、<b>白紙対策の2つの下限</b>——先頭側の
	 * {@link #minUsefulSlice(double)}と末尾側の{@link #MIN_RESCUE_SLICE}
	 * ——を課します。2つの意味と、割合の下限を末尾側に置かない理由は、
	 * この型のクラス説明§5に集約しています。
	 * </p>
	 *
	 * <p>
	 * どちらの下限も<b>救済を始めるかどうか</b>({@code offset == 0})の
	 * 判定にだけ効きます。すでに切り始めている({@code offset > 0})断片で
	 * 「小さすぎるからやめる」を選ぶと、残りの内容が失われる(=従来どおり
	 * はみ出して切り捨てられる)ため、開始後は前進保証だけを守って必ず
	 * 切り進めます。
	 * </p>
	 *
	 * @param posType          対象ボックスの配置方法
	 * @param atFragmentStart  フラグメント(ページ・段・セル)の先頭か
	 * @param capacity         フラグメンテナのページ方向内寸(容量)
	 * @param available        利用可能なページ方向の量
	 * @param sourcePageExtent 元ボックスのページ方向の占有量(不変)
	 * @param offset           すでに消費したページ方向の量
	 * @return 判定結果
	 */
	public static RescueDecision planInFragmentainer(final PosType posType, final boolean atFragmentStart,
			final double capacity, final double available, final double sourcePageExtent, final double offset) {
		final RescueDecision decision = plan(posType, atFragmentStart, available, sourcePageExtent, offset);
		if (!(decision instanceof RescueDecision.Slice slice)) {
			return decision;
		}
		if (!slice.firstFragment()) {
			// 開始後は前進保証だけを守る(やめると内容が失われる)
			return decision;
		}
		if (available < minUsefulSlice(capacity)) {
			return new RescueDecision.None(RescueDecision.Reason.SLIVER_CAPACITY);
		}
		if (sourcePageExtent - slice.nextOffset() < MIN_RESCUE_SLICE) {
			// 末尾側の守り: はみ出し量が実用上小さすぎる。数ptのために
			// 1ページ増やすと、そのページは実質白紙になる
			return new RescueDecision.None(RescueDecision.Reason.SLIVER_REMAINDER);
		}
		return decision;
	}

	/**
	 * 次の断片を決めます(前進保証だけを見る中核判定)。
	 *
	 * <p>
	 * {@code atFragmentStart}が偽のときは救済しません。まだ「次の
	 * フラグメントへ送る」という通常の手段が残っており、救済は
	 * <b>その手段を使い切った地点</b>(=現在はみ出したまま描画している
	 * 地点)だけを置き換えるものだからです。
	 * </p>
	 *
	 * @param atFragmentStart  フラグメント(ページ・段・セル)の先頭か
	 * @param available        利用可能なページ方向の量
	 * @param sourcePageExtent 元ボックスのページ方向の占有量(不変)
	 * @param offset           すでに消費したページ方向の量
	 * @return 判定結果
	 */
	public static RescueDecision plan(final boolean atFragmentStart, final double available,
			final double sourcePageExtent, final double offset) {
		if (!atFragmentStart) {
			return new RescueDecision.None(RescueDecision.Reason.NOT_FIRST);
		}
		if (!isDefined(available) || !isDefined(sourcePageExtent) || !isDefined(offset)) {
			return new RescueDecision.None(RescueDecision.Reason.UNDEFINED_GEOMETRY);
		}
		if (offset < 0 || !(sourcePageExtent > 0)) {
			return new RescueDecision.None(RescueDecision.Reason.INVALID_GEOMETRY);
		}
		final double remaining = sourcePageExtent - offset;
		// 残余の判定もLayoutUtils.compare基準にする(2026-07-25、増分4)。
		// 素の`remaining > 0`では、丸めで0.1pt等の残余が出たときに
		// 「実質白紙の断片ページ」を1枚作ってしまう。エンジンが
		// 「同一」とみなす差(THRESHOLD)以下の残余は消費済みとする
		if (LayoutUtils.compare(remaining, 0) <= 0) {
			return new RescueDecision.None(RescueDecision.Reason.EXHAUSTED);
		}

		// 残余が容量に収まるか(THRESHOLD許容つき)。収まるなら最終断片で、
		// tailを作らないため前進量の下限は要らない。収まらないなら容量
		// いっぱいを切り、必ずtailが続く。
		final boolean fits = LayoutUtils.compare(remaining, available) <= 0;
		if (fits) {
			if (offset == 0) {
				// 先頭でそもそも収まっている——救済不要(通常経路)
				return new RescueDecision.None(RescueDecision.Reason.FITS);
			}
			final double nextOffset = offset + remaining;
			if (!(nextOffset > offset)) {
				// 極大doubleの丸めなど。進めないなら救済しない
				return new RescueDecision.None(RescueDecision.Reason.NO_PROGRESS);
			}
			return new RescueDecision.Slice(offset, remaining, nextOffset, false, true);
		}

		if (!(available >= MIN_RESCUE_ADVANCE)) {
			// 容量0・容量1pt未満・負の容量。外側のfragmentainerへ委譲する
			return new RescueDecision.None(RescueDecision.Reason.INSUFFICIENT_CAPACITY);
		}
		final double nextOffset = offset + available;
		if (!(nextOffset > offset)) {
			return new RescueDecision.None(RescueDecision.Reason.NO_PROGRESS);
		}
		return new RescueDecision.Slice(offset, available, nextOffset, offset == 0, false);
	}

	/**
	 * 有限かつ「未確定」でない実数であればtrueを返します。
	 * {@code LayoutUtils.NONE}はAUTO等の未確定を表すマジック値なので、
	 * 数値としては有限でも幾何としては扱えません。
	 */
	private static boolean isDefined(final double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v) && !LayoutUtils.isNone(v);
	}
}
