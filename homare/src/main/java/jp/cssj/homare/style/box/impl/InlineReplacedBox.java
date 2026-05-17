package jp.cssj.homare.style.box.impl;

import jp.cssj.homare.style.box.AbstractReplacedBox;
import jp.cssj.homare.style.box.IInlineBox;
import jp.cssj.homare.style.box.params.InlinePos;
import jp.cssj.homare.style.box.params.Pos;
import jp.cssj.homare.style.box.params.ReplacedParams;

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
}
