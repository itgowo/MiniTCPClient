package com.itgowo.tcpclient;

public interface onMiniTCPClientListener<ResultType> {
    void onConnected(TCPClient tcpClient);

    void onReadable(TCPClient tcpClient, ResultType resultType);

    void onWritable(TCPClient tcpClient);

    void onError(String error, Exception e);

    void onStop();
}
