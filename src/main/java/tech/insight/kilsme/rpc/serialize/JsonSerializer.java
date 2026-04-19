package tech.insight.kilsme.rpc.serialize;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import tech.insight.kilsme.rpc.message.Request;

import java.nio.charset.StandardCharsets;

public class JsonSerializer implements  Serializer {

    @Override
    public byte[] serialize(Object object) {
        return JSONObject.toJSONString(object).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> objectClass) {
        String jsonString=new String(bytes,StandardCharsets.UTF_8);
        return JSONObject.parseObject(jsonString,objectClass,JSONReader.Feature.SupportClassForName);
    }

    @Override
    public String getName() {
        return "json";
    }

    @Override
    public int code() {
      return 0;
    }
}
