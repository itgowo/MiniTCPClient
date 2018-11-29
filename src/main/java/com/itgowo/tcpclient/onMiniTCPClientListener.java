package com.itgowo.tcpclient;

public interface onMiniTCPClientListener<ResultType> {
    void onConnected(MiniTCPClient tcpClient);

    void onReadable(MiniTCPClient tcpClient, ResultType resultType);

    void onWritable(MiniTCPClient tcpClient);

    void onError(String error, Exception e);

    void onStop();
}
