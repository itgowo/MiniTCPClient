package com.itgowo.tcp.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lujianchao
 * 使用Java Nio ByteBuffer进行处理的TCP粘包分包解决方案
 * 包大小长度最小为6
 * type 1 byte 消息类型  系统协议
 * length 4 byte 消息包总长度
 * dataType 1 byte 消息数据类型
 * dataSign 4 byte 消息数据校验
 * data n byte 消息数据
 */
public class PackageMessageForNio {
    public static final int BUFFER_SIZE = 256;
    /**
     * 0   new 出来默认值
     * 1   读取type值
     * 2   读取length值
     * 3   读取dataType值
     * 4   读取dataSign值
     * 5   读取data数据完整
     * 6   读取data数据部分
     * 7   无效包
     */
    public static final int STEP_DEFAULT = 0;
    public static final int STEP_TYPE = 1;
    public static final int STEP_LENGTH = 2;
    public static final int STEP_DATA_TYPE = 3;
    public static final int STEP_DATA_SIGN = 4;
    public static final int STEP_DATA_COMPLETEED = 5;
    public static final int STEP_DATA_PART = 6;
    public static final int STEP_DATA_INVALID = 7;
    /**
     * 数据包类型为定长类型，数据长度固定
     */
    public static final int TYPE_FIX_LENGTH = 120;
    /**
     * 数据包类型为动态长度类型，数据长度不固定
     */
    public static final int TYPE_DYNAMIC_LENGTH = TYPE_FIX_LENGTH + 1;

    /**
     * 数据类型，指令
     */
    public static final int DATA_TYPE_COMMAND = 1;
    /**
     * 数据类型，心跳
     */
    public static final int DATA_TYPE_HEART = 2;
    /**
     * 数据类型，二进制
     */
    public static final int DATA_TYPE_BYTE = 3;
    /**
     * 数据类型，文本
     */
    public static final int DATA_TYPE_TEXT = 4;
    /**
     * 数据类型，Json文本
     */
    public static final int DATA_TYPE_JSON = 5;
    /**
     * 标准格式协议头大小
     */
    public static final int LENGTH_HEAD = 10;
    /**
     * 处理粘包分包
     */
    private PackageMessageForNio pack;
    /**
     * 下次处理的半包数据
     */
    private ByteBuffer nextData = ByteBuffer.allocate(BUFFER_SIZE);
    /**
     * type 1 byte 消息类型  系统协议  范围-127 ~ 128
     */
    private int type = TYPE_DYNAMIC_LENGTH;
    /**
     * length 4 byte 消息包总长度
     */
    private int length = 0;
    /**
     * 数据类型，0-10 是预定义或保留值。
     */
    private int dataType = DATA_TYPE_HEART;
    /**
     * legth 4 0-Integer.MAX_VALUE 按照一定规则生成的验证信息，用来过滤脏数据请求
     */
    private int dataSign = 0;
    /**
     * 承载数据
     */
    private ByteBuffer data;
    /**
     * 当前处理进度，初始小于6byte不进入进度
     * 0   new 出来默认值
     * 1   读取type值
     * 2   读取length值
     * 3   读取dataType值
     * 4   读取dataSign值
     * 5   读取data数据完整
     * 6   读取data数据部分
     * 7   无效包
     */
    private int step = 0;

    public int getType() {
        return type;
    }

    public PackageMessageForNio setType(int type) {
        this.type = type;
        return this;
    }

    public int getLength() {
        return length;
    }

    public PackageMessageForNio setLength(int length) {
        this.length = length;
        return this;
    }

    public int getDataType() {
        return dataType;
    }

    public PackageMessageForNio setDataType(int dataType) {
        this.dataType = dataType;
        return this;
    }

    public int getDataSign() {
        return dataSign;
    }

    public PackageMessageForNio setDataSign(int dataSign) {
        this.dataSign = dataSign;
        return this;
    }

    public ByteBuffer getData() {
        return data;
    }

