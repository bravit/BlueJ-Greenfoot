/*
 This file is part of the BlueJ program. 
 Copyright (C) 2019,2020,2021,2023,2025  Michael Kolling and John Rosenberg

 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 

 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 

 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 This file is subject to the Classpath exception as provided in the
 LICENSE.txt file that accompanied this code.
 */
package bluej.editor.base;

import bluej.Config;
import bluej.prefmgr.PrefMgr;
import bluej.utility.javafx.FXPlatformConsumer;
import bluej.utility.javafx.FXPlatformFunction;
import bluej.utility.javafx.FXPlatformSupplier;
import bluej.utility.javafx.JavaFXUtil;
import javafx.beans.binding.StringExpression;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import javafx.util.Duration;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * A graphical item that contains a margin (used for line numbers and/or breakpoint symbols, step marks, etc)
 * and a text line.
 */
@OnThread(Tag.FX)
public class MarginAndTextLine extends Region
{
    private final int TEXT_LEFT_EDGE;
    private final double LINE_X;
    private final double MARGIN_BACKGROUND_WIDTH;
    private final double MARGIN_RIGHT;

    private final boolean showLeftMargin;

    private final Line dividerLine;
    private final int lineNumberToDisplay;
    private boolean hoveringMargin = false;

    // Does not include the hover icon, which is added dynamically:
    private final EnumSet<MarginDisplay> displayItems = EnumSet.noneOf(MarginDisplay.class);
    private final Tooltip breakpointHoverTooltip;
    private final Region backgroundNode;

    @OnThread(Tag.Any)
    public static enum MarginDisplay
    {
        // Important that step mark is after breakpoint, so that it appears in front:
        UNCOMPILED("bj-margin-uncompiled"), ERROR("bj-margin-error"), LINE_NUMBER, BREAKPOINT_HOVER, BREAKPOINT, STEP_MARK;

        public final String pseudoClass; // May be null

        MarginDisplay(String pseudoClass)
        {
            this.pseudoClass = pseudoClass;
        }

        MarginDisplay()
        {
            this(null);
        }
    }

    // When we added/removed these dynamically we had issues with mouse handling, so if showing left margin now we put them all in permanently but just change visibility:
    private final Label lineNumber;
    private final Node breakpointIcon;
    private final Node stepMarkIcon;
    public final TextLine textLine;

    /**
     * Get the left edge of the text, in pixels, based on whether we are showing the left margin or not.
     */
    public static int textLeftEdge(boolean showLeftMargin)
    {
        return showLeftMargin ? 32 : 2;
    }

