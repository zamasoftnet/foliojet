package jp.cssj.homare.css.style;

import java.util.ArrayList;
import java.util.List;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.style.util.ByteList;

/**
 * 再生成ボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: StyleBuffer.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class StyleBuffer {
	protected static final byte START = 1;
	protected static final byte CHARACTERS = 2;
	protected static final byte END = 3;

	protected final List<Object> contents = new ArrayList<Object>();
	protected final ByteList types = new ByteList();
	protected int depth = 0;

	public StyleBuffer() {
		// do nothing
	}

	public int getDepth() {
		return this.depth;
	}

	public void startStyle(CSSStyle style) {
		this.types.add(START);
		this.contents.add(style);
		++this.depth;
	}

	public void characters(int offset, char[] ch, int off, int len) {
		this.types.add(CHARACTERS);
		char[] chars = new char[len];
		System.arraycopy(ch, off, chars, 0, len);
		this.contents.add(offset);
		this.contents.add(chars);
	}

	public void endStyle(CSSStyle style) {
		this.types.add(END);
		this.contents.add(style);
		--this.depth;
	}

	public void restyle(StyleBuilder builder) {
		int j = 0;
		for (int i = 0; i < this.types.size(); ++i) {
			switch (this.types.get(i)) {
			case START: {
				CSSStyle style = (CSSStyle) this.contents.get(j++);
				// 上位の匿名スタイルを除去する
				for (;;) {
					CSSStyle parentStyle = style.getParentStyle();
					if (parentStyle != null && parentStyle.isAnonStyle()) {
						style.removeAnonStyle();
						continue;
					}
					break;
				}
				builder.startStyle(style);
			}
				break;
			case CHARACTERS: {
				int charOffset = (Integer) this.contents.get(j++);
				char[] chars = (char[]) this.contents.get(j++);
				builder.characters(charOffset, chars, 0, chars.length);
			}
				break;
			case END: {
				j++;
				builder.endStyle();
			}
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}
}
