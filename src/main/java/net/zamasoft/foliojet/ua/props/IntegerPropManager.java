package net.zamasoft.foliojet.ua.props;

import java.util.Map;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.UserAgent;

public class IntegerPropManager extends AbstractPropManager {
	public final int defaultInt;

	/** 受け付ける値の範囲(両端を含む)。外れた値は警告して既定値にする(2026-10-05)。 */
	private final int min, max;

	public IntegerPropManager(String name, int defaultInt) {
		this(name, defaultInt, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	public IntegerPropManager(String name, int defaultInt, int min, int max) {
		super(name);
		this.defaultInt = defaultInt;
		this.min = min;
		this.max = max;
	}

	public String getDefaultString() {
		return String.valueOf(this.defaultInt);
	}

	public int getInteger(UserAgent ua) {
		return this.getInteger(ua.getProperty(this.name), ua);
	}

	public int getInteger(Map<String, String> props, MessageHandler mh) {
		return this.getInteger((String) props.get(this.name), mh);
	}

	public int getInteger(String str, MessageHandler mh) {
		if (str == null) {
			return this.defaultInt;
		}
		try {
			final int value = Integer.parseInt(str);
			if (value >= this.min && value <= this.max) {
				return value;
			}
		} catch (NumberFormatException e) {
			// 下で警告する
		}
		mh.message(MessageCodes.WARN_BAD_IO_PROPERTY, new String[] { this.name, str });
		return this.defaultInt;
	}
}
