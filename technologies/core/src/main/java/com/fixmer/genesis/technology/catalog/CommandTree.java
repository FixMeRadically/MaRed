package com.fixmer.genesis.technology.catalog;

import java.util.*;

/** Immutable graph of command metadata. No parsers, callbacks, game objects or execution capabilities. */
public final class CommandTree {
    public enum Kind { ROOT, LITERAL, ARGUMENT }
    public record Node(int id,String name,Kind kind,String type,List<String> examples,
                       List<Integer> children,Integer redirect,boolean executable) {
        public Node { Objects.requireNonNull(name);Objects.requireNonNull(kind);examples=List.copyOf(examples);children=List.copyOf(children); }
        public String token(){return kind==Kind.ARGUMENT?"<"+name+">":name;}
    }
    public record Parameter(String name,String type,String path,List<String> examples) {
        public Parameter { examples=List.copyOf(examples); }
    }
    public record Detail(List<String> usages,List<Parameter> parameters,List<String> redirects,boolean truncated) {
        public Detail { usages=List.copyOf(usages);parameters=List.copyOf(parameters);redirects=List.copyOf(redirects); }
    }
    private record Step(int id,String path,Set<Integer> ancestors,int depth) {}
    private final Map<Integer,Node> nodes;
    private final int root;
    private final boolean truncated;
    public CommandTree(Map<Integer,Node> nodes,int root,boolean truncated) {
        this.nodes=Collections.unmodifiableMap(new LinkedHashMap<>(nodes));this.root=root;this.truncated=truncated;
        if(!this.nodes.containsKey(root))throw new IllegalArgumentException("Missing root");
        for(var e:this.nodes.entrySet())if(e.getKey()!=e.getValue().id())throw new IllegalArgumentException("Node id mismatch");
    }
    public static CommandTree empty(){return new CommandTree(Map.of(0,new Node(0,"",Kind.ROOT,"",List.of(),List.of(),null,false)),0,false);}
    public Map<Integer,Node> nodes(){return nodes;}
    public boolean truncated(){return truncated;}
    public List<String> names(){return nodes.get(root).children().stream().map(nodes::get).filter(Objects::nonNull).map(Node::name).sorted().toList();}
    public Detail describe(String name){return describe(name,256,8192,64);}
    public Detail describe(String name,int maxUsages,int maxVisits,int maxDepth) {
        if(maxUsages<1||maxVisits<1||maxDepth<1)throw new IllegalArgumentException("Positive limits required");
        Node command=nodes.get(root).children().stream().map(nodes::get).filter(Objects::nonNull).filter(n->n.name.equals(name)).findFirst().orElse(null);
        if(command==null)return new Detail(List.of(),List.of(),List.of(),truncated);
        var usages=new LinkedHashSet<String>();var parameters=new LinkedHashMap<String,Parameter>();var redirects=new LinkedHashSet<String>();
        var todo=new ArrayDeque<Step>();todo.push(new Step(command.id,command.token(),Set.of(),0));
        int visits=0;boolean limited=truncated;
        while(!todo.isEmpty()) {
            if(++visits>maxVisits||usages.size()>=maxUsages){limited=true;break;}
            Step step=todo.pop();Node node=nodes.get(step.id);
            if(node==null||step.depth>maxDepth||step.ancestors.contains(step.id)){limited=true;continue;}
            if(node.kind==Kind.ARGUMENT){
                if(parameters.size()<256)parameters.putIfAbsent(step.path,new Parameter(node.name,node.type,step.path,node.examples));
                else limited=true;
            }
            if(node.executable)usages.add(step.path);
            var trail=new HashSet<>(step.ancestors);trail.add(node.id);
            if(node.redirect!=null) {
                Node target=nodes.get(node.redirect);
                redirects.add(step.path+" → "+(target==null?"?":target.kind==Kind.ROOT?"root":target.name));
                if(target==null){limited=true;continue;}
                if(target.kind==Kind.ROOT){usages.add(step.path+" …");continue;}
                // A redirect consumes this node's token, then parses the target's children.
                if(trail.contains(target.id)){limited=true;continue;}
                trail.add(target.id);
                if(target.children.isEmpty()&&!node.executable)usages.add(step.path+" …");
                pushChildren(todo,target,step.path,trail,step.depth+1);
            }else pushChildren(todo,node,step.path,trail,step.depth);
        }
        return new Detail(new ArrayList<>(usages),new ArrayList<>(parameters.values()),new ArrayList<>(redirects),limited);
    }
    private void pushChildren(Deque<Step> todo,Node node,String path,Set<Integer> trail,int depth) {
        for(int i=node.children.size()-1;i>=0;i--){int id=node.children.get(i);Node child=nodes.get(id);todo.push(new Step(id,path+" "+(child==null?"?":child.token()),trail,depth+1));}
    }
}
