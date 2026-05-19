package net.zamasoft.foliojet.ua.props;

import java.util.Map;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.util.NumberUtils;

public class DoublePropManager extends AbstractPropManager {
	public final double defaultDouble;

	public DoublePropManager(String name, double defaultDouble) {
		super(name);
		this.defaultDouble = defaultDouble;
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
			return NumberUtils.parseDouble(str);
		} catch (NumberFormatException e) {
			mh.message(MessageCodes.WARN_BAD_IO_PROPERTY, new String[] { this.name, str });
		}
		return this.defaultDouble;
	}
}
