package edu.uc.rphash;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import edu.uc.rphash.Readers.RPHashObject;
import edu.uc.rphash.Readers.SimpleArrayReader;
import edu.uc.rphash.decoders.Decoder;
import edu.uc.rphash.decoders.Leech;
import edu.uc.rphash.decoders.Spherical;
import edu.uc.rphash.frequentItemSet.ItemSet;
import edu.uc.rphash.frequentItemSet.SimpleFrequentItemSet;
import edu.uc.rphash.lsh.LSH;
import edu.uc.rphash.projections.Projector;
import edu.uc.rphash.standardhash.HashAlgorithm;
import edu.uc.rphash.standardhash.NoHash;
import edu.uc.rphash.tests.StatTests;
import edu.uc.rphash.tests.generators.GenerateData;


import java.io.ByteArrayOutputStream;
//import java.io.File;
import java.io.ObjectOutputStream;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
//import java.util.Set;
//import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;

import edu.uc.rphash.Readers.StreamObject;
import edu.uc.rphash.projections.DBFriendlyProjection;
import edu.uc.rphash.standardhash.MurmurHash;
import edu.uc.rphash.tests.clusterers.Agglomerative3;
//import edu.uc.rphash.util.VectorUtil;





/**
 * This is the correlated multi projections approach. In this RPHash variation
 * we try to incorporate the advantage of multiple random projections in order
 * to combat increasing cluster error rates as the deviation between projected
 * and full data increases. The main idea is similar to the referential RPHash,
 * however the set union is projection id dependent. This will be done in a
 * simplified bitmask addition to the hash code in lieu of an array of sets data
 * structures.
 * 
 * @author lee
 *
 */
public class RPHashMultiProj implements Clusterer {
	float variance;

	/*public RPHashObject map() {
		Iterator<float[]> vecs = so.getVectorIterator();
		if (!vecs.hasNext())
			return so;

		long[] hash;
		int projections = so.getNumProjections();

		int k = (int) (so.getk() * 2);

		// initialize our counter
		ItemSet<Long> is = new SimpleFrequentItemSet<Long>(k);
		// create our LSH Device
		// create same LSH Device as before

		Random r = new Random(so.getRandomSeed());
		LSH[] lshfuncs = new LSH[projections];
		Decoder dec = so.getDecoderType();
		dec.setCounter(is);
		HashAlgorithm hal = new NoHash(so.getHashmod());

		// create same projection matrices as before
		for (int i = 0; i < projections; i++) {
			Projector p = so.getProjectionType();
			p.setOrigDim(so.getdim());
			p.setProjectedDim(dec.getDimensionality());
			p.setRandomSeed(r.nextLong());
			p.init();

			List<float[]> noise = LSH.genNoiseTable(dec.getDimensionality(),
					so.getNumBlur(), r,
					dec.getErrorRadius() / dec.getDimensionality());

			lshfuncs[i] = new LSH(dec, p, hal, noise, so.getNormalize());
		}

		// add to frequent itemset the hashed Decoded randomly projected vector
		while (vecs.hasNext()) {
			float[] vec = vecs.next();
			// iterate over the multiple projections
			for (LSH lshfunc : lshfuncs) {
				// could do a big parallel projection here
				hash = lshfunc.lshHashRadius(vec, so.getNumBlur());
				for (long hh : hash) {
					is.add(hh);
				}
			}
		}
		so.setPreviousTopID(is.getTop());
		List<Float> countsAsFloats = new ArrayList<Float>();
		for (long ct : is.getCounts())
			countsAsFloats.add((float) ct);
		so.setCounts(countsAsFloats);
		return so;
	}*/
	
	
	public static List<Long>[] mapphase1(int k,String inputfile) {

		SimpleArrayReader so = new SimpleArrayReader(null, k,
				RPHashObject.DEFAULT_NUM_BLUR);
		Iterator<float[]> vecs;
		try {
			vecs = new StreamObject(inputfile, 0, false)
					.getVectorIterator();
		} catch (IOException e) {
			e.printStackTrace();
			System.err
					.println("file not accessible or not found on cluster node!");
			return null;
		}

		float[] vec = vecs.next();
		so.setdim(vec.length);
		so.getDecoderType().setVariance(StatTests.variance(vec));

		long[] hash;
		int projections = so.getNumProjections();

		// initialize our counter
		ItemSet<Long> is = new SimpleFrequentItemSet<Long>(k*projections);
		// create our LSH Device
		// create same LSH Device as before
		Random r = new Random(so.getRandomSeed());
		LSH[] lshfuncs = new LSH[projections];
		Decoder dec = so.getDecoderType();
		HashAlgorithm hal = new MurmurHash(so.getHashmod());

		// create same projection matrices as before
		for (int i = 0; i < projections; i++) {
			Projector p = new DBFriendlyProjection(so.getdim(),
					dec.getDimensionality(), r.nextLong());

			List<float[]> noise = LSH.genNoiseTable(dec.getDimensionality(),
					so.getNumBlur(), r,
					dec.getErrorRadius() / dec.getDimensionality());

			lshfuncs[i] = new LSH(dec, p, hal, noise, so.getNormalize());
		}

		// add to frequent itemset the hashed Decoded randomly projected vector
		while (vecs.hasNext()) {
			// iterate over the multiple projections
			for (LSH lshfunc : lshfuncs) {
				// could do a big parallel projection here
				hash = lshfunc.lshHashRadius(vec, so.getNumBlur());
				for (long hh : hash) {
					is.add(hh);
				}
			}
			vec = vecs.next();
		}
		return new List[] { is.getTop(), is.getCounts() };
	}
	

