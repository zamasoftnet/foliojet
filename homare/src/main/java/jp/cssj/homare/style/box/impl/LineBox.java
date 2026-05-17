package jp.cssj.homare.style.box.impl;

import jp.cssj.homare.style.box.AbstractLineBox;
import jp.cssj.homare.style.box.params.AbstractLineParams;
import jp.cssj.homare.style.box.params.AbstractTextParams;
import jp.cssj.homare.style.box.params.BlockParams;
import jp.cssj.homare.style.box.params.Params;

/**
 * 行ボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: LineBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class LineBox extends AbstractLineBox {
	protected final BlockParams params;

	public LineBox(BlockParams params) {
		this.params = params;
		this.setDecoration(null);
	}

	public final AbstractTextParams getTextParams() {
		return this.params;
	}

	public final AbstractLineParams getLineParams() {
		return this.params;
	}

	public final Params getParams() {
		return this.params;
	}

	public final boolean isContextBox() {
		return false;
	}

	public String toString() {
		return "[LineBox]" + super.toString() + "[/LineBox]";
	}
}
