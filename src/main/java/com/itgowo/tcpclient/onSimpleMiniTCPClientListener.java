package com.itgowo.tcpclient;

public abstract class onSimpleMiniTCPClientListener implements onMiniTCPClientListener {
    @Override
    public void onConnected(MiniTCPClient tcpClient) throws Exception {

    }

    @Override
    public void onReconnected(MiniTCPClient tcpClient) throws Exception {

    }

    @Override
    public void onOffline(MiniTCPClient tcpClient) throws Exception {

    }

    @Override
    public void onWritable(MiniTCPClient tcpClient) throws Exception {

    }

    @Override
    public void onError(String error, Exception e) {

    }

    @Override
    public void onStop() throws Exception {

    }
}
