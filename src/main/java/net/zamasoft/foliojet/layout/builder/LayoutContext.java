package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

public interface LayoutContext extends LayoutStack {
	/**
	 * A placed float.
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: LayoutContext.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class Floating {
		public final IFloatBox box;
		public final double lineStart, pageStart, lineEnd, pageEnd;

		public Floating(IFloatBox box, double lineStart, double pageStart, WritingMode progression) {
			this(box, lineStart, pageStart, lineStart + box.getLineExtent(progression),
					pageStart + box.getPageExtent(progression));
		}

		private Floating(final IFloatBox box, final double lineStart, final double pageStart,
				final double lineEnd, final double pageEnd) {
			this.box = box;
			this.lineStart = lineStart;
			this.pageStart = pageStart;
			this.lineEnd = lineEnd;
			this.pageEnd = pageEnd;
		}

		/**
		 * Returns an immutable value with only the page-axis range translated, preserving the box
		 * and line-axis range.
		 *
		 * @param dy translation along the page axis
		 * @return translated float-registry entry
		 */
		public Floating shiftedPageAxis(final double dy) {
			return new Floating(this.box, this.lineStart, this.pageStart + dy, this.lineEnd, this.pageEnd + dy);
		}
	}

	/**
	 * A normal-flow box.
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: LayoutContext.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class Flow {
		public final AbstractContainerBox box;
		/** Position of the box's inner edge. */
		public final double lineAxis, pageAxis;
		/**
		 * Amount added to the line-direction cursor when pushing this flow (2026-08-05).
		 *
		 * <p>
		 * <b>Stored to use the same value when pushing and popping.</b> Reading {@code frame.getFrameLeft()}
		 * again on pop can include values <b>resolved inside the flow</b>, such as auto margins. This differs
		 * from the amount pushed and permanently shifts the line-direction cursor by the difference.
		 * This caused the defect where a float immediately after a {@code margin: auto} table jumped
		 * outside the sheet's left edge.
		 * </p>
		 */
		public final double frameHead;

		/**
		 * Line-count state when this flow's box has {@code line-clamp}
		 * (2026-08-29; lazily created by {@link LineClampState#find}). The flow lives only while the box
		 * is being laid out, so recreating the box for page continuation restarts the count (known limitation).
		 */
		public LineClampState lineClamp;

		public Flow(AbstractContainerBox container, double lineAxis, double pageAxis) {
			this(container, lineAxis, pageAxis, 0);
		}

		public Flow(AbstractContainerBox container, double lineAxis, double pageAxis, double frameHead) {
			this.box = container;
			this.lineAxis = lineAxis;
			this.pageAxis = pageAxis;
			this.frameHead = frameHead;
		}

		/**
		 * Returns a flow with only its page-axis position translated, preserving the box, line-axis position,
		 * frame amount at push time, and mutable {@code line-clamp} state.
		 *
		 * @param dy translation along the page axis
		 * @return translated flow
		 */
		public Flow shiftedPageAxis(final double dy) {
			final Flow shifted = new Flow(this.box, this.lineAxis, this.pageAxis + dy, this.frameHead);
			shifted.lineClamp = this.lineClamp;
			return shifted;
		}
	}

	public int getFlowCount();

	public Flow getFlow(int index);
}
