package com.fixmer.genesis.startup;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Exact upstream class only. Instruction sizes, offsets and stack maps stay unchanged. */
public final class RootPatch {
    private RootPatch() {}
    public static final String HASH = "764e9386bbec4e7869c11f120e56a2db64de5abb1eb20ea0a98e2655ebe01a5d";
    public static byte[] patch(byte[] original) throws Exception {
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)).equals(HASH)) return null;
        ByteBuffer in = ByteBuffer.wrap(original);
        if (in.getInt() != 0xcafebabe) return null;
        in.position(8); int count = u2(in); String[] strings = new String[count];
        for (int index = 1; index < count; index++) {
            int tag = u1(in);
            switch (tag) {
                case 1 -> { int start = in.position(), size = u2(in); in.position(start + 2 + size);
                    try (var utf = new DataInputStream(new ByteArrayInputStream(original, start, size + 2))) { strings[index] = utf.readUTF(); } }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.position(in.position() + 4);
                case 5, 6 -> { in.position(in.position() + 8); index++; }
                case 7, 8, 16, 19, 20 -> in.position(in.position() + 2);
                case 15 -> in.position(in.position() + 3);
                default -> throw new IOException("Unknown constant pool tag");
            }
        }
        int poolEnd = in.position();
        in.position(in.position() + 6); int interfaces = u2(in); in.position(in.position() + interfaces * 2);
        int fields = u2(in); for (int i = 0; i < fields; i++) member(in);
        int methods = u2(in), target = -1;
        for (int i = 0; i < methods; i++) {
            int flags = u2(in); String name = strings[u2(in)], descriptor = strings[u2(in)]; int attributes = u2(in);
            for (int a = 0; a < attributes; a++) {
                String attribute = strings[u2(in)]; int length = in.getInt(), start = in.position();
                if ((flags & 8) != 0 && "lambda$findCandidates$3".equals(name)
                        && "(Ljava/nio/file/Path;[Ljava/lang/String;Ljava/lang/String;Ljava/nio/file/Path;)Z".equals(descriptor)
                        && "Code".equals(attribute)) {
                    int code = start + 8;
                    byte[] expected = {0x2d, 0x2a, (byte)0xb9, 0, (byte)193, 2, 0};
                    for (int k = 0; k < expected.length; k++) if (original[code + k] != expected[k]) throw new IOException("Instruction mismatch");
                    if (target != -1) throw new IOException("Duplicate target");
                    target = code + 2;
                }
                in.position(start + length);
            }
        }
        if (target < 0 || count > 65529) return null;
        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(extra)) {
            out.writeByte(1); out.writeUTF("com/fixmer/genesis/startup/RootEquality"); // count
            out.writeByte(7); out.writeShort(count); // count+1
            out.writeByte(1); out.writeUTF("same"); // count+2
            out.writeByte(1); out.writeUTF("(Ljava/nio/file/Path;Ljava/lang/Object;)Z"); // count+3
            out.writeByte(12); out.writeShort(count+2); out.writeShort(count+3); // count+4
            out.writeByte(10); out.writeShort(count+1); out.writeShort(count+4); // count+5
        }
        byte[] body = original.clone();
        body[target] = (byte)0xb8; body[target+1] = (byte)((count+5) >>> 8); body[target+2] = (byte)(count+5);
        body[target+3] = 0; body[target+4] = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream(original.length + extra.size());
        try (DataOutputStream out = new DataOutputStream(result)) {
            out.write(body, 0, 8); out.writeShort(count+6); out.write(body, 10, poolEnd-10);
            out.write(extra.toByteArray()); out.write(body, poolEnd, body.length-poolEnd);
        }
        return result.toByteArray();
    }
    private static int u1(ByteBuffer in) { return Byte.toUnsignedInt(in.get()); }
    private static int u2(ByteBuffer in) { return Short.toUnsignedInt(in.getShort()); }
    private static void member(ByteBuffer in) {
        in.position(in.position()+6); int count=u2(in);
        for(int i=0;i<count;i++){u2(in);int length=in.getInt();in.position(in.position()+length);}
    }
}
