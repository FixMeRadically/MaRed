package com.fixmer.mared.gui2.framework.core;


import java.util.ArrayList;
import java.util.List;



public abstract class MaredComponent {


    protected MaredBounds bounds =
            new MaredBounds(0,0,0,0);


    protected boolean visible = true;

    protected boolean enabled = true;


    protected final List<MaredComponent> children =
            new ArrayList<>();


    private FocusManager focusManager;

    private PointerCaptureManager pointerManager;



    // ============================================================
    // Children
    // ============================================================


    public void addChild(
            MaredComponent component
    ){

        if(component != null){

            children.add(component);

        }

    }



    public void removeChild(
            MaredComponent component
    ){

        children.remove(component);

    }



    public List<MaredComponent> children(){

        return children;

    }




    // ============================================================
    // Managers
    // ============================================================


    public void attachFocusManager(
            FocusManager manager
    ){

        this.focusManager = manager;

    }



    public void attachPointerManager(
            PointerCaptureManager manager
    ){

        this.pointerManager = manager;

    }




    // ============================================================
    // Focus
    // ============================================================


    public boolean isFocused(){

        return focusManager != null
                &&
                focusManager.isFocused(this);

    }



    public void requestFocus(){

        if(focusManager != null){

            focusManager.request(this);

        }

    }



    public void clearFocus(){

        if(focusManager != null){

            focusManager.clear(this);

        }

    }



    @Deprecated
    public void setFocused(
            boolean focused
    ){

        if(focused){

            requestFocus();

        }
        else{

            clearFocus();

        }

    }



    public boolean focusable(){

        return false;

    }



    public void onFocusGained(){}


    public void onFocusLost(){}




    // ============================================================
    // Pointer
    // ============================================================


    public void capturePointer(
            int button
    ){

        if(pointerManager != null){

            pointerManager.capture(
                    this,
                    button
            );

        }

    }





    public void releasePointer(){

        if(pointerManager != null){

            pointerManager.release(this);

        }

    }





    public boolean hasPointerCapture(){

        return pointerManager != null
                &&
                pointerManager.isCapturedBy(this);

    }





    public boolean hasPointerCapture(
            int button
    ){

        return pointerManager != null
                &&
                pointerManager.isCapturedBy(this)
                &&
                pointerManager.button() == button;

    }





    protected MaredComponent pointerOwner(
            int button
    ){

        if(pointerManager == null){

            return null;

        }


        if(!pointerManager.isCapturedForButton(button)){

            return null;

        }


        return pointerManager.owner();

    }





    // ============================================================
    // Render
    // ============================================================


    public final void render(
            MaredRenderContext context
    ){

        if(!visible){

            return;

        }


        safeRender(context);



        for(MaredComponent child : children){

            child.render(context);

        }

    }



    protected abstract void safeRender(
            MaredRenderContext context
    );





    // ============================================================
    // Layout
    // ============================================================


    public void layout(
            MaredBounds bounds
    ){

        this.bounds = bounds;

    }



    public MaredBounds bounds(){

        return bounds;

    }



    public boolean isVisible(){

        return visible;

    }



    public void setVisible(
            boolean visible
    ){

        this.visible = visible;

    }



    public boolean isEnabled(){

        return enabled;

    }



    public void setEnabled(
            boolean enabled
    ){

        this.enabled = enabled;

    }



    public boolean contains(
            double mouseX,
            double mouseY
    ){

        return bounds.contains(
                mouseX,
                mouseY
        );

    }





    // ============================================================
    // Input
    // ============================================================


    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ){

        return false;

    }



    public boolean mouseClicked(
            double mouseX,
            double mouseY
    ){

        return mouseClicked(
                mouseX,
                mouseY,
                0
        );

    }





    public boolean mousePressed(
            double mouseX,
            double mouseY,
            int button
    ){

        return false;

    }





    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ){

        return false;

    }





    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ){

        return false;

    }





    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ){

        return false;

    }





    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ){

        return false;

    }





    public boolean charTyped(
            char codePoint,
            int modifiers
    ){

        return false;

    }





    public void mouseMoved(
            double mouseX,
            double mouseY
    ){

    }


}