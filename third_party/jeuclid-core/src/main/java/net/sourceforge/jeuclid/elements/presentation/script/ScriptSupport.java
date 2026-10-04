/*
 * Copyright 2007 - 2007 JEuclid, http://jeuclid.sf.net
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

package net.sourceforge.jeuclid.elements.presentation.script;

import java.awt.geom.Dimension2D;

import net.sourceforge.jeuclid.LayoutContext;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.elements.JEuclidElement;
import net.sourceforge.jeuclid.elements.support.Dimension2DImpl;
import net.sourceforge.jeuclid.elements.support.ElementListSupport;
import net.sourceforge.jeuclid.elements.support.attributes.AttributesHelper;
import net.sourceforge.jeuclid.elements.presentation.token.AbstractTokenWithTextLayout;
import net.sourceforge.jeuclid.font.MathTable;
import net.sourceforge.jeuclid.layout.LayoutInfo;
import net.sourceforge.jeuclid.layout.LayoutStage;
import net.sourceforge.jeuclid.layout.LayoutView;

/**
 * Support class for script elements.
 * 
 * @see AbstractSubSuper
 * @see AbstractUnderOver
 * @version $Revision$
 */
public final class ScriptSupport {
    /** Default Constructor. */
    private ScriptSupport() {
        // Empty on purpose.
    }

    /**
     * Info for baseline shifts.
     * 
     * @version $Revision$
     */
    static class ShiftInfo {
        private float superShift;

        private float subShift;

        /**
         * Creates a new ShiftInfo object.
         * 
         * @param sub
         *            subShift.
         * @param sup
         *            superShift.
         */
        protected ShiftInfo(final float sub, final float sup) {
            this.superShift = sup;
            this.subShift = sub;
        }

        /**
         * Getter method for superShift.
         * 
         * @return the superShift
         */
        public float getSuperShift() {
            return this.superShift;
        }

        /**
         * Getter method for subShift.
         * 
         * @return the subShift
         */
        public float getSubShift() {
            return this.subShift;
        }

        /**
         * Adjust this shift to contain the max shift from current shit and
         * other info.
         * 
         * @param otherInfo
         *            other info to use.
         */
        public void max(final ShiftInfo otherInfo) {
            this.subShift = Math.max(this.subShift, otherInfo.subShift);
            this.superShift = Math.max(this.superShift, otherInfo.superShift);
        }

    }

    // CHECKSTYLE:OFF
    // More than 7 parameters. But only used internally, so that's ok.
    static void layout(final LayoutView view, final LayoutInfo info,
            final LayoutStage stage, final LayoutContext now,
            final JEuclidElement parent, final JEuclidElement base,
            final JEuclidElement sub, final JEuclidElement sup,
            final String subScriptShift, final String superScriptShift) {
        // CHECKSTYLE:ON
        final LayoutInfo baseInfo = view.getInfo(base);
        final float width = baseInfo.getWidth(stage);

        final LayoutInfo subInfo = view.getInfo(sub);
        final LayoutInfo superInfo = view.getInfo(sup);

        // Copper PDF (2026-10-04): with a MATH table, place the scripts by its
        // constants (TeX rule 18) and shift the superscript by the italic
        // correction of the base glyph.
        final MathTable table = MathTable.find(now);
        final ShiftInfo shiftInfo = table == null ? ScriptSupport
                .calculateScriptShfits(stage, now, subScriptShift,
                        superScriptShift, baseInfo, subInfo, superInfo)
                : ScriptSupport.calculateMathTableShifts(stage, now, table,
                        ScriptSupport.isCharacter(base), subScriptShift,
                        superScriptShift, baseInfo, subInfo, superInfo);
        float scriptStart = width;
        float italicCorrection = 0.0f;
        if ((table != null) && (base instanceof AbstractTokenWithTextLayout)) {
            // TeX rule 18: the subscript starts at the end of the advance (it
            // tucks under an overhanging italic glyph), the superscript after
            // the italic correction. The width of the base also covers its ink.
            final AbstractTokenWithTextLayout token = (AbstractTokenWithTextLayout) base;
            final float advanceEnd = token.getAdvanceEnd(view.getGraphics(), now);
            if (advanceEnd >= 0.0f) {
                scriptStart = advanceEnd;
            }
            italicCorrection = token.getItalicCorrection(view.getGraphics(), now);
        }

        if (subInfo != null) {
            subInfo.moveTo(scriptStart, shiftInfo.getSubShift(), stage);
        }
        if (superInfo != null) {
            superInfo.moveTo(scriptStart + italicCorrection,
                    -shiftInfo.getSuperShift(), stage);
        }

        final Dimension2D borderLeftTop = new Dimension2DImpl(0.0f, 0.0f);
        final Dimension2D borderRightBottom = new Dimension2DImpl(
                table == null ? 0.0f : table.get(
                        MathTable.Constant.SPACE_AFTER_SCRIPT,
                        ScriptSupport.fontSize(now)), 0.0f);
        ElementListSupport.fillInfoFromChildren(view, info, parent, stage,
                borderLeftTop, borderRightBottom);
        info.setStretchAscent(baseInfo.getStretchAscent());
        info.setStretchDescent(baseInfo.getStretchDescent());
    }

