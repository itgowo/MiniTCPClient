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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MiniTCPClient implements Runnable {
    public static final int RESULT_TYPE_NIO = 0;
    public static final int RESULT_TYPE_PACKMESSAGE = 1;
    public static final int RESULT_TYPE_PACKMESSAGE_FOR_NIO = 2;
    public static final int SERVER_STATUS_WAIT = 1;
    public static final int SERVER_STATUS_CONNECTED = 2;
    public static final int SERVER_STATUS_RECONNECTING = 3;
    public static final int SERVER_STATUS_RECONNECTED = 4;
    public static final int SERVER_STATUS_STOP = 5;

    protected SocketChannel socketChannel;
    protected onMiniTCPClientListener clientListener;
    protected int serverStatus;
    protected int BufferSize = 1024;
    protected int resultType = RESULT_TYPE_NIO;
    protected PackageMessage packageMessageDecoder;
    protected PackageMessageForNio packageMessageForNioDecoder;
    protected String remoteServerAddress;
    protected int remoteServerPort;
    protected Thread thread;
    protected boolean daemon;


    private ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(0);
    /**
     * 是否自动重连
     */
    private boolean autoReconnect = false;
    /**
     * 最后一次接收数据时间
     */
    private long lastMsgTime;
    /**
     * 心跳检测间隔时间，单位秒
     */
    private int sendHeartTimeInterval = 30;
    /**
     * 未收到数据间隔时间，超时时间，单位毫秒
     */
    private int reconnectTimeOut = 90 * 000;

    public MiniTCPClient(String remoteServerAddress, int remoteServerPort, onMiniTCPClientListener clientListener) {
        this.remoteServerAddress = remoteServerAddress;
        this.remoteServerPort = remoteServerPort;
        this.clientListener = clientListener;
        getResultType();

    }

    /**
     * 内部方法
     * 判断服务初始类型，分为返回Java Nio ByteBuffer、PackageMessage和PackageMessageForNio三种
     */
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

    /**
     * 获取TCP缓冲区大小
     *
     * @return
     */
    public int getBufferSize() {
        return BufferSize;
    }

    /**
     * 设置TCP缓冲区大小，长连接不适合大数据量传输，请尽量设置在4k以内
     *
     * @param bufferSize
     * @return
     */
    public MiniTCPClient setBufferSize(int bufferSize) {
        BufferSize = bufferSize;
        return this;
    }

    /**
     * 服务是否正常运行，只要不是stop状态都认为是正在运行，断线重连也算正在运行。
     *
     * @return
     */
    public boolean isRunning() {
        return serverStatus != SERVER_STATUS_STOP;
    }

    /**
     * 发送数组数据
     *
     * @param bytes
     * @return
     */
    public MiniTCPClient write(byte[] bytes) {
        return write(ByteBuffer.wrap(bytes));
    }

    /**
     * 发送Java Nio ByteBuffer
     *
     * @param byteBuffer
     * @return
     */
    public MiniTCPClient write(ByteBuffer byteBuffer) {
        return write(new ByteBuffer[]{byteBuffer});
    }

    /**
     * 发送Java Nio ByteBuffer数组，由Nio按顺序组合发送
     *
     * @param byteBuffers
     * @return
     */
    public MiniTCPClient write(ByteBuffer[] byteBuffers) {
        try {
            socketChannel.write(byteBuffers);
        } catch (IOException e) {
            dispatcherError(e);
        }
        return this;
    }

    /**
     * 启动服务
     */
    public synchronized void startConnect() {
        start();
        if (autoReconnect) {
            loopHeart();
        }
    }

    /**
     * 内部方法
     * 启动线程连接
     */
    protected synchronized void start() {
        stop();
        thread = new Thread(this);
        thread.setName("MiniTCPClient");
        thread.setDaemon(daemon);
        thread.start();
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
            boolean isReconnect = serverStatus == SERVER_STATUS_RECONNECTING;
            serverStatus = SERVER_STATUS_WAIT;
            while (serverStatus != SERVER_STATUS_STOP) {
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
                                if (isReconnect) {
                                    serverStatus = SERVER_STATUS_RECONNECTED;
                                    clientListener.onReconnected(this);
                                } else {
                                    serverStatus = SERVER_STATUS_CONNECTED;
                                    clientListener.onConnected(this);
                                }
                                lastMsgTime = System.currentTimeMillis();

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
                            serverStatus = SERVER_STATUS_STOP;
                        } else if (count != 0) {
                            try {
                                onReceivedMessage(byteBuffer);
                            } catch (Exception e) {
                                clientListener.onError("消息处理异常", e);
                            }
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
            stopOrReconnect();
        }
    }

    /**
     * 根据回调方法设置的泛型类型，返回指定类型数据
     *
     * @param byteBuffer
     * @throws Exception
     */
    protected void onReceivedMessage(ByteBuffer byteBuffer) throws Exception {
        lastMsgTime = System.currentTimeMillis();
        if (resultType == RESULT_TYPE_PACKMESSAGE) {
            List<PackageMessage> list = packageMessageDecoder.getPackageMessage().packageMessage(com.itgowo.tcp.me.ByteBuffer.newByteBuffer().writeBytes(byteBuffer.array(), byteBuffer.limit()));
            for (PackageMessage p : list) {
                if (p.getDataType() != PackageMessage.DATA_TYPE_HEART) {
                    onReceivedMessageNext(p);
                }
            }
        } else if (resultType == RESULT_TYPE_PACKMESSAGE_FOR_NIO) {
            List<PackageMessageForNio> list = packageMessageForNioDecoder.getPackageMessage().packageMessage(byteBuffer);
            for (PackageMessageForNio nio : list) {
                if (nio.getDataType() != PackageMessageForNio.DATA_TYPE_HEART) {
                    onReceivedMessageNext(nio);
                }
            }
        } else {
            onReceivedMessageNext(byteBuffer);
        }
    }

    /**
     * 分发数据
     *
     * @param o
     * @throws Exception
     */
    protected void onReceivedMessageNext(Object o) throws Exception {
        clientListener.onReadable(this, o);
    }

    /**
     * 设置是否自动重连，心跳包检测，在Start之前设置有效
     *
     * @param autoReconnect
     * @return
     */
    public MiniTCPClient setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
        return this;
    }

    /**
     * 未收到数据间隔时间，超时时间，单位毫秒
     *
     * @param reconnectTimeOut
     * @return
     */
    public MiniTCPClient setReconnectTimeOut(int reconnectTimeOut) {
        this.reconnectTimeOut = reconnectTimeOut;
        return this;
    }

    /**
     * 心跳检测间隔时间，单位秒
     *
     * @param sendHeartTimeInterval
     * @return
     */
    public MiniTCPClient setSendHeartTimeInterval(int sendHeartTimeInterval) {
        this.sendHeartTimeInterval = sendHeartTimeInterval;
        return this;
    }

    /**
     * 设置是否是守护线程，默认false，start之前有效
     *
     * @param daemon
     * @return
     */
    public MiniTCPClient setDaemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    /**
     * 获取连接状态
     *
     * @return
     */
    public int getServerStatus() {
        return serverStatus;
    }

    /**
     * 获取连接状态
     *
     * @return
     */
    public String getServerStatusString() {
        switch (serverStatus) {
            case SERVER_STATUS_WAIT:
                return "wait";
            case SERVER_STATUS_CONNECTED:
                return "connected";
            case SERVER_STATUS_RECONNECTING:
                return "reconnecting";
            case SERVER_STATUS_RECONNECTED:
                return "reconnected";
            case SERVER_STATUS_STOP:
                return "stop";
        }
        return "unknow";
    }

    /**
     * 内部方法
     * 停止连接并停止线程
     */
    protected synchronized void stop() {
        if (serverStatus != SERVER_STATUS_RECONNECTING) {
            serverStatus = SERVER_STATUS_STOP;
        }
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socketChannel = null;
        }
        if (thread != null) {
            thread.stop();
        }
    }

    /**
     * 公开方法
     * 停止当前连接，并触发回调
     */
    public void stopConnect() {
        autoReconnect = false;
        stopAndCallBack();
    }

    /**
     * 内部方法
     * 根据autoReconnect参数决定是否停止服务或者重连。
     */
    protected void stopOrReconnect() {
        if (autoReconnect) {
            start();
        } else {
            serverStatus = SERVER_STATUS_STOP;
            stopAndCallBack();
        }
    }

    /**
     * 内部方法
     * 停止连接并且触发onStop回调。
     */
    protected synchronized void stopAndCallBack() {
        stop();
        try {
            if (serverStatus == SERVER_STATUS_STOP) {
                clientListener.onStop();
            }
        } catch (Exception e) {
            clientListener.onError("onStop Error", e);
        }
    }

    /**
     * 内部方法
     * 关闭连接通道
     *
     * @param socketChannel
     * @param selector
     */
    protected void close(SocketChannel socketChannel, Selector selector) {
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

    /**
     * 内部方法
     * 针对具体异常做对应处理
     *
     * @param e
     */
    protected void dispatcherError(Exception e) {
        if (e instanceof NotYetConnectedException) {
            clientListener.onError("未完成连接，请求过早", e);
        } else if (e instanceof ConnectException) {
            clientListener.onError("连接失败", e);
            stopOrReconnect();
        } else if (e instanceof AsynchronousCloseException) {
            clientListener.onError("连接被关闭", e);
            stopOrReconnect();
        } else if (e instanceof ClosedByInterruptException) {
            clientListener.onError("连接被中断", e);
            stopOrReconnect();
        } else if (e instanceof ClosedChannelException) {
            clientListener.onError("连接通道被关闭", e);
            stopOrReconnect();
        } else if (e instanceof NoConnectionPendingException) {
            clientListener.onError("连接尚未成功，连接成功前不可以操作", e);
        } else {
            clientListener.onError("", e);
        }

    }

    /**
     * 内部方法
     * 心跳发送服务
     */
    protected void loopHeart() {
        if (!autoReconnect) {
            return;
        }
        scheduledThreadPool.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (autoReconnect) {
                    if ((serverStatus == SERVER_STATUS_CONNECTED || serverStatus == SERVER_STATUS_RECONNECTED) && System.currentTimeMillis() < reconnectTimeOut + lastMsgTime) {
                        if (resultType == RESULT_TYPE_PACKMESSAGE_FOR_NIO) {
                            write(PackageMessageForNio.getHeartPackageMessage().encodePackageMessage());
                        } else if (resultType == RESULT_TYPE_PACKMESSAGE) {
                            write(PackageMessage.getHeartPackageMessage().encodePackageMessage().readableBytesArray());
                        }
                    } else {
                        serverStatus = SERVER_STATUS_RECONNECTING;
                        start();
                    }
                }
            }
        }, sendHeartTimeInterval, sendHeartTimeInterval, TimeUnit.SECONDS);

    }
}
