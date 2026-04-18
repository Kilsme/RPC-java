package tech.insight.kilsme.rpc.serialize;

public interface Serializer {
    byte[]serialize(Object object);
    <T> T deserialize(byte[] bytes,Class<T> clazz);
    enum SerializerType{
        JSON(0),HESSIAN(1);
        private final int typeCode;
        SerializerType(int typeCode){
            this.typeCode=typeCode;
        }

        public int getTypeCode() {
            return typeCode;
        }
    }
}
