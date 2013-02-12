/**
 *  OpensearchResponseWriter
 *  Copyright 2012 by Michael Peter Christen
 *  First released 06.08.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.federate.solr.responsewriter;

import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.cora.protocol.HeaderFramework;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.XML;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

public class OpensearchResponseWriter implements QueryResponseWriter {

    // define a list of simple YaCySchema -> RSS Token matchings
    private static final Map<String, String> field2tag = new HashMap<String, String>();

    // pre-select a set of YaCy schema fields for the solr searcher which should cause a better caching
    private static final YaCySchema[] extrafields = new YaCySchema[]{
        YaCySchema.id, YaCySchema.title, YaCySchema.description, YaCySchema.text_t,
        YaCySchema.h1_txt, YaCySchema.h2_txt, YaCySchema.h3_txt, YaCySchema.h4_txt, YaCySchema.h5_txt, YaCySchema.h6_txt,
        };
    static final Set<String> SOLR_FIELDS = new HashSet<String>();
    static {
        field2tag.put(YaCySchema.coordinate_p.getSolrFieldName() + "_0_coordinate", Geo.Lat.getURIref());
        field2tag.put(YaCySchema.coordinate_p.getSolrFieldName() + "_1_coordinate", Geo.Long.getURIref());
        field2tag.put(YaCySchema.publisher_t.getSolrFieldName(), DublinCore.Publisher.getURIref());
        field2tag.put(YaCySchema.author.getSolrFieldName(), DublinCore.Creator.getURIref());
        SOLR_FIELDS.addAll(field2tag.keySet());
        for (YaCySchema field: extrafields) SOLR_FIELDS.add(field.getSolrFieldName());
    }

    private String title;

    public static class ResHead {
        public int offset, rows, numFound;
        //public int status, QTime;
        //public String df, q, wt;
        //public float maxScore;
    }

    public OpensearchResponseWriter() {
        super();
    }

    public void setTitle(String searchPageTitle) {
        this.title = searchPageTitle;
    }

    @Override
    public String getContentType(final SolrQueryRequest request, final SolrQueryResponse response) {
        return CONTENT_TYPE_XML_UTF8;
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList n) {
    }

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
        
        NamedList<?> values = rsp.getValues();
        
        assert values.get("responseHeader") != null;
        assert values.get("response") != null;

        SimpleOrderedMap<Object> responseHeader = (SimpleOrderedMap<Object>) rsp.getResponseHeader();
        DocList response = ((ResultContext) values.get("response")).docs;
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> facetCounts = (SimpleOrderedMap<Object>) values.get("facet_counts");
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> facetFields = facetCounts == null || facetCounts.size() == 0 ? null : (SimpleOrderedMap<Object>) facetCounts.get("facet_fields");
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> highlighting = (SimpleOrderedMap<Object>) values.get("highlighting");
        Map<String, List<String>> snippets = highlighting(highlighting);

        // parse response header
        ResHead resHead = new ResHead();
        NamedList<?> val0 = (NamedList<?>) responseHeader.get("params");
        resHead.rows = Integer.parseInt((String) val0.get("rows"));
        resHead.offset = response.offset(); // equal to 'start'
        resHead.numFound = response.matches();
        //resHead.df = (String) val0.get("df");
        //resHead.q = (String) val0.get("q");
        //resHead.wt = (String) val0.get("wt");
        //resHead.status = (Integer) responseHeader.get("status");
        //resHead.QTime = (Integer) responseHeader.get("QTime");
        //resHead.maxScore = response.maxScore();

        // write header
        writer.write((
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<?xml-stylesheet type='text/xsl' href='/yacysearch.xsl' version='1.0'?>\n" +
                        "<rss version=\"2.0\"\n" +
                        "    xmlns:yacy=\"http://www.yacy.net/\"\n" +
                        "    xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\"\n" +
                        "    xmlns:media=\"http://search.yahoo.com/mrss/\"\n" +
                        "    xmlns:atom=\"http://www.w3.org/2005/Atom\"\n" +
                        "    xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\n" +
                        "    xmlns:geo=\"http://www.w3.org/2003/01/geo/wgs84_pos#\"\n" +
                        ">\n").toCharArray());
        openTag(writer, "channel");
        solitaireTag(writer, "opensearch:totalResults", Integer.toString(resHead.numFound));
        solitaireTag(writer, "opensearch:startIndex", Integer.toString(resHead.offset));
        solitaireTag(writer, "opensearch:itemsPerPage", Integer.toString(resHead.rows));
        solitaireTag(writer, RSSMessage.Token.title.name(), this.title);
        writer.write("<atom:link rel=\"search\" href=\"http://localhost:8090/opensearchdescription.xml\" type=\"application/opensearchdescription+xml\"/>");
        solitaireTag(writer, "description", "Search Result");
        //solitaireTag(writer, "link", "");
        //solitaireTag(writer, "image", "");

        // parse body
        final int responseCount = response.size();
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        String urlhash = null;
        for (int i = 0; i < responseCount; i++) {
            openTag(writer, "item");
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, SOLR_FIELDS);
            List<IndexableField> fields = doc.getFields();
            int fieldc = fields.size();
            List<String> texts = new ArrayList<String>();
            String description = "", title = "";
            for (int j = 0; j < fieldc; j++) {
                IndexableField value = fields.get(j);
                String fieldName = value.name();

                // apply generic matching rule
                String stag = field2tag.get(fieldName);
                if (stag != null) {
                    solitaireTag(writer, stag, value.stringValue());
                    continue;
                }
                
                // take apart the url
                if (YaCySchema.sku.getSolrFieldName().equals(fieldName)) {
                    String u = value.stringValue();
                    solitaireTag(writer, RSSMessage.Token.link.name(), u);
                    try {
                        MultiProtocolURI url = new MultiProtocolURI(u);
                        solitaireTag(writer, YaCyMetadata.host.getURIref(), url.getHost());
                        solitaireTag(writer, YaCyMetadata.path.getURIref(), url.getPath());
                        solitaireTag(writer, YaCyMetadata.file.getURIref(), url.getFileName());
                    } catch (MalformedURLException e) {}
                    continue;
                }
                
                // if the rule is not generic, use the specific here
                if (YaCySchema.id.getSolrFieldName().equals(fieldName)) {
                    urlhash = value.stringValue();
                    solitaireTag(writer, RSSMessage.Token.guid.name(), urlhash, "isPermaLink=\"false\"");
                    continue;
                }
                if (YaCySchema.title.getSolrFieldName().equals(fieldName)) {
                    title = value.stringValue();
                    texts.add(title);
                    continue;
                }
                if (YaCySchema.last_modified.getSolrFieldName().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    solitaireTag(writer, RSSMessage.Token.pubDate.name(), HeaderFramework.formatRFC1123(d));
                    continue;
                }
                if (YaCySchema.description.getSolrFieldName().equals(fieldName)) {
                    description = value.stringValue();
                    solitaireTag(writer, DublinCore.Description.getURIref(), description);
                    texts.add(description);
                    continue;
                }
                if (YaCySchema.text_t.getSolrFieldName().equals(fieldName)) {
                    texts.add(value.stringValue());
                    continue;
                }
                if (YaCySchema.size_i.getSolrFieldName().equals(fieldName)) {
                    int size = value.numericValue().intValue();
                    solitaireTag(writer, YaCyMetadata.size.getURIref(), Integer.toString(size));
                    solitaireTag(writer, YaCyMetadata.sizename.getURIref(), RSSMessage.sizename(size));
                    continue;
                }
                if (YaCySchema.h1_txt.getSolrFieldName().equals(fieldName) || YaCySchema.h2_txt.getSolrFieldName().equals(fieldName) ||
                    YaCySchema.h3_txt.getSolrFieldName().equals(fieldName) || YaCySchema.h4_txt.getSolrFieldName().equals(fieldName) ||
                    YaCySchema.h5_txt.getSolrFieldName().equals(fieldName) || YaCySchema.h6_txt.getSolrFieldName().equals(fieldName)) {
                    // because these are multi-valued fields, there can be several of each
                    texts.add(value.stringValue());
                    continue;
                }
            }
            
            // compute snippet from texts
            solitaireTag(writer, RSSMessage.Token.title.name(), title.length() == 0 ? (texts.size() == 0 ? "" : texts.get(0)) : title);
            List<String> snippet = urlhash == null ? null : snippets.get(urlhash);
            String tagname = RSSMessage.Token.description.name();
            writer.write("<"); writer.write(tagname); writer.write('>');
            XML.escapeCharData(snippet == null || snippet.size() == 0 ? description : snippet.get(0), writer);
            writer.write("</"); writer.write(tagname); writer.write(">\n");
            
            // open: where do we get the subject?
            //solitaireTag(writer, DublinCore.Subject.getURIref(), ""); // TODO: fill with actual data
            
            closeTag(writer, "item");
        }

        openTag(writer, "yacy:navigation");
        
        // the facets can be created with the options &facet=true&facet.mincount=1&facet.field=host_s&facet.field=url_file_ext_s&facet.field=url_protocol_s&facet.field=author_sxt
        @SuppressWarnings("unchecked")
        NamedList<Integer> domains = facetFields == null ? null : (NamedList<Integer>) facetFields.get(YaCySchema.host_s.getSolrFieldName());
        @SuppressWarnings("unchecked")
        NamedList<Integer> filetypes = facetFields == null ? null : (NamedList<Integer>) facetFields.get(YaCySchema.url_file_ext_s.getSolrFieldName());
        @SuppressWarnings("unchecked")
        NamedList<Integer> protocols = facetFields == null ? null : (NamedList<Integer>) facetFields.get(YaCySchema.url_protocol_s.getSolrFieldName());
        @SuppressWarnings("unchecked")
        NamedList<Integer> authors = facetFields == null ? null : (NamedList<Integer>) facetFields.get(YaCySchema.author_sxt.getSolrFieldName());
        
        if (domains != null) {
            openTag(writer, "yacy:facet name=\"domains\" displayname=\"Domains\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: domains) facetEntry(writer, "site", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        if (filetypes != null) {
            openTag(writer, "yacy:facet name=\"filetypes\" displayname=\"Filetypes\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: filetypes) facetEntry(writer, "filetype", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        if (protocols != null) {
            openTag(writer, "yacy:facet name=\"protocols\" displayname=\"Protocols\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: protocols) facetEntry(writer, "protocol", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        if (authors != null) {
            openTag(writer, "yacy:facet name=\"authors\" displayname=\"Authors\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: authors) facetEntry(writer, "author", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        closeTag(writer, "yacy:navigation");
        
        closeTag(writer, "channel");
        writer.write("</rss>\n".toCharArray());
    }
    
    /**
     * produce snippets from solr (they call that 'highlighting')
     * @param val
     * @return a map from urlhashes to a list of snippets for that url
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> highlighting(final SimpleOrderedMap<Object> val) {
        Map<String, List<String>> snippets = new HashMap<String, List<String>>();
        if (val == null) return snippets;
        int sz = val.size();
        Object v, vv;
        for (int i = 0; i < sz; i++) {
            String n = val.getName(i);
            v = val.getVal(i);
            if (v instanceof SimpleOrderedMap) {
                int sz1 = ((SimpleOrderedMap<Object>) v).size();
                List<String> t = new ArrayList<String>(sz1);
                for (int j = 0; j < sz1; j++) {
                    vv = ((SimpleOrderedMap<Object>) v).getVal(j);
                    if (vv instanceof String[]) {
                        for (String t0: ((String[]) vv)) t.add(t0);
                    }
                }
                snippets.put(n, t);
            }
        }
        return snippets;
    }

    public static void openTag(final Writer writer, final String tag) throws IOException {
        writer.write('<'); writer.write(tag); writer.write(">\n");
    }

    public static void closeTag(final Writer writer, final String tag) throws IOException {
        writer.write("</"); writer.write(tag); writer.write(">\n");
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<"); writer.write(tagname); writer.write('>');
        XML.escapeCharData(value, writer);
        writer.write("</"); writer.write(tagname); writer.write(">\n");
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value, String attr) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<"); writer.write(tagname);
        if (attr.charAt(0) != ' ') writer.write(' ');
        writer.write(attr);
        writer.write('>');
        writer.write(value);
        writer.write("</"); writer.write(tagname); writer.write(">\n");
    }

    private static void facetEntry(final Writer writer, final String modifier, final String propname, String value) throws IOException {
        writer.write("<yacy:element name=\""); XML.escapeCharData(propname, writer);
        writer.write("\" count=\""); XML.escapeCharData(value, writer);
        writer.write("\" modifier=\""); writer.write(modifier); writer.write("%3A"); XML.escapeCharData(propname, writer);
        writer.write("\" />\n");
    }

}
