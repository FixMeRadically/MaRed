package com.fixmer.mared.technology.runtime;

/** Extract full command lines/whole MR blocks from an existing mixed-file plan. Never expands scope. */
public final class CommandFragment {
    private CommandFragment() {}
    public static String extract(String source,int first,int startColumn,int last,int endColumn) {
        String[] lines=source.split("\n",-1);
        if(first<0||last<first||last>=lines.length||startColumn<0||startColumn>lines[first].length()||endColumn<0||endColumn>lines[last].length())
            throw new IllegalArgumentException("mared.fragment.range");
        if(!lines[first].substring(0,startColumn).isBlank()||!lines[last].substring(endColumn).isBlank())
            throw new IllegalArgumentException("mared.fragment.partial");
        var plan=CommandFilePlan.parse(source);
        var result=new StringBuilder("\n"); // the file-level #persistent marker cannot become fragment metadata
        for(var part:plan.parts()){
            int start=part.line()-1;
            int end=start+(part.script()?part.text().split("\n",-1).length-1:0);
            if(end<first||start>last)continue;
            if(start<first||end>last)throw new IllegalArgumentException("mared.fragment.block");
            result.append(part.text()).append('\n');
        }
        if(result.toString().isBlank())throw new IllegalArgumentException("mared.fragment.empty");
        return result.toString();
    }
}
