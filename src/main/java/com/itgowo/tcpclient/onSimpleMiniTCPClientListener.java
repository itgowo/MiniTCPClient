package com.itgowo.tcpclient;

public abstract class onSimpleMiniTCPClientListener implements onMiniTCPClientListener {
    @Override
    public void onConnected(TCPClient tcpClient) {

    }

    @Override
    public void onWritable(TCPClient tcpClient) {

    }

    @Override
    public void onError(String error, Exception e) {

    }

    @Override
    public void onStop() {

    }
}
