/*
 This file is part of the BlueJ program. 
 Copyright (C) 2014,2015,2016,2021,2022,2024 Michael Kölling and John Rosenberg

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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bluej.stride.framedjava.frames;


import java.util.ArrayList;
import java.util.List;

import bluej.Config;
import bluej.stride.framedjava.ast.ExpressionSlotFragment;
import bluej.stride.framedjava.ast.FilledExpressionSlotFragment;
import bluej.stride.framedjava.ast.HighlightedBreakpoint;
import bluej.stride.framedjava.canvases.JavaCanvas;
import bluej.stride.framedjava.elements.CodeElement;
import bluej.stride.framedjava.elements.SwitchElement;
import bluej.stride.framedjava.frames.BreakFrame.BreakEncloser;
import bluej.stride.framedjava.slots.ExpressionSlot;
import bluej.stride.framedjava.slots.FilledExpressionSlot;
import bluej.stride.generic.ExtensionDescription;
import bluej.stride.generic.ExtensionDescription.ExtensionSource;
import bluej.stride.generic.Frame;
import bluej.stride.generic.FrameCanvas;
import bluej.stride.generic.FrameContentRow;
import bluej.stride.generic.FrameCursor;
import bluej.stride.generic.FrameFactory;
import bluej.stride.generic.FrameTypeCheck;
import bluej.stride.generic.InteractionManager;
import bluej.stride.generic.MultiCanvasFrame;
import bluej.stride.operations.PullUpContentsOperation;
import bluej.stride.slots.EditableSlot;
import bluej.stride.slots.SlotLabel;
import bluej.utility.Debug;
import bluej.utility.javafx.JavaFXUtil;
import bluej.utility.javafx.SharedTransition;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Container-block representing a switch-case statement.
 */
