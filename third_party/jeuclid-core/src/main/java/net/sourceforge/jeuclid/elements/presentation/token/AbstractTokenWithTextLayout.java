/*
 * Copyright 2007 - 2009 JEuclid, http://jeuclid.sf.net
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package net.sourceforge.jeuclid.elements.presentation.token;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.Collections;
import java.util.List;

import net.sourceforge.jeuclid.LayoutContext;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.elements.AbstractJEuclidElement;
import net.sourceforge.jeuclid.elements.support.text.StringUtil;
import net.sourceforge.jeuclid.layout.LayoutInfo;
import net.sourceforge.jeuclid.layout.LayoutStage;
import net.sourceforge.jeuclid.layout.LayoutView;
import net.sourceforge.jeuclid.layout.LayoutableNode;
import net.sourceforge.jeuclid.layout.TextObject;

import org.apache.batik.dom.AbstractDocument;
import org.w3c.dom.mathml.MathMLPresentationToken;

/**
 * Common functionality for all tokens based on a text layout.
 * 
 * @version $Revision$
 */
public abstract class AbstractTokenWithTextLayout extends
        AbstractJEuclidElement implements MathMLPresentationToken {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor. Sets MathML Namespace.
     * 
     * @param qname
     *            Qualified name.
     * @param odoc
     *            Owner Document.
     */
    public AbstractTokenWithTextLayout(final String qname,
            final AbstractDocument odoc) {
        super(qname, odoc);
    }

    /** {@inheritDoc} */
    @Override
    public void layoutStageInvariant(final LayoutView view,
            final LayoutInfo info, final LayoutStage stage,
            final LayoutContext context) {
        final Graphics2D g = view.getGraphics();
        final TextLayout t = this.produceTextLayout(g, context);
        if (t != null) {
            final StringUtil.TextLayoutInfo tli = StringUtil.getTextLayoutInfo(
                    t, false);
            info.setAscentHeight(tli.getAscent(), stage);
            info.setDescentHeight(tli.getDescent(), stage);
            float width = tli.getWidth();
            // Copper PDF (2026-10-04): an identifier gets the italic correction
            // of its glyph (MATH table) after it, as in TeX, unless it is the
            // base of a script, which uses it to place the superscript.
            if (!this.isScriptBase()) {
                width = Math.max(width, tli.getOffset() + t.getAdvance()
                        + this.getItalicCorrection(g, context));
            }
            info.setHorizontalCenterOffset(width / 2.0f, stage);
            info.setWidth(width, stage);
            info.setGraphicsObject(new TextObject(t, tli.getOffset(),
                    (Color) this.applyLocalAttributesToContext(context)
                            .getParameter(Parameter.MATHCOLOR)));
        }
    }

    /** Whether this token is the base of an msub, msup or msubsup. */
    private boolean isScriptBase() {
        final org.w3c.dom.Node parent = this.getParentNode();
        return (parent instanceof net.sourceforge.jeuclid.elements.presentation.script.AbstractSubSuper)
                && ((net.sourceforge.jeuclid.elements.presentation.script.AbstractSubSuper) parent)
                        .getBase() == this;
    }

    /**
     * Copper PDF (2026-10-04): the italic correction of a single-glyph
     * identifier, from the MATH table of its font. 0 for other tokens and
     * for fonts without a MATH table.
     *
     * @param g
     *            the graphics context
     * @param context
     *            the layout context of the parent
     * @return the italic correction
     */
    public float getItalicCorrection(final Graphics2D g,
            final LayoutContext context) {
        if (!(this instanceof Mi)) {
            return 0.0f;
        }
        final LayoutContext now = this.applyLocalAttributesToContext(context);
        final AttributedCharacterIterator aci = StringUtil
                .textContentAsAttributedCharacterIterator(now, this, this, 1.0f);
        final StringBuilder text = new StringBuilder();
        for (char c = aci.first(); c != java.text.CharacterIterator.DONE; c = aci
                .next()) {
            text.append(c);
        }
        if ((text.length() == 0)
                || (text.codePointCount(0, text.length()) != 1)) {
            return 0.0f;
        }
        aci.first();
        final Object font = aci.getAttribute(java.awt.font.TextAttribute.FONT);
        if (!(font instanceof java.awt.Font)) {
            return 0.0f;
        }
        final java.awt.Font f = (java.awt.Font) font;
        final net.sourceforge.jeuclid.font.MathTable table = net.sourceforge.jeuclid.font.MathTable
                .forFont(f);
        if (table == null) {
            return 0.0f;
        }
        final java.awt.font.GlyphVector glyphs = f.createGlyphVector(
                g.getFontRenderContext(), text.toString());
        if (glyphs.getNumGlyphs() != 1) {
            return 0.0f;
        }
        return table.italicCorrection(glyphs.getGlyphCode(0), f.getSize2D());
    }

    private TextLayout produceTextLayout(final Graphics2D g2d,
            final LayoutContext context) {
        TextLayout layout;
        final LayoutContext now = this.applyLocalAttributesToContext(context);

        final AttributedCharacterIterator aci = StringUtil
                .textContentAsAttributedCharacterIterator(now, this, this, 1.0f);

        if (aci.getBeginIndex() == aci.getEndIndex()) {
            return null;
        } else {
            layout = StringUtil.createTextLayoutFromAttributedString(g2d,
                    new AttributedString(aci), now);
            return layout;
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<LayoutableNode> getChildrenToLayout() {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LayoutableNode> getChildrenToDraw() {
        return Collections.emptyList();
    }

}
