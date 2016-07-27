package edu.uc.rphash;

import java.io.File;
//import java.io.FileInputStream;
import java.io.IOException;
//import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.sql.SparkSession;

import edu.uc.rphash.Readers.RPHashObject;
import edu.uc.rphash.Readers.SimpleArrayReader;
import edu.uc.rphash.Readers.StreamObject;
import edu.uc.rphash.decoders.Dn;
import edu.uc.rphash.decoders.E8;
import edu.uc.rphash.decoders.Leech;
import edu.uc.rphash.decoders.MultiDecoder;
import edu.uc.rphash.decoders.PStableDistribution;
import edu.uc.rphash.decoders.Spherical;
import edu.uc.rphash.tests.StatTests;
import edu.uc.rphash.tests.clusterers.Kmeans;
import edu.uc.rphash.tests.clusterers.StreamingKmeans;
import edu.uc.rphash.tests.kmeanspp.DoublePoint;
import edu.uc.rphash.tests.kmeanspp.KMeansPlusPlus;
import edu.uc.rphash.util.VectorUtil;

public class RPHash {

	static String[] clusteringmethods = { "simple", "streaming", "3stage",
			"multiProj", "consensus", "redux", "kmeans", "pkmeans",
			"kmeansplusplus", "streamingkmeans" };
	static String[] ops = { "numprojections", "innerdecodermultiplier",
			"NumBlur", "randomseed", "hashmod", "decodertype","parallel",
			"streamduration", "raw", "decayrate" };
	static String[] decoders = { "dn", "e8", "multie8", "leech", "multileech",
			"pstable", "sphere" };

	public static void main(String[] args) throws NumberFormatException,
			IOException, InterruptedException {

		if (args.length < 3) {
			System.out.print("Usage: rphash InputFile k OutputFile [");
			for (String s : clusteringmethods)
				System.out.print(s + " ,");
			System.out.print("] [arg=value...]\n \t Optional Args:\n");

			for (String s : ops)
				System.out.println("\t\t" + s);
			System.out.print("\t\t\t\t:[");
			for (String s : decoders)
				System.out.print(s + " ,");
			System.out.print("]\n");

			System.exit(0);
		}

		List<float[]> data = null;
//		JavaRDD<List<Float>> dataset = null;

		final String filename =  "/var/rphash/data/data.mat";// args[0];
		int k = Integer.parseInt(args[1]);
		String outputFile = args[2];

		boolean raw = false;

		if (args.length == 3) {	
			SparkConf conf = new SparkConf().setAppName("RPHashMultiProj_Spark");
//					                        .setMaster("local[4]");
//			
//			
//			
			JavaSparkContext sc = new JavaSparkContext(conf);
//			JavaRDD<Object> dataSet = sc.emptyRDD();
//			
//			JavaRDD<String> dataFileAsString = sc.textFile("/home/anindya/Desktop/dataset.csv");
//			
//			
//			dataset = dataFileAsString.map(new Function<String, List<Float>>() {
//				public List<Float> call(String s) {
//					List<String> stringVector = Arrays.asList(s.split(","));
//					List<Float> floatVector = new ArrayList<>();
//					for (String element : stringVector)
//						floatVector.add(Float.valueOf(element));
//					return floatVector;
//				}
//			});
//			RPHashSimple clusterer = new RPHashSimple(dataset, k);
//			VectorUtil.writeFile(new File(outputFile + "."
//					+ clusterer.getClass().getName()),
//					clusterer.getCentroids(), raw);
		
//	    SparkSession spark = SparkSession
//	      .builder()
//	      .appName("RPHash")
//	      .getOrCreate();

//	    JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());

	    //make a dummy list of integers for each compute node
	    int slices =  3 ;//number of compute nodes
	    int n = slices;
	    List<Object> l = new ArrayList<>(n);
	    for (int i = 0; i < n; i++) {
	      l.add(i);
	    }

	    JavaRDD<Object> dataSet = sc.parallelize(l);

	    List<Long>[] topids = dataSet.map(new Function<Object, List<Long>[]>() 
	    {
			private static final long serialVersionUID = -7127935862696405148L;

		@Override
	      public List<Long>[] call(Object integer) {
	        return RPHashMultiProj.mapphase1(k,filename);
	      }
	      
	      
	    }).reduce(new Function2<List<Long>[], List<Long>[], List<Long>[]>() {
			private static final long serialVersionUID = 4294461355112957651L;

		@Override
		public List<Long>[] call(List<Long>[] topidsandcounts1, List<Long>[] topidsandcounts2) throws Exception {

			return RPHashMultiProj.reducephase1(topidsandcounts1,topidsandcounts2);
		}
	    });
	    
	    Object[] centroids = dataSet.map(new Function<Object, Object[]>() 
	    	    {
					private static final long serialVersionUID = 1L;

				@Override
	    	      public Object[] call(Object o) {
	    	        return RPHashMultiProj.mapphase2(topids,filename);
	    	      }
	    	    }).
	    	    reduce(new Function2<Object[], Object[], Object[]>() {

				private static final long serialVersionUID = 1L;

				@Override
	    		public Object[] call(Object[] cents1, Object[] cents2) throws Exception {
	    			return RPHashMultiProj.reducephase2(cents1,cents2);
	    		}
	    	    });
	    	       
	    //offline cluster
	    VectorUtil.writeFile(new File(outputFile + ".mat"),new Kmeans(k, (List)centroids[0]).getCentroids(), false);

//	    spark.stop();
	  }
	}

