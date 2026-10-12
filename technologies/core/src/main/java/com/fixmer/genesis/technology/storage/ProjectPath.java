package com.fixmer.genesis.technology.storage;
import java.util.Set;
import java.util.Locale;
/** Portable workspace paths; intentionally separate from Commands repository identifiers. */
public final class ProjectPath {
 private static final Set<String> DOTFILES=Set.of(".gitignore",".gitattributes",".editorconfig",".gitkeep",".github");
 private ProjectPath() {}
 public static boolean valid(String path){
  if(path==null||path.isEmpty()||path.length()>256)return false;
  String[] parts=path.split("/",-1);if(parts.length>16)return false;
  for(String part:parts){
   if(DOTFILES.contains(part))continue;
   if(part.length()>64||!part.equals(part.strip())||!part.matches("[\\p{L}\\p{N}_ -]+(\\.[\\p{L}\\p{N}_ -]+)*"))return false;
   String stem=part.split("\\.",2)[0].strip().toUpperCase(Locale.ROOT);
   if(stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))return false;
  }
  return true;
 }
 public static boolean plainTextDotfile(String path){String leaf=RepositoryPath.leaf(path);return DOTFILES.contains(leaf)&&!leaf.equals(".github");}
}
