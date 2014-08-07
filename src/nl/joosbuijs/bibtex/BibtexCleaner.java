package nl.joosbuijs.bibtex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import org.joda.time.DateTime;

import bibtex.dom.BibtexAbstractEntry;
import bibtex.dom.BibtexEntry;
import bibtex.dom.BibtexFile;
import bibtex.parser.BibtexParser;
import bibtex.parser.ParseException;

/**
 * Class that helps clean/consolidate/fix/improve an existing bibtex file by use
 * of external sources and custom/hard coded fixes.
 * 
 * NOTE AND DISCLAIMER: this code is ugly and was written to only function
 * correctly once on my (messy) thesis bibtex file.
 * 
 * @author jbuijs
 * 
 *         Using https://code.google.com/p/javabib/ as BibTex parser.
 * 
 * 
 *         Using simple DBLP parsers
 *         http://www.informatik.uni-trier.de/~LEY/db/about/simpleparser
 *         /index.html
 * 
 */
public class BibtexCleaner {

	public static int maxNrEntries2Parse = -1;//10;

	public static int AUTHORS_IN_QUERY_MAX_ELEMENTS = 10; //manifesto causes a HTTP 414 error: URL too long :D

	//TODO: sanity check for obtained search result w.r.t. current bibtex entry
	//TODO: post process: authors!
	//TODO: add option to escape special characters (% #) in url and doi fields

