package net.zamasoft.foliojet.style.util;

import java.io.Serializable;

/**
 * 任意の位置の値をセット可能なbyte値の配列です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: ByteList.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ByteList implements Serializable {
	private static final long serialVersionUID = 0;

	private static final byte[] ZERO = new byte[0];

	private byte[] array = ZERO;

	private byte defaultValue;

	private int length = 0;

	public ByteList() {
		this((byte) 0);
	}

	public ByteList(byte defaultValue) {
		this.defaultValue = defaultValue;
	}

	public void set(int pos, byte value) {
		if (this.length <= pos) {
			this.length = pos + 1;
			if (this.array.length <= pos) {
				byte[] array = new byte[Math.max(this.length + 10, this.array.length * 3 / 2)];
				for (int i = this.array.length; i < array.length; ++i) {
					array[i] = this.defaultValue;
				}
				System.arraycopy(this.array, 0, array, 0, this.array.length);
				this.array = array;
			}
		}
		this.array[pos] = value;
	}

	public void add(byte b) {
		this.set(this.length, b);
	}

	public byte[] toArray() {
		this.pack();
		return this.array;
	}

	public byte get(int i) {
		if (i >= this.array.length) {
			return this.defaultValue;
		}
		return this.array[i];
	}

	public byte remove(int i) {
		byte v = this.array[i];
		--this.length;
		for (int j = i; j < this.length; ++j) {
			this.array[j] = this.array[j + 1];
		}
		return v;
	}

	public int size() {
		return this.length;
	}

	public void pack() {
		if (this.length != this.array.length) {
			byte[] array = new byte[this.length];
			System.arraycopy(this.array, 0, array, 0, this.length);
			this.array = array;
		}
	}

	public boolean isEmpty() {
		return this.length == 0;
	}
}
