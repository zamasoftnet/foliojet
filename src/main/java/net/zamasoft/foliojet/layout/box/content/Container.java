package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public interface Container {
	public void setBox(AbstractContainerBox box);

	public void addFlow(IFlowBox box, double pageAxis);

	public void addAbsolute(IAbsoluteBox box, double staticX, double staticY);

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis);

	public boolean hasFlows();

	public boolean hasFloatings();

	public double getFirstAscent();

	public double getLastDescent();

	public double getContentSize();

	/**
	 * この内容が<b>実際に紙へ描く</b>ページ方向の終端(内辺基準)を返します。
	 *
	 * <p>
	 * {@link #getContentSize()}が「最後のフローの箱の終わり」という<b>箱の
	 * 幾何</b>を返すのに対し、こちらは<b>描画の実測</b>です。両者は次の2点で
	 * ずれます:
	 * </p>
	 * <ul>
	 * <li>浮動体は{@code getContentSize()}に入らないが、紙には描かれる</li>
	 * <li>枠線も背景も持たない箱の余った寸法(内容より大きい指定寸法・
	 * 内容の後ろの余白)は、箱としては存在しても<b>何も描かない</b></li>
	 * </ul>
	 *
	 * <p>
	 * 「ページからはみ出した部分に描くものがあるか」の判定にはこちらを使い
	 * ます——描くものがないのに断片を作ると、<b>白紙のページが1枚増える</b>
	 * (css-break-3 §4.4「各フラグメンテナは0でない量の内容を取る」違反)。
	 * </p>
	 *
	 * @return 描画が及ぶページ方向の終端(内辺基準。何も描かなければ0)
	 */
	public double paintedPageEnd();

	/**
	 * この内容が<b>紙に何か描くか</b>を返します(2026-07-28新設)。
	 *
	 * <p>
	 * {@link #paintedPageEnd()}が「ページ方向のどこまで描くか」という距離を
	 * 答えるのに対し、こちらは有無だけを答えます。距離0は「何も描かない」と
	 * ほぼ同義ですが、<b>ほぼ</b>でしかありません(行方向にだけ広がる枠、
	 * 段間罫)。何も描かないページを出力から落とす判定
	 * ({@code StyleBuilder.drawPage}、css-break-3 §4.4)には、
	 * ずれのないこちらを使います。
	 * </p>
	 *
	 * <p>
	 * <b>判定は必ず安全側(=描く)へ倒します。</b>
	 * </p>
	 *
	 * @return 紙に何か描く(かもしれない)なら true
	 */
	public boolean paintsAnything();

	public double getCutPoint(double pageAxis);

	/**
	 * 提案位置の直前の実行可能な切断位置を返します(M5-B)。
	 * getCutPoint が直後の境界へ切り上げるのに対し、こちらは実際の切断
	 * (はみ出す内容を次の断片へ送る)をボックスを変異させずに見積もる
	 * 切り下げです。提案位置より前に境界がなければ 0 を返します。
	 *
	 * @param pageAxis 提案位置(内容の始端からの距離)
	 * @return 直前の切断位置(なければ 0)
	 */
	public double getCutPointBelow(double pageAxis);

	public boolean avoidBreakBefore();

	public boolean avoidBreakAfter();

	/**
	 * {@code finishLayout}の反復化(2026-07-20、IBox.finishLayoutと同じ
	 * 理由)。子(flows/floatings/absolutes、またはcolumns)の処理
	 * ステップを、元の再帰と同じ走査順になるよう**逆順**で
	 * {@code worklist}へ積みます。
	 */
	public void pushFinishLayoutChildren(IFramedBox containerBox, Deque<FinishLayoutStep> worklist);

	/**
	 * framesの反復化(2026-07-20、IBox.pushDrawStepsと同じ理由)。通常フローの
	 * 子の枠描画手順を、元の走査順のまま**逆順**で{@code worklist}へ積みます。
	 */
	public void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist);

	/**
	 * drawの反復化(2026-07-20、IBox.drawと同じ理由)。通常フローの子の
	 * 描画手順を、元の走査順のまま**逆順**で{@code worklist}へ積みます。
	 */
	public void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist);

	/**
	 * 浮動ボックスについての{@link #pushDrawFlows}相当です。
	 */
	public void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist);

	/**
	 * 絶対配置ボックスについての{@link #pushDrawFlows}相当です。
	 */
	public void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist);

	/**
	 * 浮動ボックス(直接保持分+子flowの再帰集約)をページ分割し、移動分の
	 * 行き先を型で返します(2026-07-24、P2-4)。
	 */
	public FloatTransferResult splitFloatings(FloatTransferTarget target, double pageLimit, byte flags);

	/**
	 * 浮動ボックスをページ分割し、移動分の台帳を自分からdetachして返します
	 * (子flow再帰専用の内部契約——親の
	 * {@code FlowContainer.aggregateFloatings}だけが呼ぶ。旧2引数
	 * {@code splitFloatings}のnullable返し(null=移動なし)をOptionalへ
	 * 置換、2026-07-24 E-4)。3引数版
	 * {@link #splitFloatings(FloatTransferTarget, double, byte)}と違い、
	 * 移動float台帳をコンテナへ装着せず生のまま返す(装着先は親が決める)。
	 *
	 * @return 移動するfloatがなければempty、あればdetach済みの非空台帳
	 */
	public java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags);

	/**
	 * getTextの反復化(2026-07-20、IBox.pushDrawStepsと同じ理由)。子の
	 * テキスト抽出手順を、元の走査順のまま**逆順**で{@code worklist}へ
	 * 積みます。
	 */
	public void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist);

	/**
	 * textShapeの反復化(2026-07-20、IBox.pushDrawStepsと同じ理由)。子の
	 * 輪郭手順を{@code worklist}へ積みます。
	 */
	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist);

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes);

	/**
	 * 継続化計画付きのページ方向切断です(C1d-C)。plan が選択した
	 * チェーンメンバーの断片は ContinuationFrame として返り値で伝播する。
	 * plan が null なら従来の切断(Plain のみ)。
	 */
	public net.zamasoft.foliojet.layout.fragment.ContainerCut splitPageAxis(double pageLimit, BreakMode mode,
			byte flags, net.zamasoft.foliojet.layout.fragment.BreakPlan plan);

	/**
	 * 通常フローの子ボックスを順に渡します(M6b診断用)。
	 */
	public void eachFlowBox(java.util.function.Consumer<IFlowBox> consumer);
}
