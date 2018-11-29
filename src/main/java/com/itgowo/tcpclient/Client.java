package com.itgowo.tcpclient;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class Client extends Thread {

    private SocketChannel socketChannel;
    private SocketAddress socketAddress;
    private onMiniKeepAliveClientListener clientListener;
    private boolean isRunning;

    public Client write(byte[] bytes) {
        return write(ByteBuffer.wrap(bytes));
    }

    public Client write(ByteBuffer byteBuffer) {
        return write(new ByteBuffer[]{byteBuffer});
    }

    public Client write(ByteBuffer[] byteBuffers) {
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
            socketChannel = SocketChannel.open();
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
                                clientListener.onConnected();
                            } else {
                                key.cancel();
                                clientListener.onError("SelectionKey cancel", new Exception("requestConnect：cancel"));
                                stopConnect();
                            }
                        }
                        key.interestOps(SelectionKey.OP_WRITE);
                    } else if (key.isReadable()) {
                        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(256);
                        int count = socketChannel.read(byteBuffer);
                        byteBuffer.flip();
                        if (count == -1) {
                            isRunning = false;
                            stopConnect();
                        } else if (count != 0) {
                            clientListener.onReadable(byteBuffer);
                        }
                    } else if (key.isWritable()) {
                        key.interestOps(SelectionKey.OP_READ);
                        clientListener.onWritable();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (clientListener != null) {
                clientListener.onError("", e);
            }
            stopConnect();

        } finally {
            close(socketChannel, selector);
            stopConnect();
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
            clientListener.onStop();
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
            //todo
            clientListener.onError("未完成连接，请求过早", e);
        } else {
            clientListener.onError("", e);
        }
    }
}
