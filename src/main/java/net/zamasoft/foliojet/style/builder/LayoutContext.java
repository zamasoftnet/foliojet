package net.zamasoft.foliojet.style.builder;

import net.zamasoft.foliojet.style.box.params.WritingMode;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.IFloatBox;
import net.zamasoft.foliojet.style.util.StyleUtils;

public interface LayoutContext extends LayoutStack {
	/**
	 * 配置された浮動体です。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: LayoutContext.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class Floating {
		public final IFloatBox box;
		public final double lineStart, pageStart, lineEnd, pageEnd;

		public Floating(IFloatBox box, double lineStart, double pageStart, WritingMode progression) {
			this.box = box;
			this.lineStart = lineStart;
			this.pageStart = pageStart;
			this.lineEnd = lineStart + box.getLineExtent(progression);
			this.pageEnd = pageStart + box.getPageExtent(progression);
		}
	}

	/**
	 * 通常のフローのボックスです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: LayoutContext.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class Flow {
		public final AbstractContainerBox box;
		/** ボックスの内辺の位置です。 */
		public final double lineAxis, pageAxis;

		public Flow(AbstractContainerBox container, double lineAxis, double pageAxis) {
			this.box = container;
			this.lineAxis = lineAxis;
			this.pageAxis = pageAxis;
		}
	}

	public int getFlowCount();

	public Flow getFlow(int index);
}
