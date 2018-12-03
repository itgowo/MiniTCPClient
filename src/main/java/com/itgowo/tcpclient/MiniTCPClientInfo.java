package com.itgowo.tcpclient;

import com.itgowo.tcp.me.PackageMessage;
import com.itgowo.tcp.nio.PackageMessageForNio;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class MiniTCPClientInfo {
    public static final int RESULT_TYPE_NIO = 0;
    public static final int RESULT_TYPE_PACKMESSAGE = 1;
    public static final int RESULT_TYPE_PACKMESSAGE_FOR_NIO = 2;
    protected SocketChannel socketChannel;
    protected Selector selector;
    protected Thread thread;
    protected PackageMessage packageMessageDecoder;
    protected PackageMessageForNio packageMessageForNioDecoder;
    protected int resultType = RESULT_TYPE_NIO;
    protected boolean isOffline = false;
    protected boolean isWritable = false;

    public void close() {
        close(socketChannel, selector);
    }

    /**
     * 内部方法
     * 关闭连接通道
     *
     * @param socketChannel
     * @param selector
     */
    protected void close(SocketChannel socketChannel, Selector selector) {
        if (selector != null) {
            try {
                selector.close();
                selector = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (socketChannel != null) {
            try {
                socketChannel.close();
                socketChannel = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 内部方法
     * 判断服务初始类型，分为返回Java Nio ByteBuffer、PackageMessage和PackageMessageForNio三种
     */
    protected int getResultType(onMiniTCPClientListener clientListener) {
        Type[] types = clientListener.getClass().getGenericInterfaces();
        if (types[0] instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) types[0];
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments[0].equals(PackageMessage.class)) {
                packageMessageDecoder = PackageMessage.getPackageMessage();
                resultType = RESULT_TYPE_PACKMESSAGE;
            } else if (actualTypeArguments[0].equals(PackageMessageForNio.class)) {
                packageMessageForNioDecoder = PackageMessageForNio.getPackageMessage();
                resultType = RESULT_TYPE_PACKMESSAGE_FOR_NIO;
            }
        }
        return resultType;
    }

    public boolean isPackageMessage() {
        return resultType == RESULT_TYPE_PACKMESSAGE;
    }

    public boolean isPackageMessageForNio() {
        return resultType == RESULT_TYPE_PACKMESSAGE_FOR_NIO;
    }

    public boolean isNioByteBuffer() {
        return resultType == RESULT_TYPE_NIO;
    }
}