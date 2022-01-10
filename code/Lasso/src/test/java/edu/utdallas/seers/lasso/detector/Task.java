package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class Task implements Callable<Integer> {

    private final int n;

    public Task(int n) {
        this.n = n;
    }

    @Override
    public Integer call() throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {
        BufferedReader br = new BufferedReader(new FileReader("programs/argouml/entrypoint.txt"));
        List<String> entryPoints = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null)
            entryPoints.add(line);
        br.close();

        TestBuilderModel tsm = new TestBuilderModel("argouml", entryPoints);
        tsm.testBuilders(n);
        return 1;
    }

}
