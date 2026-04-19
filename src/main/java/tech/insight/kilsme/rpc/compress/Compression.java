package tech.insight.kilsme.rpc.compress;

public interface Compression {
    byte[]compress(byte[]bytes);

    byte[]decompress(byte[]bytes);
    enum CompressionType{
        NONE(0),
        GZIP(1);
        private final int typeCode;
        CompressionType(int typeCode) {
            this.typeCode = typeCode;
        }

        public int getTypeCode() {
            return typeCode;
        }
    }
}
