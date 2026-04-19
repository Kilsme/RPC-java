package tech.insight.kilsme.rpc.serialize;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
@Slf4j
public class HessianSerializer implements Serializer {
    @Override
    public byte[] serialize(Object object) {
        try (ByteArrayOutputStream oos = new ByteArrayOutputStream()) {
            com.caucho.hessian.io.HessianOutput hessianOutput = new com.caucho.hessian.io.HessianOutput(oos);
            hessianOutput.writeObject(object);
            hessianOutput.flush();
            return oos.toByteArray();
        } catch (Exception e) {
            log.error("Hessian serialization failed", e);
            return new byte[0];
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            com.caucho.hessian.io.HessianInput hessianInput = new com.caucho.hessian.io.HessianInput(bis);
            return (T) hessianInput.readObject();

        } catch (Exception e) {
            log.error("Hessian deserialization failed", e);
            return null;
        }
    }

    @Override
    public String getName() {
        return "hessian";
    }

    @Override
    public int code() {
        return 1;
    }
}