	public static void main(String... strings) {
		Set<String> knownKeys = new HashSet<String>();

		try {
			/*
			 * Load the dirty bibliography
			 */
			String sourceBibFile = "joosThesis.bib";
			FileReader in = new FileReader(sourceBibFile);
			BibtexParser parser = new BibtexParser(false);
			BibtexFile file = new BibtexFile();
			parser.parse(file, in);

			System.out.println("loaded dirty Bibtex file " + sourceBibFile);

			/*
			 * Create a the new bibtex file
			 */
			BibtexFile newbibtex = new BibtexFile();
			BibtexFile newCrossrefbibtex = new BibtexFile();

			/*
			 * Now for each original entry
			 */
			DBLPQueryParser dblpQuery = new DBLPQueryParser();

			System.out.println("There are " + file.getEntries().size() + " entries to parse");

			System.out.println("We expect to finish around "
					+ DateTime.now().plusSeconds(
							2
									* Math.min(maxNrEntries2Parse > 0 ? maxNrEntries2Parse : Integer.MAX_VALUE, file
											.getEntries().size()) * DBLPQueryParser.timeout));

			String toplevelComment = String.format("%% This bibTex file was cleaned by BibtexCleaner by Joos Buijs %n"
					+ "%% Cleaned version of %s%n" + "%% Cleaned on %s%n" + "%% %n%n", sourceBibFile, DateTime.now());

			newbibtex.addEntry(newbibtex.makeToplevelComment(toplevelComment));
			newCrossrefbibtex.addEntry(newCrossrefbibtex.makeToplevelComment(toplevelComment));
			newCrossrefbibtex.addEntry(newCrossrefbibtex.makeToplevelComment("%%Crossreference bibtex file!"));

			int nrEntriesCleaned = 0;
			for (BibtexAbstractEntry potentialEntry : file.getEntries()) {
				if (potentialEntry instanceof BibtexEntry) {
					BibtexEntry entry = (BibtexEntry) potentialEntry;
					System.out.println("Cleaning entry " + entry.getEntryKey());

					//Find matching entry on DBLP
					Pair<BibtexEntry, BibtexEntry> firstMatch = dblpQuery.getFirstEntryForQuery(entry.getFieldValue(
							"title").toString());
					System.out.println(" Found " + dblpQuery.getNrOfResults() + " results on DBLP");

					//add authors (last name) etc.
					if (dblpQuery.getNrOfResults() > 1) {
						System.out.println(" Fine-tuning query...");
						firstMatch = dblpQuery.getFirstEntryForQuery(entry.getFieldValue("title").toString() + " "
								+ BibtexCleaner.getAuthorNames(entry));

						if (dblpQuery.getNrOfResults() > 10) {
							System.out.println(" There were too many results. Using old entry...");
							BibtexFile bibtexFile;
							if (BibtexCleaner.isCrossrefType(entry)) {
								bibtexFile = newCrossrefbibtex;
							} else {
								bibtexFile = newbibtex;
							}
							bibtexFile.addEntry(newbibtex.makeToplevelComment("NOT CLEANED ENTRY (too many results):"));
							bibtexFile.addEntry(entry);
							continue;

						} else if (dblpQuery.getNrOfResults() == 0) {
							System.out.println(" Executing title query only and let user pick");
							firstMatch = dblpQuery.getFirstEntryForQuery(entry.getFieldValue("title").toString());
						}
					}

					//Now given the 'best' query we got:
					if (dblpQuery.getNrOfResults() > 10) {
						System.out.println(" There were too many results. Using old entry...");
						BibtexFile bibtexFile;
						if (BibtexCleaner.isCrossrefType(entry)) {
							bibtexFile = newCrossrefbibtex;
						} else {
							bibtexFile = newbibtex;
						}
						bibtexFile.addEntry(newbibtex.makeToplevelComment("NOT CLEANED ENTRY (too many results):"));
						bibtexFile.addEntry(entry);
						continue;
					}
					if (dblpQuery.getNrOfResults() > 1) {
						System.out.println(" We got too many results, please choose the best option.");
						System.out.println(" Original: ");
						System.out.println(BibtexCleaner.bibtexEntryToString(entry));
						for (int i = 0; i < dblpQuery.getNrOfResults(); i++) {
							System.out.println(" SUGGESTION " + i + ":");
							System.out.println(BibtexCleaner.bibtexEntryToString(dblpQuery.getEntryByIndex(i)
									.getFirst()));
						}
						System.out.println(" Which one should we use? Press ENTER to use old entry...");
						Scanner sysoIn = new Scanner(System.in);
						String answer = sysoIn.nextLine();
						if (answer.isEmpty()) {
							System.out.println(" Using old entry");
							BibtexFile bibtexFile;
							if (BibtexCleaner.isCrossrefType(entry)) {
								bibtexFile = newCrossrefbibtex;
							} else {
								bibtexFile = newbibtex;
							}
							if (!knownKeys.contains(entry.getEntryKey())) {
								bibtexFile.addEntry(newbibtex
										.makeToplevelComment("NOT CLEANED ENTRY (by user choice):"));
								bibtexFile.addEntry(entry);
							}
							continue;
						} else {
							int correctIndex = Integer.parseInt(answer);
							System.out.println(" Using entry at index " + correctIndex);
							firstMatch = dblpQuery.getEntryByIndex(correctIndex);
							//FIXME entry not added
						}
					} else if (dblpQuery.getNrOfResults() == 0) {
						//No results found, can not improve
						System.out.println(" No results found, copying old entry to new bibtex file");

						BibtexFile bibtexFile;
						if (BibtexCleaner.isCrossrefType(entry)) {
							bibtexFile = newCrossrefbibtex;
						} else {
							bibtexFile = newbibtex;
						}
						if (!knownKeys.contains(entry.getEntryKey())) {
							bibtexFile.addEntry(newbibtex.makeToplevelComment("NOT CLEANED ENTRY:"));
							bibtexFile.addEntry(entry);
						}
						continue;
					} else {

						nrEntriesCleaned++;

						//Update key of DBLP entry to original key
						BibtexEntry newEntry = firstMatch.getFirst();
						newEntry.addFieldValue("DBLPkey", newbibtex.makeString(newEntry.getEntryKey()));
						newEntry.setEntryKey(entry.getEntryKey());

						//Add fields of original entry to the new entry if not contained
						for (String fieldKey : entry.getFields().keySet()) {
							if (!newEntry.getFields().containsKey(fieldKey)) {
								newEntry.addFieldValue(fieldKey, entry.getFieldValue(fieldKey));
							}
						}

						//Add new entry to new bibtex 
						if (!knownKeys.contains(newEntry.getEntryKey())) {
							System.out.println(" Adding new entry ");
							if (BibtexCleaner.isCrossrefType(newEntry)) {
								newCrossrefbibtex.addEntry(newEntry);
							} else {

								newbibtex.addEntry(newEntry);
							}
							if (firstMatch.getSecond() != null) {
								System.out.println(" Adding new venue/crossref entry");
								newCrossrefbibtex.addEntry(firstMatch.getSecond());
							}
						}
					}
				} else {
					//We keep 'failed' potential entries... (like JabRef comments and such)
					newbibtex.addEntry(potentialEntry);
				}

				if (maxNrEntries2Parse >= 0 && nrEntriesCleaned >= maxNrEntries2Parse) {
					break;
				}
			}

			/*
			 * Save the new bibtex file
			 */
			String newBibFile = sourceBibFile.replace(".bib", "_cleaned.bib");
			System.out.println("Saving clean Bibtex to " + newBibFile);
			newbibtex.printBibtex(new PrintWriter(new File(newBibFile)));

			String newCrossrefBibFile = newBibFile.replace(".bib", "_crossref.bib");
			System.out.println("Saving clean crossref Bibtex to " + newCrossrefBibFile);
			newCrossrefbibtex.printBibtex(new PrintWriter(new File(newCrossrefBibFile)));

			System.out.println("DONE");
			/*-
			for (Iterator it = file.getEntries().iterator(); it.hasNext();) {
				Object potentialEntry = it.next();
				if (!(potentialEntry instanceof BibtexEntry))
					continue;
				BibtexEntry entry = (BibtexEntry) potentialEntry;
				BibtexString authorString = (BibtexString) entry.getFieldValue("author");
				if (authorString == null)
					continue;
				String content = authorString.getContent();
				String tokens[] = content.split("\\s++");
				for (int i = 0; i < tokens.length; i++) {
					if (tokens[i].toLowerCase().equals("and")) {
						System.out.println();
						continue;
					} else if (tokens[i].toLowerCase().equals("others"))
						continue;
					System.out.print(tokens[i] + " ");
				}
				System.out.println();
			}/**/

		} catch (ParseException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Returns the author names, without initials, from the given entry
	 * 
	 * @param entry
	 * @return
	 */
	public static String getAuthorNames(BibtexEntry entry) {
		String result = "";
		
		if(entry.getFieldValue("author")==null){
			return result;
		}
		
		String[] chunks = entry.getFieldValue("author").toString().split(" |,");
		int nrAuthorChunks = 0;
		for (String chunk : chunks) {
			if (chunk.length() > 2 && !chunk.equals("and")) {
				result += " " + chunk;
			}
			nrAuthorChunks++;
			if (nrAuthorChunks >= AUTHORS_IN_QUERY_MAX_ELEMENTS) {
				break;
			}
		}
		return result;
	}

	public static String bibtexEntryToString(BibtexEntry entry) {
		if (entry == null) {
			return "NULL";
		}
		Writer pwout = new StringWriter();
		PrintWriter pw = new PrintWriter(pwout);
		entry.printBibtex(pw);
		return pwout.toString();
	}

	public static boolean isCrossrefType(BibtexEntry entry) {
		return entry.getEntryType().equalsIgnoreCase("proceedings")
				|| entry.getEntryType().equalsIgnoreCase("collection");
	}
}