	/*
	 * This is the second phase after the top ids have been in the reduce phase
	 * aggregated
	 */
/*	public RPHashObject reduce() {
		Iterator<float[]> vecs = so.getVectorIterator();
		if (!vecs.hasNext())
			return so;

		// make a set of k default centroid objects
		ArrayList<Centroid> centroids = new ArrayList<Centroid>();
		for (long id : so.getPreviousTopID())
			centroids.add(new Centroid(so.getdim(), id, -1));

		long[] hash;
		int projections = so.getNumProjections();

		// create our LSH Device
		// create same LSH Device as before
		Random r = new Random(so.getRandomSeed());
		LSH[] lshfuncs = new LSH[projections];
		Decoder dec = so.getDecoderType();
		HashAlgorithm hal = new NoHash(so.getHashmod());

		// create same projection matrices as before
		for (int i = 0; i < projections; i++) {
			Projector p = so.getProjectionType();
			p.setOrigDim(so.getdim());
			p.setProjectedDim(dec.getDimensionality());
			p.setRandomSeed(r.nextLong());
			p.init();
			List<float[]> noise = LSH.genNoiseTable(dec.getDimensionality(),
					so.getNumBlur(), r,
					dec.getErrorRadius() / dec.getDimensionality());
			lshfuncs[i] = new LSH(dec, p, hal, noise, so.getNormalize());
		}

		while (vecs.hasNext()) {
			float[] vec = vecs.next();
			// iterate over the multiple projections
			for (LSH lshfunc : lshfuncs) {
				// could do a big parallel projection here
				hash = lshfunc.lshHashRadius(vec, so.getNumBlur());
				for (Centroid cent : centroids) {
					for (long hh : hash) {
						if (cent.ids.contains(hh)) {
							cent.updateVec(vec);
							cent.addID(hh);
						}
					}
				}
			}
		}
		so.setCentroids(centroids);
		return so;
	}*/

	
	public static Object[] mapphase2(List<Long>[] frequentItems, String inputfile) {

		SimpleArrayReader so = new SimpleArrayReader(null,
				frequentItems[0].size(), RPHashObject.DEFAULT_NUM_BLUR);
		Iterator<float[]> vecs;
		try {
			vecs = new StreamObject(inputfile, 0, false)
					.getVectorIterator();
		} catch (IOException e) {
			e.printStackTrace();
			System.err
					.println("file not accessible or not found on cluster node!");
			return null;
		}

		float[] vec = vecs.next();
		so.setdim(vec.length);
		so.getDecoderType().setVariance(StatTests.variance(vec));

		// make a set of k default centroid objects
		ArrayList<Centroid> centroids = new ArrayList<Centroid>();
		
		for (int i = 0; i < frequentItems[0].size(); i++) {
			centroids.add(new Centroid(so.getdim(), frequentItems[0].get(i),
					-1, frequentItems[1].get(i)));
		}

		long[] hash;
		int projections = so.getNumProjections();

		// create our LSH Device
		// create same LSH Device as before
		Random r = new Random(so.getRandomSeed());
		LSH[] lshfuncs = new LSH[projections];
		Decoder dec = so.getDecoderType();
		HashAlgorithm hal = new MurmurHash(so.getHashmod());

		// create same projection matrices as before
		for (int i = 0; i < projections; i++) {
			Projector p = new DBFriendlyProjection(so.getdim(),
					dec.getDimensionality(), r.nextLong());
			List<float[]> noise = LSH.genNoiseTable(dec.getDimensionality(),
					so.getNumBlur(), r,
					dec.getErrorRadius() / dec.getDimensionality());
			lshfuncs[i] = new LSH(dec, p, hal, noise, so.getNormalize());
		}

		while (vecs.hasNext()) {
			
			// iterate over the multiple projections
			for (LSH lshfunc : lshfuncs) {
				
				// could do a big parallel projection here
				hash = lshfunc.lshHashRadius(vec, so.getNumBlur());
				for (Centroid cent : centroids) {
					for (long hh : hash) {
						if (cent.ids.contains(hh)) {
							cent.updateVec(vec);
							cent.addID(hh);
						}
					}
				}
			}
			vec = vecs.next();
		}

		List<float[]> centvectors = new ArrayList<float[]>();

		List<ConcurrentSkipListSet<Long>> centids = new ArrayList<ConcurrentSkipListSet<Long>>();
		List<Long> centcounts = new ArrayList<Long>();
		for (Centroid cent : centroids) {
			centvectors.add(cent.centroid());
			centids.add(cent.ids);
			centcounts.add((long) cent.getCount());
			
		}
		// so.setCentroids(centvectors);
		// so.setCounts(centcounts);

		return  new Object[]{centvectors,centids,centcounts};
	}
	
	
	
//	private List<float[]> centroids = null;
	private List<Centroid> centroids = null;
	private RPHashObject so;
	private int runs;

