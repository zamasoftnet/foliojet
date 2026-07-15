package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum CSSJRubyValue implements Value {
	NONE_VALUE(CSSJRubyValue.NONE),

	RUBY_VALUE(CSSJRubyValue.RUBY),

	RB_VALUE(CSSJRubyValue.RB),

	RT_VALUE(CSSJRubyValue.RT);
	public static final byte NONE = 0;

	public static final byte RUBY = 1;

	public static final byte RB = 2;

	public static final byte RT = 3;

	private final byte ruby;

	private CSSJRubyValue(byte ruby) {
		this.ruby = ruby;
	}

	public byte getRuby() {
		return this.ruby;
	}

	public String toString() {
		switch (this.ruby) {
		case NONE:
			return "none";
		case RUBY:
			return "ruby";
		case RB:
			return "rb";
		case RT:
			return "rt";
		default:
			throw new IllegalStateException();
		}
	}
}