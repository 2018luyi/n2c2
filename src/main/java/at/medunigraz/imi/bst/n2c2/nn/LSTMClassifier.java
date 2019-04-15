package at.medunigraz.imi.bst.n2c2.nn;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.AdaGrad;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import at.medunigraz.imi.bst.n2c2.model.Patient;

/**
 * LSTM classifier for n2c2 task 2018 refactored from dl4j examples.
 *
 * @author Markus
 *
 */
public class LSTMClassifier extends BaseNNClassifier {

	// length for truncated backpropagation through time
	private int tbpttLength = 50;

	// total number of training epochs
	private int nEpochs = 50;

	// Google word vector size
	int vectorSize = 200;

	// accessing Google word vectors
	private WordVectors wordVectors;

	// tokenizer logic
	private TokenizerFactory tokenizerFactory;

	// location of precalculated vectors
	private String wordVectorsPath = "vectors.tsv";

	// logging
	private static final Logger LOG = LogManager.getLogger();

	public LSTMClassifier() {

		this.wordVectors = WordVectorSerializer.loadStaticModel(new File(wordVectorsPath));
		initializeTokenizer();
		initializeCriterionIndex();
	}

	private void initializeTokenizer() {
		tokenizerFactory = new DefaultTokenizerFactory();
		tokenizerFactory.setTokenPreProcessor(new CommonPreprocessor());
	}

	@Override
	protected void initializeNetwork() {
		initializeTruncateLength();
		initializeNetworkBinaryMultiLabelDebug();
	}

	/**
	 * SIGMOID activation and XENT loss function for binary multi-label
	 * classification.
	 */
	private void initializeNetworkBinaryMultiLabel() {

		// https://deeplearning4j.org/workspaces
		Nd4j.getMemoryManager().setAutoGcWindow(10000);
		// Nd4j.getMemoryManager().togglePeriodicGc(false);

		try {
			fullSetIterator = new N2c2PatientIteratorBML(patientExamples, wordVectors, miniBatchSize, truncateLength);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int lstmLayerSize = 128;
		double l2Regulization = 0.01;
		double adaGradCore = 0.04;
		double adaGradGraves = 0.008;

		// seed for reproducibility
		final int seed = 12345;
		MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(seed)
				.updater(AdaGrad.builder().learningRate(adaGradCore).build()).regularization(true).l2(l2Regulization)
				.weightInit(WeightInit.XAVIER).gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
				.gradientNormalizationThreshold(1.0).trainingWorkspaceMode(WorkspaceMode.SEPARATE)
				.inferenceWorkspaceMode(WorkspaceMode.SEPARATE).list()
				.layer(0,
						new GravesLSTM.Builder().nIn(vectorSize).nOut(lstmLayerSize)
								.updater(AdaGrad.builder().learningRate(adaGradGraves).build())
								.activation(Activation.SOFTSIGN).build())
				.layer(1,
						new RnnOutputLayer.Builder().activation(Activation.SIGMOID)
								.lossFunction(LossFunctions.LossFunction.XENT).nIn(lstmLayerSize).nOut(13).build())
				.pretrain(false).backprop(true).build();

		// for truncated backpropagation over time
		// .backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(tbpttLength)
		// .tBPTTBackwardLength(tbpttLength).pretrain(false).backprop(true).build();

		this.net = new MultiLayerNetwork(conf);
		this.net.init();
		this.net.setListeners(new ScoreIterationListener(1));
	}

	private void initializeNetworkBinaryMultiLabelDebug() {

		Nd4j.getMemoryManager().setAutoGcWindow(10000); // https://deeplearning4j.org/workspaces

		try {
			fullSetIterator = new N2c2PatientIteratorBML(patientExamples, wordVectors, miniBatchSize, truncateLength);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Set up network configuration
		MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(0)
				.updater(Adam.builder().learningRate(2e-2).build()).l2(1e-5).weightInit(WeightInit.XAVIER)
				.gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
				.gradientNormalizationThreshold(1.0).trainingWorkspaceMode(WorkspaceMode.SEPARATE)
				.inferenceWorkspaceMode(WorkspaceMode.SEPARATE) // https://deeplearning4j.org/workspaces
				.list().layer(0, new GravesLSTM.Builder().nIn(vectorSize).nOut(256).activation(Activation.TANH).build())
				.layer(1,
						new RnnOutputLayer.Builder().activation(Activation.SIGMOID)
								.lossFunction(LossFunctions.LossFunction.XENT).nIn(256).nOut(13).build())
				.pretrain(false).backprop(true).build();

		// for truncated backpropagation over time
		// .backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(tbpttLength)
		// .tBPTTBackwardLength(tbpttLength).pretrain(false).backprop(true).build();

		this.net = new MultiLayerNetwork(conf);
		this.net.init();
		this.net.setListeners(new ScoreIterationListener(1));
	}

	/**
	 * Get longest token sequence of all patients with respect to existing word
	 * vector out of Google corpus.
	 *
	 */
	private void initializeTruncateLength() {

		// type coverage
		Set<String> corpusTypes = new HashSet<String>();
		Set<String> matchedTypes = new HashSet<String>();

		// token coverage
		int filteredSum = 0;
		int tokenSum = 0;

		List<List<String>> allTokens = new ArrayList<>(patientExamples.size());
		int maxLength = 0;

		for (Patient patient : patientExamples) {
			String narrative = patient.getText();
			String cleaned = narrative.replaceAll("[\r\n]+", " ").replaceAll("\\s+", " ");
			List<String> tokens = tokenizerFactory.create(cleaned).getTokens();
			tokenSum += tokens.size();

			List<String> tokensFiltered = new ArrayList<>();
			for (String token : tokens) {
				corpusTypes.add(token);
				if (wordVectors.hasWord(token)) {
					tokensFiltered.add(token);
					matchedTypes.add(token);
				} else {
					LOG.info("Word2vec representation missing:\t" + token);
				}
			}
			allTokens.add(tokensFiltered);
			filteredSum += tokensFiltered.size();

			maxLength = Math.max(maxLength, tokensFiltered.size());
		}

		LOG.info("Matched " + matchedTypes.size() + " types out of " + corpusTypes.size());
		LOG.info("Matched " + filteredSum + " tokens out of " + tokenSum);

		this.truncateLength = maxLength;
	}

	@Override
	protected String getModelName() {
		return "LSTMW2V_MBL";
	}

	/**
	 * Load features from narrative.
	 *
	 * @param reviewContents
	 *            Narrative content.
	 * @param maxLength
	 *            Maximum length of token series length.
	 * @return Time series feature presentation of narrative.
	 */
	protected INDArray loadFeaturesForNarrative(String reviewContents, int maxLength) {

		List<String> tokens = tokenizerFactory.create(reviewContents).getTokens();
		List<String> tokensFiltered = new ArrayList<>();
		for (String t : tokens) {
			if (wordVectors.hasWord(t))
				tokensFiltered.add(t);
		}
		int outputLength = Math.max(maxLength, tokensFiltered.size());

		INDArray features = Nd4j.create(1, vectorSize, outputLength);

		for (int j = 0; j < tokens.size() && j < maxLength; j++) {
			String token = tokens.get(j);
			INDArray vector = wordVectors.getWordVectorMatrix(token);
			features.put(new INDArrayIndex[] { NDArrayIndex.point(0), NDArrayIndex.all(), NDArrayIndex.point(j) },
					vector);
		}
		return features;
	}
}
