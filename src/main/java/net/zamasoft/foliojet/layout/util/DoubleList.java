package net.zamasoft.foliojet.layout.util;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Array of double values that can be set at arbitrary positions.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: DoubleList.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class DoubleList implements Serializable {
	private static final long serialVersionUID = 0;

	private static final double[] ZERO = new double[0];

	private double[] array = ZERO;

	private double defaultValue;

	private int length = 0;

	public DoubleList() {
		this((byte) 0);
	}

	public DoubleList(double defaultValue) {
		this.defaultValue = defaultValue;
	}

	public void set(int pos, double value) {
		if (this.length <= pos) {
			this.length = pos + 1;
			if (this.array.length <= pos) {
				double[] array = new double[Math.max(this.length + 10, this.array.length * 3 / 2)];
				for (int i = this.array.length; i < array.length; ++i) {
					array[i] = this.defaultValue;
				}
				System.arraycopy(this.array, 0, array, 0, this.array.length);
				this.array = array;
			}
		}
		this.array[pos] = value;
	}

	public void add(double b) {
		this.set(this.length, b);
	}

	/** Removes the values from {@code from} on and returns them, in order, as a new list. */
	public DoubleList cutTail(int from) {
		final DoubleList tail = new DoubleList(this.defaultValue);
		tail.array = Arrays.copyOfRange(this.array, from, this.length);
		tail.length = this.length - from;
		this.length = from;
		return tail;
	}

	public double[] toArray() {
		this.pack();
		return this.array;
	}

	public double get(int i) {
		if (i >= this.array.length) {
			return this.defaultValue;
		}
		return this.array[i];
	}

	public int size() {
		return this.length;
	}

	public void pack() {
		if (this.length != this.array.length) {
			double[] array = new double[this.length];
			System.arraycopy(this.array, 0, array, 0, this.length);
			this.array = array;
		}
	}

	public boolean isEmpty() {
		return this.length == 0;
	}
}
