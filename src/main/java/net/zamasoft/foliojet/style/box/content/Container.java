package net.zamasoft.foliojet.style.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.IAbsoluteBox;
import net.zamasoft.foliojet.style.box.IFloatBox;
import net.zamasoft.foliojet.style.box.IFlowBox;
import net.zamasoft.foliojet.style.box.IFramedBox;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.visitor.Visitor;

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
}
