package com.itgowo.tcpclient;

import com.itgowo.tcp.me.PackageMessage;
import com.itgowo.tcp.nio.PackageMessageForNio;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MiniTCPClient implements Runnable {


    public static final int SERVER_STATUS_RESTART = 1;
    public static final int SERVER_STATUS_WAIT = 2;
    public static final int SERVER_STATUS_CONNECTED = 3;
    public static final int SERVER_STATUS_RECONNECTING = 4;
    public static final int SERVER_STATUS_RECONNECTED = 5;
    public static final int SERVER_STATUS_STOP = 6;

    protected MiniTCPClientInfo clientInfo;
    protected int serverStatus;
    protected onMiniTCPClientListener clientListener;
    protected int BufferSize = 1024;
    protected String remoteServerAddress;
    protected int remoteServerPort;
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
        this.clientInfo = new MiniTCPClientInfo();
        this.clientInfo.getResultType(clientListener);

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
            clientInfo.socketChannel.write(byteBuffers);
        } catch (IOException e) {
            dispatcherError(e);
        }
        return this;
    }

    /**
     * 启动服务
     */
    public synchronized void startConnect() {
        if (clientInfo != null && clientInfo.thread != null && clientInfo.thread.isAlive()) {
            return;
        }
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
        serverStatus = SERVER_STATUS_WAIT;
        clientInfo.thread = new Thread(this);
        clientInfo.thread.setName("MiniTCPClient");
        clientInfo.thread.setDaemon(daemon);
        clientInfo.thread.start();
    }

    @Override
    public void run() {
        initChannel();
        while (autoReconnect) {
            serverStatus = SERVER_STATUS_RECONNECTING;
            initChannel();
            try {
                Thread.sleep(sendHeartTimeInterval * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void initChannel() {
        try {
            SocketAddress socketAddress = new InetSocketAddress(remoteServerAddress, remoteServerPort);
            clientInfo.socketChannel = SocketChannel.open();
            clientInfo.socketChannel.socket().setReceiveBufferSize(BufferSize);
            clientInfo.socketChannel.configureBlocking(false);
            clientInfo.socketChannel.connect(socketAddress);
            clientInfo.selector = Selector.open();
            clientInfo.socketChannel.register(clientInfo.selector, SelectionKey.OP_CONNECT);
            boolean isReconnect = serverStatus == SERVER_STATUS_RECONNECTING;
            serverStatus = SERVER_STATUS_WAIT;
            while (serverStatus != SERVER_STATUS_STOP) {
                clientInfo.selector.select(3000);
                processSelectionKey(clientInfo.selector, isReconnect);
            }
        } catch (Exception e) {
            dispatcherError(e);
        } finally {
            clientInfo.close();
            stopOrReconnect();
        }
    }

    /**
     * 处理Select
     * @param selector
     * @param isReconnect
     * @throws Exception
     */
    private void processSelectionKey(Selector selector, boolean isReconnect) throws Exception {
        Iterator ite = selector.selectedKeys().iterator();
        while (ite.hasNext()) {
            SelectionKey key = (SelectionKey) ite.next();
            ite.remove();
            if (key.isConnectable()) {
                if (clientInfo.socketChannel.isConnectionPending()) {
                    Thread.yield();
                    try {
                        if (clientInfo.socketChannel.finishConnect()) {
                            //只有当连接成功后才能注册OP_READ事件
                            key.interestOps(SelectionKey.OP_WRITE);
                            clientInfo.isOffline = false;
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
                    } catch (Exception e) {
                        serverStatus = SERVER_STATUS_STOP;
                        break;
                    }
                }
                key.interestOps(SelectionKey.OP_WRITE);
            } else if (key.isReadable()) {
                java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(BufferSize);
                int count = clientInfo.socketChannel.read(byteBuffer);
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
                clientInfo.isWritable = true;
                clientListener.onWritable(this);
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
        } else if (e instanceof AsynchronousCloseException) {
            clientListener.onError("连接被关闭", e);
        } else if (e instanceof ClosedByInterruptException) {
            clientListener.onError("连接被中断", e);
        } else if (e instanceof ClosedChannelException) {
            clientListener.onError("连接通道被关闭", e);
        } else if (e instanceof NoConnectionPendingException) {
            clientListener.onError("连接尚未成功，连接成功前不可以操作", e);
        } else if (e instanceof CancelledKeyException) {
            clientListener.onError("连接失败，本次连接已取消", e);
        } else {
            clientListener.onError("其他", e);
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
        if (clientInfo.isPackageMessage()) {
            List<PackageMessage> list = clientInfo.packageMessageDecoder.getPackageMessage().packageMessage(com.itgowo.tcp.me.ByteBuffer.newByteBuffer().writeBytes(byteBuffer.array(), byteBuffer.limit()));
            for (PackageMessage p : list) {
                if (p.getDataType() != PackageMessage.DATA_TYPE_HEART) {
                    onReceivedMessageNext(p);
                }
            }
        } else if (clientInfo.isPackageMessageForNio()) {
            List<PackageMessageForNio> list = clientInfo.packageMessageForNioDecoder.getPackageMessage().packageMessage(byteBuffer);
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
        serverStatus = SERVER_STATUS_STOP;
    }

    /**
     * 公开方法
     * 停止当前连接，并触发回调
     */
    public void stopConnect() {
        autoReconnect = false;
        stop();
    }

    public boolean isOffline() {
        return clientInfo == null ? true : clientInfo.isOffline;
    }

    public boolean isWritable() {
        return clientInfo == null ? true : clientInfo.isWritable;
    }

    /**
     * 内部方法
     * 根据autoReconnect参数决定是否停止服务或者重连。
     */
    protected void stopOrReconnect() {
        if (autoReconnect) {
            if (clientInfo.isOffline) {
                return;
            }
            try {
                clientInfo.isOffline = true;
                clientInfo.isWritable = false;
                clientListener.onOffline(this);
            } catch (Exception e) {
                clientListener.onError("onOffline", e);
            }
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
                    if (serverStatus == SERVER_STATUS_RECONNECTING) {
                        return;
                    }
                    if (serverStatus != SERVER_STATUS_CONNECTED && serverStatus != SERVER_STATUS_RECONNECTED) {
                        return;
                    }
                    if (System.currentTimeMillis() < reconnectTimeOut + lastMsgTime) {
                        if (clientInfo.isPackageMessageForNio()) {
                            write(PackageMessageForNio.getHeartPackageMessage().encodePackageMessage());
                        } else if (clientInfo.isPackageMessage()) {
                            write(PackageMessage.getHeartPackageMessage().encodePackageMessage().readableBytesArray());
                        }
                    } else {
//                        stopOrReconnect();
                    }
                } else {
                    scheduledThreadPool.shutdown();
                }
            }
        }, sendHeartTimeInterval, sendHeartTimeInterval, TimeUnit.SECONDS);

    }


}