    public MarginAndTextLine(int lineNumberToDisplay, TextLine textLine, boolean showLeftMargin, FXPlatformSupplier<Boolean> onClick, FXPlatformFunction<ContextMenuEvent, ContextMenu> getContextMenuToShow, FXPlatformConsumer<ScrollEvent> onScroll)
    {
        this.showLeftMargin = showLeftMargin;
        if (showLeftMargin)
        {
            TEXT_LEFT_EDGE = textLeftEdge(true);
            MARGIN_BACKGROUND_WIDTH = TEXT_LEFT_EDGE - 3;
            LINE_X = TEXT_LEFT_EDGE - 2.5;
            MARGIN_RIGHT = TEXT_LEFT_EDGE - 4;
        }
        else
        {
            MARGIN_BACKGROUND_WIDTH = 0;
            LINE_X = 0;
            TEXT_LEFT_EDGE = textLeftEdge(false);
            MARGIN_RIGHT = 0;
        }
        this.dividerLine = new Line(LINE_X, 0.5, LINE_X, 1);
        dividerLine.getStyleClass().add("flow-margin-line");
        this.lineNumberToDisplay = lineNumberToDisplay;
        this.backgroundNode = new Region();
        backgroundNode.getStyleClass().add("flow-margin-background");
        this.textLine = textLine;
        if (showLeftMargin)
        {
            this.lineNumber = makeLineNumber();
            this.breakpointIcon = makeBreakpointIcon();
            this.stepMarkIcon = makeStepMarkIcon();
            getChildren().setAll(backgroundNode, textLine, dividerLine, lineNumber, breakpointIcon, stepMarkIcon);
        }
        else
        {
            getChildren().setAll(backgroundNode, textLine);
            this.lineNumber = null;
            this.breakpointIcon = null;
            this.stepMarkIcon = null;
        }
        getStyleClass().add("margin-and-text-line");
        String breakpointHoverUsualText = Config.getString("editor.set.breakpoint.hint");
        String breakpointHoverFailText = Config.getString("editor.set.breakpoint.fail");
        breakpointHoverTooltip = new Tooltip(breakpointHoverUsualText);
        breakpointHoverTooltip.setShowDelay(Duration.seconds(1));
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getX() < LINE_X)
            {
                if (e.getButton() == MouseButton.PRIMARY && !e.isShiftDown())
                {
                    if (!onClick.get())
                    {
                        breakpointHoverTooltip.setText(breakpointHoverFailText);
                        breakpointHoverTooltip.setShowDelay(Duration.ZERO);
                    }
                    else
                    {
                        breakpointHoverTooltip.setText(breakpointHoverUsualText);
                        breakpointHoverTooltip.setShowDelay(Duration.seconds(1));
                    }
                    e.consume();
                }
            }
        });
        addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            hoveringMargin = e.getX() < LINE_X;
            setMarginGraphics(EnumSet.copyOf(displayItems));
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            hoveringMargin = false;
            breakpointHoverTooltip.setText(breakpointHoverUsualText);
            breakpointHoverTooltip.setShowDelay(Duration.seconds(1));
            setMarginGraphics(EnumSet.copyOf(displayItems));
        });

        // It would be neater to handle the scroll events in FlowEditorPane.  However
        // there is an issue on Mac because the scroll gesture on the track pad uses
        // the lowest node in the tree as a target, which is this MarginAndTextLine
        // (because our children have setMouseTransparent(true)).  When this line scrolls
        // out of view it disrupts the scroll events being fed to our parent nodes,
        // including FlowEditorPane.  So the scroll gesture appeared to stop as soon
        // as the line under the mouse cursor went out of view.  However the event is still
        // delivered to this target even after it's gone out of view, so we just redirect
        // the scroll events to the FlowEditorPane and it all works out.
        // The explanation and inspiration came from a similar issue in Flowless https://github.com/FXMisc/Flowless/issues/56
        // and the commit which fixed it: https://github.com/FXMisc/Flowless/pull/64/commits/040613b3e4e9837c30ca86e09d10c06d8b5270b8
        addEventHandler(ScrollEvent.ANY, e -> {
            onScroll.accept(e);
        });

        // Context menu to show or hide line numbers
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().add(
            JavaFXUtil.makeMenuItem(
                Config.getString("editor.toggle-breakpointLabel"),
                () -> onClick.get(),
                null
            )
        );
        contextMenu.getItems().add(
            JavaFXUtil.makeMenuItem(
                Config.getString("prefmgr.edit.displaylinenumbers"),
                () -> {PrefMgr.setFlag(PrefMgr.LINENUMBERS, !PrefMgr.getFlag(PrefMgr.LINENUMBERS)); },
                null
            )
        );

        // Right-clicks/control-clicks in the left margin show this menu:
        backgroundNode.setOnContextMenuRequested(e -> {
            if (contextMenu.isShowing())
            {
                contextMenu.hide();
            }
            contextMenu.show(backgroundNode, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        // Right-clicks/control-clicks anywhere else in the line show the menu passed to us:
        this.setOnContextMenuRequested(e -> {
            getContextMenuToShow.apply(e).show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        for (Node child : getChildren())
        {
            if (child == textLine)
            {
                textLine.resizeRelocate(TEXT_LEFT_EDGE, 0, getWidth() - TEXT_LEFT_EDGE, getHeight());
            }
            else if (child == dividerLine)
            {
                dividerLine.setEndY(getHeight() - 0.5);
            }
            else if (child == backgroundNode)
            {
                backgroundNode.resizeRelocate(0, 0, MARGIN_BACKGROUND_WIDTH, getHeight());
            }
            else
            {
                double height = child.prefHeight(-1);
                double width = child.prefWidth(-1);
                child.resizeRelocate(MARGIN_RIGHT - width, (getHeight() - height) / 2.0, width, height);
            }
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefWidth(double height)
    {
        return textLine.prefWidth(height) + TEXT_LEFT_EDGE;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return textLine.prefHeight(width - TEXT_LEFT_EDGE);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMinWidth(double height)
    {
        return textLine.minWidth(height) + TEXT_LEFT_EDGE;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMinHeight(double width)
    {
        return textLine.minHeight(width - TEXT_LEFT_EDGE);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMaxWidth(double height)
    {
        return textLine.maxWidth(height) + TEXT_LEFT_EDGE;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMaxHeight(double width)
    {
        return textLine.maxHeight(width - TEXT_LEFT_EDGE);
    }

    /**
     * Updates the line after a font change
     * @param fontCSS
     */
    public void fontSizeChanged(StringExpression fontCSS)
    {
        textLine.fontSizeChanged(fontCSS);
    }

    @OnThread(Tag.FX)
    public void setMarginGraphics(EnumSet<MarginDisplay> displayItems)
    {
        if (!showLeftMargin)
            return;

        this.displayItems.clear();
        this.displayItems.addAll(displayItems);
        EnumSet<MarginDisplay> toShow = EnumSet.copyOf(displayItems);
        // Don't show line number if we are showing the breakpoint hover:
        if (hoveringMargin && !toShow.contains(MarginDisplay.BREAKPOINT))
        {
            toShow.add(MarginDisplay.BREAKPOINT_HOVER);
            toShow.remove(MarginDisplay.LINE_NUMBER);
        }
        lineNumber.setVisible(toShow.contains(MarginDisplay.LINE_NUMBER));
        stepMarkIcon.setVisible(toShow.contains(MarginDisplay.STEP_MARK));
        // We don't add remove as that can cause issues with mouse handling for context menu, so we just change opacity:
        if (toShow.contains(MarginDisplay.BREAKPOINT_HOVER))
        {
            breakpointIcon.setOpacity(0.3);
        }
        else if (toShow.contains(MarginDisplay.BREAKPOINT))
        {
            breakpointIcon.setOpacity(1);
        }
        else
        {
            breakpointIcon.setOpacity(0);
        }

        Arrays.stream(MarginDisplay.values())
                .filter(marginDisplay -> marginDisplay.pseudoClass != null)
                .forEach(marginDisplay -> JavaFXUtil.setPseudoclass(marginDisplay.pseudoClass, displayItems.contains(marginDisplay), this));
    }

    private Label makeLineNumber()
    {
        Label label = new Label(Integer.toString(lineNumberToDisplay));
        label.setEllipsisString("\u2026");
        label.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        JavaFXUtil.addStyleClass(label, "flow-line-label");
        label.styleProperty().bind(PrefMgr.getEditorFontCSS(PrefMgr.FontCSS.LINE_NUMBER_SIZE_ONLY));
        label.setMouseTransparent(true);
        return label;
    }

    // Red octagon with white STOP on it.  By doing it as a shape rather than
    // image file, we get it looking good on all HiDPI displays.
    private static Node makeBreakpointIcon()
    {
        Node icon = Config.makeStopIcon(false);
        JavaFXUtil.addStyleClass(icon, "moe-breakpoint-icon");
        icon.setMouseTransparent(true);
        return icon;
    }

    private static Node makeStepMarkIcon()
    {
        Shape arrow = Config.makeArrowShape(false);
        JavaFXUtil.addStyleClass(arrow, "moe-step-mark-icon");
        arrow.setMouseTransparent(true);
        return arrow;
    }
}
