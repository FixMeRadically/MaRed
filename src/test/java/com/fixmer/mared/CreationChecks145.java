package com.fixmer.mared;
import java.nio.file.*;import java.util.*;import com.fixmer.genesis.technology.creation.*;import com.fixmer.genesis.technology.creation.CreationCatalog145.*;import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.mared.gui2.navigation.SpaceId;import com.fixmer.mared.gui2.genesis.node.*;import com.fixmer.genesis.technology.editor.CodeSyntaxLexer;
public final class CreationChecks145 {
 static int count;static void check(boolean b,String message){if(!b)throw new AssertionError(message);count++;}
 public static void main(String[] args)throws Exception{
  check(Direction.values().length==7,"seven product directions");
  for(Direction direction:Direction.values()){
   var sections=CreationCatalog145.sections(direction);check(!sections.isEmpty()&&sections.stream().map(Section::id).distinct().count()==sections.size(),"unique nested tabs: "+direction);
   check(direction==Direction.SERVERS?sections.stream().noneMatch(s->s.tool()==Tool.SCRIPTS):sections.stream().anyMatch(s->s.tool()==Tool.SCRIPTS),"scripts follow product scope: "+direction);
   check(sections.stream().noneMatch(s->s.tool()==Tool.COMMANDS)||direction==Direction.MAPS,"Commands only belongs to Maps");
   check(SpaceId.forDirection145(direction).direction145()==direction,"Genesis/editor route: "+direction);
   check(CreationCatalog145.section(direction,"missing").equals(sections.get(0)),"old preference fallback");
  }
  check(CreationCatalog145.section(Direction.MODS,"models").accepts("src/main/resources/assets/test/models/block/cube.json"),"models within mod resources");
  check(!CreationCatalog145.section(Direction.MODS,"models").accepts("src/main/java/Main.java"),"model filter excludes unrelated code");
  check(CreationCatalog145.section(Direction.MODS,"models").accepts("assets/test/models"),"empty models folder stays visible");
  check(!CreationCatalog145.section(Direction.DATAPACKS,"scripts").accepts("data/test/recipe/example.json"),"scripts do not duplicate every project file");
  check(CreationCatalog145.section(Direction.MAPS,"scripts").accepts("scripts/events.mr"),"map scripting available");
  check(CreationCatalog145.section(Direction.DATAPACKS,"recipes").accepts("data/test/recipe/example.json"),"singular 1.21 recipe folder");
  var nodes=NodeRegistry.buildAll();check(nodes.size()==8&&nodes.get(0).type()==GenesisNodeData.NodeType.CORE,"Genesis core + seven directions");
  for(var data:nodes){var node=new GenesisNode(data);node.tick(1f,.05f,1f);check(Float.isFinite(node.worldX())&&Float.isFinite(node.worldY())&&Float.isFinite(node.worldZ())&&!node.subParticles().isEmpty(),"animated node: "+data.type());}
  for(Direction direction:Direction.values())check(nodes.stream().anyMatch(n->n.type().name().equals(direction.name())),"Genesis direction: "+direction);
  Path root=Files.createTempDirectory("creation145-");var storage=new SourceProjects(root);
  try{
   storage.create("existing",SourceProjects.Language.JAVA);String original=storage.read("existing","src/Main.java");
   for(Direction direction:Direction.values()){
    String id=direction.id();var made=storage.createContent145(id,direction);check(made.language()==SourceProjects.Language.FILES,"content is not Java: "+id);
    var templates=CreationTemplates145.files(direction);
    for(var file:templates.entrySet()){check(storage.read(id,file.getKey()).equals(file.getValue()),"template written at real root: "+file.getKey());check(storage.files(id).contains(file.getKey()),"template visible");}
    String text=storage.read(id,"README.md");storage.save(id,"README.md",text,text+"edited\n");check(storage.read(id,"README.md").endsWith("edited\n"),"content project save");
    try{storage.createContent145(id,direction);throw new AssertionError("overwrote project");}catch(java.io.IOException expected){count++;}
   }
   Path archive=storage.exportProject("datapacks",Map.of());
   try(var zip=new java.util.zip.ZipFile(archive.toFile())){check(zip.getEntry("pack.mcmeta")!=null,"data pack exports at archive root");check(zip.getEntry("data/mared/function/main.mcfunction")!=null,"function exports at correct 1.21 path");}
   check(storage.read("existing","src/Main.java").equals(original),"old Scripts project unchanged");
   try{storage.createContent145("../escape",Direction.DATAPACKS);throw new AssertionError("escaped root");}catch(java.io.IOException expected){count++;}
   try(var list=Files.list(root)){check(list.noneMatch(p->p.getFileName().toString().startsWith(".create-")),"staging directories cleaned");}
  }finally{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  check(CodeSyntaxLexer.languageFor("shaders/program/world.fsh")==CodeSyntaxLexer.Language.GLSL,"GLSL extension");
  check(CodeSyntaxLexer.lex(CodeSyntaxLexer.Language.GLSL,"uniform vec3 color;",CodeSyntaxLexer.State.NORMAL).tokens().stream().anyMatch(t->t.kind()==CodeSyntaxLexer.Kind.KEYWORD),"GLSL lexical colors");
  check(CodeSyntaxLexer.languageFor("pack.mcmeta")==CodeSyntaxLexer.Language.JSON,"pack metadata colors");
  check(CodeSyntaxLexer.languageFor("mob.bbmodel")==CodeSyntaxLexer.Language.JSON,"Blockbench source JSON colors");
  var javaLines=List.of("run(", "  /* ] */ \"}\")");
  var pairs=com.fixmer.genesis.technology.editor.CodeBrackets.pairs("Main.java",javaLines.size(),javaLines::get);
  check(pairs.equals(List.of(new com.fixmer.genesis.technology.editor.CodeBrackets.Pair(3,javaLines.get(0).length()+javaLines.get(1).length()))),"brackets ignore comments and strings across lines");
  String crowded="name + 1 + ".repeat(700)+"// [";
  var saturated=CodeSyntaxLexer.lex(CodeSyntaxLexer.Language.JAVA,crowded,CodeSyntaxLexer.State.NORMAL);
  check(saturated.truncated()&&saturated.tokens().isEmpty(),"lexer reports token overflow");
  try{
   com.fixmer.genesis.technology.editor.CodeBrackets.pairs("Main.java",1,line->crowded);
   throw new AssertionError("bracket navigation used incomplete tokens");
  }catch(IllegalArgumentException expected){count++;}
  var split=new com.fixmer.genesis.technology.editor.EditorSplitState<String>();
  split.activate("first");split.openRight("second");split.enabled(false);
  check("second".equals(split.active())&&"first".equals(split.right()),"focused document remains open after disabling split");
  for(String lang:List.of("ru","en")){
   check(com.fixmer.mared.technology.scripts.ScriptHelpManual.load(lang).article("creation145").id().equals("creation145"),"creation help in Scripts: "+lang);
   check(com.fixmer.mared.technology.scripts.ScriptHelpManual.loadCommands133(lang).article("creation145").id().equals("creation145"),"creation help in Commands: "+lang);
  }
  System.out.println("CreationChecks145: "+count+" passed");
 }
}
