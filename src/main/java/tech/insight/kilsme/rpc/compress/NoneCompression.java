package tech.insight.kilsme.rpc.compress;

import tech.insight.kilsme.rpc.spi.Spi;

@Spi(value = "none", code = 0)
public class NoneCompression implements Compression{
    @Override
    public byte[] compress(byte[] bytes) {
        return bytes;
    }

    @Override
    public byte[] decompress(byte[] bytes) {
        return  bytes;
    }

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public int code() {
        return 0;
    }
}
