bibcleaner
==========

Java code to take an existing BibTeX file and clean it using DBLP

BibTeX files can become manhandled, dirty, outdated, and then some.
DBLP provides a nicely formatted database of computer science publications.
bibcleaner parses the provided 'dirty' BibTeX file, searches for the best match on DBLP and creates a better entry:
 - the original key is kept, such that existing documents do not need to be updated, while the DBLP key is also stored
 - DBLP fields are used, existing fields are kept if not in the DBLP entry
 - 2 BibTex files are created: one with articles/inproceedings/theses/books etc. and another one with the venues, used for crossreferencing
 - Correct formatting of names, capitalization in titles etc. should be left to the BibTeX style! (see f.i. http://www.podoblaz.net/cml/?id=39 to create a .bst BibTeX style file)
 - ... TO COMPLETE


Borrowed Code
========
 - Using https://code.google.com/p/javabib/ as BibTex parser.
 - Using simple DBLP parsers http://www.informatik.uni-trier.de/~LEY/db/about/simpleparser/index.html

Version History
========

v 0.1 (initial commit)
 - Version as used by Joos Buijs to clean the bibtex file for his Ph.D. thesis. In essence, this version works well enough but contains several bugs and limitations.
  
