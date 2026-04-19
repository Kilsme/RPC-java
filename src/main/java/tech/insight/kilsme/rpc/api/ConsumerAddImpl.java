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
}
