package nl.utwente.group10.haskell.catalog;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import nl.utwente.group10.haskell.exceptions.CatalogException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Provides a convenient interface to the XML catalog of available Haskell functions.
 */
public class HaskellCatalog {
    /** Map from category to entry for use in getCategories and getCategory. */
    private final Multimap<String, Entry> byCategory;

    /** Map from function name to entry for use in get() and friends. */
    private final Map<String, Entry> byName;

    /** The default XML data source path. */
    public static final String XML_PATH = "/catalog/functions.xml";

    /** The resource path for the XML Schema definition. */
    public static final String XSD_PATH = "/catalog/functions.xsd";

    /** Construct a HaskellCatalog using the default path. */
    public HaskellCatalog() throws CatalogException {
        this(XML_PATH);
    }

    /**
     * Construct a HaskellCatalog from an XML file at the specified path.
     *
     * @throws CatalogException when the XML file can't be found, read, or parsed.
     */
    public HaskellCatalog(String path) throws CatalogException {
        URL functions = HaskellCatalog.class.getResource(path);
        URL xmlschema = HaskellCatalog.class.getResource(XSD_PATH);

        this.byName = new HashMap<String, Entry>();
        this.byCategory = HashMultimap.create();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            SchemaFactory sFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");

            dbFactory.setIgnoringElementContentWhitespace(true);
            dbFactory.setIgnoringComments(true);
            dbFactory.setSchema(sFactory.newSchema(xmlschema));

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(functions.getPath());

            NodeList funcNodes = doc.getElementsByTagName("function");

            for (int j = 0; j < funcNodes.getLength(); j++) {
                Node func = funcNodes.item(j);
                NamedNodeMap funcAttributes = func.getAttributes();

                String funcName = funcAttributes.getNamedItem("name").getTextContent();
                String funcSig = funcAttributes.getNamedItem("signature").getTextContent();
                String funcCat = func.getParentNode().getAttributes().getNamedItem("name").getTextContent();

                Entry e = new Entry(funcName, funcCat, funcSig, func.getTextContent());

                this.byName.put(funcName, e);
                this.byCategory.put(funcCat, e);
            }

        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new CatalogException(e);
        }
    }

    /**
     * Get a single Entry by its function name.
     *
     * @throws java.util.NoSuchElementException when the function is not defined.
     */
    public Entry getEntry(String key) {
        return this.byName.get(key);
    }

    /** Get a single Entry if it exists, or return the default. */
    public Entry getOrDefault(String key, Entry defaultValue) {
        return this.byName.getOrDefault(key, defaultValue);
    }

    /** Get an Optional value with a single Entry if it exists. */
    public Optional<Entry> getMaybe(String key) {
        return Optional.ofNullable(this.getOrDefault(key, null));
    }

    /** Returns a Set of the known categories. */
    public Set<String> getCategories() {
        return this.byCategory.keySet();
    }

    /** Returns all functions in the given category, or an empty collection. */
    public Collection<Entry> getCategory(String name) {
        return this.byCategory.get(name);
    }
}