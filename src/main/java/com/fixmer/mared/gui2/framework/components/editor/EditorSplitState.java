package com.fixmer.mared.gui2.framework.components.editor;

import java.util.Collection;
import java.util.Objects;

/** Two editor groups. A document's view/history can belong to only one visible group. */
public final class EditorSplitState<K> {
    private K left,right;
    private boolean enabled,rightFocused;
    public K left(){return left;}
    public K right(){return right;}
    public boolean rightFocused(){return enabled&&rightFocused;}
    public K active(){return rightFocused()?right:left;}
    public void enabled(boolean value){
        if(enabled&&!value&&rightFocused&&right!=null){K swap=left;left=right;right=swap;}
        enabled=value;if(!value)rightFocused=false;
    }
    public void focus(boolean value){rightFocused=enabled&&value;}
    public void activate(K key){
        if(key!=null&&Objects.equals(key,left)){rightFocused=false;return;}
        if(key!=null&&Objects.equals(key,right)){if(enabled)rightFocused=true;else{right=left;left=key;rightFocused=false;}return;}
        if(rightFocused())right=key;else left=key;
    }
    public void openRight(K key){
        enabled=true;
        if(key!=null&&Objects.equals(key,left)){left=right;right=null;}
        right=key;rightFocused=true;
    }
    public void retain(Collection<K> keys){
        if(left!=null&&!keys.contains(left))left=null;
        if(right!=null&&!keys.contains(right))right=null;
    }
    public void rename(K from,K to){if(Objects.equals(left,from))left=to;if(Objects.equals(right,from))right=to;}
    public static int divider(int width,float ratio){
        if(!Float.isFinite(ratio))ratio=.5f;
        int margin=Math.min(90,Math.max(1,(width-5)/2));
        return Math.max(margin,Math.min(width-margin-5,Math.round((width-5)*ratio)));
    }
}
