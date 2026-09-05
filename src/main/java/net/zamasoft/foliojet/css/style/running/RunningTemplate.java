package net.zamasoft.foliojet.css.style.running;

import java.util.List;

/** 生成内容を未評価のまま保持する、liveスタイルから独立した不変の部分木です。 */
public final class RunningTemplate {
	public sealed interface Event permits Start, End, Text, Token {
	}

	/** 疑似要素も開始・終了イベントで表し、疑似名はbefore/after/first-letter、実要素はnullです。 */
	public record Start(StyleSnapshot style, String pseudo) implements Event {
	}

	public record End() implements Event {
	}

	public record Text(String text) implements Event {
	}

	/** 内側のrunningは独立に代入し、外側には名前と文書順だけを残します。 */
	public record Token(String name, long order) implements Event {
	}

	private final String name;
	private final byte pages;
	private final boolean legacy;
	private final List<Event> events;
	private final int textBytes;
	private final int imageReferences;

	RunningTemplate(final String name, final byte pages, final boolean legacy, final List<Event> events,
			final int textBytes, final int imageReferences) {
		this.name = name;
		this.pages = pages;
		this.legacy = legacy;
		this.events = List.copyOf(events);
		this.textBytes = textBytes;
		this.imageReferences = imageReferences;
	}

	public String name() {
		return this.name;
	}

	public byte pages() {
		return this.pages;
	}

	/** 頁固定配置のlegacyテンプレートかを返します。 */
	public boolean legacy() {
		return this.legacy;
	}

	public List<Event> events() {
		return this.events;
	}

	/** コピーしたペイロードの予算消費量です(文字列はUTF-16バイト数)。 */
	public int textBytes() {
		return this.textBytes;
	}

	public int imageReferences() {
		return this.imageReferences;
	}
}
