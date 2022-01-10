package edu.utdallas.seers.lasso.detector;

import com.google.gson.Gson;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.strings.StringStuff;
import edu.utdallas.seers.lasso.detector.elements.Const;
import edu.utdallas.seers.lasso.detector.elements.DetectionSource;
import edu.utdallas.seers.lasso.detector.utils.ConstSlice;
import edu.utdallas.seers.lasso.detector.utils.Utils;
import edu.utdallas.seers.lasso.entity.*;
import edu.utdallas.seers.lasso.misc.DebugWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.convert.JavaCollectionWrappers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SuppressWarnings("ALL")
public class PatternDetector {

    final Logger logger = LoggerFactory.getLogger(PatternDetector.class);

    private final String systemName;
    private final boolean printLog;
    private final boolean hasEntryPoint;
    private final boolean hasExclusion;
    private final boolean oneCFABuilder;
    private final DebugWriter debugWriter;
    private final NullEmptyCheckDetector nullEmptyCheckDetector = new NullEmptyCheckDetector();
    private final IfChainDetector ifChainDetector = new IfChainDetector();
    private final Map<String, Set<String>> printAllIRClasses;
    private final Map<String, Set<String>> printAllUsagesMethods;
    private CallGraph cg;
    private IClassHierarchy cha;
    private SDG<InstanceKey> sdg;

