package net.zamasoft.foliojet.css.value;

/** 原位置でスタイルを確定する running 要素の名前です。 */
public record RunningPositionValue(String name) implements Value {
	public String getName() {
		return this.name;
	}
}
