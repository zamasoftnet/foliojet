package net.zamasoft.foliojet.message;

public interface MessageHandler {
	public void message(short code, String... args);
}
