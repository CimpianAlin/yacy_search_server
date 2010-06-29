//rssParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.yacy.cora.document.Hit;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSReader;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;


public class rssParser extends AbstractParser implements Parser {

	public rssParser() {
		super("Rich Site Summary/Atom Feed Parser");
        SUPPORTED_EXTENSIONS.add("rss");
        SUPPORTED_EXTENSIONS.add("xml");
        SUPPORTED_MIME_TYPES.add("XML");
        SUPPORTED_MIME_TYPES.add("text/rss");
        SUPPORTED_MIME_TYPES.add("application/rss+xml");
        SUPPORTED_MIME_TYPES.add("application/atom+xml");
	}

	public Document[] parse(final MultiProtocolURI location, final String mimeType, final String charset, final InputStream source) throws Parser.Failure, InterruptedException {

        final LinkedList<String> feedSections = new LinkedList<String>();
        final HashMap<MultiProtocolURI, String> anchors = new HashMap<MultiProtocolURI, String>();
        final HashMap<MultiProtocolURI, ImageEntry> images  = new HashMap<MultiProtocolURI, ImageEntry>();
        final ByteBuffer text = new ByteBuffer();
        final CharBuffer authors = new CharBuffer();
        
        RSSFeed feed = null;
        try {
            feed = new RSSReader(source).getFeed();
        } catch (IOException e) {
            throw new Parser.Failure("reading feed failed: " + e.getMessage(), location);
        }
        if (feed == null) throw new Parser.Failure("no feed in document", location);
        
        String feedTitle = "";
        String feedDescription = "";
        String feedPublisher = "";
        String[] feedSubject = {""};
        if (feed.getChannel() != null) {//throw new Parser.Failure("no channel in document",location);
            
            // get the rss feed title and description
            feedTitle = feed.getChannel().getTitle();

            // get feed creator
			final String feedCreator = feed.getChannel().getAuthor();
			if (feedCreator != null && feedCreator.length() > 0) authors.append(",").append(feedCreator);            
            
            // get the feed description
            feedDescription = feed.getChannel().getDescription();
            
            // the feed publisher
            feedPublisher = feed.getChannel().getCopyright();
            
            // the feed subject
            feedSubject = feed.getChannel().getSubject();
        }
        
        if (feed.getImage() != null) {
            try {
                MultiProtocolURI imgURL = new MultiProtocolURI(feed.getImage());
                images.put(imgURL, new ImageEntry(imgURL, feedTitle, -1, -1, -1));
            } catch (MalformedURLException e) {}
        }            
        
        // loop through the feed items
        for (final Hit item: feed) {
                
    			final String itemTitle = item.getTitle();
    			MultiProtocolURI itemURL = null;
                try {
                    itemURL = new MultiProtocolURI(item.getLink());
                } catch (MalformedURLException e) {
                    continue;
                }
    			final String itemDescr = item.getDescription();
    			final String itemCreator = item.getAuthor();
    			if (itemCreator != null && itemCreator.length() > 0) authors.append(",").append(itemCreator);
                
                feedSections.add(itemTitle);
                anchors.put(itemURL, itemTitle);
                
            	if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
            	text.append(AbstractScraper.stripAll(itemDescr).trim()).append(' ');
                
                final String itemContent = item.getDescription();
                if ((itemContent != null) && (itemContent.length() > 0)) {
                    
                    final ContentScraper scraper = new ContentScraper(itemURL);
                    final Writer writer = new TransformerWriter(null, null, scraper, null, false);
                    try {
                        FileUtils.copy(new ByteArrayInputStream(itemContent.getBytes("UTF-8")), writer, Charset.forName("UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        continue;
                    } catch (IOException e) {
                        continue;
                    }
                    
                    final String itemHeadline = scraper.getTitle();     
                    if (itemHeadline != null && itemHeadline.length() > 0) {
                        feedSections.add(itemHeadline);
                    }
                    
                    final Map<MultiProtocolURI, String> itemLinks = scraper.getAnchors();
                    if (itemLinks != null && !itemLinks.isEmpty()) {
                        anchors.putAll(itemLinks);
                    }
                    
                    final HashMap<MultiProtocolURI, ImageEntry> itemImages = scraper.getImages();
                    if (itemImages != null && !itemImages.isEmpty()) {
                        ContentScraper.addAllImages(images, itemImages);
                    }
                    
                    final byte[] extractedText = scraper.getText();
                    if ((extractedText != null) && (extractedText.length > 0)) {
						if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
						text.append(scraper.getText());
                    }
                    
                }
        }
        
        final Document[] docs = new Document[]{new Document(
                location,
                mimeType,
                "UTF-8",
                null,
                feedSubject,
                feedTitle,
                (authors.length() > 0)?authors.toString(1,authors.length()):"",
                feedPublisher,
                feedSections.toArray(new String[feedSections.size()]),
                feedDescription,
                text.getBytes(),
                anchors,
                images,
                false)};
        // close streams
        try {
            text.close();
            authors.close();
        } catch (IOException e) {
        }
        
        return docs;
	}
}
