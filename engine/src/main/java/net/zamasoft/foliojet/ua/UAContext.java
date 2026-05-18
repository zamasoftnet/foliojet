package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

import net.zamasoft.pdfg2d.font.FontSourceManager;

/**
 * 現在のUAでの処理に関連する状態です。
 */
public class UAContext {
	private int passCount = 0;

	private final PageRef pageRef = new PageRef();

	private FontSourceManager fsm;
	
	private Map<Object, ImageMap> maps = new HashMap<Object, ImageMap> ();

	public FontSourceManager getFontSourceManager() {
		return this.fsm;
	}

	public void setFontSourceManager(FontSourceManager fsm) {
		this.fsm = fsm;
	}

	public int getPassCount() {
		return this.passCount;
	}

	public void setPassCount(int passCount) {
		this.passCount = passCount;
	}

	public PageRef getPageRef() {
		return this.pageRef;
	}
	
	public Map<Object, ImageMap> getImageMaps() {
		return this.maps;
	}
}
