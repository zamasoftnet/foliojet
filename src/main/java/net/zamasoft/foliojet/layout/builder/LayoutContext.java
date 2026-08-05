package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

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
		/**
		 * このフローを積むときに行方向カーソルへ足した量です(2026-08-05)。
		 *
		 * <p>
		 * <b>積むときと降ろすときで同じ値を使うために持つ。</b> 降ろす側で
		 * {@code frame.getFrameLeft()} を取り直すと、自動マージンのように
		 * <b>フローの内側で解決される</b>量が入ったときに積んだ量と食い違い、
		 * 行方向カーソルが差の分だけ永久にずれる。{@code margin: auto} の表の
		 * 直後に置いた浮動体が紙の左外へ飛ぶ欠陥がこれだった。
		 * </p>
		 */
		public final double frameHead;

		public Flow(AbstractContainerBox container, double lineAxis, double pageAxis) {
			this(container, lineAxis, pageAxis, 0);
		}

		public Flow(AbstractContainerBox container, double lineAxis, double pageAxis, double frameHead) {
			this.box = container;
			this.lineAxis = lineAxis;
			this.pageAxis = pageAxis;
			this.frameHead = frameHead;
		}
	}

	public int getFlowCount();

	public Flow getFlow(int index);
}
