package net.zamasoft.balancer;

/**
 * 要素コードのスタックです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CodeStack.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class CodeStack {
	private int top = 0;

	private short[] data = new short[10];

	public void add(short code) {
		if (this.top == this.data.length) {
			short[] newarray = new short[this.top + 10];
			System.arraycopy(this.data, 0, newarray, 0, this.top);
			this.data = newarray;
		}
		this.data[this.top++] = code;
	}

	public short peek() {
		return this.data[this.top - 1];
	}

	public boolean contains(short code) {
		for (int i = 0; i < this.top; ++i) {
			if (this.data[i] == code) {
				return true;
			}
		}
		return false;
	}

	public short pop() {
		return this.data[--this.top];
	}

	public boolean isEmpty() {
		return this.top == 0;
	}
}
