package org.wisdom.maven.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.felix.ipojo.manipulator.Pojoization;

import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * Packages the bundle using BND.
 */
public class BundlePackager {
    public static final String INSTRUCTIONS_FILE = "src/main/osgi/osgi.bnd";
    private static final String DEPENDENCIES = "target/osgi/dependencies.json";
    
    private BundlePackager(){
    	//Hide default constructor
    }

    public static void bundle(File basedir, File output) throws Exception {
        Properties properties = new Properties();
        // Loads the properties inherited from Maven.
        readMavenProperties(basedir, properties);
        // Loads the properties from the BND file.
        boolean provided = readInstructionsFromBndFiles(properties, basedir);
        if (!provided) {
            // No bnd files, set default valued
            populatePropertiesWithDefaults(basedir, properties);
        }

        // Instruction loaded, start the build sequence.
        Builder builder = getOSGiBuilder(basedir, properties, computeClassPath(basedir));
        builder.build();

        reportErrors("BND ~> ", builder.getWarnings(), builder.getErrors());
        File bnd = File.createTempFile("bnd-", ".jar");
        File ipojo = File.createTempFile("ipojo-", ".jar");
        builder.getJar().write(bnd);

        Pojoization pojoization = new Pojoization();
        pojoization.pojoization(bnd, ipojo, new File(basedir, "src/main/resources"));
        reportErrors("iPOJO ~> ", pojoization.getWarnings(), pojoization.getErrors());

        Files.move(Paths.get(ipojo.getPath()), Paths.get(output.getPath()), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * We should have generate a target/osgi/osgi.properties file will all the metadata we inherit from Maven.
     *
     * @param baseDir    the project directory
     * @param properties the current set of properties in which the read metadata are written
     */
    private static void readMavenProperties(File baseDir, Properties properties) throws IOException {
        File osgi = new File(baseDir, "target/osgi/osgi.properties");
        if (osgi.isFile()) {
            FileInputStream fis = null;
            try {
                Properties read = new Properties();
                fis = new FileInputStream(osgi);
                read.load(fis);
                putAll(properties, read);
            } finally {
                IOUtils.closeQuietly(fis);
            }
        }
    }

    private static void putAll(Properties props1, Properties props2) {
        for (String name : props2.stringPropertyNames()) {
            props1.put(name, props2.getProperty(name));
        }
    }

    private static void populatePropertiesWithDefaults(File basedir, Properties properties) throws IOException {
        List<String> privates = new ArrayList<>();
        List<String> exports = new ArrayList<>();

        File classes = new File(basedir, "target/classes");

        Set<String> packages = new LinkedHashSet<>();
        if (classes.isDirectory()) {
            Jar jar = new Jar(".", classes);
            packages.addAll(jar.getPackages());
            jar.close();
        }

        for (String s : packages) {
            if (shouldBeExported(s)) {
                exports.add(s);
            } else {
                if (!s.isEmpty()) {
                    privates.add(s + ";-split-package:=merge-first");
                }
            }
        }

        properties.put(Constants.PRIVATE_PACKAGE, toClause(privates));
        if (!exports.isEmpty()) {
            properties.put(Constants.EXPORT_PACKAGE, toClause(privates));
        }
    }

    public static boolean shouldBeExported(String packageName) {
    	boolean service = packageName.endsWith(".service");
    	service = service
    			|| packageName.contains(".service.")
                || packageName.endsWith(".services")
                || packageName.contains(".services.");
    	boolean api = packageName.endsWith(".api");
    	api = api 
    			|| packageName.contains(".api.")
                || packageName.endsWith(".apis")
                || packageName.contains(".apis.");
        return !packageName.isEmpty() && (service || api);
    }

    private static String toClause(List<String> packages) {
        StringBuilder builder = new StringBuilder();
        for (String p : packages) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(p);
        }
        return builder.toString();
    }

    private static Jar[] computeClassPath(File basedir) throws IOException {
        List<Jar> list = new ArrayList<>();
        File classes = new File(basedir, "target/classes");

        if (classes.isDirectory()) {
            list.add(new Jar(".", classes));
        }

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode array = mapper.readValue(new File(basedir, DEPENDENCIES), ArrayNode.class);
        Iterator<JsonNode> items = array.elements();
        while (items.hasNext()) {
            ObjectNode node = (ObjectNode) items.next();
            String scope = node.get("scope").asText();
            if (!"test".equalsIgnoreCase(scope)) {
                File file = new File(node.get("file").asText());
                Jar jar = new Jar(node.get("artifactId").asText(), file);
                list.add(jar);
            }
        }
        Jar[] cp = new Jar[list.size()];
        list.toArray(cp);

        return cp;

    }

    private static Builder getOSGiBuilder(File basedir, Properties properties,
                                          Jar[] classpath) {
        Builder builder = new Builder();
        synchronized (BundlePackager.class) {
            builder.setBase(basedir);
        }
        builder.setProperties(sanitize(properties));
        if (classpath != null) {
            builder.setClasspath(classpath);
        }
        return builder;
    }

    private static boolean readInstructionsFromBndFiles(Properties properties, File basedir) throws IOException {
        Properties props = new Properties();
        File instructionFile = new File(basedir, INSTRUCTIONS_FILE);
        if (instructionFile.isFile()) {
            InputStream is = null;
            try {
                is = new FileInputStream(instructionFile);
                props.load(is);
            } finally {
                IOUtils.closeQuietly(is);
            }
        } else {
            return false;
        }

        // Insert in the given properties to the list of properties.
        @SuppressWarnings("unchecked") Enumeration<String> names = (Enumeration<String>) props.propertyNames();
        while (names.hasMoreElements()) {
            String key = names.nextElement();
            properties.put(key, props.getProperty(key));
        }

        return true;
    }

    private static Properties sanitize(Properties properties) {
        // convert any non-String keys/values to Strings
        Properties sanitizedEntries = new Properties();
        for (Iterator<?> itr = properties.entrySet().iterator(); itr.hasNext(); ) {
            Map.Entry entry = (Map.Entry) itr.next();
            if (!(entry.getKey() instanceof String)) {
                String key = sanitize(entry.getKey());
                if (!properties.containsKey(key)) {
                    sanitizedEntries.setProperty(key, sanitize(entry.getValue()));
                }
                itr.remove();
            } else if (!(entry.getValue() instanceof String)) {
                entry.setValue(sanitize(entry.getValue()));
            }
        }
        properties.putAll(sanitizedEntries);
        return properties;
    }

    private static String sanitize(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Iterable) {
            String delim = "";
            StringBuilder buf = new StringBuilder();
            for (Object i : (Iterable<?>) value) {
                buf.append(delim).append(i);
                delim = ", ";
            }
            return buf.toString();
        } else if (value.getClass().isArray()) {
            String delim = "";
            StringBuilder buf = new StringBuilder();
            for (int i = 0, len = Array.getLength(value); i < len; i++) {
                buf.append(delim).append(Array.get(value, i));
                delim = ", ";
            }
            return buf.toString();
        } else {
            return String.valueOf(value);
        }
    }

    private static boolean reportErrors(String prefix, List<String> warnings, List<String> errors) {
        for (String msg : warnings) {
            System.err.println(prefix + " : " + msg);
        }

        boolean hasErrors = false;
        String fileNotFound = "Input file does not exist: ";
        for (String msg : errors) {
            if (msg.startsWith(fileNotFound) && msg.endsWith("~")) {
                // treat as warning; this error happens when you have duplicate entries in Include-Resource
                String duplicate = Processor.removeDuplicateMarker(msg.substring(fileNotFound.length()));
                System.err.println(prefix + " Duplicate path '" + duplicate + "' in Include-Resource");
            } else {
                System.err.println(prefix + " : " + msg);
                hasErrors = true;
            }
        }
        return hasErrors;
    }
}
