package com.fixmer.mared.technology.catalog;

import com.fixmer.genesis.technology.catalog.CommandTree;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.tree.*;
import java.util.*;

/** Copies structure only. Does not execute commands, requirements or suggestion providers. */
public final class BrigadierCatalog {
    private BrigadierCatalog() {}
    /** Cheap structure probe: no parser examples, serializers, requirements or provider callbacks. */
    public static <S> long structuralStamp(CommandDispatcher<S> dispatcher){
        var seen=Collections.newSetFromMap(new IdentityHashMap<CommandNode<S>,Boolean>());
        var queue=new ArrayDeque<CommandNode<S>>();seen.add(dispatcher.getRoot());queue.add(dispatcher.getRoot());
        long stamp=1;int edges=0;
        while(!queue.isEmpty()){
            var node=queue.removeFirst();
            stamp=31*stamp+System.identityHashCode(node);stamp=31*stamp+node.getName().hashCode();
            stamp=31*stamp+System.identityHashCode(node.getCommand());stamp=31*stamp+System.identityHashCode(node.getRedirect());
            if(node instanceof ArgumentCommandNode<?,?> argument)stamp=31*stamp+System.identityHashCode(argument.getType());
            for(var child:node.getChildren()){
                stamp=31*stamp+System.identityHashCode(child);
                if(++edges>=131072)return stamp;
                if(seen.size()<32768&&seen.add(child))queue.addLast(child);
            }
            var redirect=node.getRedirect();if(redirect!=null&&seen.size()<32768&&seen.add(redirect))queue.addLast(redirect);
        }
        return stamp;
    }
    public static <S> CommandTree snapshot(CommandDispatcher<S> dispatcher){return snapshot(dispatcher,32768,131072);}
    public static <S> CommandTree snapshot(CommandDispatcher<S> dispatcher,java.util.function.Function<ArgumentType<?>,String> metadata){return snapshot(dispatcher,32768,131072,metadata);}
    public static <S> CommandTree snapshot(CommandDispatcher<S> dispatcher,int maxNodes,int maxEdges){return snapshot(dispatcher,maxNodes,maxEdges,null);}
    private static <S> CommandTree snapshot(CommandDispatcher<S> dispatcher,int maxNodes,int maxEdges,java.util.function.Function<ArgumentType<?>,String> metadata) {
        if(maxNodes<1||maxEdges<1)throw new IllegalArgumentException("Positive limits required");
        var ids=new IdentityHashMap<CommandNode<S>,Integer>();var queue=new ArrayDeque<CommandNode<S>>();
        ids.put(dispatcher.getRoot(),0);queue.add(dispatcher.getRoot());
        var nodes=new LinkedHashMap<Integer,CommandTree.Node>();boolean limited=false;int edges=0;
        while(!queue.isEmpty()) {
            CommandNode<S> node=queue.remove();var children=new ArrayList<Integer>();
            for(CommandNode<S> child:node.getChildren()) {
                if(++edges>maxEdges){limited=true;break;}
                Integer id=ids.get(child);
                if(id==null){if(ids.size()>=maxNodes){limited=true;continue;}id=ids.size();ids.put(child,id);queue.add(child);}
                children.add(id);
            }
            Integer redirect=null;
            if(node.getRedirect()!=null) {
                redirect=ids.get(node.getRedirect());
                if(redirect==null){if(ids.size()<maxNodes){redirect=ids.size();ids.put(node.getRedirect(),redirect);queue.add(node.getRedirect());}else limited=true;}
            }
            String type="";List<String> examples=List.of();CommandTree.Kind kind=CommandTree.Kind.LITERAL;
            if(node instanceof RootCommandNode<?>)kind=CommandTree.Kind.ROOT;
            if(node instanceof ArgumentCommandNode<?,?> argument) {
                kind=CommandTree.Kind.ARGUMENT;var parser=argument.getType();type=parser.getClass().getName();
                if(parser instanceof IntegerArgumentType p)type+=" ["+p.getMinimum()+", "+p.getMaximum()+"]";
                else if(parser instanceof LongArgumentType p)type+=" ["+p.getMinimum()+", "+p.getMaximum()+"]";
                else if(parser instanceof FloatArgumentType p)type+=" ["+p.getMinimum()+", "+p.getMaximum()+"]";
                else if(parser instanceof DoubleArgumentType p)type+=" ["+p.getMinimum()+", "+p.getMaximum()+"]";
                else if(parser instanceof StringArgumentType p)type+=" "+p.getType();
                if(metadata!=null)try{String described=metadata.apply(parser);if(described!=null)type=described;}catch(RuntimeException|LinkageError error){type+=" (serializer metadata unavailable)";}
                // getExamples is metadata, never a contextual completion request.
                try{examples=parser.getExamples().stream().filter(Objects::nonNull).limit(16).map(s->s.substring(0,Math.min(256,s.length()))).toList();}
                catch(RuntimeException|LinkageError error){type+=" (examples unavailable)";}
            }
            nodes.put(ids.get(node),new CommandTree.Node(ids.get(node),node.getName(),kind,type,examples,children,redirect,node.getCommand()!=null));
        }
        return new CommandTree(nodes,0,limited);
    }
}
