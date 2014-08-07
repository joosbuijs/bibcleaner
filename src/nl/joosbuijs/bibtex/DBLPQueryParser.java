package nl.joosbuijs.bibtex;

import java.io.IOException;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.util.Iterator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import bibtex.dom.BibtexEntry;
import bibtex.dom.BibtexFile;
import bibtex.parser.BibtexParser;
import bibtex.parser.ParseException;

public class DBLPQueryParser {

	//TODO generalize: split to query run to produce bibtex URL(s) and func to parse this URL.
	//TODO make instance: nextResult, timeout build in, etc.

	public static String queryString = "http://www.dblp.org/search/api/?q=%s&h=1000&c=4&f=0&format=xml";

	/**
	 * Timeout between queries in seconds
	 */
	public static int timeout = 3;
	/**
	 * Time in nanosec of last query, used to calculate timeouts to prevent DBLP
	 * overload
	 */
	private DateTime lastQueryTime = new DateTime(DateTimeZone.getDefault()).minusSeconds(2 * timeout);

	/**
	 * Last query run
	 */
	private String lastQuery = "NO QUERY RUN YET";

	/**
	 * The list of DBLP entry URLs of the last query
	 */
	private String[] entryURLs;

	/**
	 * The last returned entry of entryURLs
	 */
	private int lastEntryIndex = 0;

	public static void main(String[] args) {
		DBLPQueryParser parser = new DBLPQueryParser();

		Pair<BibtexEntry, BibtexEntry> result = parser
				.getBibTexEntryForUrl("http://www.dblp.org/rec/bibtex/journals/fuin/AalstV14");

		/*-*/
		Pair<BibtexEntry, BibtexEntry> qresult = parser.getFirstEntryForQuery("aalst");

		System.out.println(BibtexCleaner.bibtexEntryToString(qresult.getFirst()));
		System.out.println(BibtexCleaner.bibtexEntryToString(qresult.getSecond()));
		/**/
	}

	public DBLPQueryParser() {

	}

	/**
	 * Returns the first pair of BibTex entries for the given query. Subsequent
	 * results can be obtained via the getNextEntry() method. The list is sorted
	 * from very to least important, according to DBLP. Each pair is the 'real'
	 * entry, followed by the 'venue' crossref entry, or null if not exists.
	 * Executes executeQuery(query) followed by getBibtexEntriesForUrls on the
	 * query result.
	 * 
	 * @param query
	 * @return
	 */
	public Pair<BibtexEntry, BibtexEntry> getFirstEntryForQuery(String query) {
		lastQuery = cleanQuery(query);
		entryURLs = executeQuery(lastQuery);

		lastEntryIndex = 0;
		Pair<BibtexEntry, BibtexEntry> result = null;
		if (entryURLs.length > lastEntryIndex) {
			result = getBibTexEntryForUrl(entryURLs[lastEntryIndex]);
		}

		return result;
	}

	/**
	 * Executes the provided query and returns an array of URL where bibtex
	 * entries can be found
	 * 
	 * @param query
	 * @return
	 */
	public String[] executeQuery(String query) {
		lastQuery = cleanQuery(query);
		return parseDocumentForTag(String.format(queryString, lastQuery), "url");
	}

	/**
	 * Returns the next entry for the last run query
	 * 
	 * @return
	 */
	public Pair<BibtexEntry, BibtexEntry> getNextEntry() {
		lastEntryIndex++;
		return getBibTexEntryForUrl(entryURLs[lastEntryIndex]);
	}

	public Pair<BibtexEntry, BibtexEntry> getEntryByIndex(int index) {
		lastEntryIndex = index;
		return getBibTexEntryForUrl(entryURLs[index]);
	}

	/**
	 * Returns a pair of bibtex entries from a DBLP entry URL
	 * 
	 * @param entry
	 * @return
	 */
	public Pair<BibtexEntry, BibtexEntry> getBibTexEntryForUrl(String entry) {
		String[] entries = parseDocumentForTag(entry, "pre");

		return new Pair<BibtexEntry, BibtexEntry>(stringToBibtexEntry(entries[0]),
				entries.length > 1 ? stringToBibtexEntry(entries[1]) : null);
	}

