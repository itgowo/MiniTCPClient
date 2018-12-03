package com.itgowo.tcpclient;

import com.itgowo.tcp.me.PackageMessage;
import com.itgowo.tcp.nio.PackageMessageForNio;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * testClient() 接收类型为Java Nio的ByteBuffer
 * testPackageClient()接收类型为标准版PackageMessage
 * testPackageNioClient()接收类型为Nio版PackageMessage
 */
public class Demo {
    public static void main(String[] args) {
        testPackageNioClientAutoReConnect();
    }

    /**
     * 测试粘包分包问题
     */
    public static void testPackage() {
        byte[] a1 = new byte[]{121, 0, 0, 0, 16, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6};//完整包
        byte[] a2 = new byte[]{121, 0, 0, 0, 19, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8};//半包
        byte[] a3 = new byte[]{9, 121, 0, 0, 0, 16, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6};//粘包
        byte[] a4 = new byte[]{121, 0, 0, 0, 18, 3, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8};//完整包

        //PackageMessageForNio测试 开始
//        ByteBuffer b1 = ByteBuffer.allocate(a1.length + a2.length + a3.length + a4.length);
//        b1.put(a1).put(a2).put(a3).put(a4);
//        b1.flip();
//        PackageMessageForNio p = PackageMessageForNio.getPackageMessage();
//        List<PackageMessageForNio> pl = p.packageMessage(b1);
        //PackageMessageForNio测试 结束

        //PackageMessage测试 开始
        com.itgowo.tcp.me.ByteBuffer b2 = com.itgowo.tcp.me.ByteBuffer.newByteBuffer();
        b2.writeBytes(a1).writeBytes(a2).writeBytes(a3).writeBytes(a4);
        PackageMessage p = PackageMessage.getPackageMessage();
        List<PackageMessage> pl = p.packageMessage(b2);
        //PackageMessage测试 结束

        for (int i = 0; i < pl.size(); i++) {
            System.out.println(pl.get(i));
        }
    }

    public static void testClient() {
        MiniTCPClient client = new MiniTCPClient("localhost", 12001, new onMiniTCPClientListener<ByteBuffer>() {
            @Override
            public void onConnected(MiniTCPClient tcpClient) {
                System.out.println("Demo.onConnected");

            }

            @Override
            public void onReconnected(MiniTCPClient tcpClient) throws Exception {

            }

            @Override
            public void onOffline(MiniTCPClient tcpClient) throws Exception {
                System.out.println("Demo.onOffline");
            }

            @Override
            public void onReadable(MiniTCPClient tcpClient, ByteBuffer byteBuffer) {
                System.out.println(byteBuffer);
                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onWritable(MiniTCPClient tcpClient) {
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

    /**
     * 测试标准版PackageMessage方案，使用自定义ByteBuffer
     */
    public static void testPackageClient() {
        MiniTCPClient client = new MiniTCPClient("localhost", 12002, new onMiniTCPClientListener<PackageMessage>() {
            @Override
            public void onConnected(MiniTCPClient tcpClient) {
                System.out.println("Demo.onConnected");

            }

            @Override
            public void onReconnected(MiniTCPClient tcpClient) throws Exception {

            }

            @Override
            public void onOffline(MiniTCPClient tcpClient) throws Exception {
                System.out.println("Demo.onOffline");
            }

            @Override
            public void onReadable(MiniTCPClient tcpClient, PackageMessage resultData) {
                System.out.println(resultData);
//                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onWritable(MiniTCPClient tcpClient) {
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

    /**
     * 测试Nio版PackageMessage方案，使用Java Nio的ByteBuffer
     */
    public static void testPackageNioClient() {
        MiniTCPClient client = new MiniTCPClient("localhost", 12002, new onMiniTCPClientListener<PackageMessageForNio>() {
            @Override
            public void onConnected(MiniTCPClient tcpClient) {
                System.out.println("Demo.onConnected");

            }

            @Override
            public void onReconnected(MiniTCPClient tcpClient) throws Exception {

            }

            @Override
            public void onOffline(MiniTCPClient tcpClient) throws Exception {
                System.out.println("Demo.onOffline");
            }

            @Override
            public void onReadable(MiniTCPClient tcpClient, PackageMessageForNio resultData) {
                System.out.println(resultData);
//                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onWritable(MiniTCPClient tcpClient) {
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

    public static void testPackageNioClientAutoReConnect() {
        MiniTCPClient client = new MiniTCPClient("localhost", 12002, new onMiniTCPClientListener<PackageMessageForNio>() {
            @Override
            public void onConnected(MiniTCPClient tcpClient) {
                System.out.println("Demo.onConnected");

            }

            @Override
            public void onReconnected(MiniTCPClient tcpClient) throws Exception {
                System.out.println("Demo.onReconnected   isOffline=" + tcpClient.isOffline() + "   isWritable=" + tcpClient.isWritable());
//                tcpClient.stopConnect();
            }

            @Override
            public void onOffline(MiniTCPClient tcpClient) throws Exception {
                System.out.println("Demo.onOffline ");
            }

            @Override
            public void onReadable(MiniTCPClient tcpClient, PackageMessageForNio resultData) {
                System.out.println(resultData);
//                tcpClient.write("哈哈哈哈哈哈".getBytes());
            }

            @Override
            public void onWritable(MiniTCPClient tcpClient) {
                System.out.println("Demo.onWritable ");
                PackageMessageForNio p = PackageMessageForNio.getPackageMessage();
                p.setType(PackageMessageForNio.TYPE_DYNAMIC_LENGTH).setDataType(3).setData(new byte[]{33});
                tcpClient.write(p.encodePackageMessage());
            }

            @Override
            public void onError(String error, Exception e) {
//                e.printStackTrace();
                System.out.println("error = [" + error + "], e = [" + e + "]");
            }

            @Override
            public void onStop() {
                System.out.println("Demo.onStop");
            }
        });
        client.setAutoReconnect(false);
        client.setSendHeartTimeInterval(5);
        client.setReconnectTimeOut(30 * 1000);
        client.startConnect();
    }
}
