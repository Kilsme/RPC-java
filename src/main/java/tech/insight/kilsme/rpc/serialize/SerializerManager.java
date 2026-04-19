package tech.insight.kilsme.rpc.serialize;

import java.util.*;

public class SerializerManager {
    private final Map<Integer, Serializer> codeMap = new HashMap<>();
    private final Map<String, Serializer> nameMap = new HashMap<>();

    public SerializerManager() {
        init();
    }

    public Serializer getSerializer(int typeCode) {
        return codeMap.get(typeCode);
    }

    public Serializer getSerializer(String name) {
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);
        for (Serializer serializer : loader) {
            if (codeMap.put(serializer.code(), serializer)!=null) {
                throw new IllegalStateException("Duplicate serializer code: " + serializer.code());
            }
            if (serializer.code()>=16) {
                throw new IllegalStateException("Serializer code must be less than 16: " + serializer.code());
            }
            if (nameMap.put(serializer.getName().toUpperCase(Locale.ROOT), serializer)!=null) {
                throw new IllegalStateException("Duplicate serializer name: " + serializer.getName());
            }
        }
    }
}
