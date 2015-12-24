package edu.uc.rphash.Readers;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

import edu.uc.rphash.decoders.Decoder;
import edu.uc.rphash.decoders.Leech;
import edu.uc.rphash.decoders.MultiDecoder;
import edu.uc.rphash.decoders.Spherical;
import edu.uc.rphash.tests.StatTests;
import edu.uc.rphash.tests.TestUtil;

import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function;

public class StreamObject implements RPHashObject, Iterator<float[]> {
	public List<float[]> data;
	int numProjections;
	int decoderMultiplier;
	long randomSeed;
	int numBlur;
	String f;
	InputStream elements;
	int k;
	int dim;
	int randomseed;
	long hashmod;
	List<float[]> centroids;
	List<Long> topIDs;
	int multiDim;
	Decoder dec;
	float decayrate=0;

	ExecutorService executor;
	InputStream inputStream;
	boolean raw;

	BufferedReader assin;
	DataInputStream binin;

	// input format
	// per line
	// top ids list (integers)
	// --num of clusters ( == k)
	// --num of data( == n)
	// --num dimensions
	// --input random seed;
	public StreamObject(PipedInputStream istream, int k, int dim,
			ExecutorService executor) throws IOException {
		this.executor = executor;

		this.dim = dim;
		this.randomSeed = DEFAULT_NUM_RANDOM_SEED;
		this.hashmod = DEFAULT_HASH_MODULUS;
		this.decoderMultiplier = DEFAULT_NUM_DECODER_MULTIPLIER;
		this.dec = new MultiDecoder(this.decoderMultiplier
				* DEFAULT_INNER_DECODER.getDimensionality(),
				DEFAULT_INNER_DECODER);
		this.numProjections = DEFAULT_NUM_PROJECTIONS;
		this.numBlur = DEFAULT_NUM_BLUR;
		this.k = k;
		this.data = null;
		this.centroids = new ArrayList<float[]>();
		this.topIDs = new ArrayList<Long>();
	}

	boolean filereader = false;

	public StreamObject(String f, int k, int batchDuration, boolean raw) throws IOException {
		this.f = f;
		/*
		SparkConf conf = new SparkConf().setMaster("local[4]").setAppName("StreamingRPHash_Spark");
		JavaStreamingContext jssc = new JavaStreamingContext(conf, new Duration(batchDuration));
		
		JavaDStream<String> stringData = jssc.textFileStream(f);
		JavaDStream<Float> dataStream = stringData.flatMap(new FlatMapFunction<String, Float>() {
			 @Override
		      public List<Float> call(String x) {
		    	  List<String> stringVector = Arrays.asList(x.split(","));
		    	  List<Float> floatVector = new ArrayList<>();
		    	  for (String element : stringVector)
		    		  floatVector.add(Float.valueOf(element));		    	  
		    	  
		    	  return floatVector;
		      }
		    });
		*/

		// filereader = true;
		// if (this.f.endsWith("gz"))
		// inputStream = new BufferedReader(new InputStreamReader(
		// new GZIPInputStream(new FileInputStream(this.f))));
		// else
		// inputStream = new BufferedReader(new InputStreamReader(
		// new FileInputStream(this.f)));
		// read the n and m dimension header		
		this.raw = raw;
		
		/*
		if (this.f.endsWith("gz"))
			inputStream = new GZIPInputStream(new FileInputStream(this.f));
		else
			inputStream = new FileInputStream(this.f);

		if (!raw) {
			assin = new BufferedReader(new InputStreamReader(inputStream));
			int d = Integer.parseInt(assin.readLine());
			dim = Integer.parseInt(assin.readLine());
		} else {
			binin = new DataInputStream(new BufferedInputStream(inputStream));
			int d = binin.readInt();
			dim = binin.readInt();
		}
		*/
		
		this.randomSeed = DEFAULT_NUM_RANDOM_SEED;
		this.hashmod = DEFAULT_HASH_MODULUS;
		this.decoderMultiplier = DEFAULT_NUM_DECODER_MULTIPLIER;
		this.dec = new MultiDecoder(this.decoderMultiplier
				* DEFAULT_INNER_DECODER.getDimensionality(),
				DEFAULT_INNER_DECODER);
		this.numProjections = DEFAULT_NUM_PROJECTIONS;
		this.numBlur = DEFAULT_NUM_BLUR;
		this.k = k;
		this.data = null;
		this.centroids = new ArrayList<float[]>();
		this.topIDs = new ArrayList<Long>();
		// dec = new MultiDecoder(
		// getInnerDecoderMultiplier()*inner.getDimensionality(), inner);
		
	}

