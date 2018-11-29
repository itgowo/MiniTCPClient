package com.itgowo.tcpclient;

import java.nio.ByteBuffer;

public interface onMiniKeepAliveClientListener {
    void onConnected();

    void onReadable(ByteBuffer byteBuffer);

    void onWritable();

    void onError(String error,Exception e);

    void onStop();
}
