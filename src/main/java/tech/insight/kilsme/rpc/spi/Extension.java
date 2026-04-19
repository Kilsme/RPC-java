package tech.insight.kilsme.rpc.spi;

public interface Extension {
     String getName();
      default int code(){
           return -1;
      }
}
