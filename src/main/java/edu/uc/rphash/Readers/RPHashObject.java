package edu.uc.rphash.Readers;

import java.util.Iterator;
import java.util.List;

import edu.uc.rphash.decoders.Decoder;
import edu.uc.rphash.decoders.Spherical;

public interface RPHashObject {
	 final static int DEFAULT_NUM_PROJECTIONS = 2;
	 final static int DEFAULT_NUM_BLUR = 2;
	 final static int DEFAULT_NUM_RANDOM_SEED = 0;
	 final static int DEFAULT_NUM_DECODER_MULTIPLIER = 1;
	 final static long DEFAULT_HASH_MODULUS = Long.MAX_VALUE;
	 final static Decoder DEFAULT_INNER_DECODER = new Spherical(32,3,1);
	 
	int getk();
	//int getn();
	int getdim();
	long getRandomSeed();
	long getHashmod();
	int getNumBlur();
	
	Iterator<float[]> getVectorIterator();
	List<float[]> getCentroids( );
	
	List<Long> getPreviousTopID();
	void setPreviousTopID(List<Long> i);
	
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


}