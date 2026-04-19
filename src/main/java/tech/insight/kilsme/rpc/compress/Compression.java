package tech.insight.kilsme.rpc.compress;

import lombok.Data;
import tech.insight.kilsme.rpc.spi.Extension;


public interface Compression  extends Extension {
    byte[] compress(byte[] bytes);

    byte[] decompress(byte[] bytes);

}