    private static float fontSize(final LayoutContext now) {
        return ((Number) now.getParameter(Parameter.MATHSIZE)).floatValue();
    }

    /**
     * Whether a base is a single character (TeX rule 18a: the scripts of a
     * character do not move with its height and depth).
     */
    static boolean isCharacter(final JEuclidElement base) {
        // A single-character operator is a character too: the superscript of
        // ")" in (-x)^3 sits as high as that of x^3, not at the top of the
        // parenthesis
        if (!(base instanceof AbstractTokenWithTextLayout)
                && !(base instanceof net.sourceforge.jeuclid.elements.presentation.token.Mo)) {
            return false;
        }
        final String text = ((net.sourceforge.jeuclid.elements.AbstractJEuclidElement) base).getText();
        return (text != null) && (text.trim().codePointCount(0, text.trim().length()) == 1);
    }

    /**
     * Copper PDF (2026-10-04): script shifts from the MATH table constants
     * (OpenType MATH, TeX rule 18). The shifts used to follow the ink of the
     * base, so y_1 sat lower than x_1 and P^3 higher than x^3.
     */
    // CHECKSTYLE:OFF
    static ShiftInfo calculateMathTableShifts(final LayoutStage stage,
            final LayoutContext now, final MathTable table,
            final boolean characterBase, final String subScriptShift,
            final String superScriptShift, final LayoutInfo baseInfo,
            final LayoutInfo subInfo, final LayoutInfo superInfo) {
        // CHECKSTYLE:ON
        final float size = ScriptSupport.fontSize(now);
        float subShift = 0.0f;
        float superShift = 0.0f;
        if (subInfo != null) {
            final float drop = characterBase ? 0.0f : baseInfo
                    .getDescentHeight(stage)
                    + table.get(MathTable.Constant.SUBSCRIPT_BASELINE_DROP_MIN,
                            size);
            subShift = Math.max(Math.max(drop, table.get(
                    MathTable.Constant.SUBSCRIPT_SHIFT_DOWN, size)), subInfo
                    .getAscentHeight(stage)
                    - table.get(MathTable.Constant.SUBSCRIPT_TOP_MAX, size));
        }
        if (superInfo != null) {
            final float drop = characterBase ? 0.0f : baseInfo
                    .getAscentHeight(stage)
                    - table.get(
                            MathTable.Constant.SUPERSCRIPT_BASELINE_DROP_MAX,
                            size);
            superShift = Math.max(Math.max(drop, table.get(
                    MathTable.Constant.SUPERSCRIPT_SHIFT_UP, size)), table.get(
                    MathTable.Constant.SUPERSCRIPT_BOTTOM_MIN, size)
                    + superInfo.getDescentHeight(stage));
        }
        if ((subInfo != null) && (superInfo != null)) {
            final float superBottom = superShift
                    - superInfo.getDescentHeight(stage);
            final float gap = superBottom
                    - (subInfo.getAscentHeight(stage) - subShift);
            final float gapMin = table.get(
                    MathTable.Constant.SUB_SUPERSCRIPT_GAP_MIN, size);
            if (gap < gapMin) {
                final float needed = gapMin - gap;
                final float raise = Math.max(0.0f, Math.min(needed, table.get(
                        MathTable.Constant.SUPERSCRIPT_BOTTOM_MAX_WITH_SUBSCRIPT,
                        size)
                        - superBottom));
                superShift += raise;
                subShift += needed - raise;
            }
        }
        subShift = Math.max(subShift, AttributesHelper.convertSizeToPt(
                subScriptShift, now, AttributesHelper.PT));
        superShift = Math.max(superShift, AttributesHelper.convertSizeToPt(
                superScriptShift, now, AttributesHelper.PT));
        return new ShiftInfo(subShift, superShift);
    }

    static ShiftInfo calculateScriptShfits(final LayoutStage stage,
            final LayoutContext now, final String subScriptShift,
            final String superScriptShift, final LayoutInfo baseInfo,
            final LayoutInfo subInfo, final LayoutInfo superInfo) {
        float subShift = 0.0f;
        float superShift = 0.0f;
        if (subInfo != null) {
            subShift = Math.max(baseInfo.getDescentHeight(stage)
                    + (subInfo.getAscentHeight(stage) - subInfo
                            .getDescentHeight(stage)) / 2.0f, AttributesHelper
                    .convertSizeToPt(subScriptShift, now, AttributesHelper.PT));
        }
        if (superInfo != null) {
            superShift = Math.max(baseInfo.getAscentHeight(stage)
                    - (superInfo.getAscentHeight(stage) - superInfo
                            .getDescentHeight(stage)) / 2.0f,
                    AttributesHelper.convertSizeToPt(superScriptShift, now,
                            AttributesHelper.PT));
        }
        if ((subInfo != null) && (superInfo != null)) {
            final float topSub = -subShift + subInfo.getAscentHeight(stage)
                    + 1.0f;
            final float bottomSuper = superShift
                    - superInfo.getDescentHeight(stage) - 1.0f;

            final float overlap = Math.max(0.0f, topSub - bottomSuper);
            final float overlapShift = overlap / 2.0f;

            superShift += overlapShift;
            subShift += overlapShift;
        }
        return new ShiftInfo(subShift, superShift);
    }

}
