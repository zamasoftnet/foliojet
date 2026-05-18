package net.zamasoft.foliojet.ua.props;

public abstract class AbstractPropManager implements PropManager {
	public final String name;

	public AbstractPropManager(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

}
