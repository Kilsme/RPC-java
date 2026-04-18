package tech.insight.kilsme.rpc.serialize;

import java.util.*;

public class SerializerManager {
    private final Map<Integer,Serializer>serializerMap=new HashMap<>();

    public SerializerManager() {
        init();
    }
    public Serializer getSerializer(int typeCode){
        return serializerMap.get(typeCode);
    }

    private void init() {
        serializerMap.put(Serializer.SerializerType.JSON.getTypeCode(),new JsonSerializer());
        serializerMap.put(Serializer.SerializerType.HESSIAN.getTypeCode(),new HessianSerializer());
    }
}
