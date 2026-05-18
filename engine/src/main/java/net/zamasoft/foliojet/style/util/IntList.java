package net.zamasoft.foliojet.style.util;

import java.io.Serializable;

/**
 * 任意の位置の値をセット可能なint値の配列です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IntList.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class IntList implements Serializable {
	private static final long serialVersionUID = 0;

	private static final int[] ZERO = new int[0];

	private int[] array = ZERO;

	private int defaultValue;

	private int length = 0;

	public IntList() {
		this(0);
	}

	public IntList(int defaultValue) {
		this.defaultValue = defaultValue;
	}

	public void set(int pos, int value) {
		if (this.length <= pos) {
			this.length = pos + 1;
			if (this.array.length <= pos) {
				int[] array = new int[Math.max(this.length + 10, this.array.length * 3 / 2)];
				for (int i = this.array.length; i < array.length; ++i) {
					array[i] = this.defaultValue;
				}
				System.arraycopy(this.array, 0, array, 0, this.array.length);
				this.array = array;
			}
		}
		this.array[pos] = value;
	}

	public void add(int b) {
		this.set(this.length, b);
	}

	public int[] toArray() {
		this.pack();
		return this.array;
	}

	public int get(int i) {
		if (i >= this.array.length) {
			return this.defaultValue;
		}
		return this.array[i];
	}

	public int remove(int i) {
		int v = this.array[i];
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
			int[] array = new int[this.length];
			System.arraycopy(this.array, 0, array, 0, this.length);
			this.array = array;
		}
	}

	public boolean contains(int v) {
		for (int i = 0; i < this.length; ++i) {
			if (this.array[i] == v) {
				return true;
			}
		}
		return false;
	}

	public void clear() {
		this.length = 0;
	}

	public boolean isEmpty() {
		return this.length == 0;
	}
}
