package com.omernaci.miniflow.node.http;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.node.NodeExecutor;
import com.omernaci.miniflow.node.NodeResult;
import com.omernaci.miniflow.node.ParamField;
import com.omernaci.miniflow.node.support.HttpSupport;
import com.omernaci.miniflow.node.support.Params;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RssNode implements NodeExecutor {

    private final RestClient client;

    public RssNode(RestClient.Builder builder) {
        this.client = HttpSupport.client(builder, Duration.ofSeconds(30));
    }

    @Override
    public String type() {
        return "rss";
    }

    @Override
    public String label() {
        return "RSS / Atom";
    }

    @Override
    public Map<String, Object> defaultParams() {
        return Map.of("url", "https://github.com/spring-projects/spring-boot/releases.atom", "limit", 5);
    }

    @Override
    public List<ParamField> fields() {
        return List.of(
                ParamField.text("url").mandatory().placeholder("https://spring.io/blog.atom"),
                ParamField.number("limit"));
    }

    @Override
    public NodeResult execute(Map<String, Object> params, Map<String, Object> input, ExecutionContext ctx) {
        String url = Params.required(params, "url");
        int limit = Math.max(1, Params.integer(params, "limit", 10));

        HttpSupport.Response r = HttpSupport.get(client, url, h -> {
            h.set("User-Agent", "MiniFlow");
            h.set("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5");
        });
        if (!r.ok()) {
            throw new MiniFlowException("error.rss.http", r.status());
        }
        return NodeResult.of(parse(r.body(), limit));
    }

    static Map<String, Object> parse(String xml, int limit) {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(true);
            doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml.trim())));
        } catch (Exception e) {
            throw new MiniFlowException(e, "error.rss.parse", e.getMessage());
        }

        Element root = doc.getDocumentElement();
        boolean atom = "feed".equals(root.getLocalName());
        List<Map<String, Object>> items = new ArrayList<>();
        String feedTitle;

        if (atom) {
            feedTitle = childText(root, "title");
            NodeList entries = root.getElementsByTagNameNS("*", "entry");
            for (int i = 0; i < entries.getLength() && items.size() < limit; i++) {
                Element e = (Element) entries.item(i);
                String published = childText(e, "published");
                items.add(item(childText(e, "title"), atomLink(e),
                        published != null ? published : childText(e, "updated"),
                        childText(e, "summary") != null ? childText(e, "summary") : childText(e, "content"),
                        childText(e, "id")));
            }
        } else {
            Element channel = firstChild(root, "channel");
            feedTitle = channel != null ? childText(channel, "title") : childText(root, "title");
            NodeList list = root.getElementsByTagNameNS("*", "item");
            for (int i = 0; i < list.getLength() && items.size() < limit; i++) {
                Element e = (Element) list.item(i);
                String link = childText(e, "link");
                String guid = childText(e, "guid");
                String date = childText(e, "pubDate") != null ? childText(e, "pubDate") : childText(e, "date");
                items.add(item(childText(e, "title"), link, date, childText(e, "description"),
                        guid != null ? guid : link));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", feedTitle);
        out.put("count", items.size());
        out.put("items", items);
        out.put("latest", items.isEmpty() ? null : items.get(0));
        return out;
    }

    private static Map<String, Object> item(String title, String link, String published, String summary, String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("link", link);
        m.put("published", published);
        m.put("summary", plain(summary));
        m.put("id", id);
        return m;
    }

    private static String atomLink(Element entry) {
        String fallback = null;
        NodeList children = entry.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && "link".equals(el.getLocalName())) {
                String rel = el.getAttribute("rel");
                if (rel.isEmpty() || "alternate".equals(rel)) {
                    return el.getAttribute("href");
                }
                if (fallback == null) {
                    fallback = el.getAttribute("href");
                }
            }
        }
        return fallback;
    }

    private static Element firstChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private static String childText(Element parent, String localName) {
        Element el = firstChild(parent, localName);
        if (el == null) {
            return null;
        }
        String t = el.getTextContent();
        return t == null ? null : t.trim();
    }

    private static String plain(String html) {
        if (html == null) {
            return null;
        }
        String text = html.replaceAll("(?s)<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
        return text.length() > 500 ? text.substring(0, 497) + "..." : text;
    }
}
