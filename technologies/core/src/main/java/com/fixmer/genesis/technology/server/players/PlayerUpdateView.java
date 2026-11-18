package com.fixmer.genesis.technology.server.players;

import java.util.*;
import static com.fixmer.genesis.technology.server.players.PlayerUpdateProtocol.*;

/** Presentation model for a future Genesis screen. Does not send packets, disconnect or restart clients. */
public final class PlayerUpdateView {
    private PlayerUpdateView() {}
    public record View(String stage,String message,long decisionRemainingMillis,int ready,int cohort,int required,
                       boolean offerDecision,boolean disconnectRequested,boolean restartSuggested,boolean connectSuggested,
                       boolean controllerReviewRequired) {}
    public static View forPlayer(State s,UUID player) {
        Player p=s.players().get(player);String stage,message;boolean decision=false,disconnect=false,restart=false,connect=false;
        long remaining=p==null?0:Math.max(0,p.deadline()-s.now());
        if(s.phase()==Phase.CANCELLED){stage="CANCELLED";message="Обновление отменено. Можно продолжить игру в прежней версии.";}
        else if(s.paused()){stage="OWNER_REVIEW";message="Управление обновлением восстанавливается. Ожидаем проверки администратора.";}
        else if(p==null){stage="OFFER_REQUIRED";message="Нужно проверить версию клиента и предложить недостающие файлы.";}
        else if(!p.online()){stage="OFFLINE";message="Соединение прервано. При повторном входе можно продолжить обновление.";}
        else if(p.decision()==Decision.DECLINED){stage="DECLINED";message="Вы отказались от обновления. Установить его можно при следующем входе на сервер.";disconnect=true;}
        else if(p.decision()==Decision.EXPIRED||p.decision()==Decision.PENDING&&remaining==0){stage="DECISION_EXPIRED";message="Время на решение истекло. Обновление будет предложено при следующем входе.";disconnect=true;}
        else if(p.decision()==Decision.PENDING){stage="OFFER";message="Для продолжения игры требуется обновление. Установить или выйти с сервера?";decision=true;}
        else if(p.progress()==Progress.NONE){stage="DOWNLOAD";message="Загружаем файлы обновления. Таймер решения больше не действует.";}
        else if(!s.switchConfirmed()){stage="WAIT_SWITCH";message="Файлы загружены. Ожидаем подтверждения обновления сервера.";}
        else if(p.progress()==Progress.DOWNLOADED){stage="RESTART";message="Переход сервера подтверждён. Можно установить обновление и перезапустить клиент.";restart=true;}
        else if(p.progress()==Progress.RUNTIME_REPORTED){stage="VERIFY_RUNTIME";message="Проверяем версию запущенного клиента.";}
        else if(p.progress()==Progress.CONNECTED){stage="CONNECTED";message="Подключение к обновлённому серверу подтверждено.";}
        else if(!s.serverReady()){stage="WAIT_SERVER";message="Ожидаем готовности обновлённого сервера.";}
        else if(s.gateOpen()){stage="CONNECT";message="Сервер готов. Можно подключаться.";connect=s.canConnect(player);}
        else if(s.frozen()&&s.readyCount()>=s.requiredCount()){stage="WAIT_GATE";message="Группа готова. Ожидаем открытия подключения сервером.";}
        else if(s.frozen()&&s.now()>=s.barrierDeadline()){stage="BARRIER_TIMEOUT";message=s.policy().timeoutPolicy()==TimeoutPolicy.WAIT_FOR_ADMIN&&!s.timeoutApproved()?"Ожидание группы затянулось. Требуется решение администратора.":"Время ожидания группы истекло. Ожидаем открытия подключения для готовых клиентов.";}
        else {stage="WAIT_COHORT";message="Готовы "+s.readyCount()+" из "+s.cohort().size()+"; для начала нужно "+s.requiredCount()+".";}
        return new View(stage,message,remaining,s.readyCount(),s.cohort().size(),s.requiredCount(),decision,disconnect,restart,connect,s.paused()||stage.equals("BARRIER_TIMEOUT")&&s.policy().timeoutPolicy()==TimeoutPolicy.WAIT_FOR_ADMIN&&!s.timeoutApproved());
    }
}
