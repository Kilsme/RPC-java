package tech.insight.kilsme.rpc.serialize;

import tech.insight.kilsme.rpc.spi.Extension;

public interface Serializer extends Extension {
    byte[] serialize(Object object);

    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