public class SwitchFrame extends MultiCanvasFrame
  implements CodeFrame<SwitchElement>, DebuggableParentFrame
{
    private static final String SWITCH_STYLE_PREFIX = "switch-";
    private final ExpressionSlot<FilledExpressionSlotFragment> expression;
    private final JavaCanvas casesCanvas;
    private JavaCanvas defaultCanvas;
    private SwitchElement element;
    private final InteractionManager editor; // Saved for creating default canvas.
    private SlotLabel defaultLabel = new SlotLabel("default");

    /**
     * Default constructor.
     */
    private SwitchFrame(InteractionManager editor)
    {
        super(editor, "switch", SWITCH_STYLE_PREFIX);
        this.editor = editor;

        casesCanvas = new JavaCanvas(editor, this, SWITCH_STYLE_PREFIX, false);
        addCanvas(null, casesCanvas);

        //Parameters
        expression = new FilledExpressionSlot(editor, this, this, getHeaderRow(), SWITCH_STYLE_PREFIX){
            @Override
            @OnThread(Tag.FXPlatform)
            public boolean backspaceAtStart()
            {
                if (isAlmostBlank()) {
                    new PullUpContentsOperation(editor).activate(getParentFrame());
                    return true;
                }
                return super.backspaceAtStart();
            }
        };
        expression.setSimplePromptText("expression");
        setHeaderRow(new SlotLabel("("), expression, new SlotLabel(")"));
        expression.onTextPropertyChange(updateSidebarCurried("switch "));
    }

    public SwitchFrame(InteractionManager editor, ExpressionSlotFragment expression, boolean enabled)
    {
        this(editor);
        this.expression.setText(expression);
        frameEnabledProperty.set(enabled);

        //cherry
        frameName = "switch block"  + this.expression.getScreenreaderText();
        this.expression.setSlotName("switch statement expression");
    }

    @Override
    public String getScreenReaderText(View viewMode) {
        return frameName+" with condition "+ expression.getScreenreaderText();
    }

    //cherry
    /**
     * Get the help text of this frame, to pass to setAccessibilityHelp().
     * Calls the parent frame if there is one, to get the parent's description
     * plus the descriptions of that parent's parents.
     */
    public String getScreenReaderHelp() {
        return "you are " + getParentCanvas().getParentLocationDescription();
    }

    //cherry
    public String getLocationDescription(FrameCanvas c) {
        String expressionStr, text;
        expressionStr = (expression.getText().equals(""))? "blank" : expression.getText();

        text = " in a 'switch' frame for expression " + expressionStr + ",";
        if (getParentCanvas()!=null && getParentCanvas().getParent() != null) {
            text += getParentCanvas().getParentLocationDescription();
        }
        return text;
    }

    public boolean addDefault()
    {
        if (defaultCanvas != null) {
            return false; //Already have one
        }

        defaultCanvas = new JavaCanvas(editor, this, "default-case-", false);
        JavaFXUtil.addStyleClass(defaultLabel, "divider-default-case");
        addCanvas(new FrameContentRow(this, defaultLabel), defaultCanvas);
        defaultCanvas.getFirstCursor().requestFocus();
        editor.modifiedFrame(this, false);
        return true;
    }

    private void removeDefault()
    {
        if (defaultCanvas != null) {
            removeCanvas(defaultCanvas);
            defaultCanvas = null;
            editor.modifiedFrame(this, false);
        }
    }

    public static FrameFactory<SwitchFrame> getFactory()
    {
        return new FrameFactory<SwitchFrame>() {
            @Override
            public SwitchFrame createBlock(InteractionManager editor)
            {
                SwitchFrame switchFrame = new SwitchFrame(editor);
                switchFrame.getFirstInternalCursor().insertBlockAfter(CaseFrame.getFactory().createBlock(editor));
                return switchFrame;
            }

            @Override
            public Class<SwitchFrame> getBlockClass()
            { 
                return SwitchFrame.class;
            }
        };
    }

    @Override
    public void pullUpContents()
    {
        // casesCanvas
        // Make copy because we're about to modify the contents:
        List<Frame> casesFrames = new ArrayList<>(casesCanvas.getBlockContents());
        casesFrames.forEach(c -> casesCanvas.removeBlock(c));
        final List<Frame> contents = new ArrayList<>();
        boolean previousCaseIsEmpty = true;
        for (Frame caseFrame : casesFrames)
        {
            // Add a BlankFrame before each case only if that's not the first case in the switch, that the content isn't empty
            // and that the previous case wasn't empty
            // (note that we already don't have the BreakFrame from the case content when calling getValidPulledStatements())
            List<Frame> caseFrameContent = ((CaseFrame) caseFrame).getValidPulledStatements();
            caseFrameContent.forEach(child -> ((CaseFrame) caseFrame).getCanvas().removeBlock(child));
            if (!previousCaseIsEmpty && caseFrameContent.size() > 0)
                contents.add(new BlankFrame(editor));
            contents.addAll(caseFrameContent);
            previousCaseIsEmpty = contents.isEmpty();
        }
        getCursorBefore().insertFramesAfter(contents);

        if (defaultCanvas != null) {
            // Make copy because we're about to modify the contents:
            List<Frame> defaultContents = new ArrayList<>(defaultCanvas.getBlockContents());
            defaultContents.forEach(c -> defaultCanvas.removeBlock(c));

            // Add a BlankFrame in between (if there were cases and the content of default isn't empty
            // (note that here a default frame isn't empty is something else than a BreakFrame is found)
            if (!previousCaseIsEmpty && defaultContents.stream().anyMatch(f -> !(f instanceof  BreakFrame)))
                defaultContents.add(0, new BlankFrame(editor));

            getCursorBefore().insertFramesAfter(defaultContents);
        }
        //notify the editor that a change has been occurred. That will trigger a file save
        editor.modifiedFrame(this, false);
    }

    public void pullUpInnerCaseContents(CaseFrame frame)
    {
        int index = casesCanvas.getBlockContents().indexOf(frame);
        if (index < 0) {
            throw new IllegalStateException("CaseFrame should be in the casesCanvas");
        }
        else if (index == 0) {
            // TODO waiting a design decision
            Debug.message("pullUpInnerCaseContents @ SwitchFrame: Unimplemented case, waiting a design decision");
        }
        else {
            List<Frame> contents = frame.getValidPulledStatements();
            contents.forEach(child -> frame.getCanvas().removeBlock(child));
            CaseFrame previous = (CaseFrame) casesCanvas.getBlockContents().get(index - 1);
            FrameCursor previousCursor = previous.getLastInternalCursor();
            previousCursor.insertFramesAfter(contents);
            // Add a BlankFrame in between only if the content we pull isn't empty and the destination isn't empty
            // (note that it will already not contain a BreakFrame when we call getValidPulledStatements())
            if(previousCursor.getCursorIndex() > 0 && contents.size() > 0)
                previousCursor.insertBlockAfter(new BlankFrame(getEditor()));
            casesCanvas.removeBlock(frame);
            previousCursor.requestFocus();
        }
        //notify the editor that a change has been occurred. That will trigger a file save
        editor.modifiedFrame(this, false);
    }

    private void pullUpDefaultContents()
    {
        if (defaultCanvas == null) {
            throw new IllegalStateException("Default couldn't be null if this method is invoked.");
        }
        int casesCount = casesCanvas.blockCount();
        if (casesCount == 0) {
            // TODO waiting a design decision
            Debug.message("pullUpDefaultContents @ SwitchFrame: Unimplemented case, waiting a design decision");
        }
        else {
            List<Frame> defaultContents = new ArrayList<>(defaultCanvas.getBlockContents());
            defaultContents.forEach(c -> defaultCanvas.removeBlock(c));

            CaseFrame previous = (CaseFrame) casesCanvas.getBlockContents().get(casesCount - 1);
            FrameCursor previousCursor = previous.getLastInternalCursor();
            previousCursor.insertFramesAfter(defaultContents);
            // Add a BlankFrame in between only if the content we pull isn't empty and the destination isn't empty
            // (here, meaning it has any other frame than a BreakFrame)
            if (previousCursor.getCursorIndex() > 0 && defaultContents.stream().anyMatch(f -> !(f instanceof BreakFrame)))
                previousCursor.insertBlockAfter(new BlankFrame(getEditor()));
            removeDefault();
            previousCursor.requestFocus();
        }
        //notify the editor that a change has been occurred. That will trigger a file save
        editor.modifiedFrame(this, false);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public HighlightedBreakpoint showDebugAtEnd(DebugInfo debug)
    {
        return getCasesCanvas().showDebugBefore(null, debug);
    }

    @Override
    public void regenerateCode()
    {
        List<CodeElement> casesContents = new ArrayList<>();
        casesCanvas.getBlocksSubtype(CodeFrame.class).forEach(f -> {
            f.regenerateCode();
            casesContents.add(f.getCode());
        });

        List<CodeElement> defaultContents = null;
        if (defaultCanvas != null) {
            defaultContents = new ArrayList<>();
            for (CodeFrame<?> f : defaultCanvas.getBlocksSubtype(CodeFrame.class)) {
                f.regenerateCode();
                defaultContents.add(f.getCode());
            }
        }
        element = new SwitchElement(this, expression.getSlotElement(), casesContents, defaultContents, frameEnabledProperty.get());
    }

    @Override
    public SwitchElement getCode()
    {
        return element;
    }

    @Override
    public BreakEncloser asBreakEncloser()
    {
        return BreakEncloser.SWITCH;
    }

    public JavaCanvas getCasesCanvas()
    {
        return casesCanvas;
    }

    public JavaCanvas getDefaultCanvas()
    {
        return defaultCanvas;
    }

    public DebuggableParentFrame getCasesDebug()
    {
        return new DebuggableParentFrame() {

            @Override
            @OnThread(Tag.FXPlatform)
            public HighlightedBreakpoint showDebugBefore(DebugInfo debug)
            {
                return ((JavaCanvas) getParentCanvas()).showDebugBefore(SwitchFrame.this, debug);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public HighlightedBreakpoint showDebugAtEnd(DebugInfo debug)
            {
                return getCasesCanvas().showDebugBefore(null, debug);
            }

            @Override
            public FrameCanvas getParentCanvas()
            {
                //TODO is this right?
                return SwitchFrame.this.getParentCanvas();
            }
        };
    }

    public DebuggableParentFrame getDefaultDebug()
    {
        return new DebuggableParentFrame() {

            @Override
            @OnThread(Tag.FXPlatform)
            public HighlightedBreakpoint showDebugBefore(DebugInfo debug)
            {
                // TODO check it
                return ((JavaCanvas)getParentCanvas()).showDebugBefore(SwitchFrame.this, debug);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public HighlightedBreakpoint showDebugAtEnd(DebugInfo debug)
            {
                // TODO check it
                return getDefaultCanvas().showDebugBefore(null, debug);
            }

            @Override
            public FrameCanvas getParentCanvas()
            {
                //TODO is this right?
                return SwitchFrame.this.getParentCanvas();
            }
        };
    }

    @Override
    public boolean focusWhenJustAdded()
    {
        expression.requestFocus();
        return true;
    }

    // TODO AA fix it
//    @Override
//    public void focusUp(HeaderItem src, boolean toEnd)
//    {
//        casesCanvas.getLastCursor().requestFocus();
//    }

    // TODO AA fix it
//    @Override
//    public void focusDown(HeaderItem src)
//    {
//        casesCanvas.getFirstCursor().requestFocus();
//    }

    /*
    // TODO AA fix it
    @Override
    public Stream<EditableSlot> getHeaderItems()
    {
        Stream<EditableSlot> s = Stream.concat(casesCanvas.getHeaderItems(), Stream.of(exceptions));
        s = Stream.concat(s, catchCanvas.getHeaderItems());
        return (defaultCanvas == null) ? s : Stream.concat(s, defaultCanvas.getHeaderItems());
    }
    */

    @Override
    public FrameTypeCheck check(FrameCanvas canvas)
    {
        if (canvas == casesCanvas)
        {
            return new FrameTypeCheck() {
                @Override
                public boolean canInsert(StrideCategory category)
                {
                    return category == StrideCategory.CASE;
                }

                @Override
                public boolean canPlace(Class<? extends Frame> type)
                {
                    return type.equals(CaseFrame.class);
                }
            };
        }
        else if (canvas == defaultCanvas)
        {
            return StrideDictionary.checkStatement();
        }
        else
        {
            throw new IllegalStateException("Asking about unknown child of SwitchFrame");
        }
    }

    @Override
    public List<ExtensionDescription> getAvailableExtensions(FrameCanvas canvas, FrameCursor cursorInCanvas)
    {
        List<ExtensionDescription> result = new ArrayList<>(super.getAvailableExtensions(canvas, cursorInCanvas));

        if (defaultCanvas == null) {
            result.add(new ExtensionDescription(StrideDictionary.DEFAULT_EXTENSION_CHAR,
                    "Add default", SwitchFrame.this::addDefault, true, ExtensionSource.INSIDE_FIRST, ExtensionSource.INSIDE_LATER, ExtensionSource.AFTER));
        }

        if (canvas == casesCanvas)
        {
            result.add(new ExtensionDescription('\b', Config.getString("frame.switch.remove.switch"),
                    () -> new PullUpContentsOperation(editor).activate(getFrame()), false, ExtensionSource.INSIDE_FIRST));
        }
        if (defaultCanvas != null && canvas == defaultCanvas)
        {
            result.add(new ExtensionDescription('\b', Config.getString("frame.switch.remove.default"),
                    SwitchFrame.this::pullUpDefaultContents, false, ExtensionSource.INSIDE_FIRST));
        }

        return result;
    }

    public boolean isAlmostBlank()
    {
        return getEditableSlotsDirect().allMatch(EditableSlot::isAlmostBlank) &&
                defaultCanvas == null &&
                (casesCanvas.blockCount() == 0 || (casesCanvas.blockCount() == 1 && casesCanvas.getBlockContents().get(0).isAlmostBlank()));
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void setView(View oldView, View newView, SharedTransition animate)
    {
        super.setView(oldView, newView, animate);
        JavaFXUtil.setPseudoclass("bj-java-preview", newView == View.JAVA_PREVIEW, sidebar.getStyleable());

        boolean java = newView == View.JAVA_PREVIEW;
        if (isFrameEnabled() && (java || oldView == View.JAVA_PREVIEW))
        {
            if (defaultCanvas != null) {
                casesCanvas.previewCurly(java, true, false, header.getLeftFirstItem(), null, animate);
                defaultCanvas.previewCurly(java, false, true, header.getLeftFirstItem(), null, animate);
            }
            else {
                casesCanvas.previewCurly(java, header.getLeftFirstItem(), null, animate);
            }
        }

        getCanvases().forEach(c -> {
            c.getCursors().forEach(cur -> cur.setView(newView, animate));
            c.setView(oldView, newView, animate);
        });

        defaultLabel.setText(newView == View.JAVA_PREVIEW ? "default :" : "default");
    }


    //manvi
    @Override
    public void updateAppearance(FrameCanvas parentCanvas)
    {
        super.updateAppearance(parentCanvas);
        if(getParentCanvas() != null && getParentCanvas().getParent() != null)
        {
            expression.setAccessibilityHelpSlots();
        }
    }

    //Manvi jain
    @Override
    public String getHelpContext()
    {
        String parent = "";
        if(getParentCanvas() != null && getParentCanvas().getParent() != null)
        {
            parent = getParentCanvas().getParent().getHelpContext();
        }
        return "in switch statement " + parent;
    }
}
