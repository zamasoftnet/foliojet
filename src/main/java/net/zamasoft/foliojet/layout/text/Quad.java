package net.zamasoft.foliojet.layout.text;

import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * A non-character filler within text.
 *
 * <p>
 * A classification marker (foliojet terminology). Inherits the constants
 * ({@code JOIN}/{@code BREAK}/{@code CONTINUE_BEFORE}/{@code CONTINUE_AFTER})
 * and {@code getString()} from {@link TextControl}
 * (duplicate declarations were removed during consolidation on 2026-08-01).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public abstract class Quad extends TextControl {
}