	public static void runner(List<Clusterer> runitems, String outputFile,
			boolean raw) {
		for (Clusterer clu : runitems) {
			String[] ClusterHashName = clu.getClass().getName().split("\\.");
			String[] DecoderHashName = clu.getParam().toString().split("\\.");
			System.out.print(ClusterHashName[ClusterHashName.length - 1] + "{"
					+ DecoderHashName[DecoderHashName.length - 1]
					+ "} processing time : ");
			long startTime = System.nanoTime();
			clu.getCentroids();
			System.out.println((System.nanoTime() - startTime) / 1000000000f);
			VectorUtil.writeFile(new File(outputFile + "."
					+ ClusterHashName[ClusterHashName.length - 1]),
					clu.getCentroids(), raw);
		}

	}

	/**
	 * Compute the average time to read a file
	 * 
	 * @param streamDuration
	 * @param f
	 *            - file name string
	 * @param testsize
	 * @return the number of milliseconds it takes on average to read
	 *         streamduration vectors
	 * @throws IOException
	 */
	public static long computeAverageReadTime(Integer streamDuration,
			String f, int testsize, boolean raw) throws IOException {
		StreamObject streamer = new StreamObject(f, 0, raw);
		int i = 0;

		ArrayList<float[]> vecsInThisRound = new ArrayList<float[]>();
		long startTime = System.nanoTime();
		while (streamer.hasNext() && i < testsize) {
			i++;
			float[] nxt = streamer.next();
			vecsInThisRound.add(nxt);
		}
		streamer.reset();
		return (System.nanoTime() - startTime);
	}

