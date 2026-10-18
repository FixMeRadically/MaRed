package com.fixmer.genesis.startup;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Guarded, size-only rewrite of the standard CodepointMap.keySet constructor sequence. */
public final class FontSizingPatch {
    private FontSizingPatch() {}
    private static final String OWNER="net/minecraft/client/gui/font/CodepointMap";
    private static final String SET="it/unimi/dsi/fastutil/ints/IntOpenHashSet";
    private record Cp(int tag,int a,int b,String text) {}
    private static String text(Cp[] pool,int index){return pool[index].text();}
    private static String clazz(Cp[] pool,int index){return text(pool,pool[index].a());}
    private static boolean ref(Cp[] pool,int index,int tag,String owner,String name,String descriptor) {
        Cp value=pool[index];if(value.tag()!=tag)return false;Cp nt=pool[value.b()];
        return clazz(pool,value.a()).equals(owner)&&text(pool,nt.a()).equals(name)&&text(pool,nt.b()).equals(descriptor);
    }
    private static int field(Cp[] pool,String name,String descriptor) {
        for(int i=1;i<pool.length;i++)if(pool[i]!=null&&ref(pool,i,9,OWNER,name,descriptor))return i;
        return -1;
    }
    public static byte[] patch(byte[] original)throws IOException {
        try{return checked(original);}catch(IndexOutOfBoundsException|IllegalArgumentException|NullPointerException|java.nio.BufferUnderflowException malformed){return null;}
    }
    private static byte[] checked(byte[] original)throws IOException {
        ByteBuffer in=ByteBuffer.wrap(original);if(in.getInt()!=0xcafebabe)return null;
        in.position(8);int count=u2(in);if(count>65526)return null;Cp[] pool=new Cp[count];
        for(int i=1;i<count;i++) {
            int tag=u1(in),a=0,b=0;String value=null;
            switch(tag) {
                case 1 -> {int start=in.position(),size=u2(in);in.position(start+2+size);try(var utf=new DataInputStream(new ByteArrayInputStream(original,start,size+2))){value=utf.readUTF();}}
                case 3,4 -> in.position(in.position()+4);
                case 5,6 -> in.position(in.position()+8);
                case 7,8,16,19,20 -> a=u2(in);
                case 9,10,11,12,17,18 -> {a=u2(in);b=u2(in);}
                case 15 -> {a=u1(in);b=u2(in);}
                default -> {return null;}
            }
            pool[i]=new Cp(tag,a,b,value);if(tag==5||tag==6)i++;
        }
        int poolEnd=in.position(),classFlags=u2(in),thisClass=u2(in);u2(in);
        if((classFlags&0x200)!=0||!OWNER.equals(clazz(pool,thisClass)))return null;
        int interfaces=u2(in);in.position(in.position()+interfaces*2);
        boolean blocks=false,empty=false;int fields=u2(in);
        for(int i=0;i<fields;i++) {
            int flags=u2(in);String name=text(pool,u2(in)),descriptor=text(pool,u2(in));
            if((flags&8)==0&&name.equals("blockMap")&&descriptor.equals("[[Ljava/lang/Object;"))blocks=true;
            if((flags&8)==0&&name.equals("empty")&&descriptor.equals("[Ljava/lang/Object;"))empty=true;
            attributes(in);
        }
        if(!blocks||!empty)return null;
        int blocksRef=field(pool,"blockMap","[[Ljava/lang/Object;"),emptyRef=field(pool,"empty","[Ljava/lang/Object;");
        if(blocksRef<0||emptyRef<0)return null;
        int methods=u2(in),lengthOffset=-1,oldEnd=-1;byte[] newCodeAttribute=null;
        ByteArrayOutputStream extra=new ByteArrayOutputStream();
        int newConstructor=count+8;
        try(var out=new DataOutputStream(extra)) {
            out.writeByte(1);out.writeUTF("com/fixmer/genesis/startup/FontSizing");
            out.writeByte(7);out.writeShort(count);
            out.writeByte(1);out.writeUTF("expected");
            out.writeByte(1);out.writeUTF("([[Ljava/lang/Object;[Ljava/lang/Object;)I");
            out.writeByte(12);out.writeShort(count+2);out.writeShort(count+3);
            out.writeByte(10);out.writeShort(count+1);out.writeShort(count+4);
            out.writeByte(1);out.writeUTF("(I)V");
            // Remaining NameAndType and Methodref depend on the matched original constructor.
        }
        for(int m=0;m<methods;m++) {
            int flags=u2(in);String name=text(pool,u2(in)),descriptor=text(pool,u2(in));int attrs=u2(in);
            for(int a=0;a<attrs;a++) {
                int attributeName=u2(in),lengthPosition=in.position(),length=in.getInt(),start=in.position();
                if((flags&8)==0&&name.equals("keySet")&&descriptor.equals("()Lit/unimi/dsi/fastutil/ints/IntSet;")&&text(pool,attributeName).equals("Code")) {
                    if(lengthOffset!=-1)return null;
                    ByteBuffer codeInput=ByteBuffer.wrap(original);codeInput.position(start);
                    int stack=u2(codeInput),locals=u2(codeInput),size=codeInput.getInt();
                    if(size!=20||locals!=2)return null;byte[] code=new byte[size];codeInput.get(code);
                    int[] positions={0,3,4,7,8,9,10,13,14,15,18,19};int[] opcodes={0xbb,0x59,0xb7,0x4c,0x2a,0x2b,0xba,0,0,0xb6,0x2b,0xb0};
                    for(int k=0;k<positions.length;k++)if(Byte.toUnsignedInt(code[positions[k]])!=opcodes[k])return null;
                    int setClass=operand(code,1),constructor=operand(code,5),callback=operand(code,11),forEach=operand(code,16);
                    if(pool[setClass].tag()!=7||!clazz(pool,setClass).equals(SET)||!ref(pool,constructor,10,SET,"<init>","()V")||
                            !ref(pool,forEach,10,OWNER,"forEach","(L"+OWNER+"$Output;)V"))return null;
                    Cp dynamic=pool[callback];if(dynamic.tag()!=18)return null;Cp nt=pool[dynamic.b()];
                    if(!text(pool,nt.a()).equals("accept")||!text(pool,nt.b()).equals("(L"+SET+";)L"+OWNER+"$Output;"))return null;
                    if(u2(codeInput)!=0)return null; // The matched straight-line constructor sequence has no handlers.
                    ByteArrayOutputStream instructions=new ByteArrayOutputStream();
                    try(var out=new DataOutputStream(instructions)) {
                        out.write(code,0,4);
                        out.writeByte(0x2a);out.writeByte(0xb4);out.writeShort(blocksRef);
                        out.writeByte(0x2a);out.writeByte(0xb4);out.writeShort(emptyRef);
                        out.writeByte(0xb8);out.writeShort(count+5);
                        out.writeByte(0xb7);out.writeShort(newConstructor);out.write(code,7,code.length-7);
                    }
                    int delta=instructions.size()-size;
                    ByteArrayOutputStream body=new ByteArrayOutputStream();
                    try(var out=new DataOutputStream(body)) {
                        out.writeShort(Math.max(4,stack));out.writeShort(locals);out.writeInt(instructions.size());out.write(instructions.toByteArray());out.writeShort(0);
                        int nested=u2(codeInput);out.writeShort(nested);
                        for(int n=0;n<nested;n++) {
                            int nestedName=u2(codeInput),nestedLength=codeInput.getInt(),end=codeInput.position()+nestedLength;String kind=text(pool,nestedName);
                            out.writeShort(nestedName);out.writeInt(nestedLength);
                            if(kind.equals("LineNumberTable")) {
                                int entries=u2(codeInput);out.writeShort(entries);for(int e=0;e<entries;e++){out.writeShort(shift(u2(codeInput),delta));out.writeShort(u2(codeInput));}
                            } else if(kind.equals("LocalVariableTable")||kind.equals("LocalVariableTypeTable")) {
                                int entries=u2(codeInput);out.writeShort(entries);
                                for(int e=0;e<entries;e++) {
                                    int begin=u2(codeInput),span=u2(codeInput),newBegin=shift(begin,delta);
                                    out.writeShort(newBegin);out.writeShort(shift(begin+span,delta)-newBegin);out.writeShort(u2(codeInput));out.writeShort(u2(codeInput));out.writeShort(u2(codeInput));
                                }
                            } else if(kind.equals("StackMapTable")&&nestedLength==2) {if(u2(codeInput)!=0)return null;out.writeShort(0);}
                            else return null;
                            if(codeInput.position()!=end)return null;
                        }
                        if(codeInput.position()!=start+length)return null;
                    }
                    try(var out=new DataOutputStream(extra)) {
                        Cp oldConstructor=pool[constructor];int initName=pool[oldConstructor.b()].a();
                        out.writeByte(12);out.writeShort(initName);out.writeShort(count+6);
                        out.writeByte(10);out.writeShort(setClass);out.writeShort(count+7);
                    }
                    lengthOffset=lengthPosition;oldEnd=start+length;newCodeAttribute=body.toByteArray();
                }
                in.position(start+length);
            }
        }
        attributes(in);if(in.hasRemaining())return null;
        if(lengthOffset<0)return null;
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(result)) {
            out.write(original,0,8);out.writeShort(count+9);out.write(original,10,poolEnd-10);out.write(extra.toByteArray());
            out.write(original,poolEnd,lengthOffset-poolEnd);out.writeInt(newCodeAttribute.length);out.write(newCodeAttribute);out.write(original,oldEnd,original.length-oldEnd);
        }
        return result.toByteArray();
    }
    private static int shift(int pc,int delta){return pc>=4?pc+delta:pc;}
    private static int operand(byte[] code,int offset){return (Byte.toUnsignedInt(code[offset])<<8)|Byte.toUnsignedInt(code[offset+1]);}
    private static int u1(ByteBuffer in){return Byte.toUnsignedInt(in.get());}
    private static int u2(ByteBuffer in){return Short.toUnsignedInt(in.getShort());}
    private static void attributes(ByteBuffer in){int count=u2(in);for(int i=0;i<count;i++){u2(in);int length=in.getInt();in.position(in.position()+length);}}
}
