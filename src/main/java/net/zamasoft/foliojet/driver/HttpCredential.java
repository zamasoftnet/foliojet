package net.zamasoft.foliojet.driver;



// 2026-09-02 に MyHttpSourceResolver.java から分けた(本文は移しただけ。設計レビュー「10クラス 1,560行」)。
class HttpCredential {
	final String host;
	final int port;
	final String user;
	final String password;

	HttpCredential(String host, int port, String user, String password) {
		this.host = host;
		this.port = port;
		this.user = user;
		this.password = password;
	}

	boolean matches(String host, int port) {
		return this.host.equalsIgnoreCase(host) && (this.port == -1 || this.port == port);
	}
}