	public static void runStream(List<Clusterer> runitems, String outputFile,
			Integer streamDuration, int k, boolean raw) throws IOException,
			InterruptedException {

		Iterator<Clusterer> cluit = runitems.iterator();
		// needs work, just use for both to be more accurate
		long avgtimeToRead = 0;// computeAverageReadTime(streamDuration,f,streamDuration);
		Runtime rt = Runtime.getRuntime();

		while (cluit.hasNext()) {
			Clusterer clu = cluit.next();
			StreamObject streamer = (StreamObject) clu.getParam();
			if (clu instanceof StreamClusterer) {
				String[] ClusterHashName = clu.getClass().getName()
						.split("\\.");
				String[] DecoderHashName = clu.getParam().toString()
						.split("\\.");
				System.out.print("Streaming -- "
						+ ClusterHashName[ClusterHashName.length - 1] + "{"
						+ DecoderHashName[DecoderHashName.length - 1]
						+ ",stream_duration:" + streamDuration
						+ "} \n cpu time \t wcsse \t\t\t mem(kb)\n");

				long startTime = System.nanoTime() + avgtimeToRead;
				int i = 0;
				ArrayList<float[]> vecsInThisRound = new ArrayList<float[]>();

				while (streamer.hasNext()) {

					i++;
					float[] nxt = streamer.next();
					vecsInThisRound.add(nxt);
					((StreamClusterer) clu).addVectorOnlineStep(nxt);

					if (i % streamDuration == 0 || !streamer.hasNext()) {
						List<float[]> cents = ((StreamClusterer) clu)
								.getCentroidsOfflineStep();

						long time = System.nanoTime() - startTime;
						double wcsse = StatTests.WCSSE(cents, vecsInThisRound);
						vecsInThisRound = new ArrayList<float[]>();

						rt.gc();
						Thread.sleep(10);
						rt.gc();

						long usedkB = (rt.totalMemory() - rt.freeMemory()) / 1024;

						System.out.println(time / 1000000000f + "\t" + wcsse
								+ "\t" + usedkB);

						VectorUtil.writeFile(new File(outputFile + "_round" + i
								+ "."
								+ ClusterHashName[ClusterHashName.length - 1]),
								cents, raw);
						startTime = System.nanoTime() + avgtimeToRead;
					}
				}
				streamer.reset();
				cluit.remove();
			}
		}
	}

