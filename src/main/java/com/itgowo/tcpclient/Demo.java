package com.itgowo.tcpclient;

import com.itgowo.tcp.nio.PackageMessageForNio;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class Demo {
    public static void main(String[] args) {
//        ByteBuffer byteBuffer=ByteBuffer.allocate(15);
//        byteBuffer.position(3);
//        byteBuffer.put((byte) 11);
//        byteBuffer.put((byte) 22);
//        byteBuffer.put((byte) 33);
//        byteBuffer.put((byte) 44);
//        byteBuffer.put((byte) 55);
//        byteBuffer.put((byte) 66);
//        byteBuffer.put((byte) 77);
//        byteBuffer.put((byte) 88);
//        byteBuffer.put((byte) 99);
//        byteBuffer.put((byte) 100);
//        System.out.println(Arrays.toString(byteBuffer.array()));
//        byteBuffer.flip();
//        byteBuffer.get();
//        byteBuffer.get();
//        byteBuffer.get();
//        System.out.println(byteBuffer);
//        System.out.println(byteBuffer.compact());
//        byteBuffer.flip();
//        System.out.println(byteBuffer);
//        System.out.println(Arrays.toString(byteBuffer.array()));

        byte[] a1 = new byte[]{121, 0, 0, 0, 16, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6};
        byte[] a2 = new byte[]{121, 0, 0, 0, 12, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8};


        ByteBuffer b1 = ByteBuffer.allocate(a1.length+a2.length);
        b1.put(a1).put(a2);
        System.out.println(Arrays.toString(b1.array()));

        System.out.println("解码");
        b1.flip();
        PackageMessageForNio p=PackageMessageForNio.getPackageMessage();
        List<PackageMessageForNio> pl = p.packageMessage(b1);
        for (int i = 0; i < pl.size(); i++) {
            System.out.println(pl.get(i));
        }
    }
}
