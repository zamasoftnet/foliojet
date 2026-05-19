package net.zamasoft.foliojet.style.box.content;

import net.zamasoft.foliojet.style.box.IBox;

public abstract class BoxHolder implements Comparable<BoxHolder> {
	public final int serial;

	public BoxHolder(int serial) {
		this.serial = serial;
	}

	public abstract IBox getBox();

	public int compareTo(BoxHolder o) {
		BoxHolder holder = (BoxHolder) o;
		if (this.serial > holder.serial) {
			return 1;
		}
		if (this.serial < holder.serial) {
			return -1;
		}
		return 0;
	}

}
