package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.*;
import java.util.*;
public final class ServerReturnChecks110 {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("110 check "+checks);}
 static RestartReturn108 connecting(){var p=new RestartReturn108();UUID id=UUID.randomUUID();ok(p.begin(id,0));p.answer(id,true,1);p.tick(2,false,false,false);p.tick(1002,false,false,false);ok(p.phase()==RestartReturn108.Phase.CONNECTING);return p;}
 static void loading(){
  var p=connecting();p.loading(1003);ok(p.phase()==RestartReturn108.Phase.LOADING);ok(!p.attemptTimedOut(999999));p.tick(9999,false,false,false);ok(p.phase()==RestartReturn108.Phase.LOADING);ok(!p.loadingSlow(121002));ok(p.loadingSlow(121003));p.continueWaiting(121004);ok(!p.loadingSlow(121005));p.tick(121006,false,true,false);ok(p.phase()==RestartReturn108.Phase.COMPLETE);
  p=connecting();p.loading(1003);p.failed(2000);ok(p.phase()==RestartReturn108.Phase.WAITING);p.tick(2999,false,false,false);ok(p.attempts()==1);p.tick(3000,false,false,false);ok(p.attempts()==2);
  p=connecting();p.pause(2000);ok(!p.attemptTimedOut(999999));p.tick(999999,false,true,false);ok(p.phase()==RestartReturn108.Phase.CONNECTING);p.resume(102000);ok(!p.attemptTimedOut(116001));ok(p.attemptTimedOut(116002));
  p=connecting();p.loading(2000);p.pause(3000);ok(!p.loadingSlow(999999));p.resume(203000);ok(!p.loadingSlow(321999));ok(p.loadingSlow(322000));p.cancel();p.resume(999999);p.tick(999999,false,true,false);ok(p.phase()==RestartReturn108.Phase.CANCELLED);
 }
 static void presentation(){
  for(var phase:RestartReturn108.Phase.values())for(var surface:ReturnPresentation110.Surface.values()){
   ok(!ReturnPresentation110.masks(phase,surface,true,false,false,true));ok(!ReturnPresentation110.masks(phase,surface,true,true,true,true));
   if(surface==ReturnPresentation110.Surface.OTHER||surface==ReturnPresentation110.Surface.COVER||surface==ReturnPresentation110.Surface.NONE)ok(!ReturnPresentation110.masks(phase,surface,true,true,false,true));
   if(Set.of(RestartReturn108.Phase.IDLE,RestartReturn108.Phase.PENDING,RestartReturn108.Phase.CANCELLED,RestartReturn108.Phase.COMPLETE).contains(phase))ok(!ReturnPresentation110.masks(phase,surface,true,true,false,true));
  }
  ok(ReturnPresentation110.masks(RestartReturn108.Phase.CONNECTING,ReturnPresentation110.Surface.OWN_CONNECT,true,true,false,true));
  ok(!ReturnPresentation110.masks(RestartReturn108.Phase.CONNECTING,ReturnPresentation110.Surface.OWN_CONNECT,false,true,false,true));
  ok(ReturnPresentation110.masks(RestartReturn108.Phase.LOADING,ReturnPresentation110.Surface.RECEIVING,true,true,false,true));
  ok(!ReturnPresentation110.masks(RestartReturn108.Phase.LOADING,ReturnPresentation110.Surface.RECEIVING,false,true,false,true));
  ok(ReturnPresentation110.masks(RestartReturn108.Phase.ARMED,ReturnPresentation110.Surface.DISCONNECTED,false,true,false,true));
  ok(!ReturnPresentation110.masks(RestartReturn108.Phase.ARMED,ReturnPresentation110.Surface.DISCONNECTED,false,true,false,false));
 }
 static void layouts(){
  for(int width:new int[]{160,240,320,480,640,960,1920})for(int height:new int[]{90,120,180,240,360,720})for(int requested:new int[]{1,2,3,7})for(int font:new int[]{9,12}){
   var p=ReturnLayout110.of(width,height,requested,font);ok(p.buttonY()>=0&&p.buttonY()+p.buttonHeight()<=height);ok(p.buttonWidth()+2*p.padding()<=width);
   ok(p.titleY()+font<=p.statusY());ok(p.statusY()+p.rows()*(font+2)-2<=p.attemptY());ok(p.attemptY()+font<=p.buttonY());ok(p.rows()>=1&&p.rows()<=requested);
   if(p.radius()>0)ok(p.coreY()+p.radius()*1.35<p.titleY());
  }
 }
 public static void main(String[]args){loading();presentation();layouts();System.out.println("Return110 loading/pause/retry/cancel/presentation-scope/responsive-layout assertions passed: "+checks+"; screen/connection observations are explicit inputs, no Minecraft renderer.");}
}
