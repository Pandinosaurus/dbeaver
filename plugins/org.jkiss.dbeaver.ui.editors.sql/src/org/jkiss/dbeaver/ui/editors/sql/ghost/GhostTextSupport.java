/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jkiss.dbeaver.ui.editors.sql.ghost;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;

public class GhostTextSupport {

    private GhostTextSupport() {}

    /**
     * Draws the first (possibly truncated) line of ghost text at the given offset.
     */
    public static void drawFirstLine(
        ITextViewer viewer,
        String text,
        GC gc,
        StyledText textWidget,
        int widgetOffset
    ) {
        // ensure offset in range
        widgetOffset = Math.max(0, Math.min(widgetOffset, textWidget.getCharCount() - 1));
        int line;
        try {
            line = textWidget.getLineAtOffset(widgetOffset);
        } catch (IllegalArgumentException e) {
            return;
        }
        int bias = getBaselineBias(gc, textWidget, line);
        Rectangle bounds = textWidget.getTextBounds(widgetOffset, widgetOffset);
        int x = bounds.x + bounds.width;
        int y = bounds.y + bounds.height - textWidget.getLineHeight();
        if (text != null && gc != null) {
            text = stripMatchingLineContent(text, widgetOffset, textWidget);
            gc.drawString(text, x, y + bias, true);
        }
    }

    /**
     * Draws second and subsequent lines of ghost text.
     */
    public static void drawNextLines(
        String text,
        GC gc,
        StyledText textWidget,
        int offset
    ) {
        int lineHeight = textWidget.getLineHeight();
        int fontHeight = gc.getFontMetrics().getHeight();
        Point origin = textWidget.getLocationAtOffset(offset);
        int x = textWidget.getLeftMargin();
        int y = origin.y + lineHeight + (lineHeight - fontHeight);
        gc.drawText(text, x, y, true);
    }

    /**
     * Adjusts a StyleRange to accommodate ghost text width, if needed.
     */
    static StyleRange updateStyle(
        int widgetOffset,
        String text,
        StyleRange style,
        FontMetrics fontMetrics,
        ITextViewer viewer,
        int spaceWidth,
        int textWidth
    ) {
        int totalWidth = spaceWidth + textWidth;
        if (style == null) {
            style = new StyleRange();
            style.start = widgetOffset;
            style.length = 1;
        }
        GlyphMetrics gm = style.metrics;
        if (gm == null || gm.width != totalWidth) {
            style.metrics = new GlyphMetrics(
                fontMetrics.getAscent(),
                fontMetrics.getDescent(),
                totalWidth
            );
            return style;
        }
        return null;
    }

    /**
     * Removes any leading characters that match existing content on the line.
     */
    public static String stripMatchingLineContent(
        String text,
        int offset,
        StyledText widget
    ) {
        String remaining = getRemainingLineContent(offset, widget);
        if (!remaining.isEmpty() && text.endsWith(remaining)) {
            return text.substring(0, text.length() - remaining.length());
        }
        return text;
    }

    /**
     * Computes vertical bias to align text with widget baseline.
     */
    public static int getBaselineBias(
        GC gc,
        StyledText textWidget,
        int widgetLine
    ) {
        if (gc == null) {
            return 0;
        }
        int offset = textWidget.getOffsetAtLine(widgetLine);
        int widgetBaseline = textWidget.getBaseline(offset);
        FontMetrics fm = gc.getFontMetrics();
        int fontBaseline = fm.getAscent() + fm.getLeading();
        return Math.max(0, widgetBaseline - fontBaseline);
    }

    private static String getRemainingLineContent(int offset, StyledText widget) {
        int line = widget.getLineAtOffset(offset);
        int start = widget.getOffsetAtLine(line);
        String contents = widget.getLine(line);
        return contents.substring(offset - start);
    }

    /**
     * Maps a widget offset to model offset if viewer supports it.
     */
    public static int widgetOffset2ModelOffset(
        ITextViewer viewer,
        int widgetOffset
    ) {
        return viewer instanceof ITextViewerExtension5 ext5
            ? ext5.widgetOffset2ModelOffset(widgetOffset)
            : widgetOffset;
    }
}
