package tech.insight.kilsme.rpc.compress;

public class NoneCompression implements Compression{
    @Override
    public byte[] compress(byte[] bytes) {
        return bytes;
    }

    @Override
    public byte[] decompress(byte[] bytes) {
        return  bytes;
    }

}
