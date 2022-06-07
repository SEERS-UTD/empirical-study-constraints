package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.ipa.slicer.Statement;
import edu.utdallas.seers.lasso.detector.ast.PatternStore;
import edu.utdallas.seers.lasso.entity.*;

import java.util.*;

// TODO this class can be made to inherit from OneInputDetector but we will need to change the return types
public class IfChainDetector {
    List<Pattern> detectPattern(Slice slice, PatternStore patternStore) {
        HashSet<BasicASTPattern> patternSet = new HashSet<>();
        for (Statement s : slice.getSliceStatements()) {
            Pattern temp = new SimplePattern(s, PatternType.IF_CHAIN);
            Optional<BasicASTPattern> astPattern = patternStore.lookUpInstance(temp);
            if (astPattern.isPresent()) {
                patternSet.add(astPattern.get());
            }
        }

        List<Pattern> patterns = new ArrayList<>();
        for (BasicASTPattern p : patternSet) {
            patterns.add(new NoStatementPattern(p));
        }
        return patterns;
    }
}