package com.itgowo.tcpclient;

public abstract class onSimpleMiniTCPClientListener implements onMiniTCPClientListener {
    @Override
    public void onConnected(MiniTCPClient tcpClient) {

    }

    @Override
    public void onWritable(MiniTCPClient tcpClient) {

    }

    @Override
    public void onError(String error, Exception e) {

    }

    @Override
    public void onStop() {

    }
}
