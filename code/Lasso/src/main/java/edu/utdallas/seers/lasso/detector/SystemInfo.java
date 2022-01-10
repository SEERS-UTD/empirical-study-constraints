package edu.utdallas.seers.lasso.detector;

import java.util.HashMap;

public class SystemInfo {

    private String systemName;
    private boolean hasExclusion;
    private boolean hasEntryPoint;
    private boolean oneCFABuilder;

    public SystemInfo(String systemName, boolean hasExclusion, boolean hasEntryPoint, boolean oneCFABuilder) {
        this.systemName = systemName;
        this.hasExclusion = hasExclusion;
        this.hasEntryPoint = hasEntryPoint;
        this.oneCFABuilder = oneCFABuilder;
    }

    public String getSystemName() {
        return systemName;
    }

    public boolean hasExclusion() {
        return hasExclusion;
    }

    public boolean hasEntryPoint() {
        return hasEntryPoint;
    }

    public boolean isOneCFABuilder() {
        return oneCFABuilder;
    }

    public static HashMap<String, SystemInfo> buildInfo() {
        HashMap<String, SystemInfo> hm = new HashMap<>();
        hm.put("apache-ant-1.10.6", new SystemInfo("apache-ant-1.10.6", false, true, true));
        hm.put("argouml-0.35.4", new SystemInfo("argouml-0.35.4", true, false, false));
        hm.put("guava-28.0", new SystemInfo("guava-28.0", true, false, true));
        hm.put("httpcomponents", new SystemInfo("httpcomponents", false, false, true));
        hm.put("itrust", new SystemInfo("itrust", false, true, true));
        hm.put("joda_time-2.10.3", new SystemInfo("joda_time-2.10.3", false, false, true));
        hm.put("jedit-5.6pre0", new SystemInfo("jedit-5.6pre0", false, false, true));
        hm.put("rhino-1.6R5", new SystemInfo("rhino-1.6R5", true, true, false));
        hm.put("swarm-2.8.11", new SystemInfo("swarm-2.8.11", true, false, true));
        hm.put("sample", new SystemInfo("sample", true, false, true));
        return hm;
    }
}
