package net.zamasoft.foliojet.style.builder.impl;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.builder.LayoutStack;

public class ColumnBuilder extends BreakableBuilder {
	public ColumnBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox) {
		super(layoutStack, contextBox, MODE_AUTO);
	}

	protected final boolean pageBreak(BreakMode mode, byte flags) {
		int depth;
		if (this.flowStack != null && !this.flowStack.isEmpty()) {
			depth = this.flowStack.size() + 1;
		} else {
			depth = 1;
		}
		flags |= IPageBreakableBox.FLAGS_LAST;
		final double lastFrame = this.lastFrame(this.contextFlow, depth);
		return this.columnBreak(this.contextFlow, mode, flags, lastFrame, depth);
	}

	public final void finish() {
		assert this.flowStack == null || this.flowStack.isEmpty();
		assert this.textBuilder == null;
	}
}
