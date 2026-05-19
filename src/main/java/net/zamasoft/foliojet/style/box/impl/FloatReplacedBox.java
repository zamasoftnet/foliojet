package net.zamasoft.foliojet.style.box.impl;

import net.zamasoft.foliojet.style.box.AbstractReplacedBox;
import net.zamasoft.foliojet.style.box.IFloatBox;
import net.zamasoft.foliojet.style.box.params.FloatPos;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.ReplacedParams;

/**
 * 画像ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FloatReplacedBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FloatReplacedBox extends AbstractReplacedBox implements IFloatBox {
	protected final FloatPos pos;

	public FloatReplacedBox(final ReplacedParams params, final FloatPos pos) {
		super(params);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final FloatPos getFloatPos() {
		return this.pos;
	}
}
