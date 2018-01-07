import java.awt.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import AugmentedTree.IntervalTree;
import net.sf.samtools.*;

public class Psi {

	public static void main(String[] args) {
//		long startTime = System.currentTimeMillis();		
		String gtfPath ="";
		String bamPath ="";
		String outputPath ="";
		for(int i =0; i < args.length-1; i++){
			if(args[i].equals("-gtf")){
				gtfPath = args[i+1];
				i++;
			}
			else if(args[i].equals("-bam")){
				bamPath = args[i+1];
				i++; 
			}
			else if(args[i].equals("-o")){
				outputPath = args[i+1];
				i++; 
			}
		}
		
		if(gtfPath.equals("") || bamPath.equals("") || outputPath.equals("")){
			System.out.println("Usage Info:\n-gtf <filepath for GTF>\n-bam <filepath for BAM>\n-o <filepath for ouput>");
		}
		else{
			
			String baiPath = bamPath.concat(".bai");
			
			Path gtfFilePath = Paths.get(gtfPath);
			Path bamFilePath = Paths.get(bamPath);
			Path baiFilePath = Paths.get(baiPath);
			Path outputFilePath = Paths.get(outputPath);
			
			File gtfFile = gtfFilePath.toFile();
			File bamFile = bamFilePath.toFile();
			File baiFile = baiFilePath.toFile();
			
			Psi psi = new Psi(gtfFile, bamFile, baiFile);
		
			String output = psi.getOutput();
			ArrayList<String> outputAsList = new ArrayList<String>();
			outputAsList.add(output);
			try {
				Files.write(outputFilePath, outputAsList, Charset.forName("UTF-8"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
//		long stopTime = System.currentTimeMillis();
//		System.out.println("Input:" + (stopTime-startTime));	
	}

	private HashMap<Integer,Gene> geneSet;
	private HashMap<String,HashMap<Character,IntervalTree<Gene>>> geneTree;
	private HashMap<String,HashMap<Character,IntervalTree<Read>>> readTree;
	
	public Psi (File gtfFile, File bamFile, File baiFile) {
//		long startTime = System.currentTimeMillis();
		
		SAMFileReader bam_reader = new SAMFileReader(bamFile,baiFile);
		bam_reader.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);
		
		geneSet = new HashMap<Integer,Gene>();
		
	    try {
	        BufferedReader br = new BufferedReader (new FileReader(gtfFile));
	        String line;
	        Gene curGene = null;
	        Annotation curGAnno = null;
	        Transcript curTrans = null;
	        Annotation curTAnno = null;
	        Annotation curCAnno = null;
	        ArrayList<Transcript> unknownGene = new ArrayList<Transcript>();
	        ArrayList<Region> unknownTranscript = new ArrayList<Region>();
	        HashMap<String,Annotation> mapAnno = new HashMap<String,Annotation>(); 
	        while ((line = br.readLine()) != null){
	        	String[] lineSplit = line.split("\t");
	        	if(lineSplit.length >= 8){
	        	String[] attrSplit = lineSplit[8].split(";");
	        	HashMap<String,String> attr;
	        	if(lineSplit[2].equals("gene")){
	        		attr = getAttributes(attrSplit);
	        		String gene_id = attr.get("gene_id");
	        		String gene_name = attr.get("gene_name");
	        		String type = lineSplit[1];
	        		char strand = lineSplit[6].charAt(0);
	        		String chr = lineSplit[0];
	        		int start = Integer.parseInt(lineSplit[3]);
	        		int stop = Integer.parseInt(lineSplit[4]);
	        		
	        		Annotation geneAnno = new Annotation(gene_id,gene_name,chr,strand,type);
	        		Gene gene = new Gene(start, stop, geneAnno);
	        		geneSet.put(gene.hashCode(),gene);
	        		curGene = gene;
	        		curGAnno = geneAnno;
	        	}
	        	if(lineSplit[2].equals("CDS")){
	        		attr = getAttributes(attrSplit);
	        		String super_super_id = attr.get("gene_id");
	        		String gene_name = attr.get("gene_name");
	        		String super_id = attr.get("transcript_id");
	        		String id = attr.get("protein_id");
	        		String type = lineSplit[1];
	        		char strand = lineSplit[6].charAt(0);
	        		String chr = lineSplit[0];
	        		int start = Integer.parseInt(lineSplit[3]);
	        		int stop = Integer.parseInt(lineSplit[4]);
	        		Region cds;
	        		Annotation cdsAnno;
	        		if(curCAnno != null && curCAnno.getId().equals(id) && curCAnno.getSuperId().equals(super_id) && curCAnno.getSuperSuperId().equals(super_super_id)){
	        			cdsAnno = curCAnno;
	        		}
	        		else{
	        			cdsAnno = mapAnno.get(id+super_id+super_super_id);
	        			if(!(cdsAnno != null)){
	        				cdsAnno = new Annotation(id,chr,strand,super_id,super_super_id,type,gene_name);
	        				mapAnno.put(id+super_id+super_super_id,cdsAnno);
	        			}
	        		}
	        		cds = new Region(start,stop,cdsAnno);
	        		
	        		
	        		if(cdsAnno.isSub(curTAnno)){
	        			curTrans.add(cds);
	        		}
	        		else {
		        		String transcript_name = attr.get("transcript_name");	        			
		        		Annotation transAnno = new Annotation(super_id,transcript_name,chr,strand,super_super_id,type,gene_name);
		        		Transcript trans = new Transcript(start,stop,transAnno);

		        		if(transAnno.isSub(curGAnno)){
		        			curGene.add(trans);
		        		}
		        		else{
		        			System.out.println(transAnno.getSuperId() + " is missing");
//			        		Annotation geneAnno = new Annotation(super_super_id,gene_name,chr,strand,type);
//			        		Gene gene = new Gene(start, stop, geneAnno);
//			        		geneSet.put(gene.hashCode(),gene);
//			        		curGene = gene;
//			        		curGAnno = geneAnno;
//			        		curGene.add(trans);
		        		}
		        		
		        		curTAnno = transAnno;
		        		curTrans = trans;
		        		curTrans.add(cds);
	        		}
	        		
	        	}
	        	else{}
	        	}
	        }
	        br.close();	
	    	
	    } catch (Exception e) {
			e.printStackTrace();
		}
	    
	    geneTree = new HashMap<String,HashMap<Character,IntervalTree<Gene>>>();
	    
	    for(int key: geneSet.keySet()){
	    	Gene curGene = geneSet.get(key);
	    	Annotation curAnno = curGene.getAnnotation();
	    	char str = curAnno.getStrand();
	    	String chr = curAnno.getChromosome();
	    	if(geneTree.containsKey(chr)){
	    		HashMap<Character,IntervalTree<Gene>> chrTree = geneTree.get(chr);
	    		if(chrTree.containsKey(str)){
	    			IntervalTree<Gene> strTree = chrTree.get(str);
	    			strTree.add(curGene);
	    		}
	    		else{
	    			IntervalTree<Gene> strTree = new IntervalTree<Gene>();
	    			strTree.add(curGene);
	    			chrTree.put(str, strTree);
	    		}
	    	}
	    	else{
    			IntervalTree<Gene> strTree = new IntervalTree<Gene>();
    			strTree.add(curGene);
    			HashMap<Character,IntervalTree<Gene>> chrTree = new HashMap<Character,IntervalTree<Gene>>();
    			chrTree.put(str, strTree);
    			geneTree.put(chr, chrTree);
	    	}
	    }
	    
	    this.setReadTree(bam_reader);
	    
	    System.out.println(geneTree.keySet().toString());
	    
	    for(String chr : geneTree.keySet()){
	    	System.out.println(geneTree.get(chr).keySet().toString());
	    	for(char str: geneTree.get(chr).keySet()){
	    		if(readTree.containsKey(chr)){
		    		if(readTree.get(chr).containsKey(str)){
		    			System.out.println(geneTree.get(chr).get(str).size() + " : " + readTree.get(chr).get(str).size());
		    		}
	    		}
	    	}
	    }
	    
//		long stopTime = System.currentTimeMillis();
//		System.out.println("Input:" + (stopTime-startTime));
	}
	
	public static HashMap<String,String> getAttributes (String[] attrs){
		HashMap <String,String> attrMap = new HashMap <String,String>();
		for(int i = 0; i < attrs.length; i++){
			String curattr = attrs[i];
			curattr = curattr.trim();
			String[] attr = curattr.split("\\s+");
			attr[0] = attr[0].trim();
			attr[1] = attr[1].trim();
			attr[1] = attr[1].replaceAll("\"", "");
			attrMap.put(attr[0],attr[1]);
		}
		return attrMap;
	}
	
	
	public String getOutput(){
//		long startTime = System.currentTimeMillis();
		String tab = "\t";
		String brk = "\n";
		char sep = ':';
		char sip = '|';
		char lin = '-';
//		StringBuilder resultBuilder = new StringBuilder("id\tsymbol\tchr\tstrand\tnprots\tntrans\tSV\tWT\tWT_exons\tWT_prots\tSV_prots\tmin_skipped_exon\tmax_skipped_exon\tmin_skipped_bases\tmax_skipped_bases\n");

		StringBuilder resultBuilder = new StringBuilder("gene\texon\tnum_incl_reads\tnum_excl_reads\tnum_total_reads\tpsi\n");
//
//		for(int key: geneSet.keySet()){
//			Gene gene = geneSet.get(key);
//			Annotation geneAnno = gene.getAnnotation();
//			resultBuilder.append(geneAnno.getId());
//			resultBuilder.append(tab);
//			resultBuilder.append(geneAnno.getName());
//			resultBuilder.append(tab);
//			resultBuilder.append(gene.getStart());
//			resultBuilder.append(sep);
//			resultBuilder.append(gene.getStop());
//			resultBuilder.append(brk);
//		}
		
		String keyString = "ENSG00000158109.10";
		int keyInt = keyString.hashCode();
		Gene gene = geneSet.get(keyInt);
		geneSet.clear();
		geneSet.put(keyInt, gene);

		
		for (Integer key: geneSet.keySet()) {
			Gene curGene = geneSet.get(key);
			Annotation curGAnno = curGene.getAnnotation();
//			System.out.println("getExonSkips");
			ArrayList<ExonSkip> skips = curGene.getExonSkips();
			
//			printExonSkip(resultBuilder, curGene, curGAnno, skips, tab, brk, sep, sip);
			
			String geneid = curGAnno.getId();
			char str = curGAnno.getStrand();
			String chr = curGAnno.getChromosome();
			
			resultBuilder.append((curGene.getStart() + " " + curGene.getStop()));
			resultBuilder.append(brk);
			
			HashSet<Read> readSet = new HashSet<Read>();
			readSet = readTree.get(chr).get(str).getIntervalsSpannedBy(curGene.getStart(), curGene.getStop(), readSet);
			
			IntervalTree<Read> reads = new IntervalTree<Read>(readSet);
			
			System.out.println(reads.size());
			
//			System.out.println(reads.toTreeString());
			
			
			
			for(ExonSkip skip: skips){
				ArrayList<Region> exons = new ArrayList<Region>(skip.getWTExons());
				exons.sort(new StartRegionComparator());
				for( Region exon : exons){
					int start = exon.getStart();
					int stop = exon.getStop()+1;
					resultBuilder.append(geneid);
					resultBuilder.append(tab);
					resultBuilder.append(start);
					resultBuilder.append(lin);
					resultBuilder.append(stop);
					
					int c1 = 0;
					int c2 = 0;
					int d1 = 0;
					int d2 = 0;
					int ges = 0;
					
					ArrayList<Read> exonReads = new ArrayList<Read>();
					exonReads = reads.getIntervalsIntersecting(start, stop, exonReads);
					
					System.out.println(exonReads.size());
					
					resultBuilder.append(brk);
					
					exonReads.sort(new StartReadComparator());
					
					int[] incl = new int[]{29,190,170,171,153,175,230,110,111,134,135,157,136,215,216,91,70,31,76,99,56,180,100,200,222,103,169,6,204,107,85,20,64};
					int[] excl = new int[]{391,260,272,363,253,364,365,277,255,300,388,312,356,346,303,315,338};
					
					
					System.out.println(incl.length + " " + excl.length + " " + (incl.length+excl.length));
					
					for(Read er : exonReads){
						boolean inincl = false;
						boolean inexcl = false;
						resultBuilder.append(er.toString());
						
						ges++;
						
						ArrayList<RegionBlock> in = new ArrayList<RegionBlock>();
						in = er.getAlignmentBlocks().getIntervalsSpannedBy(start, stop, in);
						
						ArrayList<RegionBlock> overl = new ArrayList<RegionBlock>();
						overl = er.getAlignmentBlocks().getIntervalsSpannedBy(start, stop, overl);
						
						resultBuilder.append(tab);						
						if(in.size() > 0){
							if(overl.size() == in.size()){
								c1++;
								resultBuilder.append("incl");
							}
							else{
								resultBuilder.append("overl");
							}
						}
						else{
							d1++;
							resultBuilder.append("excl");
						}
						
						for(int id : incl){
							if(er.getReadName().equals("" + id)){
								inincl = true;
							}
						}
						for(int id : excl){
							if(er.getReadName().equals("" + id)){
								inexcl = true;
							}
						}
						
						if(inincl){
							resultBuilder.append(tab);
							resultBuilder.append("incl");
						}
						else if(inexcl){
							resultBuilder.append(tab);
							resultBuilder.append("excl");
						}
						else{
							resultBuilder.append(tab);
							resultBuilder.append("miss");
						}
						
//						ArrayList<RegionBlock> in1 = new ArrayList<RegionBlock>();
//						in1 = er.getAlignmentBlocksFoP().getIntervalsSpannedBy(start, stop, in1);
//						
//						ArrayList<RegionBlock> in2 = new ArrayList<RegionBlock>();
//						in2 = er.getAlignmentBlocksSoP().getIntervalsSpannedBy(start, stop, in2);
//						
//						resultBuilder.append(tab);
//						
//						if(in1.size() > 0 || in2.size() > 0){
//							c2++;
//							resultBuilder.append("incl");
//						}
//						else{
//							d2++;
//							resultBuilder.append("excl");
//						}
						
						resultBuilder.append(brk);
						
					}
					
					resultBuilder.append(tab);
					resultBuilder.append(c1);
					
					resultBuilder.append(tab);
					resultBuilder.append(c2);
					
					resultBuilder.append(tab);
					resultBuilder.append(d1);
					
					resultBuilder.append(tab);
					resultBuilder.append(d2);
					
					resultBuilder.append(tab);
					resultBuilder.append(ges);
//					
//					int e = c-d;
//					resultBuilder.append(tab);
//					resultBuilder.append(e);
//					
//					resultBuilder.append(tab);
//					resultBuilder.append(c);
					
//					int f = 0;
//					Iterator<SAMRecord>  exon_iterator_in = bam_reader.query(chr, exon.getStart(), exon.getStop()+1, true);
//					
//					ids.clear();
//					
//					while(exon_iterator_in.hasNext()){
//						SAMRecord curRec = exon_iterator_in.next();
//						if(ids.add(curRec.getReadName())){
//							f++;
//						}
//					}
					
//					resultBuilder.append(tab);
//					resultBuilder.append(f);
					
					resultBuilder.append(brk);
				}
				
			}
			
		}
//		long stopTime = System.currentTimeMillis();
//		System.out.println("Skips:" + (stopTime-startTime));
		return resultBuilder.toString();
	}
	
	public void printExonSkip(StringBuilder resultBuilder, Gene curGene, Annotation curGAnno, ArrayList<ExonSkip> skips, String tab, String brk, char sep, char sip){

		//		System.out.println("gotExonSkips");
		int tn = curGene.getTranscriptNumber();
		int pn = curGene.getProteinNumber();
		
		String geneid = curGAnno.getId();
		String genename = curGAnno.getName();
		String chr = curGAnno.getChromosome();
		char str = curGAnno.getStrand();
		
		StringBuilder geneInfo = new StringBuilder(geneid);
		geneInfo.append(tab);
		geneInfo.append(genename);
		geneInfo.append(tab);
		geneInfo.append(chr);
		geneInfo.append(tab);
		geneInfo.append(str);
		geneInfo.append(tab);
		geneInfo.append(tn);
		geneInfo.append(tab);
		geneInfo.append(pn);
		geneInfo.append(tab);
		
		for(ExonSkip skip: skips){
			resultBuilder.append(geneInfo);
			resultBuilder.append(skip.getStart());
			resultBuilder.append(sep);
			resultBuilder.append(skip.getStop());
			resultBuilder.append(tab);
			
			ArrayList<Region> introns = new ArrayList<Region>(skip.getWTIntrons());
			introns.sort(new StartRegionComparator());
			resultBuilder.append(introns.get(0).getStart());
			resultBuilder.append(sep);
			resultBuilder.append(introns.get(0).getStop());
			for(int i = 1; i < introns.size(); i++ ){
				Region curIntron = introns.get(i);
				resultBuilder.append(sip);
				resultBuilder.append(curIntron.getStart());
				resultBuilder.append(sep);
				resultBuilder.append(curIntron.getStop());
			}
			resultBuilder.append(tab);
			
			ArrayList<Region> exons = new ArrayList<Region>(skip.getWTExons());
			exons.sort(new StartRegionComparator());
			resultBuilder.append(exons.get(0).getStart());
			resultBuilder.append(sep);
			resultBuilder.append(exons.get(0).getStop());
			for(int i = 1; i < exons.size(); i++ ){
				Region curExon = exons.get(i);
				resultBuilder.append(sip);
				resultBuilder.append(curExon.getStart());
				resultBuilder.append(sep);
				resultBuilder.append(curExon.getStop());
			}
			resultBuilder.append(tab);
			
			ArrayList<String> wt = new ArrayList<String>(skip.getWTProt());
			resultBuilder.append(wt.get(0));
			for(int i = 1; i < wt.size(); i++ ){
				resultBuilder.append(sip);					
				resultBuilder.append(wt.get(i));
			}
			resultBuilder.append(tab);
			
			ArrayList<String> sv = new ArrayList<String>(skip.getSVProt());
			resultBuilder.append(sv.get(0));
			for(int i = 1; i < sv.size(); i++ ){
				resultBuilder.append(sip);					
				resultBuilder.append(sv.get(i));
			}
			resultBuilder.append(tab);
			
			resultBuilder.append(skip.getMinEx());
			resultBuilder.append(tab);
			resultBuilder.append(skip.getMaxEx());
			resultBuilder.append(tab);
			resultBuilder.append(skip.getMinBase());
			resultBuilder.append(tab);
			resultBuilder.append(skip.getMaxBase());
			resultBuilder.append(brk);
		
		}
	}
	
	public ArrayList<Gene> getGenes(){
		ArrayList<Gene> result = new ArrayList<Gene>();
		for (Integer key: geneSet.keySet()) {
		   result.add(geneSet.get(key));
		}
		return result;
	}
	
	public void setReadTree(SAMFileReader bam_reader){
		SAMRecordIterator bam_it = bam_reader.iterator();

		HashMap<String,SAMRecord> store = new HashMap<String,SAMRecord>();
		
		readTree = new HashMap<String,HashMap<Character,IntervalTree<Read>>>();
		
		while(bam_it.hasNext()){

			SAMRecord samr = bam_it.next();
			
			boolean ignore = samr.getNotPrimaryAlignmentFlag() || samr.getReadUnmappedFlag() || samr.getMateUnmappedFlag() || (samr.getMateNegativeStrandFlag() == samr.getReadNegativeStrandFlag());

			boolean inPair = samr.getFirstOfPairFlag()||samr.getSecondOfPairFlag();

			if(inPair && !ignore){
				String readname = samr.getReadName();
//				&& readname.equals("87178")	
//				Integer x= count.put(readname,1);
//				if(x != null){
//					count.put(readname, x+1);
//				}
				if(store.containsKey(readname)){
//					System.out.println(readname);
					Read curRead = new Read(samr, store.get(readname));
					if(curRead.isConsistent()){
					    char str = curRead.getStrand();
					    String chr = curRead.getChromosome();
					    
					    int start = curRead.getStart();
					    int stop = curRead.getStop();
					    
					    boolean transcriptomic = false;
					   
					    HashSet<Gene> contin = new HashSet<Gene>();
					    contin = geneTree.get(chr).get(str).getIntervalsSpanning(start, stop, contin);
					    
					    if(contin.size() > 0){
					    	for(Gene g : contin){
					    		if(!transcriptomic){
					    			transcriptomic |= g.inTranscript(curRead);
					    		}
					    	}
					    }
					    
					    if(transcriptomic){
						    if(readTree.containsKey(chr)){
						    	HashMap<Character,IntervalTree<Read>> chrTree = readTree.get(chr);
						    	if(chrTree.containsKey(str)){
						    		IntervalTree<Read> strTree = chrTree.get(str);
						    		strTree.add(curRead);
						    	}
						    	else{
						    		IntervalTree<Read> strTree = new IntervalTree<Read>();
						    		strTree.add(curRead);
						    		chrTree.put(str, strTree);
						    	}
						   	}
					    	else{
					    		IntervalTree<Read> strTree = new IntervalTree<Read>();
					    		strTree.add(curRead);
					    		HashMap<Character,IntervalTree<Read>> chrTree = new HashMap<Character,IntervalTree<Read>>();
					    		chrTree.put(str, strTree);
					   			readTree.put(chr, chrTree);
					    	}
					    }
					}
					store.remove(readname);
				}
				else{
					store.put(readname, samr);
				}
			}
		}
		bam_it.close();
		
		
	}
	
	class StartRegionComparator implements Comparator<Region>
	{
	    public int compare(Region x1, Region x2)
	    {
	        return x1.getStart() - x2.getStart();
	    }
	}
	
	class StartReadComparator implements Comparator<Read>
	{
	    public int compare(Read x1, Read x2)
	    {
	        return x1.getStart() - x2.getStart();
	    }
	}

}
