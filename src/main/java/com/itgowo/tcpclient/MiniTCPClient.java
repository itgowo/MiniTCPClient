package com.itgowo.tcpclient;

import com.itgowo.tcp.me.PackageMessage;
import com.itgowo.tcp.nio.PackageMessageForNio;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.List;

public class MiniTCPClient extends Thread {
    public static final int RESULT_TYPE_NIO = 0;
    public static final int RESULT_TYPE_PACKMESSAGE = 1;
    public static final int RESULT_TYPE_PACKMESSAGE_FOR_NIO = 2;
    private SocketChannel socketChannel;
    private onMiniTCPClientListener clientListener;
    private boolean isRunning;
    private int BufferSize = 1024;
    private int resultType = RESULT_TYPE_NIO;
    private PackageMessage packageMessageDecoder;
    private PackageMessageForNio packageMessageForNioDecoder;
    private String remoteServerAddress;
    private int remoteServerPort;

    public MiniTCPClient(String remoteServerAddress, int remoteServerPort, onMiniTCPClientListener clientListener) {
        this.remoteServerAddress = remoteServerAddress;
        this.remoteServerPort = remoteServerPort;
        this.clientListener = clientListener;
        getResultType();
        setName("MiniTCPClient");
    }

    private void getResultType() {
        Type[] types = clientListener.getClass().getGenericInterfaces();
        if (types[0] instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) types[0];
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments[0].getTypeName().equals(PackageMessage.class.getTypeName())) {
                packageMessageDecoder = PackageMessage.getPackageMessage();
                resultType = RESULT_TYPE_PACKMESSAGE;
            } else if (actualTypeArguments[0].getTypeName().equals(PackageMessageForNio.class.getTypeName())) {
                packageMessageForNioDecoder = PackageMessageForNio.getPackageMessage();
                resultType = RESULT_TYPE_PACKMESSAGE_FOR_NIO;
            }
        }
    }

    public int getBufferSize() {
        return BufferSize;
    }

    public MiniTCPClient setBufferSize(int bufferSize) {
        BufferSize = bufferSize;
        return this;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public MiniTCPClient write(byte[] bytes) {
        return write(ByteBuffer.wrap(bytes));
    }

    public MiniTCPClient write(ByteBuffer byteBuffer) {
        return write(new ByteBuffer[]{byteBuffer});
    }

    public MiniTCPClient write(ByteBuffer[] byteBuffers) {
        try {
            socketChannel.write(byteBuffers);
        } catch (IOException e) {
            dispatcherError(e);
        }
        return this;
    }

    @Override
    public synchronized void start() {
        startConnect();
    }

    public synchronized void startConnect() {
        super.start();
    }

    @Override
    public void run() {
        Selector selector = null;
        try {
            SocketAddress socketAddress = new InetSocketAddress(remoteServerAddress, remoteServerPort);
            socketChannel = SocketChannel.open();
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, BufferSize);
            socketChannel.configureBlocking(false);
            socketChannel.connect(socketAddress);
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
            isRunning = true;
            while (isRunning) {
                selector.select();
                Iterator ite = selector.selectedKeys().iterator();
                while (ite.hasNext()) {
                    SelectionKey key = (SelectionKey) ite.next();
                    ite.remove();
                    if (key.isConnectable()) {
                        if (socketChannel.isConnectionPending()) {
                            if (socketChannel.finishConnect()) {
                                //只有当连接成功后才能注册OP_READ事件
                                key.interestOps(SelectionKey.OP_WRITE);
                                clientListener.onConnected(this);
                            } else {
                                key.cancel();
                            }
                        }
                        key.interestOps(SelectionKey.OP_WRITE);
                    } else if (key.isReadable()) {
                        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(BufferSize);
                        int count = socketChannel.read(byteBuffer);
                        byteBuffer.flip();
                        if (count == -1) {
                            isRunning = false;
                        } else if (count != 0) {
                            onReceivedMessage(this, byteBuffer);
                        }
                    } else if (key.isWritable()) {
                        key.interestOps(SelectionKey.OP_READ);
                        clientListener.onWritable(this);
                    }
                }
            }
        } catch (Exception e) {
            dispatcherError(e);
        } finally {
            close(socketChannel, selector);
            clientListener.onStop();
            stopConnect();
        }
    }

    private void onReceivedMessage(MiniTCPClient tcpClient, ByteBuffer byteBuffer) {
        if (resultType == RESULT_TYPE_PACKMESSAGE) {
            List<PackageMessage> list = packageMessageDecoder.getPackageMessage().packageMessage(com.itgowo.tcp.me.ByteBuffer.newByteBuffer().writeBytes(byteBuffer.array(), byteBuffer.limit()));
            for (PackageMessage p : list) {
                clientListener.onReadable(tcpClient, p);
            }
        } else if (resultType == RESULT_TYPE_PACKMESSAGE_FOR_NIO) {
            List<PackageMessageForNio> list = packageMessageForNioDecoder.getPackageMessage().packageMessage(byteBuffer);
            for (PackageMessageForNio nio : list) {
                clientListener.onReadable(tcpClient, nio);
            }
        } else {
            clientListener.onReadable(tcpClient, byteBuffer);
        }
    }

    public void stopConnect() {
        isRunning = false;
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socketChannel = null;
        }
    }

    private void close(SocketChannel socketChannel, Selector selector) {
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void dispatcherError(Exception e) {
        if (e instanceof NotYetConnectedException) {
            clientListener.onError("未完成连接，请求过早", e);
        } else if (e instanceof ConnectException) {
            clientListener.onError("连接失败", e);
            stopConnect();
        } else if (e instanceof AsynchronousCloseException) {
            clientListener.onError("连接被关闭", e);
            stopConnect();
        } else if (e instanceof ClosedByInterruptException) {
            clientListener.onError("连接被中断", e);
            stopConnect();
        } else if (e instanceof ClosedChannelException) {
            clientListener.onError("连接通道被关闭", e);
            stopConnect();
        } else if (e instanceof NoConnectionPendingException) {
            clientListener.onError("连接尚未成功，连接成功前不可以操作", e);
        } else {
            clientListener.onError("", e);
        }

    }
}
