package tech.insight.kilsme.rpc.version;

public enum Version {
    v1(0);
    private final int versionNum;

    Version(int versionNum) {
        this.versionNum = versionNum;
    }

    public int getVersionNum() {
        return versionNum;
    }
}
