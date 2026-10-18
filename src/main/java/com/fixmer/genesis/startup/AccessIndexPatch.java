package com.fixmer.genesis.startup;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Whole-class hash gates the exact AT 10.0.1 constant pool and original lambda call sites. */
public final class AccessIndexPatch {
    private AccessIndexPatch() {}
    public static final String HASH="829bc76a40546bf39782650b9bb999e269c1cca0fd08d358ea4a5ffb7589091c";
    public static byte[] patch(byte[] original)throws Exception {
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)).equals(HASH))return null;
        ByteBuffer in=ByteBuffer.wrap(original);if(in.getInt()!=0xcafebabe)return null;
        in.position(8);int count=u2(in);String[] strings=new String[count];int[] tags=new int[count];
        for(int i=1;i<count;i++) {
            int tag=u1(in);tags[i]=tag;
            switch(tag) {
                case 1 -> {int start=in.position(),size=u2(in);in.position(start+2+size);try(var utf=new DataInputStream(new ByteArrayInputStream(original,start,size+2))){strings[i]=utf.readUTF();}}
                case 3,4,9,10,11,12,17,18 -> in.position(in.position()+4);
                case 5,6 -> {in.position(in.position()+8);i++;}
                case 7,8,16,19,20 -> in.position(in.position()+2);
                case 15 -> in.position(in.position()+3);
                default -> throw new IOException("Unknown constant pool tag");
            }
        }
        int poolEnd=in.position();
        // The strict hash covers target field/lambda identities as well as code and bootstrap methods.
        if(tags[10]!=9||tags[22]!=9||tags[220]!=18||tags[289]!=18||tags[290]!=18||count>65529)return null;
        in.position(in.position()+6);int interfaces=u2(in);in.position(in.position()+interfaces*2);
        int fields=u2(in);for(int i=0;i<fields;i++)member(in);
        int methods=u2(in),lengthOffset=-1,oldEnd=-1;
        for(int i=0;i<methods;i++) {
            int flags=u2(in);String name=strings[u2(in)],descriptor=strings[u2(in)];int attributes=u2(in);
            for(int a=0;a<attributes;a++) {
                String attribute=strings[u2(in)];int lengthPosition=in.position(),length=in.getInt(),start=in.position();
                if((flags&8)==0&&name.equals("getTransformersForTarget")&&descriptor.equals("(Lorg/objectweb/asm/Type;)Ljava/util/Map;")&&attribute.equals("Code")) {
                    if(lengthOffset!=-1)throw new IOException("Duplicate target method");
                    lengthOffset=lengthPosition;oldEnd=start+length;
                }
                in.position(start+length);
            }
        }
        if(lengthOffset<0)return null;
        ByteArrayOutputStream extra=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(extra)) {
            out.writeByte(1);out.writeUTF("com/fixmer/genesis/startup/AccessIndex");
            out.writeByte(7);out.writeShort(count);
            out.writeByte(1);out.writeUTF("lookup");
            out.writeByte(1);out.writeUTF("(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/util/Map;");
            out.writeByte(12);out.writeShort(count+2);out.writeShort(count+3);
            out.writeByte(10);out.writeShort(count+1);out.writeShort(count+4);
        }
        ByteArrayOutputStream code=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(code)) {
            out.writeByte(0x2a);out.writeByte(0xb4);out.writeShort(10); // this.accessTransformers
            out.writeByte(0x2a);out.writeByte(0xb4);out.writeShort(22); // this.targetedClassCache
            out.writeByte(0x2b); // Type
            for(int index:new int[]{220,289,290}){out.writeByte(0xba);out.writeShort(index);out.writeShort(0);}
            out.writeByte(0xb8);out.writeShort(count+5);out.writeByte(0xb0);
        }
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(body)) {
            out.writeShort(6);out.writeShort(2);out.writeInt(code.size());out.write(code.toByteArray());out.writeShort(0);out.writeShort(0);
        }
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(result)) {
            out.write(original,0,8);out.writeShort(count+6);out.write(original,10,poolEnd-10);out.write(extra.toByteArray());
            out.write(original,poolEnd,lengthOffset-poolEnd);out.writeInt(body.size());out.write(body.toByteArray());out.write(original,oldEnd,original.length-oldEnd);
        }
        return result.toByteArray();
    }
    private static int u1(ByteBuffer in){return Byte.toUnsignedInt(in.get());}
    private static int u2(ByteBuffer in){return Short.toUnsignedInt(in.getShort());}
    private static void member(ByteBuffer in){in.position(in.position()+6);int count=u2(in);for(int i=0;i<count;i++){u2(in);int length=in.getInt();in.position(in.position()+length);}}
}
