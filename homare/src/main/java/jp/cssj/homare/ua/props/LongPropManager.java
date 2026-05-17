package jp.cssj.homare.ua.props;

import java.util.Map;

import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.message.MessageHandler;
import jp.cssj.homare.ua.UserAgent;

public class LongPropManager extends AbstractPropManager {
	public final long defaultLong;

	public LongPropManager(String name, long defaultLong) {
		super(name);
		this.defaultLong = defaultLong;
	}

	public String getDefaultString() {
		return String.valueOf(this.defaultLong);
	}

	public long getLong(UserAgent ua) {
		return this.getLong(ua.getProperty(this.name), ua);
	}

	public long getInteger(Map<String, String> props, MessageHandler mh) {
		return this.getLong((String) props.get(this.name), mh);
	}

	public long getLong(String str, MessageHandler mh) {
		if (str == null) {
			return this.defaultLong;
		}
		try {
			return Long.parseLong(str);
		} catch (NumberFormatException e) {
			mh.message(MessageCodes.WARN_BAD_IO_PROPERTY, new String[] { this.name, str });
		}
		return this.defaultLong;
	}
}
