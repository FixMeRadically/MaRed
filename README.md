# MaRed / Genesis 0.4.0

Supervisor performance stage: [docs/PERFORMANCE_05_RU.md](docs/PERFORMANCE_05_RU.md). Includes mod-cycle-04; see the new guide for protocol 4, watch, timings and optional tuning.

Native mod import/edit/build/apply/export cycle (singleplayer): [docs/MOD_CYCLE_04_RU.md](docs/MOD_CYCLE_04_RU.md). Includes supervisor stable-03 fixes.

Development of 0.4.0 starts with Genesis stage 7.1. Release tracking: [docs/RELEASE_0_4_0_TRACKING_RU.md](docs/RELEASE_0_4_0_TRACKING_RU.md). Publication instructions will be prepared at the project owner's final request.


Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/


## Supervisor / Dev Mode

Режим разработки включается автоматически с перезапуском клиента после успешного «Применить на сервере». Перед рестартом сохраняются recovery-черновики редактора; возврат выполняется в тот же мир/сервер. Служебных кнопок на главном экране и F9 нет.

Карта кода, runtime-файлы, ограничения integrated server и проверки: [docs/SUPERVISOR_STABLE_03_RU.md](docs/SUPERVISOR_STABLE_03_RU.md).
