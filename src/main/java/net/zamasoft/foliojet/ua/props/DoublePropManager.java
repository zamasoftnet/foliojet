package net.zamasoft.foliojet.ua.props;

import java.util.Map;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.util.NumberUtils;

public class DoublePropManager extends AbstractPropManager {
	public final double defaultDouble;

	/** 受け付ける値の範囲(両端を含む)。外れた値と有限でない値は警告して既定値にする(2026-10-05)。 */
	private final double min, max;

	public DoublePropManager(String name, double defaultDouble) {
		this(name, defaultDouble, -Double.MAX_VALUE, Double.MAX_VALUE);
	}

	public DoublePropManager(String name, double defaultDouble, double min, double max) {
		super(name);
		this.defaultDouble = defaultDouble;
		this.min = min;
		this.max = max;
	}

	public String getDefaultString() {
		return String.valueOf(this.defaultDouble);
	}

	public double getDouble(UserAgent ua) {
		return this.getDouble(ua.getProperty(this.name), ua);
	}

	public double getDouble(Map<String, String> props, MessageHandler mh) {
		return this.getDouble((String) props.get(this.name), mh);
	}

	public double getDouble(String str, MessageHandler mh) {
		if (str == null) {
			return this.defaultDouble;
		}
		try {
			final double value = NumberUtils.parseDouble(str);
			if (value >= this.min && value <= this.max) {
				return value;
			}
		} catch (NumberFormatException e) {
			// 下で警告する
		}
		mh.message(MessageCodes.WARN_BAD_IO_PROPERTY, new String[] { this.name, str });
		return this.defaultDouble;
	}
}
