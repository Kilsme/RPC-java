package tech.insight.kilsme.rpc.api;

public class ConsumerAddImpl implements Add{

    @Override
    public Integer add(int a, int b) {

        return a+b;
    }

    @Override
    public Integer minus(int a, int b) {
        return 0;
    }

    @Override
    public User mergeAge(User user1, User user2) {
        User user=new User();
        user.setAge(user1.getAge()+user2.getAge());
        user.setName("consumer创建");
        return user;
    }
}
