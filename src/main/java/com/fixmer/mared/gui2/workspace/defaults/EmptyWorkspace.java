package com.fixmer.mared.gui2.workspace.defaults;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;
/** Honest placeholder for category routes with no editor registered. */
public final class EmptyWorkspace extends MaredComponent {
 @Override protected void safeRender(MaredRenderContext ctx){
  var g=ctx.graphics();int x=bounds.x()+12,y=bounds.y()+16,w=Math.max(1,bounds.width()-24);
  g.enableScissor(bounds.x(),bounds.y(),Math.max(bounds.x(),bounds.right()),Math.max(bounds.y(),bounds.bottom()));
  try{y=TextUtils.wrapped(g,ctx.font(),MaredLang.get("mared.workspace.not_ready"),x,y,w,text())+10;
   TextUtils.wrapped(g,ctx.font(),MaredLang.get("mared.workspace.go_logic"),x,y,w,dim());
  }finally{g.disableScissor();}
 }
}
