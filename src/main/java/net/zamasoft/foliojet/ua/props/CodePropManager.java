package net.zamasoft.foliojet.ua.props;

import java.util.Map;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.UserAgent;

public class CodePropManager extends AbstractPropManager {
	public final String[] idents;
	public final short defaultCode;

	public CodePropManager(String name, String[] idents, short defaultCode) {
		super(name);
		this.idents = idents;
		this.defaultCode = defaultCode;
	}

	public String getDefaultString() {
		return this.idents[this.defaultCode - 1];
	}

	public short getCode(UserAgent ua) {
		String str = ua.getProperty(this.name);
		return this.getCode(str, ua);
	}

	public short getCode(Map<String, String> props, MessageHandler mh) {
		String str = (String) props.get(this.name);
		return this.getCode(str, mh);
	}

	private short getCode(String str, MessageHandler mh) {
		if (str == null) {
			return this.defaultCode;
		}
		for (short i = 0; i < this.idents.length; ++i) {
			if (str.equalsIgnoreCase(this.idents[i])) {
				return (short) (i + 1);
			}
		}
		mh.message(MessageCodes.WARN_BAD_IO_PROPERTY, new String[] { this.name, str });
		return this.defaultCode;
	}
}
