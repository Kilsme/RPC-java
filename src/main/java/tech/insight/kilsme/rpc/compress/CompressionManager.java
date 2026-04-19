package tech.insight.kilsme.rpc.compress;

import java.util.HashMap;
import java.util.Map;

public class CompressionManager {
    private final Map<Integer, Compression> compressionMap =new HashMap<>();

    public CompressionManager() {
        init();
    }
    public Compression getCompression(int typeCode){
        return compressionMap.get(typeCode);
    }

    private void init() {
        compressionMap.put(Compression.CompressionType.NONE.getTypeCode(), new NoneCompression());
        compressionMap.put(Compression.CompressionType.GZIP.getTypeCode(), new GzipCompression());
    }
}