	public RPHashMultiProj(List<float[]> data ,int k) {
		so = new SimpleArrayReader(data, k, RPHashObject.DEFAULT_NUM_BLUR);
		runs = 1;
	}

	public RPHashMultiProj(RPHashObject so) {
		this.so = so;
	}

	public RPHashMultiProj() {
		so = new SimpleArrayReader();
	}

	public List<Centroid> getCentroids(RPHashObject so) {
		this.so = so;
		
		if (centroids == null)
			try {
				run();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return centroids;
	}

	@Override
	public List<Centroid> getCentroids() {

		if (centroids == null)
			try {
				run();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return new Agglomerative3((List)so.getCentroids(), so.getk()).getCentroids();
		
	}
	/*private void run() {
		runs = 1;
		double minwcss = Double.MAX_VALUE;
		List<Centroid> mincentroids = new ArrayList<>();
		for (int currun = 0; currun < runs;) {
			
			map();
			reduce();

			Clusterer offlineclusterer = so.getOfflineClusterer();
			List<Centroid> tmpcents;
			if (offlineclusterer != null) {
				offlineclusterer.setMultiRun(1);// is deterministic
				offlineclusterer.setData(so.getCentroids());
				offlineclusterer.setWeights(so.getCounts());
				offlineclusterer.setK(so.getk());
				tmpcents = offlineclusterer.getCentroids();
			} else {
				tmpcents = so.getCentroids().subList(0, so.getk());
			}

			if (tmpcents.size() == so.getk()) {// skip bad clusterings
				double tmpwcss = StatTests.WCSSECentroidsFloat(tmpcents,
						so.getRawData());
				// System.out.println(tmpwcss + ":" + so.getCounts());
				if (tmpwcss < minwcss) {
					minwcss = tmpwcss;
					mincentroids = tmpcents;
				}
				currun++;
			}

			this.reset(new Random().nextInt());

		}
		
		

		this.centroids = mincentroids;
	}*/

	
	private void run() throws IOException {
		runs = 1;
		String fs = "/work/deysn/rphash/data/data500.mat";
		List<Long>[] l1 = mapphase1(so.getk(),fs);
		List<Long>[] l2 = mapphase1(so.getk(),fs);
		List<Long>[] lres = reducephase1(l1,l2);

		Object[] c1 = mapphase2(lres,fs);
		Object[] c2 = mapphase2(lres,fs);

		//test serialization
		new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(c1);
		
		Object[] cres = reducephase2(c1,c2);
		
		centroids = new Agglomerative3((List) cres[0],so.getk() ).getCentroids();
		
		System.out.println(StatTests.WCSSE((List)centroids, "/work/deysn/rphash/data/data500.mat", false));
	}	
	
	
	
	
	public static void main(String[] args) {

		int k = 10;
		int d = 1000;
		int n = 10000;
		float var = .6f;
		int count = 5;
		
		try {
			new RPHashMultiProj(null, k).run();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	/*	System.out.printf("Decoder: %s\n","Spherical");
		System.out.printf("ClusterVar\t");
		for (int i = 0; i < count; i++)
			System.out.printf("Trial%d\t", i);
		System.out.printf("RealWCSS\n");
		
		

		for (float f = var; f < 3.01; f += .1f) {
			float avgrealwcss = 0;
			float avgtime = 0;
			System.out.printf("%f\t", f);
			for (int i = 0; i < count; i++) {
				GenerateData gen = new GenerateData(k, n / k, d, f, true, 1f);
				RPHashObject o = new SimpleArrayReader(gen.data, k);
				o.setDecoderType(new Spherical(32,4,1));
				o.setDimparameter(32);
				RPHashMultiProj rphit = new RPHashMultiProj(o);
				long startTime = System.nanoTime();
				List<Centroid> centsr = rphit.getCentroids();

				avgtime += (System.nanoTime() - startTime) / 100000000;

				avgrealwcss += StatTests.WCSSEFloatCentroid(gen.getMedoids(),
						gen.getData());

				System.out.printf("%.0f\t",
						StatTests.WCSSECentroidsFloat(centsr, gen.data));
				System.gc();
				
			}
			System.out.printf("%.0f\n", avgrealwcss / count);
		}*/
	}

	@Override
	public RPHashObject getParam() {
		return so;
	}

	
	public static List<Long>[] reducephase1(List<Long>[] topidsandcounts1,
			List<Long>[] topidsandcounts2) {
		if(topidsandcounts1==null )return topidsandcounts2;
		if(topidsandcounts2==null )return topidsandcounts1;
		int k = Math.max(topidsandcounts1[0].size(), topidsandcounts2[0].size());
		k =(int) (k *Math.log(k)+.5);
		// merge lists
		HashMap<Long, Long> idsandcounts = new HashMap<Long, Long>();
		for (int i = 0; i < topidsandcounts1[0].size(); i++) {
			idsandcounts.put(topidsandcounts1[0].get(i), topidsandcounts1[1].get(i));
		}

		//merge in set 2's counts and hashes
		for (int i = 0; i < topidsandcounts2[0].size(); i++) {
			Long id = topidsandcounts2[0].get(i);
			Long count = topidsandcounts2[1].get(i);

			if (idsandcounts.containsKey(id)) {
				idsandcounts.put(id, idsandcounts.get(id) + count);
			} else {
				idsandcounts.put(id, count);
			}
		}

		// truncate list
		int count = 0;
		
		List<Long> retids    = new ArrayList<Long>();
		List<Long> retcounts = new ArrayList<Long>();
		
		LinkedHashMap<Long,Long> map = sortByValue(idsandcounts);
		
		//truncate
		for(Long entry : map.keySet()) {
			retids.add(entry);
			retcounts.add(map.get(entry));
			if(count++==k)
			{
				return new List[]{retids,retcounts};
			}
		}
		return new List[]{retids,retcounts};
	}
	
	public static LinkedHashMap<Long, Long> sortByValue(
			Map<Long, Long> map) {
		
		LinkedHashMap<Long, Long> result = new LinkedHashMap<>();
		Stream<Map.Entry<Long, Long>> st = map.entrySet().stream();

		st.sorted(new Comparator<Map.Entry<Long, Long>>(){
			@Override
			public int compare(Entry<Long, Long> o1, Entry<Long, Long> o2) {
				long l1 = o1.getValue().longValue();
				long l2 = o2.getValue().longValue();
				return (int) (l2-l1);
			}	
		}).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));

		return result;
	}

	
	public static Object[] reducephase2(Object[] in1,
			Object[] in2) {
		
		int k = Math.max(((List)in1[0]).size(),((List) in2[0]).size());

		List<Centroid> cents1 = new ArrayList<Centroid>();
		
		//create a map of centroid hash ids to the centroids idx
		HashMap<Long,Integer> idsToIdx = new HashMap<>();
		for (int i = 0;i<((List)in1[0]).size();i++) 
		{
			
			Centroid vec = new Centroid((float[])((List)in1[0]).get(i),-1);

			vec.ids = (ConcurrentSkipListSet<Long>) ((List)in1[1]).get(i);
			vec.id = vec.ids.first();
			vec.setCount((long)((List)in1[2]).get(i));
			
			cents1.add(vec);
			
			for(Long id: cents1.get(i).ids)
			{
				idsToIdx.put(id, i);
			}	
		}

		// merge centroids from centroid set 2
		for (int i = 0;i<((List)in2[0]).size();i++) 
		{
			
			Centroid vec = new Centroid((float[])((List)in2[0]).get(i),-1);
			vec.ids = (ConcurrentSkipListSet<Long>) ((List)in2[1]).get(i);
			vec.id = vec.ids.first();
			vec.setCount((long)((List)in2[2]).get(i));
			
			boolean matchnotfound = true;
			for(Long id: vec.ids)
			{
				if(idsToIdx.containsKey(id))
				{
					cents1.get(idsToIdx.get(id)).updateVec(vec);
					matchnotfound = false;
				}
			}
			if(matchnotfound)cents1.add(vec);
		}
		
		Collections.sort(cents1);//reverse sort, centroid compareTo is inverted
		
		List<float[]> retcents = new ArrayList<float[]>();
		List<ConcurrentSkipListSet<Long>> retids = new ArrayList<ConcurrentSkipListSet<Long>>();
		List<Long> retcount = new ArrayList<Long>();

		for(Centroid c : cents1.subList(0, Math.min(k,cents1.size()))){
			retcents.add(c.centroid());
			retcount.add((long) c.getCount());
			retids.add(c.ids);
		}

		return new Object[]{retcents,retids,retcount};
	}
	
	
	
	
	@Override
	public void setWeights(List<Float> counts) {
	}

	@Override
	public void setData(List<Centroid> data) {
		centroids = new ArrayList<>();
		for (Centroid c : data) {
			so.addRawData(c.centroid);
		}
		so.setDimparameter(data.get(0).dimensions);
	}

	@Override
	public void setK(int getk) {
		this.so.setK(getk);
	}

	@Override
	public void setRawData(List<float[]> data) {
		so.setRawData(data);
		this.so.setDimparameter(data.get(0).length);
	}

	@Override
	public void reset(int randomseed) {
		centroids = null;
		so.setRandomSeed(randomseed);
	}

	@Override
	public boolean setMultiRun(int runs) {
		this.runs = runs;
		return true;
	}

}
