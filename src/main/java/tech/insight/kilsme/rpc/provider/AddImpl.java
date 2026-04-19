package tech.insight.kilsme.rpc.provider;

import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.api.User;

/**
 * Add 接口的 Provider 侧实现。
 * <p>
 * 这是实际被远程反射调用的业务类，
 * ProviderRegistry 会把该实例与接口名建立映射关系。
 * </p>
 */
public class AddImpl implements Add {
    @Override
    public User mergeAge(User user1, User user2) {
       User user=new User();
       user.setAge(user1.getAge()+user2.getAge());
       user.setName("provider创建");
        return user;
    }

    @Override
    public Integer add(int a, int b) {
        // 正常暴露给 RPC 的方法。
        // 如果需要测试超时，可以把下面那行 parkNanos 打开。
        // LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4)); // 模拟长时间运行的服务方法，便于测试超时和异常场景。
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
