package jp.cssj.homare.style.builder;

import jp.cssj.homare.style.box.AbstractContainerBox;
import jp.cssj.homare.style.box.IFloatBox;
import jp.cssj.homare.style.util.StyleUtils;

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

		public Floating(IFloatBox box, double lineStart, double pageStart, byte progression) {
			this.box = box;
			this.lineStart = lineStart;
			this.pageStart = pageStart;
			if (StyleUtils.isVertical(progression)) {
				// 縦書き
				this.lineEnd = lineStart + box.getHeight();
				this.pageEnd = pageStart + box.getWidth();
			} else {
				// 横書き
				this.lineEnd = lineStart + box.getWidth();
				this.pageEnd = pageStart + box.getHeight();
			}
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
