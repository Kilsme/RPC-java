package tech.insight.kilsme.rpc.provider;

import tech.insight.kilsme.rpc.api.Add;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Add 接口的服务端实现。
 *
 * <p>这里就是 Provider 真正执行业务逻辑的地方。RPC 框架负责网络和协议，
 * 业务实现只需要像普通 Java 类一样写方法即可。</p>
 */
public class AddImpl implements Add {
    @Override
    public Integer add(int a, int b) {
        // 正常暴露给 RPC 的方法。
        // 如果需要测试超时，可以把下面那行 parkNanos 打开。
//        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4)); // 模拟长时间运行的服务方法，便于测试超时和异常场景。
        return a + b;
    }

    @Override
    public Integer minus(int a, int b) {
        // 示例中的第二个公开方法，同样可被 RPC 反射调用。
        return a - b;
    }

    // 私有方法不会通过接口注册暴露，仅用于本地测试反射异常场景。
    // 这个方法不会被 RPC 正常调用，因为 ProviderRegistry 是基于接口暴露方法的。
    private int privateAdd(int a, int b) {
        return a - b;
    }
}
