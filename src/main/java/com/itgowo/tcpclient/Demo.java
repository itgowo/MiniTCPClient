package com.itgowo.tcpclient;

import com.itgowo.tcp.me.PackageMessage;
import com.itgowo.tcp.nio.PackageMessageForNio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class Demo {
    public static void main(String[] args) {
        testPackageClient();


    }

    public static void testPackage() {
        byte[] a1 = new byte[]{121, 0, 0, 0, 16, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6};
        byte[] a2 = new byte[]{121, 0, 0, 0, 14, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8};


        ByteBuffer b1 = ByteBuffer.allocate(a1.length + a2.length);
        b1.put(a1).put(a2);
        System.out.println(Arrays.toString(b1.array()));

        System.out.println("解码");
        b1.flip();
        PackageMessageForNio p = PackageMessageForNio.getPackageMessage();
        List<PackageMessageForNio> pl = p.packageMessage(b1);
        for (int i = 0; i < pl.size(); i++) {
            System.out.println(pl.get(i));
        }
    }

    public static void testClient() {
        SocketAddress socketAddress = new InetSocketAddress("localhost", 12001);
        TCPClient client = new TCPClient(socketAddress, new onMiniTCPClientListener<ByteBuffer>() {
            @Override
            public void onConnected(TCPClient tcpClient) {
                System.out.println("Demo.onConnected");

            }

            @Override
            public void onReadable(TCPClient tcpClient, ByteBuffer byteBuffer) {
                System.out.println(byteBuffer);
                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onWritable(TCPClient tcpClient) {
                System.out.println("Demo.onWritable");
                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onError(String error, Exception e) {
                System.out.println("error = [" + error + "], e = [" + e + "]");
            }

            @Override
            public void onStop() {
                System.out.println("Demo.onStop");
            }
        });
        client.startConnect();
    }

    public static void testPackageClient() {
        SocketAddress socketAddress = new InetSocketAddress("localhost", 12002);
        TCPClient client = new TCPClient(socketAddress, new onMiniTCPClientListener<PackageMessage>() {
            @Override
            public void onConnected(TCPClient tcpClient) {
                System.out.println("Demo.onConnected");

            }

            @Override
            public void onReadable(TCPClient tcpClient, PackageMessage resultData) {
                System.out.println(resultData);
//                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onWritable(TCPClient tcpClient) {
                System.out.println("Demo.onWritable");
                PackageMessage p = PackageMessage.getPackageMessage();
                p.setType(PackageMessage.TYPE_DYNAMIC_LENGTH).setDataType(3).setData(new byte[]{33});
                byte[] bytes = p.encodePackageMessage().readableBytesArray();
                tcpClient.write(bytes);
            }

            @Override
            public void onError(String error, Exception e) {
                e.printStackTrace();
                System.out.println("error = [" + error + "], e = [" + e + "]");
            }

            @Override
            public void onStop() {
                System.out.println("Demo.onStop");
            }
        });
        client.startConnect();
    }

    public static void testPackageNioClient() {
        SocketAddress socketAddress = new InetSocketAddress("localhost", 12002);
        TCPClient client = new TCPClient(socketAddress, new onMiniTCPClientListener<PackageMessageForNio>() {
            @Override
            public void onConnected(TCPClient tcpClient) {
                System.out.println("Demo.onConnected");

            }

            @Override
            public void onReadable(TCPClient tcpClient, PackageMessageForNio resultData) {
                System.out.println(resultData);
//                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onWritable(TCPClient tcpClient) {
                System.out.println("Demo.onWritable");
                PackageMessageForNio p = PackageMessageForNio.getPackageMessage();
                p.setType(PackageMessageForNio.TYPE_DYNAMIC_LENGTH).setDataType(3).setData(new byte[]{33});
                tcpClient.write(p.encodePackageMessage());
            }

            @Override
            public void onError(String error, Exception e) {
                e.printStackTrace();
                System.out.println("error = [" + error + "], e = [" + e + "]");
            }

            @Override
            public void onStop() {
                System.out.println("Demo.onStop");
            }
        });
        client.startConnect();
    }
}
