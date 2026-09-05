package net.zamasoft.foliojet.ua;

import java.util.List;

/**
 * {@code string-set}の配置確定待ちエントリです。{@code parts}は{@link String}(build時に
 * 解決済みの断片)と{@link #CONTENT}(要素のボックステキストで置き換える
 * 位置を示すマーカー)の混在リストです。配置確定時に
 * {@code AbstractVisitor.visitAssignment}が{@code box.getText()}で
 * {@link #CONTENT}を実体化し、連結して{@link PageAssignmentState#assign}へ
 * 渡します。
 *
 * @author MIYABE Tatsuhiko
 */
public final class PendingStringSet {
	/** {@code content()}の位置を示すマーカー。 */
	public static final Object CONTENT = new Object();

	public final String name;
	public final List<Object> parts;

	/** 代入元の安定な文書順です。 */
	public final long order;

	public PendingStringSet(final String name, final List<Object> parts, final long order) {
		this.name = name;
		this.parts = List.copyOf(parts);
		this.order = order;
	}
}
