package net.zamasoft.foliojet.epub;

import java.io.IOException;
import java.io.InputStream;

/**
 * 
 * @author MIYABE Tatsuhiko
 */
public interface ArchiveFile {
	public boolean exists(String path);

	public InputStream getInputStream(String path) throws IOException;

	public void close() throws IOException;
}
