package net.zamasoft.foliojet.ua;

import java.util.List;

/**
 * {@code string-set}の値リストに{@code content()}が含まれる場合の
 * build時未完成エントリです。{@code parts}は{@link String}(build時に
 * 解決済みの断片)と{@link #CONTENT}(要素のボックステキストで置き換える
 * 位置を示すマーカー)の混在リストです。draw時に
 * {@code AbstractVisitor.visitBox}が{@code box.getText()}で
 * {@link #CONTENT}を実体化し、連結して{@link NamedStringState#set}へ
 * 渡します。
 *
 * @author MIYABE Tatsuhiko
 */
public final class PendingStringSet {
	/** {@code content()}の位置を示すマーカー。 */
	public static final Object CONTENT = new Object();

	public final String name;
	public final List<Object> parts;

	public PendingStringSet(String name, List<Object> parts) {
		this.name = name;
		this.parts = parts;
	}
}