    {
        printAllIRClasses = Stream.of(
                "argouml-77&org.argouml.core.propertypanels.ui.UMLMultiplicityPanel$MultiplicityCheckBox",
                "jEdit-38&org.gjt.sp.jedit.textarea.TextAreaPainter$PaintText",
                "jEdit-31&org.gjt.sp.jedit.browser.VFSBrowser$FavoritesMenuButton$ActionHandler",
                "argouml-47&org.argouml.uml.diagram.ui.FigGeneralization",
                "joda-time-12&org.joda.time.chrono.GJChronology"
        )
                .map(s -> {
                    String[] split = s.split("&");
                    return Pair.make(split[0], "L" + split[1].replace(".", "/"));
                })
                .collect(Collectors.groupingBy(p -> p.fst))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue().stream().map(p -> p.snd).collect(Collectors.toSet())
                ));

        printAllUsagesMethods = Stream.of(
                "Ant-5&getDescription",
                "argouml-77&java.awt.event.ItemListener#itemStateChanged",
                "jEdit-38&org.gjt.sp.jedit.textarea.TextAreaExtension#paintValidLine",
                "jEdit-31&java.awt.event.ActionListener#actionPerformed"
        )
                .collect(Collectors.groupingBy(s -> s.split("&")[0]))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            String constraintID = e.getKey();

                            return e.getValue().stream()
                                    .map(s -> s.substring(constraintID.length() + 1))
                                    .collect(Collectors.toSet());
                        }
                ));
    }

    public PatternDetector(String systemName, SystemInfo systemInfo, boolean printLog, Path debugDir)
            throws ClassHierarchyException, CancelException, IOException {
        this.systemName = systemName;
        hasEntryPoint = systemInfo.hasEntryPoint();
        hasExclusion = systemInfo.hasExclusion();
        this.printLog = printLog;
        oneCFABuilder = systemInfo.isOneCFABuilder();

        debugWriter = new DebugWriter(systemName, debugDir);

        String scopeFile = String.format("programs/%s/scope.txt", systemName);
        String exclusionFile = String.format("programs/%s/exclusions.txt", systemName);
        buildGraphs(scopeFile, exclusionFile);
    }

    public static Iterable<Entrypoint> makeEntrypoints(
            AnalysisScope scope, IClassHierarchy cha, List<String> entryPoints) {
        Collection<Entrypoint> ret = new ArrayList<Entrypoint>();
        for (String entryPoint : entryPoints) {
            String entryClass = entryPoint.split("#")[0];
            String entryMetohd = entryPoint.split("#")[1];
            IClass klass =
                    cha.lookupClass(
                            TypeReference.findOrCreate(
                                    ClassLoaderReference.Application,
                                    StringStuff.deployment2CanonicalTypeString(entryClass)));
            for (IMethod m : klass.getDeclaredMethods()) {
                if (m.getSelector().getName().toString().equals(entryMetohd) && m.isPublic()) {
                    ret.add(new DefaultEntrypoint(m, cha));
                }
            }
        }
        return ret;
    }

    public static void main(String[] args)
            throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException, InvalidClassFileException {

        HashMap<String, SystemInfo> hm = SystemInfo.buildInfo();
        Gson gson = new Gson();

        for (String systemName : args) {
            PatternEntry[] patterns =
                    gson.fromJson(
                            new FileReader("programs/" + systemName + "/inputs_test.json"), PatternEntry[].class);
            SystemInfo si = hm.get(systemName);

            List<PatternEntry> tmpList = new ArrayList<>();
            for (PatternEntry pe : patterns) {

                tmpList.add(pe);
            }

            PatternDetector pd = new PatternDetector(systemName, si, false, Paths.get("/home/zenong/Documents/tracability programs"));
            pd.printIR("SampleClass", "execute");
            pd.detectPatterns(tmpList.toArray(new PatternEntry[0]));
        }
    }

    private void buildGraphs(String scopeFile, String exclusionFile)
            throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException {
        long start = System.currentTimeMillis();
        AnalysisScope scope;
        ClassLoader classLoader = getClass().getClassLoader();
        if (hasExclusion) {
            scope = AnalysisScopeReader.readJavaScope(
                    scopeFile, new File(exclusionFile), classLoader);
        } else {
            scope = AnalysisScopeReader.readJavaScope(
                    scopeFile, null, classLoader);
            ExclusionUtils.addDefaultExclusions(scope);
        }

        cha = ClassHierarchyFactory.makeWithRoot(scope);

        AnalysisOptions options = new AnalysisOptions();

        Iterable<Entrypoint> entrypoints;
        if (hasEntryPoint) {
            List<String> entryList = new ArrayList<>();
            BufferedReader br =
                    new BufferedReader(
                            new FileReader(String.format("programs/%s/entrypoint.txt", systemName)));
            String line;
            while ((line = br.readLine()) != null) entryList.add(line);
            br.close();
            entrypoints = makeEntrypoints(scope, cha, entryList);
        } else entrypoints = makeAllPublicEntrypoints(cha);
        options.setEntrypoints(entrypoints);

        //         you can dial down reflection handling if you like
        //            options.setReflectionOptions(ReflectionOptions.NONE);
        AnalysisCache cache = new AnalysisCacheImpl();

        CallGraphBuilder builder;
        if (oneCFABuilder) builder = Util.makeNCFABuilder(1, options, cache, cha, scope);
        else builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);

        logger.info(withSystemName("Building call graph..."));
        cg = builder.makeCallGraph(options, null);

        long end = System.currentTimeMillis();

        Slicer.DataDependenceOptions dOptions = Slicer.DataDependenceOptions.NO_HEAP_NO_EXCEPTIONS;
        Slicer.ControlDependenceOptions cOptions = Slicer.ControlDependenceOptions.NONE;

        start = System.currentTimeMillis();
        logger.info(withSystemName("Building SDG..."));
        sdg = new SDG<>(cg, builder.getPointerAnalysis(), dOptions, cOptions);
        end = System.currentTimeMillis();
    }

    public List<PatternOutputFormat> detectPatterns(PatternEntry[] entries)
            throws IllegalArgumentException {

        debugWriter.writeCallGraphClasses(cg);

        long[] time_traces = new long[entries.length];
        int t = 0;

        List<PatternOutputFormat> outputs = new ArrayList<>();
        for (PatternEntry pe : entries) {
            logger.info(withSystemName("Processing constraint: " + pe.getConstraintId()));

            debugWriter.writeGroundTruthIR(pe.getConstraintId(), Arrays.asList(pe.getpTrus()), cg);
            debugWriter.writeSliceConstraint(pe.getConstraintId());
            Set<String> currentPrintAllIRClasses = printAllIRClasses.getOrDefault(pe.getConstraintId(), Collections.emptySet());

            debugWriter.writeAllIR(currentPrintAllIRClasses, cg);

            Set<String> currentPrintAllUsages = printAllUsagesMethods.getOrDefault(pe.getConstraintId(), Collections.emptySet());

            debugWriter.writeAllUsages(pe.getConstraintId(), currentPrintAllUsages, cg);

            long start = System.currentTimeMillis();
            try {
                outputs.add(detectPattern(pe));
            } catch (CancelException e) {
                throw new RuntimeException(e);
            }
            time_traces[t++] = System.currentTimeMillis() - start;
        }

        return outputs;
    }

    public PatternOutputFormat detectPattern(PatternEntry patternEntry)
            throws IllegalArgumentException, CancelException {
        // TODO instead of converting the inputs here, make everything take input type instead of strings
        // FIXME do not use the final constant value if it is 0 or 1 because it will mistakenly detect every true or false
        String[] inputs = patternEntry.getInputs().stream()
                .map(i -> i.getType() == DetectorInput.Type.FINAL_CONSTANT ? i.getValue().getVal() : i.getIdentifier())
                .toArray(String[]::new);
        int size = inputs.length;
        String operand1;
        String operand2;
        String operator;
        List<Pattern> patterns = new ArrayList<>();
        switch (size) {
            case 3:
                operand1 = inputs[0];
                operand2 = inputs[2];
                operator = inputs[1];
                patterns.addAll(handleBinComp(operand1, operand2, operator));
                break;
            case 2:
                operand1 = inputs[0];
                operand2 = inputs[1];
                patterns.addAll(handleRegex(operand1, operand2));
                // TODO: combine following detections to reduce running time
                patterns.addAll(handleConstArg(operand1, operand2));
                patterns.addAll(handleAssignConst(operand1, operand2));
                patterns.addAll(handleBinFlag(operand1, operand2));
                // STR_STARTS:
                patterns.addAll(
                        findAndSlice(operand1)
                                .map(s ->
                                        detectComparisonConst(
                                                s,
                                                new Const(operand2, Const.ConstType.STR),
                                                "str-starts",
                                                PatternEntry.PatternType.STR_STARTS
                                        )
                                )
                                .orElse(Collections.emptyList())
                );

                break;
            case 1:
                operand1 = inputs[0];

                // Slice only once, since all of these do it the same way
                Optional<Slice> slice = findAndSlice(operand1);

                List<Pattern> oneInputPatterns = slice.map(
                        // TODO utility method concat lists
                        s -> Stream.of(
                                nullEmptyCheckDetector.detectPattern(s),
                                ifChainDetector.detectPattern(s),
                                detectComparisonConst(
                                        s,
                                        new Const("", Const.ConstType.BOOL),
                                        "eq",
                                        PatternEntry.PatternType.BOOLEAN_PROPERTY
                                ),
                                detectEqualsOrChain(s)
                        )
                                .flatMap(Collection::stream)
                                .collect(Collectors.toList())
                )
                        .orElse(Collections.emptyList());

                patterns.addAll(oneInputPatterns);
                break;
        }
        // TODO convert patterns to PSLF and filter via AST
        return patternEntry.toOutputFormat(patterns);
    }

    // FIXME it's only optional in case the attribute is not found, but that should be an error instead

    private Optional<Slice> findAndSlice(String attributeName) throws CancelException {
        List<DetectionSource> getAttributeInsts = findAttributes(attributeName, true, true, true, true);

        List<Pair<Statement, Collection<Statement>>> slices = slice(sdg, getAttributeInsts);

        if (slices.isEmpty()) {
            logger.error(withSystemName(String.format("Attribute %s had no slicing sources", attributeName)));

            return Optional.empty();
        }

        Slice slice = new Slice(
                // Pick an arbitrary source
                slices.get(0).fst,
                slices.stream()
                        /* Ignore the sources. We don't need to know exactly which use of the
                        property has the pattern*/
                        .flatMap(p -> p.snd.stream())
                        .distinct()
                        .collect(Collectors.toList())
        );

        return Optional.of(slice);
    }
    private List<Pattern> detectComparisonConst(Slice slice, Const constant, String operator, PatternEntry.PatternType patternType) {
        List<Pair<Statement, Collection<Statement>>> fakeList =
                Collections.singletonList(Pair.make(slice.getSource(), slice.getSliceStatements()));

        List<Pair<Statement, List<Statement>>> matchedStatements1 =
                findOperatorConst(fakeList, operator);
        List<Pattern> patterns = findConstInSlice(matchedStatements1, constant);

        List<Pair<Statement, List<Statement>>> matchedInvokeStatements = findInvokeConst(fakeList);
        List<DetectionSource> actualCallers = findConstInInvokes(matchedInvokeStatements, constant);

        List<Pair<Statement, Collection<Statement>>> slices2 = null;
        try {
            slices2 = slice(sdg, actualCallers);
        } catch (CancelException e) {
            throw new RuntimeException(e);
        }

        List<Pair<Statement, List<Statement>>> matchedStatements2 =
                findOperatorConst(slices2, operator);
        patterns.addAll(getIntersection(matchedStatements1, matchedStatements2));

        return patterns.stream()
                .peek(p -> p.setPatternType(patternType))
                .collect(Collectors.toList());
    }

    private List<Pattern> detectEqualsOrChain(Slice slice) {
        List<Pattern> patterns = new ArrayList<>();
        Map<CGNode, List<StatementWithInstructionIndex>> hm = new HashMap<>();
        Collection<Statement> sliceStmts = slice.getSliceStatements();
        for (Statement stmt : sliceStmts) {
            if (stmt instanceof StatementWithInstructionIndex) {
                StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                CGNode node = s.getNode();
                if (!hm.containsKey(node)) {
                    hm.put(node, new ArrayList<>());
                }
                hm.get(node).add(s);
            }
        }

        for (CGNode node : hm.keySet()) {
            SymbolTable st = node.getIR().getSymbolTable();
            List<StatementWithInstructionIndex> stmts = hm.get(node);
            List<Integer>  vList = new ArrayList<>();
            // 0 = normal, 1 = check, 2 = skip
            int status = 0;
            boolean skip = false;
            for (int i = 0; i < stmts.size(); i++) {
                StatementWithInstructionIndex s = stmts.get(i);
                SSAInstruction inst = s.getInstruction();
                if (status == 0) {
                    if (inst instanceof SSAConditionalBranchInstruction) {
                        status++;
                        vList = new ArrayList<>();
                        for (int j = 0; j < inst.getNumberOfUses(); j++) {
                            int v = inst.getUse(j);
                            if (!st.isConstant(v)) {
                                vList.add(v);
                            }
                        }
                    }
                } else if (status == 1) {
                    if (inst instanceof SSAConditionalBranchInstruction) {
                        boolean match = false;
                        for (int j = 0; j < inst.getNumberOfUses(); j++) {
                            int v = inst.getUse(j);
                            if (vList.contains(v)) {
                                match = true;
                                break;
                            }
                        }
                        if (match) {
                            status++;
                            patterns.add(new SimplePattern(s));
                        } else
                            status--;
                    } else {
                        status--;
                    }
                } else if (status == 2) {
                    if (inst instanceof SSAConditionalBranchInstruction) {
                        boolean match = false;
                        for (int j = 0; j < inst.getNumberOfUses(); j++) {
                            int v = inst.getUse(j);
                            if (vList.contains(v)) {
                                match = true;
                                break;
                            }
                        }
                        if (!match)
                            status = 0;
                    } else {
                        status = 0;
                    }
                }

            }
        }
        return patterns.stream()
                .peek(p -> p.setPatternType(PatternEntry.PatternType.EQUALS_OR_CHAIN))
                .collect(Collectors.toList());
    }

    private List<Pattern> handleBinComp(String operand1, String operand2, String operator)
            throws CancelException {
        List<Pattern> patterns;
        // operand is an expression
        if (operand1.split(" ").length > 1 || operand2.split(" ").length > 1)
            patterns = detectExpressionComparison(operand1, operand2, operator);
            // FIXME what if operand1 is a constant?
        else if (!operand2.contains("#")
                || operand2.equals("java.lang.Integer#MAX_VALUE")
                || operand2.equals("java.lang.Integer#MIN_VALUE")
                || operand2.equals("java.lang.Long#MAX_VALUE")
                || operand2.equals("java.lang.Long#MIN_VALUE")
                || operand2.equals("java.lang.Double#POSITIVE_INFINITY")
                || operand2.equals("java.lang.Double#NEGATIVE_INFINITY")
                || operand2.equals("java.lang.Double#MAX_VALUE")
                || operand2.equals("java.lang.Double#MIN_VALUE")) {
            patterns = findAndSlice(operand1)
                    .map(s -> detectComparisonConst(
                            s,
                            new Const(operand2),
                            operator,
                            PatternEntry.PatternType.BINARY_COMPARISON
                    ))
                    .orElse(Collections.emptyList());
        } else {
            patterns = detectComparison(operand1, operand2, operator);
        }
        for (Pattern p : patterns) {
            p.setPatternType(PatternEntry.PatternType.BINARY_COMPARISON);
        }
        return patterns;
    }

    private List<Pattern> handleRegex(String operand1, String operand2) throws CancelException {
        String operator = "regex";
        List<Pattern> patterns = detectComparison(operand1, operand2, operator);
        for (Pattern p : patterns) {
            p.setPatternType(PatternEntry.PatternType.REGEX);
        }
        return patterns;
    }

    private List<Pattern> handleConstArg(String operand1, String operand2) throws CancelException {
        List<Pattern> patterns = detectConstArg(operand1, operand2);
        for (Pattern p : patterns) {
            p.setPatternType(PatternEntry.PatternType.CONSTANT_ARGUMENT);
        }
        return patterns;
    }

    private List<Pattern> handleAssignConst(String operand1, String operand2) throws CancelException {
        List<Pattern> patterns = detectConstAssign(operand1, operand2);
        for (Pattern p : patterns) {
            p.setPatternType(PatternEntry.PatternType.ASSIGN_CONSTANT);
        }
        return patterns;
    }

    private List<Pattern> handleBinFlag(String operand1, String operand2) throws CancelException {
        List<Pattern> patterns = detectComparisonBinaryFlag(operand1, operand2);
        for (Pattern p : patterns) {
            p.setPatternType(PatternEntry.PatternType.BINARY_FLAG_CHECK);
        }
        return patterns;
    }

    private List<Pattern> handleStrFormat(String operand) throws CancelException {
        List<Pattern> patterns = detectStrFormat(operand);
        for (Pattern p : patterns) {
            p.setPatternType(PatternEntry.PatternType.STR_FORMAT);
        }
        return patterns;
    }

    private List<Pattern> detectComparison(String operand1, String operand2, String operator)
            throws IllegalArgumentException, CancelException {
        List<DetectionSource> getAttributeInsts1 = findAttributes(operand1, true, true, true, true);
        List<Pair<Statement, Collection<Statement>>> slices1 = slice(sdg, getAttributeInsts1);

        List<Pair<Statement, List<Statement>>> matchedStatements1 = findOperator(slices1, operator);

        List<DetectionSource> getAttributeInsts2 = findAttributes(operand2, true, true, true, true);
        List<Pair<Statement, Collection<Statement>>> slices2 = slice(sdg, getAttributeInsts2);
        List<Pair<Statement, List<Statement>>> matchedStatements2 = findOperator(slices2, operator);

        List<Pattern> patterns = getIntersection(matchedStatements1, matchedStatements2);
        return patterns;
    }

    private List<Pattern> detectStrFormat(String operand) throws CancelException {
        List<DetectionSource> getAttributeInsts = findAttributes(operand, true, true, true, true);
        return findStrFormatInMethod(getAttributeInsts);
    }


