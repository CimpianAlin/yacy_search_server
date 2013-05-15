package net.yacy;
// migration.java
// -----------------------
// (C) by Alexander Schier
//
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

import net.yacy.search.index.ReindexSolrBusyThread;
import java.io.File;
import java.io.IOException;
import java.util.List;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

import com.google.common.io.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.yacy.cora.storage.Configuration.Entry;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.search.index.Fulltext;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.LukeRequest;
import org.apache.solr.client.solrj.response.LukeResponse;

public class migration {
    //SVN constants
    public static final int USE_WORK_DIR=1389; //wiki & messages in DATA/WORK
    public static final int TAGDB_WITH_TAGHASH=1635; //tagDB keys are tagHashes instead of plain tagname.
    public static final int NEW_OVERLAYS=4422;
    public static final int IDX_HOST=7724; // api for index retrieval: host index
   
    public static void migrate(final Switchboard sb, final int fromRev, final int toRev){
        if(fromRev < toRev){
            if(fromRev < TAGDB_WITH_TAGHASH){
                migrateBookmarkTagsDB(sb);
            }
            if(fromRev < NEW_OVERLAYS){
                migrateDefaultFiles(sb);
            }
            Log.logInfo("MIGRATION", "Migrating from "+ fromRev + " to " + toRev);
            presetPasswords(sb);
            migrateSwitchConfigSettings(sb);
            migrateWorkFiles(sb);
        }
        installSkins(sb); // FIXME: yes, bad fix for quick release 0.47
    }
    /*
     * remove the static defaultfiles. We use them through a overlay now.
     */
    public static void migrateDefaultFiles(final Switchboard sb){
        File file=new File(sb.htDocsPath, "share/dir.html");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "share/dir.class");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "share/dir.java");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "www/welcome.html");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "www/welcome.java");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "www/welcome.class");
        if(file.exists())
            delete(file);
    }

    /*
     * copy skins from the release to DATA/SKINS.
     */
    public static void installSkins(final Switchboard sb){
        final File skinsPath = sb.getDataPath("skinPath", "DATA/SKINS");
        final File defaultSkinsPath = new File(sb.getAppPath(), "skins");
        if (defaultSkinsPath.exists()) {
            final List<String> skinFiles = FileUtils.getDirListing(defaultSkinsPath.getAbsolutePath());
            mkdirs(skinsPath);
            for (final String skinFile : skinFiles){
                if (skinFile.endsWith(".css")){
                    final File from = new File(defaultSkinsPath, skinFile);
                    final File to = new File(skinsPath, skinFile);
                    if (from.lastModified() > to.lastModified()) try {
                        Files.copy(from, to);
                    } catch (final IOException e) {}
                }
            }
        }
        String skin=sb.getConfig("currentSkin", "default");
        if(skin.equals("")){
            skin="default";
        }
        final File skinsDir=sb.getDataPath("skinPath", "DATA/SKINS");
        final File skinFile=new File(skinsDir, skin+".css");
        final File htdocsPath=new File(sb.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT), "env");
        final File styleFile=new File(htdocsPath, "style.css");
        if(!skinFile.exists()){
            if(styleFile.exists()){
                Log.logInfo("MIGRATION", "Skin "+skin+" not found. Keeping old skin.");
            }else{
                Log.logSevere("MIGRATION", "Skin "+skin+" and no existing Skin found.");
            }
        }else{
            try {
                mkdirs(styleFile.getParentFile());
                Files.copy(skinFile, styleFile);
                Log.logInfo("MIGRATION", "copied new Skinfile");
            } catch (final IOException e) {
                Log.logSevere("MIGRATION", "Cannot copy skinfile.");
            }
        }
    }

	/**
	 * @param path
	 */
	private static void mkdirs(final File path) {
		if (!path.exists()) {
			if(!path.mkdirs())
				Log.logWarning("MIGRATION", "could not create directories for "+ path);
		}
	}
    public static void migrateBookmarkTagsDB(final Switchboard sb){
        sb.bookmarksDB.close();
        final File tagsDBFile=new File(sb.workPath, "bookmarkTags.db");
        if(tagsDBFile.exists()){
            delete(tagsDBFile);
            Log.logInfo("MIGRATION", "Migrating bookmarkTags.db to use wordhashs as keys.");
        }
        try {
            sb.initBookmarks();
        } catch (final IOException e) {
            Log.logException(e);
        }
    }

	/**
	 * @param filename
	 */
	private static void delete(final File filename) {
		if(!filename.delete())
			Log.logWarning("MIGRATION", "could not delete "+ filename);
	}
    public static void migrateWorkFiles(final Switchboard sb){
        File file=new File(sb.getDataPath(), "DATA/SETTINGS/wiki.db");
        File file2;
        if (file.exists()) {
            Log.logInfo("MIGRATION", "Migrating wiki.db to "+ sb.workPath);
            sb.wikiDB.close();
            file2 = new File(sb.workPath, "wiki.db");
            try {
                Files.copy(file, file2);
                file.delete();
            } catch (final IOException e) {
            }

            file = new File(sb.getDataPath(), "DATA/SETTINGS/wiki-bkp.db");
            if (file.exists()) {
                Log.logInfo("MIGRATION", "Migrating wiki-bkp.db to "+ sb.workPath);
                file2 = new File(sb.workPath, "wiki-bkp.db");
                try {
                    Files.copy(file, file2);
                    file.delete();
                } catch (final IOException e) {}
            }
            try {
                sb.initWiki();
            } catch (final IOException e) {
                Log.logException(e);
            }
        }


        file=new File(sb.getDataPath(), "DATA/SETTINGS/message.db");
        if(file.exists()){
            Log.logInfo("MIGRATION", "Migrating message.db to "+ sb.workPath);
            sb.messageDB.close();
            file2=new File(sb.workPath, "message.db");
            try {
                Files.copy(file, file2);
                file.delete();
            } catch (final IOException e) {}
            try {
                sb.initMessages();
            } catch (final IOException e) {
                Log.logException(e);
            }
        }
    }

    public static void presetPasswords(final Switchboard sb) {
        // set preset accounts/passwords
        String acc;
        if ((acc = sb.getConfig("adminAccount", "")).length() > 0) {
            sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString(acc)));
            sb.setConfig("adminAccount", "");
        }

        // fix unsafe old passwords
        if ((acc = sb.getConfig("proxyAccountBase64", "")).length() > 0) {
            sb.setConfig("proxyAccountBase64MD5", Digest.encodeMD5Hex(acc));
            sb.setConfig("proxyAccountBase64", "");
        }
        if ((acc = sb.getConfig("uploadAccountBase64", "")).length() > 0) {
            sb.setConfig("uploadAccountBase64MD5", Digest.encodeMD5Hex(acc));
            sb.setConfig("uploadAccountBase64", "");
        }
        if ((acc = sb.getConfig("downloadAccountBase64", "")).length() > 0) {
            sb.setConfig("downloadAccountBase64MD5", Digest.encodeMD5Hex(acc));
            sb.setConfig("downloadAccountBase64", "");
        }
    }

    public static void migrateSwitchConfigSettings(final Switchboard sb) {

        // migration for additional parser settings
        String value = "";
        //Locales in DATA, because DATA must be writable, htroot not.
        if(sb.getConfig("locale.translated_html", "DATA/LOCALE/htroot").equals("htroot/locale")){
        	sb.setConfig("locale.translated_html", "DATA/LOCALE/htroot");
        }

        // migration for blacklists
        if ((value = sb.getConfig("proxyBlackListsActive","")).length() > 0) {
            sb.setConfig("proxy.BlackLists", value);
            sb.setConfig("crawler.BlackLists", value);
            sb.setConfig("dht.BlackLists", value);
            sb.setConfig("search.BlackLists", value);
            sb.setConfig("surftips.BlackLists", value);

            sb.setConfig("BlackLists.Shared",sb.getConfig("proxyBlackListsShared",""));
            sb.setConfig("proxyBlackListsActive", "");
        }

        // migration of http specific crawler settings
        if ((value = sb.getConfig("crawler.acceptLanguage","")).length() > 0) {
            sb.setConfig("crawler.http.acceptEncoding", sb.getConfig("crawler.acceptEncoding","gzip,deflate"));
            sb.setConfig("crawler.http.acceptLanguage", sb.getConfig("crawler.acceptLanguage","en-us,en;q=0.5"));
            sb.setConfig("crawler.http.acceptCharset",  sb.getConfig("crawler.acceptCharset","ISO-8859-1,utf-8;q=0.7,*;q=0.7"));
        }
    }
    /**
     * converts old urldb to Solr.
     * In chunks of 1000 entries.
     * Creates a lock file in workdir to allow only one active migration thread
     * @return current size of urldb index
     */
    @SuppressWarnings("deprecation")
    public static int migrateUrldbtoSolr(final Switchboard sb) {
        int ret = 0;
        final File f;
        final Fulltext ft = sb.index.fulltext();

        if (ft.getURLDb() != null) {
            ret = ft.getURLDb().size();
            f = new File(sb.workPath, "migrateUrldbtoSolr.lck");
            f.deleteOnExit();
            if (f.exists()) {
                return ret;
            }
            try {
                f.createNewFile();                    
            } catch (IOException ex) {
                Log.logInfo("migrateUrldbtoSolr","could not create lock file");
            }

            final Thread t = new Thread() {
                boolean go = true;
                final Index urldb = ft.getURLDb();

                public void run() {
                    try {
                        Thread.currentThread().setName("migration.migrateUrldbtoSolr");

                        int i = urldb.size();
                        while (go && i > 0) {

                            List<Row.Entry> chunk = urldb.random(1000);
                            if ((chunk == null) || (chunk.size() == 0)) {
                                go = false;
                                break;
                            }
                            Iterator<Row.Entry> chunkit = chunk.iterator();

                            while (go && chunkit.hasNext()) {
                                try { // to catch any data errors 
                                    URIMetadataRow row = new URIMetadataRow(chunkit.next(), null);
                                    ft.putMetadata(row); // this deletes old urldb-entry first and inserts into Solr
                                    i--;
                                    if (Switchboard.getSwitchboard().shallTerminate()) {
                                        go = false;
                                    }
                                } catch (Exception e) {
                                    Log.logInfo("migrateUrldbtoSolr", "some error while adding old data to new index, continue with next entry");
                                }
                            }
                            Log.logInfo("migrateUrldbtoSolr", Integer.toString(i) + " entries left (convert next chunk of 1000 entries)");
                        }
                        ft.commit(true);

                    } catch (IOException ex) {
                        Log.logInfo("migrateUrldbtoSolr", "error reading old urldb index");
                    } finally {
                        if (f.exists()) {
                            f.delete(); // delete lock file
                        }
                    }
                }

                public void exit() {
                    go = false;
                }
            };
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
        return ret;
    }
    
    /**
     * Reindex embedded solr index
     *   - all documents with inactive fields (according to current schema)
     *   - all documents with obsolete fields
     * A worker thread is initialized with fieldnames or a solr query which selects the documents for reindexing
     * implemented via deployed BusyThread which is called repeatedly by system
     * reindexes a fixed chunk of documents per cycle (allowing to easy interrupt process after completion of a chunck)
     * and monitoring in default process monitor (PerformanceQueues_p.html)
     */
    public static int reindexToschema (final Switchboard sb) {

        BusyThread bt = sb.getThread("reindexSolr");
        // a reindex job is already running 
        if (bt != null) {
            return bt.getJobCount();
        }
        
        boolean lukeCheckok = false;
        Set<String> omitFields = new HashSet<String>(3);
        omitFields.add(CollectionSchema.author_sxt.getSolrFieldName()); // special fields to exclude from disabled check
        omitFields.add(CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName());
        CollectionConfiguration colcfg = Switchboard.getSwitchboard().index.fulltext().getDefaultConfiguration();
        ReindexSolrBusyThread reidx = new ReindexSolrBusyThread(null); // ("*:*" would reindex all);
        
        try { // get all fields contained in index
            LukeRequest lukeRequest = new LukeRequest();
            lukeRequest.setNumTerms(1);
            LukeResponse lukeResponse = lukeRequest.process(Switchboard.getSwitchboard().index.fulltext().getDefaultEmbeddedConnector().getServer());            

            for (LukeResponse.FieldInfo solrfield : lukeResponse.getFieldInfo().values()) {
                if (!colcfg.contains(solrfield.getName()) && !omitFields.contains(solrfield.getName())) { // add found fields not in config for reindexing
                    reidx.addSelectFieldname(solrfield.getName());
                }
            }
            lukeCheckok = true;
        } catch (SolrServerException ex) {
            Log.logException(ex);
        } catch (IOException ex) {
            Log.logException(ex);
        }
  
        if (!lukeCheckok) {  // if luke failed alternatively use config and manual list                
            // add all disabled fields
            Iterator<Entry> itcol = colcfg.entryIterator();
            while (itcol.hasNext()) { // check for disabled fields in config
                Entry etr = itcol.next();
                if (!etr.enabled() && !omitFields.contains(etr.key())) {
                    reidx.addSelectFieldname(etr.key());
                }
            }

            // add obsolete fields (not longer part of main index)
            reidx.addSelectFieldname("author_s");
            reidx.addSelectFieldname("css_tag_txt");
            reidx.addSelectFieldname("css_url_txt");
            reidx.addSelectFieldname("scripts_txt");
            reidx.addSelectFieldname("images_tag_txt");
            reidx.addSelectFieldname("images_urlstub_txt");
            reidx.addSelectFieldname("canonical_t");
            reidx.addSelectFieldname("frames_txt");
            reidx.addSelectFieldname("iframes_txt");

            reidx.addSelectFieldname("inboundlinks_tag_txt");
            reidx.addSelectFieldname("inboundlinks_relflags_val");
            reidx.addSelectFieldname("inboundlinks_name_txt");
            reidx.addSelectFieldname("inboundlinks_rel_sxt");
            reidx.addSelectFieldname("inboundlinks_text_txt");
            reidx.addSelectFieldname("inboundlinks_text_chars_val");
            reidx.addSelectFieldname("inboundlinks_text_words_val");
            reidx.addSelectFieldname("inboundlinks_alttag_txt");

            reidx.addSelectFieldname("outboundlinks_tag_txt");
            reidx.addSelectFieldname("outboundlinks_relflags_val");
            reidx.addSelectFieldname("outboundlinks_name_txt");
            reidx.addSelectFieldname("outboundlinks_rel_sxt");
            reidx.addSelectFieldname("outboundlinks_text_txt");
            reidx.addSelectFieldname("outboundlinks_text_chars_val");
            reidx.addSelectFieldname("outboundlinks_text_words_val");
            reidx.addSelectFieldname("outboundlinks_alttag_txt");
        }
        sb.deployThread("reindexSolr", "Reindex Solr", "reindex documents with obsolete fields in embedded Solr index", "/IndexReIndexMonitor_p.html",reidx , 0);
        return 0;
    }
}
