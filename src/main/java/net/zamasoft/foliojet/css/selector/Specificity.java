package net.zamasoft.foliojet.css.selector;


/**
 * セレクタの固有性(specificity)です。a=ID、b=クラス・属性・擬似クラス、c=要素・擬似要素。
 */
public final class Specificity implements Comparable<Specificity> {
	private final int a, b, c;

	public Specificity(int a, int b, int c) {
		this.a = a;
		this.b = b;
		this.c = c;
	}

	public Specificity add(Specificity o) {
		return new Specificity(this.a + o.a, this.b + o.b, this.c + o.c);
	}

	public int compareTo(Specificity o) {
		if (this.a != o.a) {
			return Integer.compare(this.a, o.a);
		}
		if (this.b != o.b) {
			return Integer.compare(this.b, o.b);
		}
		return Integer.compare(this.c, o.c);
	}

	public boolean equals(Object o) {
		if (!(o instanceof Specificity)) {
			return false;
		}
		Specificity s = (Specificity) o;
		return this.a == s.a && this.b == s.b && this.c == s.c;
	}

	public int hashCode() {
		return (this.a * 31 + this.b) * 31 + this.c;
	}

	public String toString() {
		return "(" + this.a + "," + this.b + "," + this.c + ")";
	}
}
