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

import org.eclipse.jface.text.*;
import org.eclipse.swt.custom.LineBackgroundEvent;
import org.eclipse.swt.custom.LineBackgroundListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.jkiss.dbeaver.ui.UIUtils;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class GhostTextPainter implements IPainter, PaintListener, LineBackgroundListener {

    public static final String GHOST = "ghost";
    private final ITextViewer textViewer;
    private Color ghostColor;
    private Mode mode;
    private final Semaphore semaphore;
    private boolean active;
    private SuggestionData current;
    private IPositionUpdater positionUpdater;
    private boolean independentMode = false;

    public GhostTextPainter(ITextViewer viewer) {
        this.textViewer = viewer;
        this.mode = Mode.NONE;
        this.semaphore = new Semaphore(1);
        this.current = SuggestionData.create(0, "");
        // register painter on UI thread
        UIUtils.asyncExec(() -> ((ITextViewerExtension2) textViewer).addPainter(this));
    }

    public void setTextColor(Color color) {
        this.ghostColor = color;
    }

    public void clearGhostText() {
        if (!acquire()) {
            return;
        }
        this.mode = Mode.CLEAR;
        UIUtils.asyncExec(this::performClear);
    }

    public void drawGhostText(String text, boolean clearPrevious) {
        if (!acquire()) {
            return;
        }
        this.mode = Mode.DRAW;
        UIUtils.asyncExec(() -> {
            if (clearPrevious) {
                performClear();
            }
            performDraw(text);
        });
    }

    public boolean isUpdating() {
        return mode != Mode.NONE;
    }

    public void activate() {
        if (!active) {
            active = true;
            StyledText widget = getStyledText();
            widget.addPaintListener(this);
            widget.addLineBackgroundListener(this);
            textViewer.getDocument().addPositionCategory(GHOST);
            positionUpdater = new DefaultPositionUpdater(GHOST);
            textViewer.getDocument().addPositionUpdater(positionUpdater);
        }
    }

    public void deactivate(boolean clear) {
        if (!active) {
            return;
        }
        if (clear) {
            clearGhostText();
        }
        StyledText widget = getStyledText();
        widget.removePaintListener(this);
        widget.removeLineBackgroundListener(this);
        textViewer.getDocument().removePositionUpdater(positionUpdater);
        try {
            textViewer.getDocument().removePositionCategory(GHOST);
        } catch (BadPositionCategoryException ignore) {
        }
        mode = Mode.NONE;
        active = false;
    }

    @Override
    public void setPositionManager(IPaintPositionManager manager) {

    }

    @Override
    public void dispose() {

    }

    @Override
    public void paint(int reason) {
        if (!active) {
            activate();
        }
    }

    @Override
    public void paintControl(PaintEvent e) {
        if (independentMode && mode == Mode.DRAW) {
            renderDraw(e.gc);
            return;
        }
        if (!hasDisplayText()) {
            resetMode();
            return;
        }
        switch (mode) {
            case DRAW:
                renderDraw(e.gc);
                break;
            case CLEAR:
                resetMode();
                renderDraw(e.gc); // no-op since current text cleared
                break;
            default:
                renderDraw(e.gc);
                break;
        }
    }

    @Override
    public void lineGetBackground(LineBackgroundEvent event) {
        // no-op
    }

    public void acceptSuggestion() {
        if (!hasDisplayText()) {
            return;
        }
        privInsertText(current.getText());
        clearGhostText();
    }

    public boolean hasDisplayText() {
        return current != null && !current.isEmpty();
    }

    public int getCurrentOffset() {
        return current != null ? current.getOffset() : -1;
    }

    private void renderDraw(GC gc) {
        initGC(gc);
        int off = current.getOffset();
        String[] lines = current.getLines();
        if (lines.length > 0) {
            GhostTextSupport.drawFirstLine(textViewer, lines[0], gc, getStyledText(), off);
            initGC(gc);
            if (lines.length > 1) {
                GhostTextSupport.drawNextLines(lines[1], gc, getStyledText(), off);
            }
        }
        resetMode();
    }

    private void performDraw(String text) {
        int off = getCaretOffset();
        String prefix = extractPrefix();
        String frag = text;
        if (!prefix.isEmpty() && frag.toLowerCase().startsWith(prefix.toLowerCase())) {
            frag = frag.substring(prefix.length());
        }
        current = SuggestionData.create(off, frag);
        getStyledText().redraw();
    }

    private void performClear() {
        current = SuggestionData.create(current.getOffset(), "");
        getStyledText().redraw();
    }

    private void privInsertText(String text) {
        try {
            IDocument doc = textViewer.getDocument();
            int modelOff = GhostTextSupport.widgetOffset2ModelOffset(textViewer, current.getOffset());
            doc.replace(modelOff, 0, text);
            getStyledText().setCaretOffset(current.getOffset() + text.length());
        } catch (Exception ignored) {
        }
    }

    private boolean acquire() {
        try {
            return semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    private void resetMode() {
        mode = Mode.NONE;
        if (semaphore.availablePermits() == 0) {
            semaphore.release();
        }
    }

    private void initGC(GC gc) {
        if (ghostColor != null) {
            gc.setForeground(ghostColor);
        }
        gc.setBackground(getStyledText().getBackground());
    }

    private int getCaretOffset() {
        return getStyledText().getCaretOffset();
    }

    private String extractPrefix() {
        StyledText w = getStyledText();
        int off = getCaretOffset();
        String line = w.getText().substring(0, off);
        int sep = Math.max(line.lastIndexOf(' '), line.lastIndexOf('\t'));
        return sep >= 0 ? line.substring(sep + 1) : line;
    }

    private StyledText getStyledText() {
        return textViewer.getTextWidget();
    }

    private static class SuggestionData {
        private final int offset;
        private final String text;

        private SuggestionData(int offset, String text) {
            this.offset = offset;
            this.text = text == null ? "" : text;
        }

        static SuggestionData create(int offset, String text) {
            return new SuggestionData(offset, text);
        }

        int getOffset() {
            return offset;
        }

        String getText() {
            return text;
        }

        boolean isEmpty() {
            return text.isEmpty();
        }

        String[] getLines() {
            return text.split("\\R", 2);
        }
    }

    private enum Mode {
        NONE,
        DRAW,
        CLEAR
    }
}

