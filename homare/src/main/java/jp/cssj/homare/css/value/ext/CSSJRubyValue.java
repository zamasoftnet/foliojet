package jp.cssj.homare.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJRubyValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJRubyValue implements ExtValue {
	public static final byte NONE = 0;

	public static final byte RUBY = 1;

	public static final byte RB = 2;

	public static final byte RT = 3;

	public static final CSSJRubyValue NONE_VALUE = new CSSJRubyValue(NONE);

	public static final CSSJRubyValue RUBY_VALUE = new CSSJRubyValue(RUBY);

	public static final CSSJRubyValue RB_VALUE = new CSSJRubyValue(RB);

	public static final CSSJRubyValue RT_VALUE = new CSSJRubyValue(RT);

	private final byte ruby;

	private CSSJRubyValue(byte ruby) {
		this.ruby = ruby;
	}

	public short getValueType() {
		return TYPE_CSSJ_RUBY;
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