package edu.uc.rphash.Readers;

import java.util.Iterator;
import java.util.List;

import edu.uc.rphash.decoders.Decoder;
import edu.uc.rphash.decoders.Spherical;

public interface RPHashObject {
	 final static int DEFAULT_NUM_PROJECTIONS = 2;
	 public final static int DEFAULT_NUM_BLUR = 1;
	 final static long DEFAULT_NUM_RANDOM_SEED = 38006359550206753L;
	 final static int DEFAULT_NUM_DECODER_MULTIPLIER = 1;
	 final static long DEFAULT_HASH_MODULUS = Long.MAX_VALUE;
	 final static Decoder DEFAULT_INNER_DECODER =  new Spherical(64,4,1);
	 
	int getk();
	int getdim();
	long getRandomSeed();
	long getHashmod();
	int getNumBlur();
	
	Iterator<float[]> getVectorIterator();
	List<float[]> getCentroids( );
	
	List<Long> getPreviousTopID();
	void setPreviousTopID(List<Long> i);
	
	List<Float> getCounts( );
	void setCounts(List<Float> counts);
	
	void addCentroid(float[] v);
	void setCentroids(List<float[]> l);
	
	void reset();//TODO rename to resetDataStream
	int getNumProjections();
	void setNumProjections(int probes);
	void setInnerDecoderMultiplier(int multiDim);
	int getInnerDecoderMultiplier();
	void setNumBlur(int parseInt);
	void setRandomSeed(long parseLong);
	void setHashMod(long parseLong);
	void setDecoderType(Decoder dec);
	Decoder getDecoderType();
	String toString();
	void setVariance(List<float[]> data);
	void setDecayRate(float parseFloat);
	float getDecayRate();
	void setParallel(boolean parseBoolean);
	boolean getParallel();


}
