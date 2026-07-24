package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;

/**
 * 画像ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: InlineReplacedBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class InlineReplacedBox extends AbstractReplacedBox implements IInlineBox {
	protected final InlinePos pos;

	public InlineReplacedBox(final ReplacedParams params, final InlinePos pos) {
		super(params);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final InlinePos getInlinePos() {
		return this.pos;
	}

	public InlineReplacedBox newReplayInstance(final ReplacedParams params) {
		return new InlineReplacedBox(params, this.pos);
	}
}
