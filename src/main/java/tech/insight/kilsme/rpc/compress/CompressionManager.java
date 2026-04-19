package tech.insight.kilsme.rpc.compress;

import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.spi.Spi;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
@Slf4j
public class CompressionManager {
    private final Map<Integer, Compression> codeMap =new HashMap<>();
    private final Map<String, Compression> nameMap =new HashMap<>();

    public CompressionManager() {
        init();
    }
    public Compression getCompression(int typeCode){
        return codeMap.get(typeCode);
    }
    public Compression getCompression(String name){
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }


    private void init() {
        for(Compression compression : ServiceLoader.load(Compression.class)){
//            Class<? extends Compression> aClass = compression.getClass();
//            Spi annotation = aClass.getAnnotation(Spi.class);
//            if(annotation==null){
//                log.warn(aClass.getName()+"没有加上注解");
//                continue;
//            }
            if (codeMap.put(compression.code(), compression)!=null) {
                throw new IllegalStateException("Duplicate compression code: " + compression.code());
            }
            if (compression.code()>=16) {
                throw new IllegalStateException("compression code must be less than 16: " + compression.code());
            }
            if (nameMap.put(compression.getName().toUpperCase(Locale.ROOT), compression)!=null) {
                throw new IllegalStateException("Duplicate compression name: " +compression.getName());
            }
        }
    }
}
