package net.zamasoft.foliojet.ua.props;

import java.util.Map;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 選択肢から1つを選ぶプロパティです。
 */
public final class CodePropManager<E extends Enum<E> & PropCode> extends AbstractPropManager {
	private final Class<E> type;

	private final E defaultValue;

	public CodePropManager(String name, Class<E> type, E defaultValue) {
		super(name);
		this.type = type;
		this.defaultValue = defaultValue;
	}

	public String getDefaultString() {
		return this.defaultValue.ident();
	}

	public E get(UserAgent ua) {
		return this.get(ua.getProperty(this.name), ua);
	}

	public E get(Map<String, String> props, MessageHandler mh) {
		return this.get(props.get(this.name), mh);
	}

	private E get(String str, MessageHandler mh) {
		if (str == null) {
			return this.defaultValue;
		}
		for (E e : this.type.getEnumConstants()) {
			if (str.equalsIgnoreCase(e.ident())) {
				return e;
			}
		}
		mh.message(MessageCodes.WARN_BAD_IO_PROPERTY, this.name, str);
		return this.defaultValue;
	}
}
