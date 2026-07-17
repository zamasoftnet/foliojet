package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
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

	public void finishLayout(IFramedBox containerBox);

	public void drawFlowFrames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y);

	public void drawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y);

	public void drawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y);

	public void drawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y);

	public Container splitPageAxis(double pageLimit, final BreakMode mode, final byte flags);

	public Container splitFloatings(Container nextBox, double pageLimit, byte flags);

	public Floatings splitFloatings(double pageLimit, byte flags);

	public void getText(StringBuilder textBuff);

	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y);

	public void restyle(BlockBuilder builder, int depth, boolean restyleAbsolutes);

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
