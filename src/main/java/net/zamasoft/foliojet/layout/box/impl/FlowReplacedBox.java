package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;


/**
 * 画像ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FlowReplacedBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FlowReplacedBox extends AbstractReplacedBox implements IFlowBox {
	protected final FlowPos pos;

	public FlowReplacedBox(final ReplacedParams params, final FlowPos pos) {
		super(params);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final FlowPos getFlowPos() {
		return this.pos;
	}

	public final boolean avoidBreakAfter() {
		return this.pos.pageBreakAfter == PageBreakMode.AVOID;
	}

	public final boolean avoidBreakBefore() {
		return this.pos.pageBreakBefore == PageBreakMode.AVOID;
	}

}