//    private List<Pattern> detectComparisonBinaryFlag(String operand1, String operator2)
//            throws IllegalArgumentException, CancelException {
//        List<DetectionSource> getAttributeInsts = findAttributes(operand1, true, true, true, true);
//        List<Pair<Statement, Collection<Statement>>> pairs = slice(sdg, getAttributeInsts);
//
//        List<Pair<Statement, List<Statement>>> a = findOperatorConst(pairs, "binOP");
//
//        return findConstInSlice(a, new Const(operator2));
//    }
    private List<Pattern> detectComparisonBinaryFlag(String operand1, String operand2)
            throws IllegalArgumentException, CancelException {
        List<DetectionSource> getAttributeInsts = findAttributes(operand1, true, true, true, true);
        List<Pair<Statement, Collection<Statement>>> slices1 = slice(sdg, getAttributeInsts);
        List<Pair<Statement, List<Statement>>> matchedStatements1 = findOperatorConst(slices1, "binOP");

        Collection<Statement> slices2 = ConstSlice.computeSlice(cg, new Const(operand2));
        List<Pair<Statement, List<Statement>>> matchedStatements2 = findOperatorConst(slices2, "binOP");
        List<Pattern> patterns = getIntersection(matchedStatements1, matchedStatements2);

        return patterns;
    }

    private List<Pattern> detectExpressionComparison(
            String operand1, String operand2, String operator) throws CancelException {
        String[] expression1 = operand1.split(" ");
        String[] expression2 = operand2.split(" ");
        List<Pattern> patterns = new ArrayList<>();
        // both sides are expressions
        if (expression1.length > 1 && expression2.length > 1)
            patterns = detectDoubleExpressionComparison(expression1, expression2, operator);
        else if (expression1.length > 1)
            // lhs is an expression
            patterns = detectLeftExpressionComparison(expression1, expression2[0], operator);
            // rhs is an expression
        else patterns = detectLeftExpressionComparison(expression2, expression1[0], operator);
        return patterns;
    }

    private List<Pattern> detectDoubleExpressionComparison(
            String[] expression1, String[] expression2, String operator) {
        throw new RuntimeException("Not implemented");
    }

    private List<Pattern> detectLeftExpressionComparison(
            String[] expression, String operand, String operator) throws CancelException {
        String left = expression[0];
        String expOperator = expression[1];
        String right = expression[2];

        String[] leftSplit = left.split("#");
        String[] rightSplit = right.split("#");
        // both are local variables
        if (leftSplit.length == 3 && rightSplit.length == 3) {
            if (leftSplit[0].equals(rightSplit[0]) && leftSplit[1].equals(rightSplit[1])) {
                List<DetectionSource> sources =
                        findPairLocalVars(leftSplit[0], leftSplit[1], leftSplit[2], rightSplit[2]);
                List<Pattern> patterns = new ArrayList<>();
                for (DetectionSource s : sources) {
                    patterns.add(
                            new ConstantPattern(s.getSliceSource(), new Const(right), s.getSliceSource()));
                }
                return patterns;
            }
        } else if (rightSplit.length == 2) return new ArrayList<>();
        else if (rightSplit.length == 1) {
            // rhs of expression is a constant
            Const constant = new Const(right);
            List<Pattern> patterns = new ArrayList<>();
            List<DetectionSource> expSources = findAttributes(left, true, true, true, true);
            List<Pair<Statement, Collection<Statement>>> slices = slice(sdg, expSources);
            for (Pair<Statement, Collection<Statement>> p : slices) {
                Collection<Statement> slice = p.snd;
                List<DetectionSource> sources = findOpConstInSlice(slice, constant, expOperator);
                for (DetectionSource s : sources) {
                    patterns.add(new ConstantPattern(p.fst, new Const(right), s.getSliceSource()));
                }
            }
            return patterns;
        }
        return new ArrayList<>();
    }

    private List<Pattern> detectConstArg(String operand, String constant) throws CancelException {
        List<Pattern> patterns = new ArrayList<>();
        List<DetectionSource> filteredSources;
        List<DetectionSource> getAttributeInsts = findAttributes(operand, true, false, true, false);

        if (constant.contains("#")) {
            List<DetectionSource> finalFields = findAttributes(constant, true, false, false, false);
            filteredSources = findConstInMethod(getAttributeInsts, finalFields);

        } else {
            Collection<Statement> constSlice = ConstSlice.computeSlice(cg, new Const(constant));
            filteredSources = findConstInMethod(getAttributeInsts, constSlice);
        }

        for (DetectionSource d : filteredSources) {
            patterns.add(
                    new ConstantPattern(d.getSliceSource(), new Const(constant), d.getSliceSource()));
        }

        return patterns;
    }

    private List<Pattern> detectConstAssign(String operand, String constant) throws CancelException {
        if (operand.split("#").length > 2) {
            return detectLocVarAssign(operand, constant);
        } else if (constant.split("#").length > 1) return findObjectAssign(operand, constant);
        else return findConstAssign(operand, constant);
    }

    private List<Pattern> detectLocVarAssign(String operand, String constant) throws CancelException {
        List<Pattern> patterns = new ArrayList<>();
        String[] inputSplit = operand.split("#");
        String inputClass = inputSplit[0];
        inputClass = "L" + inputClass.replace('.', '/');
        String inputMet = inputSplit[1];
        String inputVar = inputSplit[2];
        Map<CGNode, Set<Integer>> sliceMap = ConstSlice.computeSliceMap(cg, new Const(constant));
        for (Map.Entry<CGNode, Set<Integer>> pair : sliceMap.entrySet()) {
            CGNode node = pair.getKey();
            Set<Integer> values = pair.getValue();
            IR ir = node.getIR();
            if (node.getMethod().getDeclaringClass().getName().toString().equals(inputClass)
                    && node.getMethod().getSelector().getName().toString().equals(inputMet))  {
                if (ir != null) {
                    Iterator<SSAInstruction> instructionIterator = ir.iterateAllInstructions();
                    while (instructionIterator.hasNext()) {
                        SSAInstruction inst = instructionIterator.next();
                        if (inst.iindex > 0) {
                            for (int i = 0; i < inst.getNumberOfUses(); i++) {
                                int vn = inst.getUse(i);
                                if (values.contains(vn)) {
                                    String[] localNames = ir.getLocalNames(inst.iindex, vn);
                                    if (localNames != null) {
                                        for (String name : localNames) {
                                            if (inputVar.equals(name)) {
                                                patterns.add(new SimplePattern(new NormalStatement(node, inst.iindex)));
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }

        }
        return patterns;

//        List<Pattern> patterns = new ArrayList<>();
//        String[] inputSplit = operand.split("#");
//        String inputClass = inputSplit[0];
//        inputClass = "L" + inputClass.replace('.', '/');
//        String inputMet = inputSplit[1];
//        String inputVar = inputSplit[2];
//
//
//
//        List<Pair<CGNode, SSAGetInstruction>> leftPuts = findGetInstruction(inputClass, inputMet, inputVar);
//        List<DetectionSource> constSources = findAttributes(constant, true, true, false, false);
//        List<Pair<Statement, Collection<Statement>>> slices = slice(sdg, constSources);
//        List<Pair<CGNode, SSAGetInstruction>> rightPuts = findGetInSlice(slices);
//        List<Pair<CGNode, SSAGetInstruction>> tmp = leftPuts;
//        tmp.retainAll(rightPuts);
//
//        for (Pair<CGNode, SSAGetInstruction> p : tmp) {
//            patterns.add(new SimplePattern(new NormalStatement(p.fst, p.snd.iindex)));
//        }
//        return patterns;
    }

    private List<Pattern> findObjectAssign(String operand, String constant) throws CancelException {
        List<Pattern> patterns = new ArrayList<>();
        String[] inputSplit = operand.split("#");
        String inputClass = inputSplit[0];
        inputClass = "L" + inputClass.replace('.', '/');
        String inputAttr = inputSplit[1];

        List<Pair<CGNode, SSAPutInstruction>> leftPuts = findPutInstruction(inputClass, inputAttr);

        List<DetectionSource> constSources = findAttributes(constant, true, true, false, false);
        List<Pair<Statement, Collection<Statement>>> slices = slice(sdg, constSources);
        List<Pair<CGNode, SSAPutInstruction>> rightPuts = findPutInSlice(slices);

        List<Pair<CGNode, SSAPutInstruction>> tmp = leftPuts;
        tmp.retainAll(rightPuts);

        for (Pair<CGNode, SSAPutInstruction> p : tmp) {
            patterns.add(new SimplePattern(new NormalStatement(p.fst, p.snd.iindex)));
        }
        return patterns;
    }

    private List<Pair<CGNode, SSAGetInstruction>> findGetInSlice(
            List<Pair<Statement, Collection<Statement>>> pairs) {
        List<Pair<CGNode, SSAGetInstruction>> ret = new ArrayList<>();
        for (Pair<Statement, Collection<Statement>> pair : pairs) {
            for (Statement stmt : pair.snd) {
                if (stmt instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                    if (s.getNode()
                            .getMethod()
                            .getReference()
                            .getDeclaringClass()
                            .getClassLoader()
                            .equals(ClassLoaderReference.Application)) {
                        if (s.getInstruction() instanceof SSAGetInstruction) {
                            SSAGetInstruction getInst = (SSAGetInstruction) s.getInstruction();
                            ret.add(Pair.make(s.getNode(), getInst));
                        }
                    }
                }
            }
        }
        return ret;
    }

    private List<Pair<CGNode, SSAPutInstruction>> findPutInSlice(
            List<Pair<Statement, Collection<Statement>>> pairs) {
        List<Pair<CGNode, SSAPutInstruction>> ret = new ArrayList<>();
        for (Pair<Statement, Collection<Statement>> pair : pairs) {
            for (Statement stmt : pair.snd) {
                if (stmt instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                    if (s.getNode()
                            .getMethod()
                            .getReference()
                            .getDeclaringClass()
                            .getClassLoader()
                            .equals(ClassLoaderReference.Application)) {
                        if (s.getInstruction() instanceof SSAPutInstruction) {
                            SSAPutInstruction putInst = (SSAPutInstruction) s.getInstruction();
                            ret.add(Pair.make(s.getNode(), putInst));
                        }
                    }
                }
            }
        }
        return ret;
    }

    private List<Pair<CGNode, SSAPutInstruction>> findPutInstruction(String inputClass, String inputAttr) {
        List<Pair<CGNode, SSAPutInstruction>> ret = new ArrayList<>();

        Iterator<CGNode> it = cg.iterator();
        while (it.hasNext()) {
            CGNode node = it.next();
            IR ir = node.getIR();
            if (ir != null) {
                Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                while (instIt.hasNext()) {
                    SSAInstruction inst = instIt.next();
                    if (inst instanceof SSAPutInstruction) {
                        SSAPutInstruction putInst = (SSAPutInstruction) inst;
                        if (putInst
                                .getDeclaredField()
                                .getDeclaringClass()
                                .getName()
                                .toString()
                                .equals(inputClass)
                                && putInst.getDeclaredField().getName().toString().equals(inputAttr)) {
                            ret.add(Pair.make(node, putInst));
                        }
                    }
                }
            }
        }
        return ret;
    }

    private List<Pair<CGNode, SSAGetInstruction>> findGetInstruction(
            String inputClass, String inputAttr, String inputVar) {
        List<Pair<CGNode, SSAGetInstruction>> ret = new ArrayList<>();

        Iterator<CGNode> it = cg.iterator();
        while (it.hasNext()) {
            CGNode node = it.next();
            IR ir = node.getIR();
            if (ir != null) {
                Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                while (instIt.hasNext()) {
                    SSAInstruction inst = instIt.next();
                    if (inst instanceof SSAGetInstruction) {
                        SSAGetInstruction getInst = (SSAGetInstruction) inst;
                        if (node.getMethod().getDeclaringClass().getName().toString().equals(inputClass)
                                && node.getMethod().getSelector().getName().toString().equals(inputAttr)) {
                            int vn = getInst.getDef(0);
                            if (getInst.iindex >= 0) {
                                String[] localNames = node.getIR().getLocalNames(inst.iindex, vn);
                                if (localNames != null) {
                                    for (String name : localNames) {
                                        if (name != null && name.equals(inputVar)) {
                                            ret.add(Pair.make(node, getInst));
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    private List<Pattern> findConstAssign(String operand, String constant) {
        String[] inputSplit = operand.split("#");
        if (inputSplit.length < 2) return new ArrayList<>();
        String inputClass = inputSplit[0];
        inputClass = "L" + inputClass.replace('.', '/');
        String inputAttr = inputSplit[1];

        List<Pair<CGNode, SSAPutInstruction>> pairs = findPutInstruction(inputClass, inputAttr);
        List<Pattern> patterns = new ArrayList<>();
        for (Pair<CGNode, SSAPutInstruction> p : pairs) {
            CGNode node = p.fst;
            SSAPutInstruction putInst = p.snd;
            if (matchConstValue(putInst.getUse(1), new Const(constant), node.getIR().getSymbolTable())) {
                patterns.add(new SimplePattern(new NormalStatement(node, putInst.iindex)));
            }
        }
        return patterns;
    }


//    private List<DetectionSource> findConstInMethod(
//            List<DetectionSource> methodSources, List<DetectionSource> finalFields, String constant) {
//        Set<DetectionSource> set = new LinkedHashSet<>();
//        for (DetectionSource d : methodSources) {
//            SymbolTable st = d.getNode().getIR().getSymbolTable();
//            for (int i = 0;
//                 i < d.getNode().getIR().getInstructions()[d.getIndex()].getNumberOfUses();
//                 i++) {
//                int vn = d.getNode().getIR().getInstructions()[d.getIndex()].getUse(i);
//                if (constant.equals("TRUE") || constant.equals("FALSE")) {
//                    if (st.isIntegerConstant(vn)) {
//                        int boolValue = st.getIntValue(vn);
//                        if (boolValue == 0 || boolValue == 1) {
//                            set.add(
//                                    new DetectionSource(
//                                            d.getNode(), d.getIndex(), DetectionSource.StmtType.RET_CALLER));
//                        }
//                    } else if (st.isBooleanConstant(vn)) {
//                        set.add(
//                                new DetectionSource(
//                                        d.getNode(), d.getIndex(), DetectionSource.StmtType.RET_CALLER));
//                    }
//                } else if (st.isConstant(vn)
//                        && !st.isNullConstant(vn)
//                        && constant.equals(st.getConstantValue(vn).toString())) {
//                    set.add(
//                            new DetectionSource(d.getNode(), d.getIndex(), DetectionSource.StmtType.RET_CALLER));
//                } else {
//                    for (DetectionSource f : finalFields) {
//                        if (vn == f.getNode().getIR().getInstructions()[f.getIndex()].getDef()) {
//                            set.add(
//                                    new DetectionSource(d.getNode(), d.getIndex(), DetectionSource.StmtType.RET_CALLER));
//                        }
//                    }
//                }
//
//            }
//        }
//
//        List<DetectionSource> ret = new ArrayList<>();
//        ret.addAll(set);
//        return ret;
//    }
    private List<DetectionSource> findConstInMethod(
            List<DetectionSource> methodSources, List<DetectionSource> finalFields) {
        Set<DetectionSource> set = new LinkedHashSet<>();
        for (DetectionSource d : methodSources) {
            SymbolTable st = d.getNode().getIR().getSymbolTable();
            for (int i = 0;
                 i < d.getNode().getIR().getInstructions()[d.getIndex()].getNumberOfUses();
                 i++) {
                int vn = d.getNode().getIR().getInstructions()[d.getIndex()].getUse(i);
                for (DetectionSource f : finalFields) {
                    if (vn == f.getNode().getIR().getInstructions()[f.getIndex()].getDef()) {
                        set.add(
                                new DetectionSource(d.getNode(), d.getIndex(), DetectionSource.StmtType.RET_CALLER));
                    }
                }
            }
        }

        List<DetectionSource> ret = new ArrayList<>();
        ret.addAll(set);
        return ret;
    }

    private List<DetectionSource> findConstInMethod(
            List<DetectionSource> methodSources, Collection<Statement> constSlice) {
        Set<DetectionSource> set = new LinkedHashSet<>();
        for (DetectionSource d : methodSources) {
            for (Statement constSliceStmt : constSlice) {
                if (constSliceStmt instanceof StatementWithInstructionIndex && d.getSliceSource() instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s_d = (StatementWithInstructionIndex) constSliceStmt;
                    StatementWithInstructionIndex s_c = (StatementWithInstructionIndex) d.getSliceSource();
                    if (s_d.getInstruction().equals(s_c.getInstruction()))
                        set.add(d);
                }
            }
        }

        List<DetectionSource> ret = new ArrayList<>();
        ret.addAll(set);
        return ret;
    }

    private List<DetectionSource> findOpConstInSlice(
            Collection<Statement> slice, Const constant, String operator) {
        return slice.stream()
                .flatMap(s -> {
                    if (s instanceof StatementWithInstructionIndex) {
                        StatementWithInstructionIndex si = (StatementWithInstructionIndex) s;
                        SSAInstruction inst = si.getInstruction();
                        if (inst instanceof SSABinaryOpInstruction) {
                            SSABinaryOpInstruction opInst = (SSABinaryOpInstruction) inst;
                            return IntStream.range(0, opInst.getNumberOfUses())
                                    .boxed()
                                    .map(i -> {
                                        if (matchConstValue(opInst.getUse(i), constant, s.getNode().getIR().getSymbolTable())) {
                                            return new DetectionSource(s.getNode(), opInst.iindex, DetectionSource.StmtType.NORMAL);
                                        } else {
                                            return null;
                                        }
                                    })
                                    .filter(Objects::nonNull);
                        }

                    }
                    return Stream.empty();
                })
                .distinct()
                .collect(Collectors.toList());
    }

    private List<DetectionSource> findConstInInvokes(List<Pair<Statement, List<Statement>>> statements, Const constant) {
        List<DetectionSource> ret = new ArrayList<>();
        for (Pair<Statement, List<Statement>> pair1 : statements) {
            Statement source = pair1.fst;
            List<Statement> stmts = pair1.snd;
            for (Statement operator : stmts) {
                if (operator instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) operator;
                    SymbolTable st = operator.getNode().getIR().getSymbolTable();

                    for (int i = 0; i < s.getInstruction().getNumberOfUses(); i++) {
                        int v = s.getInstruction().getUse(i);
                        String val = constant.getVal();
                        // TODO replace with Utils.isconstant
                        if (st.isNumberConstant(v)) {
                            if (st.isIntegerConstant(v)) {
                                int constInt;
                                if (constant.getConstType() == Const.ConstType.BOOL) {
                                    if ((int) st.getConstantValue(v) == 0 || (int) st.getConstantValue(v) == 1) {
                                        Pattern pattern = new ConstantPattern(source, constant, operator);
                                        ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                                    }
                                } else if ((constant.getConstType() == Const.ConstType.CHAR)) {
                                    constInt = (int) st.getConstantValue(v);
                                    if (constInt >= -128 && constInt < 127) {
                                        if ((char) constInt == constant.getVal().charAt(0)) {
                                            Pattern pattern = new ConstantPattern(source, constant, operator);
                                            ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                                        }
                                    }
                                } else if (constant.getConstType() == Const.ConstType.NUM) {
                                    if (!Utils.isNumeric(constant.getVal())) {
                                        if (val.equals("java.lang.Integer#MAX_VALUE")) {
                                            constInt = Integer.MAX_VALUE;
                                        } else if (val.equals("java.lang.Integer#MIN_VALUE")) {
                                            constInt = Integer.MIN_VALUE;
                                        } else {
                                            char c = constant.getVal().charAt(0);
                                            constInt = c;
                                        }
                                    } else {
                                        constInt = Integer.valueOf(val);
                                    }
                                    if (constInt == (int) st.getConstantValue(v)) {
                                        Pattern pattern = new ConstantPattern(source, constant, operator);
                                        ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                                    }
                                }
                            } else if (st.isLongConstant(v)) {
                                long constLong = 0;
                                if (!Utils.isNumeric(constant.getVal())) {
                                    if (val.equals("java.lang.Long#MAX_VALUE")) {
                                        constLong = Long.MAX_VALUE;
                                    } else if (val.equals("java.lang.Long#MIN_VALUE")) {
                                        constLong = Long.MIN_VALUE;
                                    }
                                } else {
                                    constLong = Long.valueOf(val);
                                }
                                if (constLong == (long) st.getConstantValue(v)) {
                                    Pattern pattern = new ConstantPattern(source, constant, operator);
                                    ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                                }
                            } else if (st.isDoubleConstant(v)) {
                                double constDouble = 0.0;
                                if (!Utils.isNumeric(constant.getVal())) {
                                    if (val.equals("java.lang.Double#POSITIVE_INFINITY")) {
                                        constDouble = Double.POSITIVE_INFINITY;
                                    } else if (val.equals("java.lang.Double#NEGATIVE_INFINITY")) {
                                        constDouble = Double.NEGATIVE_INFINITY;
                                    } else if (val.equals("java.lang.Double#MAX_VALUE")) {
                                        constDouble = Double.MAX_VALUE;
                                    } else if (val.equals("java.lang.Double#MIN_VALUE")) {
                                        constDouble = Double.MIN_VALUE;
                                    }
                                } else {
                                    constDouble = Double.valueOf(val);
                                }
                                if (constDouble == (double) st.getConstantValue(v)) {
                                    Pattern pattern = new ConstantPattern(source, constant, operator);
                                    ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                                }
                            } else if (st.isFloatConstant(v)) {
                                float constFloat = (float) 0.0;
                                if (!Utils.isNumeric(constant.getVal())) {
                                    if (val.equals("java.lang.Float#POSITIVE_INFINITY")) {
                                        constFloat = Float.POSITIVE_INFINITY;
                                    } else if (val.equals("java.lang.Float#NEGATIVE_INFINITY")) {
                                        constFloat = Float.NEGATIVE_INFINITY;
                                    } else if (val.equals("java.lang.Float#MAX_VALUE")) {
                                        constFloat = Float.MAX_VALUE;
                                    } else if (val.equals("java.lang.Float#MIN_VALUE")) {
                                        constFloat = Float.MIN_VALUE;
                                    }
                                } else {
                                    constFloat = Float.valueOf(val);
                                }
                                if (constFloat == (Float) st.getConstantValue(v)) {
                                    Pattern pattern = new ConstantPattern(source, constant, operator);
                                    ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                                }
                            }
                        } else if (st.isStringConstant(v) && constant.getConstType() == Const.ConstType.STR) {
                            if (st.getConstantValue(v).equals(constant.getVal())) {
                                Pattern pattern = new ConstantPattern(source, constant, operator);
                                ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                            }
                        } else if (st.isBooleanConstant(v) && constant.getConstType() == Const.ConstType.BOOL) {
                            Pattern pattern = new ConstantPattern(source, constant, operator);
                            ret.add(new DetectionSource(operator.getNode(), s.getInstructionIndex(), DetectionSource.StmtType.PARAMCALLER, v));
                        }
                    }
                }
            }
        }
        return ret;
    }

    private List<Pattern> findConstInSlice(
            List<Pair<Statement, List<Statement>>> statements, Const constant) {
        List<Pattern> ret = new ArrayList<>();
        for (Pair<Statement, List<Statement>> pair1 : statements) {
            Statement source = pair1.fst;
            List<Statement> stmts = pair1.snd;
            for (Statement operator : stmts) {
                if (operator instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) operator;
                    SymbolTable st = operator.getNode().getIR().getSymbolTable();

                    for (int i = 0; i < s.getInstruction().getNumberOfUses(); i++) {
                        int v = s.getInstruction().getUse(i);
                        String val = constant.getVal();
                        if (st.isNumberConstant(v)) {
                            if (st.isIntegerConstant(v)) {
                                int constInt;
                                if (constant.getConstType() == Const.ConstType.BOOL) {
                                    if ((int) st.getConstantValue(v) == 0 || (int) st.getConstantValue(v) == 1) {
                                        Pattern pattern = new ConstantPattern(source, constant, operator);
                                        ret.add(pattern);
                                    }
                                } else if ((constant.getConstType() == Const.ConstType.CHAR)) {
                                    constInt = (int) st.getConstantValue(v);
                                    if (constInt >= -128 && constInt < 127) {
                                        if ((char) constInt == constant.getVal().charAt(0)) {
                                            Pattern pattern = new ConstantPattern(source, constant, operator);
                                            ret.add(pattern);
                                        }
                                    }
                                } else if (constant.getConstType() == Const.ConstType.NUM) {
                                    if (!Utils.isNumeric(constant.getVal())) {
                                        if (val.equals("java.lang.Integer#MAX_VALUE")) {
                                            constInt = Integer.MAX_VALUE;
                                        } else if (val.equals("java.lang.Integer#MIN_VALUE")) {
                                            constInt = Integer.MIN_VALUE;
                                        } else {
                                            char c = constant.getVal().charAt(0);
                                            constInt = c;
                                        }
                                    } else {
                                        constInt = Integer.valueOf(val);
                                    }
                                    if (constInt == (int) st.getConstantValue(v)) {
                                        Pattern pattern = new ConstantPattern(source, constant, operator);
                                        ret.add(pattern);
                                    }
                                }
                            } else if (st.isLongConstant(v)) {
                                long constLong = 0;
                                if (!Utils.isNumeric(constant.getVal())) {
                                    if (val.equals("java.lang.Long#MAX_VALUE")) {
                                        constLong = Long.MAX_VALUE;
                                    } else if (val.equals("java.lang.Long#MIN_VALUE")) {
                                        constLong = Long.MIN_VALUE;
                                    }
                                } else {
                                    constLong = Long.valueOf(val);
                                }
                                if (constLong == (long) st.getConstantValue(v)) {
                                    Pattern pattern = new ConstantPattern(source, constant, operator);
                                    ret.add(pattern);
                                }
                            } else if (st.isDoubleConstant(v)) {
                                double constDouble = 0.0;
                                if (!Utils.isNumeric(constant.getVal())) {
                                    if (val.equals("java.lang.Double#POSITIVE_INFINITY")) {
                                        constDouble = Double.POSITIVE_INFINITY;
                                    } else if (val.equals("java.lang.Double#NEGATIVE_INFINITY")) {
                                        constDouble = Double.NEGATIVE_INFINITY;
                                    } else if (val.equals("java.lang.Double#MAX_VALUE")) {
                                        constDouble = Double.MAX_VALUE;
                                    } else if (val.equals("java.lang.Double#MIN_VALUE")) {
                                        constDouble = Double.MIN_VALUE;
                                    }
                                } else {
                                    constDouble = Double.valueOf(val);
                                }
                                if (constDouble == (double) st.getConstantValue(v)) {
                                    Pattern pattern = new ConstantPattern(source, constant, operator);
                                    ret.add(pattern);
                                }
                            } else if (st.isFloatConstant(v)) {
                                float constFloat = (float) 0.0;
                                if (!Utils.isNumeric(constant.getVal())) {
                                    if (val.equals("java.lang.Float#POSITIVE_INFINITY")) {
                                        constFloat = Float.POSITIVE_INFINITY;
                                    } else if (val.equals("java.lang.Float#NEGATIVE_INFINITY")) {
                                        constFloat = Float.NEGATIVE_INFINITY;
                                    } else if (val.equals("java.lang.Float#MAX_VALUE")) {
                                        constFloat = Float.MAX_VALUE;
                                    } else if (val.equals("java.lang.Float#MIN_VALUE")) {
                                        constFloat = Float.MIN_VALUE;
                                    }
                                } else {
                                    constFloat = Float.valueOf(val);
                                }
                                if (constFloat == (Float) st.getConstantValue(v)) {
                                    Pattern pattern = new ConstantPattern(source, constant, operator);
                                    ret.add(pattern);
                                }
                            }
                        } else if (st.isStringConstant(v) && constant.getConstType() == Const.ConstType.STR) {
                            if (st.getConstantValue(v).equals(constant.getVal())) {
                                Pattern pattern = new ConstantPattern(source, constant, operator);
                                ret.add(pattern);
                            }
                        } else if (st.isBooleanConstant(v) && constant.getConstType() == Const.ConstType.BOOL) {
                            Pattern pattern = new ConstantPattern(source, constant, operator);
                            ret.add(pattern);
                        }
                    }
                }
            }
        }
        return ret;
    }

    private Iterable<Entrypoint> makeAllPublicEntrypoints(IClassHierarchy cha) {
        Collection<Entrypoint> ret = new ArrayList<Entrypoint>();
        Iterator<IClass> classIteration = cha.iterator();
        while (classIteration.hasNext()) {
            IClass ic = classIteration.next();
            ClassLoaderReference type = ic.getClassLoader().getReference();
            if (type.equals(ClassLoaderReference.Application)) {
                for (IMethod m : ic.getDeclaredMethods()) {
                    if (m.isPublic()) {
                        ret.add(new DefaultEntrypoint(m, cha));
                    }
                }
            }
        }
        return ret;
    }

    // TODO simplify this method

    private Set<DetectionSource> findLocalVars(
            Set<DetectionSource> set, String inputClass, String inputAttr, String inputVar) {
        Iterator<CGNode> it = cg.iterator();
        while (it.hasNext()) {
            CGNode node = it.next();
            if (node.getMethod().getDeclaringClass().getName().toString().equals(inputClass)
                    && node.getMethod().getSelector().getName().toString().equals(inputAttr)) {
                IR ir = node.getIR();
                if (ir != null) {
                    Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                    while (instIt.hasNext()) {
                        SSAInstruction inst = instIt.next();
                        if (inst.getNumberOfUses() > 0) {
                            int numUse = inst.getNumberOfUses();
                            for (int i = 0; i < numUse; i++) {
                                int vn = inst.getUse(i);
                                if (inst.iindex >= 0) {
                                    String[] localNames = node.getIR().getLocalNames(inst.iindex, vn);
                                    if (localNames != null) {
                                        for (String name : localNames) {
                                            if (name != null && name.equals(inputVar)) {
                                                if (inst instanceof SSAInvokeInstruction) {
                                                    SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
                                                    set.add(
                                                            new DetectionSource(
                                                                    node,
                                                                    inst.iindex,
                                                                    DetectionSource.StmtType.PARAMCALLER,
                                                                    vn));
                                                } else {
                                                    set.add(
                                                            new DetectionSource(
                                                                    node, inst.iindex, DetectionSource.StmtType.NORMAL, vn));
                                                }
                                                break;
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return set;
    }
    private List<DetectionSource> findTwoStepAttributes(String input) throws CancelException {
        input = input.substring(1);
        String attriInput = input.split("@")[0];
        String mName = input.split("@")[1];
        List<DetectionSource> ret = new ArrayList<>();
        List<DetectionSource> getAttributeInsts = findAttributes(attriInput, true, true, true, false);
        List<Pair<Statement, Collection<Statement>>> slices = slice(sdg, getAttributeInsts);
        List<Pair<Statement, List<Statement>>> matchedStatements = findOperator(slices, mName);
        Set<Statement> matchedSet = new HashSet();
        for (Pair<Statement, List<Statement>> pair : matchedStatements) {
            Statement source = pair.fst;
            List<Statement> list = pair.snd;
            matchedSet.addAll(list);
        }
        for (Statement s : matchedSet) {
            if (s instanceof StatementWithInstructionIndex) {
                StatementWithInstructionIndex si = (StatementWithInstructionIndex) s;
                ret.add(
                        new DetectionSource(
                                s.getNode(), si.getInstruction().iindex, DetectionSource.StmtType.RET_CALLER));
            }
        }
        return ret;
    }

    private List<DetectionSource> findMethodInSlice(
            List<Pair<Statement, Collection<Statement>>> slices, String mName) {
        Set<DetectionSource> set = new LinkedHashSet<>();
        for (Pair<Statement, Collection<Statement>> pair : slices) {
            Collection<Statement> slice = pair.snd;
            for (Statement stmt : slice) {
                if (stmt instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                    SSAInstruction inst = s.getInstruction();
                    if (inst instanceof SSAInvokeInstruction) {
                        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
                        if (invokeInst.getDeclaredTarget().getSelector().getName().toString().equals(mName)) {
                            set.add(
                                    new DetectionSource(
                                            s.getNode(), inst.iindex, DetectionSource.StmtType.RET_CALLER));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(set);
    }

    // TODO Break up this by attribute type
    // TODO find fields and methods via the cha instead of iterating the cg

    private List<DetectionSource> findAttributes(
            String input, boolean field, boolean value, boolean method, boolean constant)
            throws CancelException {
        Set<DetectionSource> set = new LinkedHashSet<>();

        if (input.length() == 0) return new ArrayList<>();
        if (input.charAt(0) == '!') return findTwoStepAttributes(input);

        String[] inputSplit = input.split("#");
        if (inputSplit.length < 2) return new ArrayList<>();
        String inputClass = "L" + inputSplit[0].replace('.', '/');
        List<String> classNames = getChaStrings(inputClass);
        String inputAttr = inputSplit[1];
        // local variable of a method
        if (inputSplit.length == 3) {
            String inputVar = inputSplit[2];
            set = findLocalVars(set, inputClass, inputAttr, inputVar);
        } else {
            Iterator<CGNode> it = cg.iterator();
            while (it.hasNext()) {
                CGNode node = it.next();
                if (node.getMethod()
                        .getReference()
                        .getDeclaringClass()
                        .getClassLoader()
                        .equals(ClassLoaderReference.Application)) {

                    IR ir = node.getIR();
                    if (ir == null) {
                        continue;
                    }

                    String nodeClass = node.getMethod().getDeclaringClass().getName().toString();

                    // Write IR if the input is a method
                    if (inputAttr.equals(node.getMethod().getName().toString()) &&
                            inputClass.equals(nodeClass)) {
                        debugWriter.writeIR(node);
                        // Or if all methods from the class should be printed
                    }

                    Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                    while (instIt.hasNext()) {
                        SSAInstruction inst = instIt.next();
                        if (inst instanceof SSAGetInstruction) {
                            SSAGetInstruction getInst = (SSAGetInstruction) inst;
                            if (field) {
                                String f = getInst.getDeclaredField().getName().toString();
                                if (inputAttr.equals(f) && matchClassName(getInst, classNames)) {
                                    set.add(
                                            new DetectionSource(node, getInst.iindex, DetectionSource.StmtType.NORMAL));
                                }
                            }
                            if (value) {
                                String v = getInst.getDeclaredField().getName().toString();
                                String cd = getInst.getDeclaredField().getDeclaringClass().getName().toString();
                                if (inputAttr.equals(v) && inputClass.equals(cd)) {
                                    set.add(
                                            new DetectionSource(node, getInst.iindex, DetectionSource.StmtType.NORMAL));
                                }
                            }
                        } else if (inst instanceof SSAInvokeInstruction) {
                            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
                            if (method) {
                                String m = invokeInst.getDeclaredTarget().getSelector().getName().toString();
                                if (inputAttr.equals(m) && matchClassName(invokeInst, classNames)) {
                                    if (!invokeInst.getDeclaredTarget().getReturnType().equals(TypeReference.Void)) {
                                        set.add(
                                                new DetectionSource(
                                                        node, invokeInst.iindex, DetectionSource.StmtType.RET_CALLER));
                                    } else {
                                        set.add(
                                                new DetectionSource(
                                                        node, invokeInst.iindex, DetectionSource.StmtType.NORMAL));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        List<DetectionSource> ret = new ArrayList<>();
        ret.addAll(set);
        return ret;
    }
    private List<DetectionSource> findPairLocalVars(
            String cName, String mName, String var1, String var2) {
        cName = "L" + cName.replace('.', '/');
        Set<DetectionSource> set = new LinkedHashSet<>();
        Iterator<CGNode> it = cg.iterator();
        while (it.hasNext()) {
            CGNode node = it.next();
            if (node.getMethod().getDeclaringClass().getName().toString().equals(cName)
                    && node.getMethod().getSelector().getName().toString().equals(mName)) {
                IR ir = node.getIR();
                if (ir != null) {
                    Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                    while (instIt.hasNext()) {
                        SSAInstruction inst = instIt.next();
                        if (inst instanceof SSABinaryOpInstruction) {
                            SSABinaryOpInstruction opInst = (SSABinaryOpInstruction) inst;
                            if (opInst.iindex >= 0) {
                                int vn1 = opInst.getUse(0);
                                int vn2 = opInst.getUse(1);
                                String[] localNames1 = node.getIR().getLocalNames(inst.iindex, vn1);
                                String[] localNames2 = node.getIR().getLocalNames(inst.iindex, vn2);
                                if ((checkContainsName(localNames1, var1) && checkContainsName(localNames2, var2))
                                        || checkContainsName(localNames1, var2) && checkContainsName(localNames2, var1))
                                    set.add(
                                            new DetectionSource(node, opInst.iindex, DetectionSource.StmtType.NORMAL));
                            }
                        }
                    }
                }
            }
        }
        List<DetectionSource> ret = new ArrayList<>();
        ret.addAll(set);
        return ret;
    }

    // TODO break this up by constant type

    private boolean matchConstValue(int v, Const constant, SymbolTable st) {
        String val = constant.getVal();
        if (st.isNumberConstant(v)) {
            if (st.isIntegerConstant(v)) {
                int constInt;
                if (constant.getConstType() == Const.ConstType.BOOL) {
                    if ((int) st.getConstantValue(v) == 0 || (int) st.getConstantValue(v) == 1) {
                        return true;
                    }
                } else if (constant.getConstType() == Const.ConstType.NUM) {
                    if (!Utils.isNumeric(constant.getVal())) {
                        if (val.equals("java.lang.Integer#MAX_VALUE")) {
                            constInt = Integer.MAX_VALUE;
                        } else if (val.equals("java.lang.Integer#MIN_VALUE")) {
                            constInt = Integer.MIN_VALUE;
                        } else {
                            char c = constant.getVal().charAt(0);
                            constInt = c;
                        }
                    } else {
                        constInt = Integer.valueOf(val);
                    }
                    if (constInt == (int) st.getConstantValue(v)) {
                        return true;
                    }
                }
            } else if (st.isLongConstant(v)) {
                long constLong = 0;
                if (!Utils.isNumeric(constant.getVal())) {
                    if (val.equals("java.lang.Long#MAX_VALUE")) {
                        constLong = Long.MAX_VALUE;
                    } else if (val.equals("java.lang.Long#MIN_VALUE")) {
                        constLong = Long.MIN_VALUE;
                    }
                } else {
                    constLong = Long.valueOf(val);
                }
                if (constLong == (long) st.getConstantValue(v)) {
                    return true;
                }
            } else if (st.isDoubleConstant(v)) {
                double constDouble = 0.0;
                if (!Utils.isNumeric(constant.getVal())) {
                    if (val.equals("java.lang.Double#POSITIVE_INFINITY")) {
                        constDouble = Double.POSITIVE_INFINITY;
                    } else if (val.equals("java.lang.Double#NEGATIVE_INFINITY")) {
                        constDouble = Double.NEGATIVE_INFINITY;
                    } else if (val.equals("java.lang.Double#MAX_VALUE")) {
                        constDouble = Double.MAX_VALUE;
                    } else if (val.equals("java.lang.Double#MIN_VALUE")) {
                        constDouble = Double.MIN_VALUE;
                    }
                } else {
                    constDouble = Double.valueOf(val);
                }
                if (constDouble == (double) st.getConstantValue(v)) {
                    return true;
                }
            } else if (st.isFloatConstant(v)) {
                float constFloat = (float) 0.0;
                if (!Utils.isNumeric(constant.getVal())) {
                    if (val.equals("java.lang.Float#POSITIVE_INFINITY")) {
                        constFloat = Float.POSITIVE_INFINITY;
                    } else if (val.equals("java.lang.Float#NEGATIVE_INFINITY")) {
                        constFloat = Float.NEGATIVE_INFINITY;
                    } else if (val.equals("java.lang.Float#MAX_VALUE")) {
                        constFloat = Float.MAX_VALUE;
                    } else if (val.equals("java.lang.Float#MIN_VALUE")) {
                        constFloat = Float.MIN_VALUE;
                    }
                } else {
                    constFloat = Float.valueOf(val);
                }
                if (constFloat == (Float) st.getConstantValue(v)) {
                    return true;
                }
            }
        } else if (st.isStringConstant(v)) {
            if (st.getConstantValue(v).equals(constant.getVal())) {
                return true;
            }
        }
        return false;
    }
    private boolean checkContainsName(String[] nameList, String name) {
        if (nameList == null) return false;
        for (String n : nameList) {
            if (n != null && n.equals(name)) return true;
        }
        return false;
    }

    // TODO make this return Slice type

    private List<Pair<Statement, Collection<Statement>>> slice(SDG sdg, List<DetectionSource> sources)
            throws CancelException {
        List<Pair<Statement, Collection<Statement>>> ret =
                new ArrayList<>();
        for (DetectionSource s : sources) {
            Statement statement = s.getSliceSource();
            Collection<Statement> slice = Slicer.computeForwardSlice(sdg, statement);

            debugWriter.writeSlice(statement, slice);

            if (slice.size() > 0) {
                Collection<Statement> filteredSlice = new ArrayList<>();
                for (Statement stmt : slice) {
                    if (stmt.getNode()
                            .getMethod()
                            .getReference()
                            .getDeclaringClass()
                            .getClassLoader()
                            .equals(ClassLoaderReference.Application)) {
                        filteredSlice.add(stmt);
                    }
                }
                Pair<Statement, Collection<Statement>> p = Pair.make(statement, filteredSlice);
                ret.add(p);
            }
        }
        return ret;
    }
    private List<Pattern> findStrFormatInMethod(List<DetectionSource> sources) {
        List<Pattern> patterns = new ArrayList<>();
        for (DetectionSource d : sources) {
            Statement stmt = d.getSliceSource();
            int v = d.v;
            // match v number with def of instruction will miss some cases
            IR ir = stmt.getNode().getIR();
            if (ir != null) {
                Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                while (instIt.hasNext()) {
                    SSAInstruction inst = instIt.next();
                    if (inst instanceof SSAInvokeInstruction) {
                        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
                        if (invokeInst
                                .getDeclaredTarget()
                                .getDescriptor()
                                .toString()
                                .contains("Ljava/lang/String")
                                && invokeInst
                                .getDeclaredTarget()
                                .getSelector()
                                .getName()
                                .toString()
                                .equals("format")) {
                            patterns.add(new SimplePattern(new NormalStatement(stmt.getNode(), invokeInst.iindex)));
                        }
                    }
                }
            }
        }
        return patterns;
    }

    public static List<Pair<Statement, List<Statement>>> findOperatorConst(
            List<Pair<Statement, Collection<Statement>>> pairs, String operator)
            throws IllegalArgumentException {
        List<Pair<Statement, List<Statement>>> ret = new ArrayList<>();
        for (Pair<Statement, Collection<Statement>> pair : pairs) {
            List<Statement> matchedStatements = new ArrayList<>();
            for (Statement stmt : pair.snd) {
                if (stmt instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                    if (s.getNode()
                            .getMethod()
                            .getReference()
                            .getDeclaringClass()
                            .getClassLoader()
                            .equals(ClassLoaderReference.Application)) {
                        if (operator.equals("eq") || operator.equals("neq")) {
                            if (s.getInstruction() instanceof SSAInvokeInstruction) {
                                SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) s.getInstruction();
                                if (invokeInst.getDeclaredTarget().getName().toString().contains("equals")) {
                                    matchedStatements.add(s);
                                } else {
                                    Iterator<SSAInstruction> it = s.getNode().getIR().getBasicBlockForInstruction(invokeInst).iterator();
                                    while (it.hasNext()) {
                                        SSAInstruction inst = it.next();
                                        if (inst.equals(invokeInst)) {
                                            if (it.hasNext()) {
                                                SSAInstruction next = it.next();
                                                if (next instanceof SSAConditionalBranchInstruction) {
                                                    SSAConditionalBranchInstruction condInst = (SSAConditionalBranchInstruction) next;
                                                    for (int i = 0; i < condInst.getNumberOfUses(); i++) {
                                                        int v = condInst.getUse(i);
                                                        if (v == invokeInst.getDef()) {
                                                            matchedStatements.add(s);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (s.getInstruction() instanceof SSAConditionalBranchInstruction) {
                                SSAConditionalBranchInstruction conInst =
                                        (SSAConditionalBranchInstruction) s.getInstruction();
                                if (conInst.getOperator().equals(IConditionalBranchInstruction.Operator.EQ)
                                        || conInst.getOperator().equals(IConditionalBranchInstruction.Operator.NE)) {
                                    matchedStatements.add(s);
                                }
                            }
                        } else if (operator.equals("gt")
                                || operator.equals("lt")
                                || operator.equals("gte")
                                || operator.equals("lte")) {
                            if (s.getInstruction() instanceof SSAConditionalBranchInstruction) {
                                SSAConditionalBranchInstruction conInst =
                                        (SSAConditionalBranchInstruction) s.getInstruction();
                                if (operator.equals("gt") || operator.equals("lte")) {
                                    if (conInst.getOperator().equals(IConditionalBranchInstruction.Operator.GT)
                                            || conInst.getOperator().equals(IConditionalBranchInstruction.Operator.LE)) {
                                        matchedStatements.add(s);
                                    }
                                } else if (operator.equals("lt") || operator.equals("gte")) {
                                    if (conInst.getOperator().equals(IConditionalBranchInstruction.Operator.LT)
                                            || conInst.getOperator().equals(IConditionalBranchInstruction.Operator.GE)) {
                                        matchedStatements.add(s);
                                    }
                                }
                            } else if (s.getInstruction() instanceof SSAComparisonInstruction) {
                                SSAComparisonInstruction compInst = (SSAComparisonInstruction) s.getInstruction();
                                matchedStatements.add(s);
                            }
                        } else if (operator.equals("binOP")) {
                            if (s.getInstruction() instanceof SSABinaryOpInstruction) {
                                SSABinaryOpInstruction binOpInst = (SSABinaryOpInstruction) s.getInstruction();
                                matchedStatements.add(s);
                            }
                        } else {
                            String mName = null;
                            if (operator.equals("regex")) mName = "match";
                            else if (operator.equals("str-starts")) mName = "startsWith";
                            else if (operator.equals("str-format")) mName = "format";
                            if (s.getInstruction() instanceof SSAInvokeInstruction) {
                                SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) s.getInstruction();
                                if (invokeInst.getDeclaredTarget().getName().toString().contains(mName)) {
                                    matchedStatements.add(s);
                                }
                            }
                        }
                    }
                }
            }
            if (matchedStatements.size() > 0) {
                Pair<Statement, List<Statement>> p = Pair.make(pair.fst, matchedStatements);
                ret.add(p);
            }
        }
        return ret;
    }

    public static List<Pair<Statement, List<Statement>>> findOperatorConst(Collection<Statement> statements, String operator) {
        if (statements.size() == 0)
            return new ArrayList<>();
        List<Pair<Statement, Collection<Statement>>> fakeList = new ArrayList<>();
        List<Statement> stmtlist = new ArrayList<>(statements);
        fakeList.add(Pair.make(stmtlist.get(0), statements));
        return findOperatorConst(fakeList, operator);
    }

    private List<Pair<Statement, List<Statement>>> findInvokeConst(
            List<Pair<Statement, Collection<Statement>>> pairs)
            throws IllegalArgumentException {
        List<Pair<Statement, List<Statement>>> ret = new ArrayList<>();
        for (Pair<Statement, Collection<Statement>> pair : pairs) {
            List<Statement> matchedStatements = new ArrayList<>();
            for (Statement stmt : pair.snd) {
                if (stmt instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                    if (s.getNode()
                            .getMethod()
                            .getReference()
                            .getDeclaringClass()
                            .getClassLoader()
                            .equals(ClassLoaderReference.Application)) {
                        if (s.getInstruction() instanceof SSAInvokeInstruction) {
                            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) s.getInstruction();
                            SymbolTable st = s.getNode().getIR().getSymbolTable();
                            matchedStatements.add(s);
                        }
                    }
                }
            }
            if (matchedStatements.size() > 0) {
                Pair<Statement, List<Statement>> p = Pair.make(pair.fst, matchedStatements);
                ret.add(p);
            }
        }
        return ret;
    }

    // TODO is this a duplicate of findBinaryOperationStatements?

    private List<Pair<Statement, List<Statement>>> findOperator(
            List<Pair<Statement, Collection<Statement>>> pairs, String operator)
            throws IllegalArgumentException {
        List<Pair<Statement, List<Statement>>> ret = new ArrayList<Pair<Statement, List<Statement>>>();
        for (Pair<Statement, Collection<Statement>> pair : pairs) {
            List<Statement> matchedStatements = new ArrayList<Statement>();
            for (Statement stmt : pair.snd) {
                if (stmt instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                    if (s.getNode()
                            .getMethod()
                            .getReference()
                            .getDeclaringClass()
                            .getClassLoader()
                            .equals(ClassLoaderReference.Application)) {
                        if (operator.equals("eq") || operator.equals("neq")) {
                            if (s.getInstruction() instanceof SSAInvokeInstruction) {
                                SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) s.getInstruction();
                                if (invokeInst.getDeclaredTarget().getName().toString().contains("equals")) {
                                    matchedStatements.add(s);
                                }
                            } else if (s.getInstruction() instanceof SSAConditionalBranchInstruction) {
                                SSAConditionalBranchInstruction condInst =
                                        (SSAConditionalBranchInstruction) s.getInstruction();
                                if (condInst.getOperator().equals(IConditionalBranchInstruction.Operator.EQ)
                                        || condInst.getOperator().equals(IConditionalBranchInstruction.Operator.NE)) {
                                    matchedStatements.add(s);
                                }
                            }
                        } else if (operator.equals("gt") || operator.equals("lt") || operator.equals("gte") || operator.equals("lte")) {
                            if (s.getInstruction() instanceof SSAConditionalBranchInstruction) {
                                SSAConditionalBranchInstruction conInst =
                                        (SSAConditionalBranchInstruction) s.getInstruction();
                                if (operator.equals("gt") || operator.equals("lte")) {
                                    if (conInst.getOperator().equals(IConditionalBranchInstruction.Operator.GT)
                                            || conInst.getOperator().equals(IConditionalBranchInstruction.Operator.LE)) {
                                        matchedStatements.add(s);
                                    }
                                } else if (operator.equals("lt") || operator.equals("gte")) {
                                    if (conInst.getOperator().equals(IConditionalBranchInstruction.Operator.LT)
                                            || conInst.getOperator().equals(IConditionalBranchInstruction.Operator.GE)) {
                                        matchedStatements.add(s);
                                    }
                                }
                            }
                        } else if (operator.equals("regex")) {
                            if (s.getInstruction() instanceof SSAInvokeInstruction) {
                                SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) s.getInstruction();
                                if (invokeInst.getDeclaredTarget().getName().toString().equals("matcher")) {
                                    matchedStatements.add(s);
                                }
                            }
                        } else if (operator.equals("invoke")) {
                            if (s.getInstruction() instanceof SSAInvokeInstruction) {
                                SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) s.getInstruction();
                                matchedStatements.add(s);

                            }
                        } else {
                            if (s.getInstruction() instanceof SSAInvokeInstruction) {
                                SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) s.getInstruction();
                                if (invokeInst.getDeclaredTarget().getName().toString().equals(operator)) {
                                    matchedStatements.add(s);
                                }
                            }
                        }
                    }
                }
            }
            if (matchedStatements.size() > 0) {
                Pair<Statement, List<Statement>> p = Pair.make(pair.fst, matchedStatements);
                ret.add(p);
            }
        }
        return ret;
    }
    private List<Pattern> getIntersection(
            List<Pair<Statement, List<Statement>>> statements1,
            List<Pair<Statement, List<Statement>>> statements2) {
        List<Pattern> ret = new ArrayList<Pattern>();

        Set<Statement> left = new HashSet<>();
        Set<Statement> right = new HashSet<>();

        for (Pair<Statement, List<Statement>> pair : statements1) {
            for (Statement s : pair.snd) {
                left.add(s);
            }
        }
        for (Pair<Statement, List<Statement>> pair : statements2) {
            for (Statement s : pair.snd) {
                right.add(s);
            }
        }
        Set<Statement> intersection = new HashSet<>(left);
        left.retainAll(right);
        if (intersection.size() > 0) {
            for (Statement operator : intersection) {
                Pattern pattern = new SimplePattern(operator);
                ret.add(pattern);
            }
        }
        return ret;
    }

    private List<String> getChaStrings(String inputClass) {
        Iterator<IClass> iter = cha.iterator();
        IClass inputIClass = null;
        while (iter.hasNext()) {
            IClass iClass = iter.next();
            if (iClass.getName().toString().equals(inputClass)) {
                inputIClass = iClass;
                break;
            }
        }
        if (inputIClass == null) {
            logger.error(withSystemName(inputClass + " not found in class hierarchy"));
            return Collections.emptyList();
        }

        List<String> ret = iterateSubClasses(inputIClass);
        return ret;
    }

    private List<String> iterateSubClasses(IClass iClass) {
        List<String> ret = new ArrayList<>();
        ret.add(iClass.getName().toString());
        for (IClass klass : cha.getImmediateSubclasses(iClass)) {
            ret.addAll(iterateSubClasses(klass));
        }
        return ret;
    }

    private boolean matchClassName(SSAInvokeInstruction invokeInst, List<String> classNames) {
        String c = invokeInst.getCallSite().getDeclaredTarget().getDeclaringClass().getName().toString();
        return classNames.contains(c);
    }

    private boolean matchClassName(SSAGetInstruction getInst, List<String> classNames) {
        String c = getInst.getDeclaredField().getDeclaringClass().getName().toString();
        return classNames.contains(c);
    }

    public void printIR(CharSequence className, CharSequence methodName) throws InvalidClassFileException {
        Iterator<CGNode> it = cg.iterator();
        while (it.hasNext()) {
            CGNode node = it.next();

            String nodeString = node.toString();

            if (nodeString.contains(className) && nodeString.contains(methodName)) {
                System.out.println("----------------" + className + "----------------");
                System.out.println(node.getIR());
                IMethod m = node.getMethod();
                if (m instanceof ShrikeCTMethod) {
                    ShrikeCTMethod btM = (ShrikeCTMethod) m;
                    for (byte b : btM.getBytecodes())
                        System.out.println(b);
                }
            }
        }
    }

    private String withSystemName(String s) {
        return String.format("[%s] %s", systemName, s);
    }
}


