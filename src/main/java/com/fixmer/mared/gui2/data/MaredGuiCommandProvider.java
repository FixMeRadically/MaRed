package com.fixmer.mared.gui2.data;


import java.util.List;


import com.fixmer.mared.commands.registry.MaredCommandRegistry;



/**
 * Прослойка между GUI и системой команд MaRed.
 *
 * GUI никогда не работает напрямую
 * с Registry.
 */
public final class MaredGuiCommandProvider {


    private MaredGuiCommandProvider(){}



    public static void initialize(){

        MaredCommandRegistry.load();

    }



    public static List<MaredCommandRegistry.CommandInfo> commands(){

        return MaredCommandRegistry.all();

    }



    public static MaredCommandRegistry.CommandInfo find(
            String name
    ){

        return MaredCommandRegistry.findByName(name);

    }



    public static List<String> categories(){

        return MaredCommandRegistry.categories();

    }



    public static List<MaredCommandRegistry.CommandInfo> search(
            String query
    ){

        return MaredCommandRegistry.search(query);

    }

}