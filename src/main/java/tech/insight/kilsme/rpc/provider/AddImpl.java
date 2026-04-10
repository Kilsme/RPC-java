package tech.insight.kilsme.rpc.provider;

import tech.insight.kilsme.rpc.api.Add;

/**
 * Add 接口的服务端实现。
 */
public class AddImpl implements Add {
    @Override
    public int add(int a, int b) {
        // 正常暴露给 RPC 的方法。
        return a+b;
    }

    // 私有方法不会通过接口注册暴露，仅用于本地测试反射异常场景。
    private  int privateAdd(int a,int b){
        return a-b;
    }
}
