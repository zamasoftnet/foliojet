package net.zamasoft.foliojet.driver;



// Split from MyHttpSourceResolver.java on 2026-09-02 (body only moved; design review: 10 classes, 1,560 lines).
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
