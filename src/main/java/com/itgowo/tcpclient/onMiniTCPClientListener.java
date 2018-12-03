package com.itgowo.tcpclient;

public interface onMiniTCPClientListener<ResultType> {
    void onConnected(MiniTCPClient tcpClient) throws Exception;

    void onReconnected(MiniTCPClient tcpClient) throws Exception;

    void onOffline(MiniTCPClient tcpClient) throws Exception;

    void onReadable(MiniTCPClient tcpClient, ResultType resultType) throws Exception;

    void onWritable(MiniTCPClient tcpClient) throws Exception;

    void onError(String error, Exception e);

    void onStop() throws Exception;
}
