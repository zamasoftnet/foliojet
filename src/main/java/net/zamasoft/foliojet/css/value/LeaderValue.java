package net.zamasoft.foliojet.css.value;

/**
 * {@code content}プロパティの{@code leader()}です(css-content-3、
 * consult-codex-2026-07-31-leader.txt)。パターンはパース時に正規化
 * する: {@code dotted}→"." / {@code solid}→"_" / {@code space}→" " /
 * カスタム文字列はそのまま(空文字列は構文エラー——ゼロ周期の無限
 * 反復を避ける)。
 *
 * @author MIYABE Tatsuhiko
 */
public class LeaderValue implements Value {
	public static final LeaderValue DOTTED = new LeaderValue(".");
	public static final LeaderValue SOLID = new LeaderValue("_");
	public static final LeaderValue SPACE = new LeaderValue(" ");

	private final String pattern;

	public LeaderValue(final String pattern) {
		assert !pattern.isEmpty();
		this.pattern = pattern;
	}

	public String getPattern() {
		return this.pattern;
	}

	public String toString() {
		return "leader(\"" + this.pattern + "\")";
	}
}