	public static List<Clusterer> runConfigs(List<String> untaggedArgs,
			Map<String, String> taggedArgs, JavaRDD<List<Float>> dataset, String f,
			boolean raw) throws IOException {

		List<Clusterer> runitems = new ArrayList<>();
		int i = 3;
		// List<float[]> data = TestUtil.readFile(new
		// File(untaggedArgs.get(0)));
		// float variance = StatTests.varianceSample(data, .01f);

		int k = Integer.parseInt(untaggedArgs.get(1));
		RPHashObject o = new SimpleArrayReader(dataset, k);
		StreamObject so = new StreamObject(f, k, raw);

		if (taggedArgs.containsKey("numprojections")) {
			so.setNumProjections(Integer.parseInt(taggedArgs
					.get("numprojections")));
			o.setNumProjections(Integer.parseInt(taggedArgs
					.get("numprojections")));
		}
		if (taggedArgs.containsKey("innerdecodermultiplier")) {
			o.setInnerDecoderMultiplier(Integer.parseInt(taggedArgs
					.get("innerdecodermultiplier")));
			so.setInnerDecoderMultiplier(Integer.parseInt(taggedArgs
					.get("innerdecodermultiplier")));
		}
		if (taggedArgs.containsKey("numblur")) {
			o.setNumBlur(Integer.parseInt(taggedArgs.get("numblur")));
			so.setNumBlur(Integer.parseInt(taggedArgs.get("numblur")));
		}
		if (taggedArgs.containsKey("randomseed")) {
			o.setRandomSeed(Long.parseLong(taggedArgs.get("randomseed")));
			so.setRandomSeed(Long.parseLong(taggedArgs.get("randomseed")));
		}
		if (taggedArgs.containsKey("hashmod")) {
			o.setHashMod(Long.parseLong(taggedArgs.get("hashmod")));
			so.setHashMod(Long.parseLong(taggedArgs.get("hashmod")));
		}
		if (taggedArgs.containsKey("decayrate")) {
			o.setDecayRate(Float.parseFloat(taggedArgs.get("decayrate")));
			so.setDecayRate(Float.parseFloat(taggedArgs.get("decayrate")));
		}
		if (taggedArgs.containsKey("parallel")) {
			o.setParallel(Boolean.parseBoolean(taggedArgs.get("parallel")));
			so.setParallel(Boolean.parseBoolean(taggedArgs.get("parallel")));
		}
		if (taggedArgs.containsKey("decodertype")) {
			switch (taggedArgs.get("decodertype").toLowerCase()) {
			case "dn": {
				o.setDecoderType(new Dn(o.getInnerDecoderMultiplier()));
				so.setDecoderType(new Dn(o.getInnerDecoderMultiplier()));
				break;
			}
			case "e8": {
				o.setDecoderType(new E8(1f));
				so.setDecoderType(new E8(1f));
				break;
			}
			case "multie8": {
				o.setDecoderType(new MultiDecoder(
						o.getInnerDecoderMultiplier() * 8, new E8(1f)));
				so.setDecoderType(new MultiDecoder(so
						.getInnerDecoderMultiplier() * 8, new E8(1f)));
				break;
			}
			case "leech": {
				o.setDecoderType(new Leech(1f));
				so.setDecoderType(new Leech(1f));
				break;
			}
			case "multileech": {
				o.setDecoderType(new MultiDecoder(
						o.getInnerDecoderMultiplier() * 24, new Leech(1f)));
				so.setDecoderType(new MultiDecoder(so
						.getInnerDecoderMultiplier() * 24, new Leech(1f)));
				break;
			}
			case "pstable": {
				o.setDecoderType(new PStableDistribution(1f));
				so.setDecoderType(new PStableDistribution(1f));
				break;
			}
			case "sphere": {
				o.setDecoderType(new Spherical(so.getInnerDecoderMultiplier(),
						4, 1));
				so.setDecoderType(new Spherical(so.getInnerDecoderMultiplier(),
						4, 1));
				break;
			}
			default: {
				System.out.println(taggedArgs.get("decodertype")
						+ " decoder does not exist");
				o.setDecoderType(null);
				so.setDecoderType(null);
			}
			}
		}

		while (i < untaggedArgs.size()) {
			switch (untaggedArgs.get(i).toLowerCase()) {
			case "simple":
				runitems.add(new RPHashSimple(o));
				break;
			case "streaming":
				runitems.add(new RPHashStream(so));
				break;
			case "3stage":
				runitems.add(new RPHash3Stage(o));
				break;
			case "concensus":
				runitems.add(new RPHashConsensusRP(o));
				break;
			case "multiproj":
				runitems.add(new RPHashMultiProj(o));
				break;
			case "redux":
				runitems.add(new RPHashIterativeRedux(o));
				break;
			/*	
			case "kmeans":
				runitems.add(new Kmeans(k, data));
				break;
			case "pkmeans":
				runitems.add(new Kmeans(k, data, o.getNumProjections()));
				break;
			case "kmeansplusplus":
				runitems.add(new KMeansPlusPlus<DoublePoint>(data, k));
				break;
			*/	
			case "streamingkmeans":
				runitems.add(new StreamingKmeans(so));
				break;
			default:
				System.out.println(untaggedArgs.get(i) + " does not exist");
				break;
			}
			i++;
		}
		return runitems;

	}

	/**
	 * Parse the cmd options, fill in non-default values
	 * 
	 * @param args
	 * @param mpsim
	 */
	public static Map<String, String> argsUI(String[] args,
			List<String> truncatedArgs) {

		Map<String, String> cmdMap = new HashMap<String, String>();
		for (String s : args) {
			String[] cmd = s.split("=");
			if (cmd.length > 1)
				cmdMap.put(cmd[0].toLowerCase(), cmd[1].toLowerCase());
			else
				truncatedArgs.add(s);
		}
		args = new String[truncatedArgs.size()];
		for (int i = 0; i < truncatedArgs.size(); i++)
			args[i] = truncatedArgs.get(i);
		return cmdMap;
	}

}