    public PackageMessageForNio setData(ByteBuffer data) {
        this.data = data;
        data.position(0);
        length = data.remaining() + LENGTH_HEAD;
        dataSign = dataSign();
        return this;
    }

    public PackageMessageForNio setData(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        setData(buffer);
        return this;
    }

    private PackageMessageForNio() {
    }

    public static PackageMessageForNio getPackageMessage() {
        return new PackageMessageForNio();
    }

    public static PackageMessageForNio getHeartPackageMessage() {
        PackageMessageForNio packageMessage = new PackageMessageForNio().setType(PackageMessageForNio.TYPE_DYNAMIC_LENGTH).setLength(6).setDataType(PackageMessageForNio.DATA_TYPE_HEART);
        return packageMessage;
    }

    public ByteBuffer encodePackageMessage() {
        if (type != TYPE_FIX_LENGTH && type != TYPE_DYNAMIC_LENGTH) {
            return null;
        }

        if (length < 6) {
            return null;
        }
        if (length == 6) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(6);
            byteBuffer.put((byte) type).putInt(length).put((byte) dataType).flip();
            return byteBuffer;
        }
        if (dataType == 0) {
            return null;
        }

        if (data.position(0).remaining() != length - LENGTH_HEAD) {
            return null;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);
        byteBuffer.put((byte) type).putInt(length).put((byte) dataType).putInt(dataSign).put(data).flip();
        return byteBuffer;
    }

    public List<PackageMessageForNio> packageMessage(ByteBuffer byteBuffer) {
        List<PackageMessageForNio> messageList = new ArrayList<>();
        try {
            while (true) {
                PackageMessageForNio packageMessage = decodePackageMessage(byteBuffer);
                if (packageMessage != null && packageMessage.isCompleted()) {
                    if (packageMessage.getData() != null) {
                        packageMessage.getData().position(0);
                    }
                    messageList.add(packageMessage);
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return messageList;
    }

    private PackageMessageForNio decodeFixLengthPackageMessage(ByteBuffer byteBuffer) throws IOException {
        //Todo 定长比较简单，这里暂不做实现
        return null;
    }

    private PackageMessageForNio decodeDynamicLengthPackageMessage(ByteBuffer byteBuffer) throws IOException {
        pack.setLength(byteBuffer.getInt());
        pack.setDataType(byteBuffer.get());
        if (pack.getLength() < 6) {
            pack.step = STEP_DATA_INVALID;
            byteBuffer.position(0);
            return pack;
        }
        if (pack.getLength() == 6) {
            pack.step = STEP_DATA_COMPLETEED;
            return pack;
        }
        //pack.getLength>6情况
        if (byteBuffer.remaining() < 4) {
            byteBuffer.position(0);
            //可能存在数据读取一半情况，直接返回，返回后由上游处理器暂存输入流剩余数据，下次合并输入流。
            return pack;
        }
        pack.dataType = byteBuffer.get();
        pack.dataSign = byteBuffer.getInt();
        //数据包大小在已有数据范围内，即要执行拆包操作
        int dataLength = pack.getLength() - LENGTH_HEAD;
        pack.data = ByteBuffer.allocate(dataLength);
        if (dataLength <= byteBuffer.remaining()) {
            pack.data.put(byteBuffer.array(), byteBuffer.position(), dataLength);
            pack.step = STEP_DATA_COMPLETEED;
            pack.data.flip();
            byteBuffer.position(byteBuffer.position() + dataLength);
            return pack;
        } else {
            byteBuffer.position(0);
            pack.step = STEP_DATA_PART;
            return pack;
        }
    }

    /**
     * 获取data长度，如果没有data，则返回0，返回结果只作为正常数据参考,不一定是data真实长度
     *
     * @return
     */
    public int getDataLength() {
        if (length <= 6 || data == null) {
            return 0;
        }
        return length - LENGTH_HEAD;
    }

    private synchronized PackageMessageForNio decodePackageMessage(ByteBuffer byteBuffer) throws IOException {
        autoExpandCapacity(byteBuffer);
        nextData.put(byteBuffer);
        if (nextData.remaining() < 6) {
            return null;
        }
        //nextData大于6，则正常处理
        nextData.flip();
        if (nextData.remaining() < 6) {
            return null;
        }
        int type = nextData.get();
        if (TYPE_FIX_LENGTH == type || TYPE_DYNAMIC_LENGTH == type) {
            if (pack == null || pack.step == STEP_DATA_COMPLETEED) {
                pack = new PackageMessageForNio();
            }
            pack.setType(type);
            pack.step = STEP_TYPE;
        } else {
            return null;
        }
        PackageMessageForNio packageMessage = null;
        if (pack.getType() == TYPE_FIX_LENGTH) {
            packageMessage = decodeFixLengthPackageMessage(nextData);
        } else if (pack.getType() == TYPE_DYNAMIC_LENGTH) {
            packageMessage = decodeDynamicLengthPackageMessage(nextData);
        }
        nextData.compact();
        return packageMessage;
    }

    /**
     * 是否数据结束，是完整包数据
     *
     * @return
     */
    public boolean isCompleted() {
        if (step != STEP_DATA_COMPLETEED) {
            return false;
        }
        return dataSign == dataSign();
    }

    /**
     * 数组转换成整数型，数组长度小于等于4有效，长度多余4则只转换前4个。
     *
     * @param b
     * @return b=null return 0；
     */
    public static int byteArrayToInt(byte[] b) {
        if (b == null) {
            return 0;
        }
        if (b.length == 3) {
            return (b[2] & 0xFF) | (b[1] & 0xFF) << 8 | (b[0] & 0xFF) << 16;
        }
        if (b.length == 2) {
            return (b[1] & 0xFF) | (b[0] & 0xFF) << 8;
        }
        if (b.length == 1) {
            return b[0] & 0xFF;
        }
        return b[3] & 0xFF | (b[2] & 0xFF) << 8 | (b[1] & 0xFF) << 16 | (b[0] & 0xFF) << 24;
    }

    /**
     * 整数转换成数组
     *
     * @param a
     * @return byte length=4
     */
    public static byte[] intToByteArray(int a) {
        return new byte[]{(byte) ((a >> 24) & 0xFF), (byte) ((a >> 16) & 0xFF), (byte) ((a >> 8) & 0xFF), (byte) (a & 0xFF)};
    }

    /**
     * 获取简单数据签名，注意先初始化length
     *
     * @return
     */
    public int dataSign() {
        byte[] bytes1 = new byte[4];
        if (data == null) {
            return 0;
        }
        if (length < 10) {
            return 1;
        }
        if (data.remaining() < 10) {
            return 1;
        }
        int length = data.remaining();
        int position = length / 4;
        bytes1[0] = (byte) position;
        bytes1[1] = data.array()[position];
        position = length * 3 / 4;
        bytes1[2] = (byte) position;
        bytes1[3] = data.array()[position];
        return byteArrayToInt(bytes1);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PackageMessageForNio{");
        sb.append("type=").append(type);
        sb.append(", length=").append(length);
        sb.append(", dataType=").append(dataType);
        sb.append(", dataSign=").append(dataSign);
        sb.append(", data=").append(data);
        sb.append(", step=").append(step);
        sb.append('}');
        return sb.toString();
    }

    /**
     * 自动扩容,递增值为BUFFER_SIZE的倍数
     */
    private void autoExpandCapacity(ByteBuffer byteBuffer) {
        int old = nextData.remaining();
        if (old < byteBuffer.remaining()) {
            int newSize = nextData.limit() + byteBuffer.remaining();
            int size = 0;
            while (size < newSize) {
                size += BUFFER_SIZE;
            }
            ByteBuffer newByteBuffer = ByteBuffer.allocate(size);
            nextData.flip();
            newByteBuffer.put(nextData);
            newByteBuffer.put(byteBuffer);
            nextData = newByteBuffer;
        }
    }

}
