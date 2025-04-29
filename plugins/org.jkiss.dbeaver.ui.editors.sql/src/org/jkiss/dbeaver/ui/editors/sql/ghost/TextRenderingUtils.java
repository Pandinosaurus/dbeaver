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
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

public class TextRenderingUtils {

    private TextRenderingUtils() {
    }

    /**
     * Renders first line of hint text
     */
    public static void drawFirstLine(
        String text,
        GC gc,
        StyledText textWidget,
        int widgetOffset
    ) {
        widgetOffset = Math.max(0, Math.min(widgetOffset, textWidget.getCharCount()));

        int line;
        try {
            line = textWidget.getLineAtOffset(widgetOffset);
        } catch (IllegalArgumentException e) {
            return;
        }
        int bias = calculateBaselineOffset(gc, textWidget, line);

        Point origin;
        try {
            origin = textWidget.getLocationAtOffset(widgetOffset);
        } catch (IllegalArgumentException e) {
            origin = textWidget.getLocationAtOffset(textWidget.getCharCount() - 1);
            origin.y += textWidget.getLineHeight();
            origin.x = textWidget.getLeftMargin();
        }

        FontMetrics fm = gc.getFontMetrics();
        int fontHeight = fm.getHeight();
        int lineHeight = textWidget.getLineHeight();
        int y = origin.y + (lineHeight - fontHeight);

        if (text != null) {
            text = trimOverlappingText(text, widgetOffset, textWidget);
            gc.drawString(text, origin.x, y + bias, true);
        }
    }

    /**
     * Renders continuation lines
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
     * Removes duplicate text
     */
    public static String trimOverlappingText(
        String text,
        int offset,
        StyledText widget
    ) {
        String remaining = getLineRemainder(offset, widget);
        if (!remaining.isEmpty() && text.endsWith(remaining)) {
            return text.substring(0, text.length() - remaining.length());
        }
        return text;
    }

    /**
     * Calculates the baseline offset for a specified line in a StyledText widget.
     */
    public static int calculateBaselineOffset(
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

    private static String getLineRemainder(int offset, StyledText widget) {
        int line = widget.getLineAtOffset(offset);
        int start = widget.getOffsetAtLine(line);
        String contents = widget.getLine(line);
        return contents.substring(offset - start);
    }


    /**
     * Converts a widget offset to a corresponding model offset in the text viewer.
     *
     * @param viewer       the text viewer from which the widget offset is taken
     * @param widgetOffset the offset in the widget
     * @return the corresponding model offset in the viewer
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