	@Override
	public void reset() {

		this.centroids = null;
		try {
			if (filereader) {
				inputStream.close();
				if (this.f.endsWith("gz"))
					inputStream = new GZIPInputStream(new FileInputStream(
							this.f));
				else
					inputStream = new FileInputStream(this.f);

				if (!raw) {
					assin = new BufferedReader(new InputStreamReader(
							inputStream));
					int d = Integer.parseInt(assin.readLine());
					dim = Integer.parseInt(assin.readLine());
				} else {
					binin = new DataInputStream(new BufferedInputStream(
							inputStream));
					int d = binin.readInt();
					dim = binin.readInt();

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void addCentroid(float[] v) {
		centroids.add(v);
	}

	@Override
	public void setCentroids(List<float[]> l) {
		centroids = l;
	}

	@Override
	public int getNumBlur() {
		return numBlur;
	}

	@Override
	public List<Long> getPreviousTopID() {
		return topIDs;
	}

	@Override
	public void setPreviousTopID(List<Long> top) {
		topIDs = top;
	}

	@Override
	public Iterator<float[]> getVectorIterator() {
		return this;
	}

	@Override
	public List<float[]> getCentroids() {
		return centroids;
	}

	@Override
	public void setNumProjections(int probes) {
		this.numProjections = probes;
	}

	@Override
	public int getNumProjections() {
		return numProjections;
	}

	@Override
	public void setInnerDecoderMultiplier(int multiDim) {
		this.decoderMultiplier = multiDim;
	}

	@Override
	public int getInnerDecoderMultiplier() {
		return decoderMultiplier;
	}

	@Override
	public void setHashMod(long parseLong) {
		hashmod = (int) parseLong;
	}

	@Override
	public int getk() {
		return k;
	}

	@Override
	public int getdim() {
		return dim;
	}

	public long getHashmod() {
		return hashmod;
	}

	@Override
	public long getRandomSeed() {
		return randomSeed;
	}

	@Override
	public void setDecoderType(Decoder dec) {
		this.dec = dec;
	}

	@Override
	public String toString() {
		String ret = "Decoder:";
		if (dec != null)
			ret += dec.getClass().getName();
		ret += ", Blur:" + numBlur;
		ret += ", Projections:" + numProjections;
		ret += ", Outer Decoder Multiplier:" + decoderMultiplier;
		return ret;
	}

	@Override
	public Decoder getDecoderType() {
		return dec;
	}

	@Override
	public void setNumBlur(int parseInt) {
		this.numBlur = parseInt;
	}

	@Override
	public void setRandomSeed(long parseLong) {
		randomSeed = parseLong;
	}

	@Override
	public boolean hasNext() {
		try {
			return inputStream.available() > 0;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public float[] next() {
		float[] readFloat = new float[dim];
		try {

			if (!raw) {
				for (int i = 0; i < dim; i++)
					readFloat[i] = Float.parseFloat(assin.readLine());
			} else {
				for (int i = 0; i < dim; i++)
					readFloat[i] = binin.readFloat();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return readFloat;
	}

	@Override
	public void setVariance(List<float[]> data) {
		dec.setVariance(StatTests.varianceSample(data, .01f));
	}

	public void setDecayRate(float parseFloat) {
		this.decayrate = parseFloat;
	}
	
	public float getDecayRate(){
		return this.decayrate;
	}
}
