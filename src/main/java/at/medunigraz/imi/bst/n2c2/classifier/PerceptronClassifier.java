package at.medunigraz.imi.bst.n2c2.classifier;

import at.medunigraz.imi.bst.n2c2.fasttext.FastTextFacade;
import at.medunigraz.imi.bst.n2c2.model.Criterion;
import at.medunigraz.imi.bst.n2c2.model.Eligibility;
import at.medunigraz.imi.bst.n2c2.model.Patient;
import at.medunigraz.imi.bst.n2c2.nn.DataUtilities;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class PerceptronClassifier extends CriterionBasedClassifier {

    private static final File PRETRAINED_VECTORS = new File(PerceptronClassifier.class.getClassLoader().getResource("vectors.vec").getFile());

    public PerceptronClassifier(Criterion c) {
        super(c);
    }

    private String preprocess(String text) {
        // TODO reduce ntokens (remove numbers with AlphabeticTokenizer?) or change fasttext's MAX_SIZE
        // TODO consider removing stopwords, punctuation, stemming, and time restriction.
        return String.join(" ", DataUtilities.getTokens(text));
    }

    @Override
    public List<Patient> predict(List<Patient> patientList) {
        List<String> texts = patientList.stream().map(p -> preprocess(p.getText())).collect(Collectors.toList());
        List<String> predictions = FastTextFacade.predict(texts);
        for (int i = 0; i < patientList.size(); i++) {
            patientList.get(i).withCriterion(criterion, Eligibility.valueOf(predictions.get(i)));
        }
        return patientList;
    }

    @Override
    public Eligibility predict(Patient p) {
        return Eligibility.valueOf(FastTextFacade.predict(preprocess(p.getText())));
    }

    @Override
    public void train(List<Patient> examples) {
        Map<String, String> trainData = new TreeMap<>();
        for (Patient p : examples) {
            trainData.put(preprocess(p.getText()), p.getEligibility(criterion).name());
        }

//        FastTextFacade.train(trainData);
        FastTextFacade.train(trainData, PRETRAINED_VECTORS);
    }
}