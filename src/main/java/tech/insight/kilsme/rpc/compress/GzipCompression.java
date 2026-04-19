package tech.insight.kilsme.rpc.compress;

import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.exception.RpcException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
@Slf4j
public class GzipCompression implements Compression {
    @Override
    public byte[] compress(byte[] bytes) {
        try (ByteArrayOutputStream oos = new ByteArrayOutputStream()) {
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(oos);
            gzipOutputStream.write(bytes);
            gzipOutputStream.finish();
            return oos.toByteArray();
        } catch (Exception e) {
            log.error("压缩失败", e);
            return new byte[0];
        }
    }

    @Override
    public byte[] decompress(byte[] bytes) {
        try (ByteArrayOutputStream bis = new ByteArrayOutputStream();
             GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[4096];
            int len;
            while((len=gzipInputStream.read(buffer))!=-1){
                bis.write(buffer,0,len);
            }
            return bis.toByteArray();
        } catch (Exception e) {
            log.error("解压缩失败", e);
           throw  new RpcException("解压缩失败");
        }
    }

}