	/**
	 * Instantiates a bibtex entry from a bibtex String
	 * 
	 * @param string
	 * @return
	 */
	public BibtexEntry stringToBibtexEntry(String string) {
		BibtexParser parser = new BibtexParser(false);
		BibtexFile bibtexFile = new BibtexFile();
		try {
			parser.parse(bibtexFile, new StringReader(string));
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (Iterator it = bibtexFile.getEntries().iterator(); it.hasNext();) {
			Object potentialEntry = it.next();
			if (!(potentialEntry instanceof BibtexEntry))
				continue;
			return (BibtexEntry) potentialEntry;
		}

		return null;
	}

	/**
	 * The entry as shown in the DBLP webpage contains an URL which should be
	 * removed...
	 * 
	 * @param entry
	 * @return
	 */
	public String cleanUrlFromBibTexEntry(String entry) {
		int startHRef = entry.indexOf("<a href");
		int endHRef = entry.indexOf("</a>");
		return entry.replace(entry.substring(startHRef, endHRef + 4), "DBLP");
	}

	/**
	 * Standard function that returns the contents of the specified tags in the
	 * given URL
	 * 
	 * @param url
	 * @param tag
	 * @return
	 */
	public String[] parseDocumentForTag(String url, String tag) {
		try {
			waitForQueryTime();
			org.jsoup.nodes.Document doc;
			try {
				doc = Jsoup.connect(url).get();
			} catch (SocketTimeoutException ste) {
				//Just try again after a second
				System.out.println(" URL read timeout, trying again in 1 sec...");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				doc = Jsoup.connect(url).get();
			}
			Elements elements = doc.select(tag);

			String[] result = new String[elements.size()];
			for (int i = 0; i < elements.size(); i++) {
				result[i] = elements.get(i).text();
			}
			return result;
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*-
		try {
			//Document code from http://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/

			//Execute the query
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			waitForQueryTime();
			//FIXME doc empty? extract by tag not good? something wrong here!!
			Document doc = dBuilder.parse(url);
			doc.getDocumentElement().normalize();

			//Extract all entries
			NodeList nList = doc.getElementsByTagName(tag);
			String[] results = new String[nList.getLength()];

			for (int i = 0; i < nList.getLength(); i++) {
				Node nNode = nList.item(i);
				results[i] = nNode.getNodeValue();
			}

			return results;

		} catch (ParserConfigurationException pce) {
			pce.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}/**/

		return null;
	}

	/*-
	public static Pair<BibtexEntry, BibtexEntry> parseQuery(String query) {
		try {
			//Execute query on DBLP

			//Send query
			URL searchUrl = new URL(String.format(queryString, query));

			BufferedReader searchIn = new BufferedReader(new InputStreamReader(searchUrl.openStream()));

			String inputLine;
			String resultUrl = "";
			while ((inputLine = searchIn.readLine()) != null) {
				//Search for the first URL tag
				if (inputLine.contains("<url>")) {
					resultUrl = inputLine;
					break;
				}
			}
			resultUrl = resultUrl.replace("<url>", "");
			resultUrl = resultUrl.replace("</url>", "");
			searchIn.close();

			if (resultUrl.isEmpty()) {
				System.out.println("No search results for :" + query);
				return null;
			}

			URL bibtexUrl = new URL(resultUrl);
			BufferedReader resultIn = new BufferedReader(new InputStreamReader(bibtexUrl.openStream()));

			System.out.println("Parsing URL " + resultUrl);

			//Instantiate main and proceedings/venue bibtex (if exists)
			String resultLine;
			String firstResult = "";
			String secondResult = "";
			boolean inPre = false;
			boolean first = true;
			while ((resultLine = resultIn.readLine()) != null) {
				if (resultLine.startsWith("<pre>")) {
					inPre = true;
					resultLine = resultLine.replaceFirst("<pre>", "");
					int startHRef = resultLine.indexOf("<a href");
					int endHRef = resultLine.indexOf("</a>");
					resultLine = resultLine.replace(resultLine.substring(startHRef, endHRef + 4), "DBLP");
				}

				if (resultLine.endsWith("</pre>")) {
					inPre = false;
					first = !first;
				}

				if (inPre) {
					if (first) {
						firstResult += resultLine;
					} else {
						secondResult += resultLine;
					}
				}
			}

			//Now instantiate bibtex stuff
			System.out.println("1st: " + firstResult);
			System.out.println("2nd: " + secondResult);

			BibtexEntry firstEntry = null;
			BibtexEntry secondEntry;
			if (!firstResult.isEmpty()) {
				BibtexParser parser = new BibtexParser(false);
				BibtexFile bibtexFile = new BibtexFile();
				try {
					parser.parse(bibtexFile, new StringReader(firstResult));
				} catch (ParseException e) {
					e.printStackTrace();
				}
				for (Iterator it = bibtexFile.getEntries().iterator(); it.hasNext();) {
					Object potentialEntry = it.next();
					if (!(potentialEntry instanceof BibtexEntry))
						continue;
					firstEntry = (BibtexEntry) potentialEntry;
				}
			}

			System.out.print("1st Entry: ");
			if (firstEntry != null) {
				Writer pwout = new StringWriter();
				PrintWriter pw = new PrintWriter(pwout);
				firstEntry.printBibtex(pw);
				System.out.println(pwout);
			} else {
				System.out.println("NULL");
			}

			System.out.println("DONE");
			return null;
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return null;
	}
	/**/

	/**
	 * Returns the total number of results of the last query
	 * 
	 * @return
	 */
	public int getNrOfResults() {
		return entryURLs.length;
	}

	/**
	 * This method waits until a request to DBLP can be send, and updates the
	 * time a last request was send.
	 */
	private void waitForQueryTime() {
		DateTime now = DateTime.now();
		while (!now.isAfter(lastQueryTime.plusSeconds(timeout))) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			now = DateTime.now();
		}
		lastQueryTime = DateTime.now();
	}

	/**
	 * Removes/replaces bad characters from the query string.
	 * 
	 * @param query
	 * @return
	 */
	public String cleanQuery(String query) {
		//Clean new line and tab characters, symbols such as : - ? !
		return query.replaceAll("(\\r|\\n|\\t|[^\\dA-Za-z ])", " ");
	}

}
