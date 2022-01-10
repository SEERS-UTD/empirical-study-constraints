package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TestBuilderModel {

    private AnalysisScope scope;
    private IClassHierarchy cha;
    private  AnalysisOptions options;

    public TestBuilderModel(String systemName, List<String> entryPoints) throws IOException, ClassHierarchyException {
        String scopeFile = String.format("programs/%s/scope.txt", systemName);
        String exclusionFile = String.format("programs/%s/exclusions.txt", systemName);
        scope =
                AnalysisScopeReader.readJavaScope(
                        scopeFile, new File(exclusionFile), getClass().getClassLoader());
        cha = ClassHierarchyFactory.makeWithRoot(scope);
        options = new AnalysisOptions();
        Iterable<Entrypoint> entrypoints = PatternDetector.makeEntrypoints(scope, cha, entryPoints);
        options.setEntrypoints(entrypoints);
    }

    private void testZeroCFABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with ZeroCFABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeZeroCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }

    private void testOneCFABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with OneCFABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeNCFABuilder(1, options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }

    private void testZeroOneCFABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with ZeroOneCFABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }

    private void testZeroOneContainerCFABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with ZeroOneContainerCFABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }

    private void testRTABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with RTABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeRTABuilder(options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }

    private void testVanillaOneCFABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with VanillaOneCFABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeVanillaNCFABuilder(1, options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }

    private void testVanillaZeroOneCFABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with VanillaZeroOneCFABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }

    private void testVanillaZeroOneContainerCFABuilder() throws CallGraphBuilderCancelException {
        System.out.println("start building with VanillaZeroOneContainerCFABuilder");
        long start = System.currentTimeMillis();
        CallGraphBuilder builder = Util.makeVanillaZeroOneContainerCFABuilder(options, new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("took " + (end - start) + "ms");
    }


    public void testBuilders(int n) throws CallGraphBuilderCancelException {
        switch (n) {
            case 0:
                testZeroCFABuilder();
                break;
            case 1:
                testOneCFABuilder();
                break;
            case 2:
                testZeroOneCFABuilder();
                break;
            case 3:
                testZeroOneContainerCFABuilder();
                break;
            case 4:
                testRTABuilder();
                break;
            case 5:
                testVanillaOneCFABuilder();
                break;
            case 6:
                testVanillaZeroOneCFABuilder();
                break;
            case 7:
                testVanillaZeroOneContainerCFABuilder();
                break;
        }
    }
}
