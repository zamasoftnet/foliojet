package jp.cssj.homare.ua.props;

import java.util.Map;

import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.message.MessageHandler;
import jp.cssj.homare.ua.UserAgent;

public class BooleanPropManager extends AbstractPropManager {
	public final boolean defaultBoolean;

	public BooleanPropManager(String name, boolean defaultBoolean) {
		super(name);
		this.defaultBoolean = defaultBoolean;
	}

	public String getDefaultString() {
		return String.valueOf(this.defaultBoolean);
	}

	public boolean getBoolean(Map<String, String> props, MessageHandler mh) {
		String str = (String) props.get(this.name);
		return this.getBoolean(str, mh);
	}

	public boolean getBoolean(UserAgent ua) {
		String str = ua.getProperty(this.name);
		return this.getBoolean(str, ua);
	}

	public boolean getBoolean(String str, MessageHandler mh) {
		if (str == null) {
			return this.defaultBoolean;
		}
		if (str.equalsIgnoreCase("true")) {
			return true;
		}
		if (str.equalsIgnoreCase("false")) {
			return false;
		}
		mh.message(MessageCodes.WARN_BAD_IO_PROPERTY, new String[] { this.name, str });
		return false;
	}
}
