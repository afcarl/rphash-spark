package edu.uc.rphash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.ArrayUtils;
import org.apache.spark.api.java.JavaRDD;

import java.io.Serializable;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.ForkJoinPool;
// import java.util.concurrent.RecursiveAction;
// import java.util.concurrent.TimeUnit;

import edu.uc.rphash.Readers.RPHashObject;
import edu.uc.rphash.Readers.SimpleArrayReader;
import edu.uc.rphash.concurrent.VectorLevelConcurrency;
import edu.uc.rphash.decoders.Decoder;
import edu.uc.rphash.decoders.MultiDecoder;
import edu.uc.rphash.frequentItemSet.KHHCentroidCounter;
//import edu.uc.rphash.frequentItemSet.KHHCountMinSketch.Tuple;
import edu.uc.rphash.lsh.LSH;
import edu.uc.rphash.projections.DBFriendlyProjection;
import edu.uc.rphash.projections.Projector;
import edu.uc.rphash.standardhash.HashAlgorithm;
import edu.uc.rphash.standardhash.MurmurHash;
import edu.uc.rphash.tests.ClusterGenerator;
import edu.uc.rphash.tests.GenerateData;
import edu.uc.rphash.tests.GenerateStreamData;
import edu.uc.rphash.tests.Kmeans;
import edu.uc.rphash.tests.StatTests;
import edu.uc.rphash.tests.StreamingKmeans;
import edu.uc.rphash.tests.TestUtil;

public class RPHashStream implements StreamClusterer, Serializable {
	public KHHCentroidCounter is;
	private LSH[] lshfuncs;
	private StatTests vartracker;
	private List<float[]> centroids = null;
	private RPHashObject so;
	public boolean parallel = false;
	// ExecutorService executor;
	// private final int processors;

	/*
	public int getProcessors() {
		return processors;
	}
	*/

	@Override
	public synchronized long addVectorOnlineStep(final float[] vec) {
		/*
		if (parallel) 
		{
			VectorLevelConcurrency r = new VectorLevelConcurrency(vec, so,
					lshfuncs, vartracker, is);
			executor.execute(r);
			return is.count;
		}
		*/

//		long hash[];
		Centroid c = new Centroid(vec);
		for (LSH lshfunc : lshfuncs) {
//			hash = lshfunc.lshHashRadiusNo2Hash(vec, so.getNumBlur());
//			for (long h : hash)
//				c.addID(h);
			long hash = lshfunc.lshHash(vec);
			c.addID(hash);
		}
		is.add(c);
		return is.count;
	}

	public void init() {
		Random r = new Random(so.getRandomSeed());
		this.vartracker = new StatTests(.01f);
		int projections = so.getNumProjections();
		int k = (int) (so.getk() * Math.log(so.getk() / Math.log(2.0)));
		// initialize our counter
		float decayrate = so.getDecayRate();// 1f;// bottom number is window size
		is = new KHHCentroidCounter(k,decayrate);// , decayrate); //add back for decayed
		// create LSH Device
		lshfuncs = new LSH[projections];
		Decoder dec = so.getDecoderType();
		HashAlgorithm hal = new MurmurHash(so.getHashmod());
		// create projection matrices add to LSH Device
		for (int i = 0; i < projections; i++) {
			Projector p = new DBFriendlyProjection(so.getdim(),
					dec.getDimensionality(), r.nextLong());
			List<float[]> noise = LSH.genNoiseTable(dec.getDimensionality(),
					SimpleArrayReader.DEFAULT_NUM_BLUR, r, dec.getErrorRadius()
							/ dec.getDimensionality());
			lshfuncs[i] = new LSH(dec, p, hal, noise);
		}
	}

	public RPHashStream(int k, ClusterGenerator c) {
		so = new SimpleArrayReader(k, c);
		/*
		if (parallel)
			this.processors = Runtime.getRuntime().availableProcessors();
		else
			this.processors = 1;
		executor = Executors.newFixedThreadPool(this.processors);
		*/
		init();
	}

	public RPHashStream(JavaRDD<List<Float>> dataset, int k) {
		so = new SimpleArrayReader(dataset, k);
		/*
		if (parallel)
			this.processors = Runtime.getRuntime().availableProcessors();
		else
			this.processors = 1;
		executor = Executors.newFixedThreadPool(this.processors);
		*/
		init();
	}

	public RPHashStream(RPHashObject so) {
		this.so = so;
		
		/*
		if (parallel)
			this.processors = Runtime.getRuntime().availableProcessors();
		else
			this.processors = 1;
		executor = Executors.newFixedThreadPool(this.processors);
		*/
		
		init();
	}

	public RPHashStream(int k, GenerateStreamData c, int processors) {
		so = new SimpleArrayReader(k, c);
		/*
		if (parallel)
			this.processors = processors;
		else
			this.processors = 1;
		executor = Executors.newFixedThreadPool(this.processors);
		*/
		init();
	}


	@Override
	public List<float[]> getCentroids() {
		if (centroids == null) {
			init();
			run();
			getCentroidsOfflineStep();
		}
		return centroids;
	}

	public List<float[]> getCentroidsOfflineStep() {
		/*
		if (parallel) {
			executor.shutdown();
			try {
				executor.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// executor = Executors.newFixedThreadPool(getProcessors());
		}
		*/

		centroids = new ArrayList<float[]>();
		List<Centroid> cents = is.getTop();
		List<Float> counts = is.getCounts();

		for (int i = 0; i < cents.size(); i++) {
			centroids.add(cents.get(i).centroid());
		}

		centroids = new Kmeans(so.getk(), centroids, counts).getCentroids();

		return centroids;
	}
	
	public void run() {
		// add to frequent itemset the hashed Decoded randomly projected vector
		Iterator<float[]> vecs = so.getVectorIterator();
		while (vecs.hasNext()) {
			/*
			if (parallel) {
				float[] vec = vecs.next();
				executor.execute(new VectorLevelConcurrency(vec, so,
						lshfuncs, vartracker, is));
			} else {
			*/
			addVectorOnlineStep(vecs.next());
		}
	}

	public List<Float> getTopIdSizes() {
		return is.getCounts();
	}

	@Override
	public RPHashObject getParam() {
		return so;
	}

}
