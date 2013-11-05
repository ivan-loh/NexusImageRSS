package com.eriad;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NexusRSS extends HttpServlet {

    public static final String NEXUS_FACTORY = "https://developers.google.com/android/nexus/images";

    private long lastAccessed;
    private Map<String, RSSsss> devicesRSS;
    private Object lock;

    @Override
    public void init() throws ServletException {
        super.init();
        devicesRSS = null;
        while (devicesRSS == null) {
            devicesRSS = generate();
        }

        lastAccessed = System.currentTimeMillis();
        lock         = new Object();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        long currentAccess = System.currentTimeMillis();
        String deviceName  = req.getRequestURI().replaceFirst("/device/", "");

        /**
         * Refreshing the item
         */
        long diff = (currentAccess - lastAccessed);
        if (diff > 600000) {
            synchronized (lock) {
                diff = (currentAccess - lastAccessed);
                if (diff > 600000) {
                    Map<String, RSSsss> newDeviceRSS = generate();
                    if (newDeviceRSS != null) {
                        devicesRSS   = newDeviceRSS;
                        lastAccessed = currentAccess;
                    }
                }
            }
        }

        /**
         * Response
         */
        resp.setContentType("application/rss+xml");
        RSSsss responseRSS = devicesRSS.get(deviceName);
        resp.getWriter()
                .write(
                        responseRSS == null ? "" : responseRSS.toString()
                );
    }

    public static Map<String, RSSsss> generate() {

        Connection.Response response = null;
        Document document            = null;

        try {
            response = Jsoup
                    .connect(NEXUS_FACTORY)
                    .method(Connection.Method.GET)
                    .execute();
            document = response.parse();
        } catch (IOException e) {
            return null;
        }


        HashMap<String, RSSsss> rssMap = new HashMap<String, RSSsss>();

        Elements toc = document.getElementsByClass("toc");
        for (Element li : toc.first().children()) {

            // Not a release
            if (li.children().size() < 2)
                continue;

            /**
             * Device Header
             */
            Element title = li.getElementsByTag("a").first();
            String device = title.attr("href");
            String desc   = title.text();

            RSSsss rss    = new RSSsss(device.substring(1), NEXUS_FACTORY + device, desc);

            /**
             * Device Releases.
             */
            Elements deviceLis = li.getElementsByTag("ol").first().getElementsByTag("li");
            for (Element deviceLi : deviceLis) {

                // Get release ID
                Element release      = deviceLi.getElementsByTag("a").first();
                String releaseElemID = release.attr("href").substring(1);

                // Operate on release Element
                Element releaseElement = document.getElementById(releaseElemID);
                Element codeElem       = releaseElement.children().get(0);
                Element linkElem       = releaseElement.children().get(1).getElementsByTag("a").first();

                // Add RSS Item
                String code = codeElem.text();
                String link = linkElem.attr("href");

                rss.add(link, code);
            }

            rssMap.put(device.substring(1), rss);
        }

        return rssMap;
    }

    public static class RSSsss {

        static final String HEAD = "<rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><channel>";
        static final String TAIL = "</channel></rss>";
        final String title;
        final String link;
        final String description;
        final Map<String, String> items;

        public RSSsss(String title,
                      String link,
                      String description) {
            this.title       = title;
            this.link        = link;
            this.description = description;
            this.items       = new HashMap<String, String>();
        }

        private static String createItem(final String link, final String title) {
            return new StringBuilder("  <item>").append("\n")
                    .append("    <link>").append(link).append("</link>").append("\n")
                    .append("    <title>").append(title).append("</title>").append("\n")
                    .append("  </item>").append("\n")
                    .toString();
        }

        public String toString() {

            // Head
            StringBuilder content =
                    new StringBuilder(HEAD).append("\n")
                            .append("  <title>").append(this.title).append("</title>").append("\n")
                            .append("  <link>").append(this.link).append("</link>").append("\n")
                            .append("  <description>").append(this.description).append("</description>").append("\n");

            // Body
            Iterator<String> itemIter = items.values().iterator();
            while (itemIter.hasNext()) {
                content.append(itemIter.next());
            }

            // Tail
            return content
                    .append(TAIL)
                    .toString();
        }

        public void add(String link, String title) {
            items.put(title, createItem(link, title));
        }
    }
}
