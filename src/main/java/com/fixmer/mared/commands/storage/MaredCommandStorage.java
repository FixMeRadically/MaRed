package com.fixmer.mared.commands.storage;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import com.fixmer.genesis.technology.storage.RepositoryPath;
import net.neoforged.fml.loading.FMLPaths;
import com.fixmer.genesis.technology.storage.TextRepository;
import com.fixmer.mared.Mared;
/** Minecraft adapter; all writes require an existing file and an unexpired version ticket. */
public final class MaredCommandStorage {
 private MaredCommandStorage() {}
 public static final int MAX_NAME_LEN=RepositoryPath.MAX_LENGTH;
 private static TextRepository repository;
 private static Path repositoryPath;
 private static synchronized TextRepository repo() {
  Path path=FMLPaths.CONFIGDIR.get().resolve("mared/commands").toAbsolutePath().normalize();
  if(!path.equals(repositoryPath)){repository=new TextRepository(path);repositoryPath=path;}
  return repository;
 }
    public static String validateName(String name) {
        if (name == null || name.isEmpty()) return "empty";
        if (name.length() > MAX_NAME_LEN) return "too_long";
        if (name.indexOf('\\') >= 0 || name.startsWith("/")) return "slash";
        return RepositoryPath.valid(name) ? null : "charset";
    }

    public static boolean isValidName(String name) {
        return validateName(name) == null;
    }


 public static List<TextRepository.Entry> entries(String folder) {try{return repo().entries(folder);}catch(IOException e){throw new UncheckedIOException(e);}}
 public static boolean directoryExists(String folder) {try{return repo().directoryExists(folder);}catch(IOException e){return false;}}
 public static void createDirectory(String folder) throws IOException {repo().createDirectory(folder);}
 public static com.fixmer.genesis.technology.editor.DocumentSearch.Result search(String query,
  com.fixmer.genesis.technology.editor.DocumentSearch.Mode mode, boolean matchCase,
  java.util.function.BooleanSupplier cancelled) throws IOException {
  return com.fixmer.genesis.technology.editor.DocumentSearch.search(repo(),query,mode,matchCase,cancelled);
 }
 public static com.fixmer.genesis.technology.storage.ProjectArchive.Plan projectSnapshot() throws IOException {
  return com.fixmer.genesis.technology.storage.ProjectArchive.snapshot(repo());
 }
 public static List<String> projectConflicts(com.fixmer.genesis.technology.storage.ProjectArchive.Plan plan) throws IOException {
  return repo().previewBatch(plan.documents(),plan.directories());
 }
 public static void importProject(com.fixmer.genesis.technology.storage.ProjectArchive.Plan plan) throws IOException {
  repo().createBatch(plan.documents(),plan.directories());
  com.fixmer.mared.technology.links.ScriptLinkService.changed();
 }
 public static List<String> listCommands(){try{return repo().list();}catch(IOException e){throw new UncheckedIOException(e);}}
 public static boolean exists(String name){if(!isValidName(name))return false;try{return repo().exists(name);}catch(IOException e){return false;}}
 public static String readCommand(String name){try{return repo().read(name);}catch(IOException e){throw new UncheckedIOException(e);}}
 public static TextRepository.Ticket captureWrite(String name,String base) throws IOException{return repo().capture(name,base);}
 public static TextRepository.Ticket captureWrite(String name,String base,String earlierSave) throws IOException{return repo().capture(name,base,earlierSave);}
 public static void invalidateWrite(String name) throws IOException{repo().invalidate(name);}
 public static boolean writeCommand(TextRepository.Ticket ticket,String content){try{boolean changed=ticket.owner().write(ticket,content);if(changed)com.fixmer.mared.technology.links.ScriptLinkService.changed();return changed;}catch(IOException e){Mared.LOGGER.error("Save failed",e);return false;}}
 public static boolean writeCommand(String name,String content){try{boolean changed=repo().write(repo().capture(name),content==null?"":content);if(changed)com.fixmer.mared.technology.links.ScriptLinkService.changed();return changed;}catch(IOException e){Mared.LOGGER.error("Save failed",e);return false;}}
 public static boolean createCommand(String name){return createCommand(name,"// MaRed script\n");}
 public static boolean createCommand(String name,String content){try{boolean changed=repo().create(name,content);if(changed)com.fixmer.mared.technology.links.ScriptLinkService.changed();return changed;}catch(IOException e){Mared.LOGGER.error("Create failed",e);return false;}}
 public static boolean deleteCommand(String name){try{boolean changed=repo().delete(name);if(changed)com.fixmer.mared.technology.links.ScriptLinkService.changed();return changed;}catch(IOException e){Mared.LOGGER.error("Delete failed",e);return false;}}
 public static boolean rename(String from,String to){try{boolean changed=repo().rename(from,to);if(changed)com.fixmer.mared.technology.links.ScriptLinkService.changed();return changed;}catch(IOException e){Mared.LOGGER.error("Rename failed",e);return false;}}
}
