package org.allureIQ.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure Java search engine that connects directly to MongoDB Atlas.
 * - No Spring
 * - No HTTP server
 * - Keeps same logic (collections, sanitize, convert _id, fuzzy search)
 *
 * Usage (examples in README below):
 *   java -jar AllureIQ.jar search "login error"
 *   java -jar AllureIQ.jar export-json ./embedded_data.json
 */
public class AiUnifiedSearchEngineAtlas implements Closeable {

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final String openRouterApiKey;
    private final Gson gson;

    private static final List<String> COLLECTIONS = List.of(
            "LuffyFramework",
            "ai_context_logs",
            "ai_executions",
            "ai_hints",
            "ai_reports"
    );

    // ---------- constructor ----------
    public AiUnifiedSearchEngineAtlas(String mongoUri, String dbName, String apiKey) {
        this.mongoClient = MongoClients.create(mongoUri);
        this.database = this.mongoClient.getDatabase(dbName);
        this.openRouterApiKey = apiKey;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // ---------- main public API: same signature & behavior ----------
    public Map<String, Object> search(String query) {
        if (query == null || (query = query.trim()).isEmpty()) {
            return Map.of("error", "Search query cannot be empty");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("timestamp", new Date().toString());

        Map<String, List<Document>> resultsMap = new LinkedHashMap<>();
        StringBuilder summaryBuilder = new StringBuilder();

        // Direct collection name case
        if (COLLECTIONS.contains(query)) {
            List<Document> allDocs = findAllDocs(query);
            String finalQuery = query;
            allDocs.forEach(doc -> {
                convertId(doc, finalQuery);
                sanitize(doc);
            });
            resultsMap.put(query, allDocs);

            summaryBuilder.append("Summaries for collection ").append(query)
                    .append(" having ").append(allDocs.size()).append(" records.");

            response.put("results", resultsMap);
            response.put("ai_summary", callOpenRouter(summaryBuilder.toString()));
            return response;
        }

        // Fuzzy search across each collection (limit 60)
        for (String collectionName : COLLECTIONS) {
            List<Document> results = searchCollection(collectionName, query);
            resultsMap.put(collectionName, results);

            if (!results.isEmpty()) {
                summaryBuilder.append("Collection: ").append(collectionName).append("\n");
                results.stream().limit(3)
                        .forEach(doc -> summaryBuilder.append(doc.toJson()).append("\n\n"));
            }
        }

        response.put("results", resultsMap);
        response.put("ai_summary", callOpenRouter(summaryBuilder.toString()));
        return response;
    }

    // ---------- helper: search one collection ----------
    private List<Document> searchCollection(String collectionName, String query) {
        List<Document> matches = new ArrayList<>();
        Pattern pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        try {
            MongoCollection<Document> coll = database.getCollection(collectionName);
            FindIterable<Document> docs = coll.find().limit(60);
            for (Document doc : docs) {
                if (containsMatch(doc, pattern)) {
                    convertId(doc, collectionName);
                    sanitize(doc);
                    matches.add(doc);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error searching collection " + collectionName + ": " + e.getMessage());
        }
        return matches;
    }

    // ---------- helper: find all docs in a collection ----------
    private List<Document> findAllDocs(String collectionName) {
        try {
            MongoCollection<Document> coll = database.getCollection(collectionName);
            List<Document> list = new ArrayList<>();
            coll.find().into(list);
            return list;
        } catch (Exception e) {
            System.err.println("❌ Failed to read collection " + collectionName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ---------- same recursive fuzzy matcher ----------
    private boolean containsMatch(Object value, Pattern pattern) {
        if (value == null) return false;

        if (value instanceof String s) {
            return pattern.matcher(s).find();
        } else if (value instanceof Document doc) {
            for (Object v : doc.values()) if (containsMatch(v, pattern)) return true;
        } else if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) if (containsMatch(v, pattern)) return true;
        } else if (value instanceof List<?> list) {
            for (Object item : list) if (containsMatch(item, pattern)) return true;
        }
        return false;
    }

    // ---------- convert _id to hex and attach collection name ----------
    private void convertId(Document doc, String collection) {
        Object id = doc.get("_id");
        if (id instanceof ObjectId) {
            doc.put("_id", ((ObjectId) id).toHexString());
        } else if (id != null) {
            doc.put("_id", id.toString());
        }
        doc.put("_collection", collection);
    }

    // ---------- sanitize redirect/localhost-like fields ----------
    private void sanitize(Document doc) {
        for (String key : new ArrayList<>(doc.keySet())) {
            Object value = doc.get(key);
            if (value instanceof String str) {
                if (str.matches("(?i).*localhost.*|.*http://.*|.*https://.*|.*login.*|.*redirect.*")) {
                    doc.put(key, "[hidden]");
                }
            } else if (value instanceof Document nested) {
                sanitize(nested);
            } else if (value instanceof List<?> list) {
                for (Object item : list) if (item instanceof Document d) sanitize(d);
            }
        }
    }

    // ---------- call OpenRouter (same approach) ----------
    private String callOpenRouter(String text) {
        try {
            if (openRouterApiKey == null || openRouterApiKey.isEmpty()) {
                return "AI summary skipped: no API key";
            }

            URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + openRouterApiKey);
            conn.setDoOutput(true);

            JSONObject payload = new JSONObject();
            payload.put("model", "gpt-4o-mini");
            payload.put("messages", List.of(
                    new JSONObject().put("role", "system").put("content", "You are an expert AI that summarizes MongoDB testing and automation data."),
                    new JSONObject().put("role", "user").put("content", "Analyze this dataset:\n" + text)
            ));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            try (InputStream in = conn.getInputStream(); var rdr = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = rdr.readLine()) != null) sb.append(line);
                JsonElement root = JsonParser.parseString(sb.toString());
                JsonElement content = root.getAsJsonObject()
                        .getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content");
                return content != null ? content.getAsString() : "AI returned empty";
            }

        } catch (Exception e) {
            return "AI failed: " + e.getMessage();
        }
    }

    // ---------- export ALL collections into a JSON structure usable for embedding ----------
    public String exportAllCollectionsAsJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (String col : COLLECTIONS) {
            List<Document> list = findAllDocs(col);
            // ensure convert/sanitize for exported data
            list.forEach(d -> { convertId(d, col); sanitize(d); });
            // convert each Document to JSON element
            List<JsonElement> elems = list.stream()
                    .map(d -> JsonParser.parseString(d.toJson()))
                    .collect(Collectors.toList());
            root.put(col, elems);
        }
        return gson.toJson(root);
    }

    // ---------- helper: run a quick search and return JSON string ----------
    public String searchAsJson(String q) {
        Map<String, Object> map = search(q);
        // convert Documents to JSON-friendly objects
        Map<String, List<JsonElement>> out = new LinkedHashMap<>();
        Object maybeResults = map.get("results");
        if (maybeResults instanceof Map<?, ?> resultsMap) {
            for (var entry : resultsMap.entrySet()) {
                String col = (String) entry.getKey();
                List<Document> docs = (List<Document>) entry.getValue();
                List<JsonElement> elems = docs.stream().map(d -> JsonParser.parseString(d.toJson()))
                        .collect(Collectors.toList());
                out.put(col, elems);
            }
        }
        Map<String, Object> resultWrapper = new LinkedHashMap<>();
        resultWrapper.put("query", map.get("query"));
        resultWrapper.put("timestamp", map.get("timestamp"));
        resultWrapper.put("results", out);
        resultWrapper.put("ai_summary", map.get("ai_summary"));
        return gson.toJson(resultWrapper);
    }

    // ---------- close resources ----------
    @Override
    public void close() {
        try {
            if (mongoClient != null) mongoClient.close();
        } catch (Exception ignored) {}
    }

    // ---------- CLI entrypoint ----------
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage:");
            System.out.println("  java -jar AllureIQ.jar search \"query text\"");
            System.out.println("  java -jar AllureIQ.jar export-json ./embedded_data.json");
            System.out.println("Environment variables: MONGO_URI, MONGO_DB, OPENROUTER_API_KEY (optional)");
            return;
        }

        String mongoUri = System.getenv().getOrDefault("MONGO_URI", "");
        String dbName = System.getenv().getOrDefault("MONGO_DB", "");
        String apiKey = System.getenv().getOrDefault("OPENROUTER_API_KEY", "");

        if (mongoUri.isEmpty() || dbName.isEmpty()) {
            System.err.println("Set MONGO_URI and MONGO_DB environment variables (Atlas connection string and DB name).");
            return;
        }

        String cmd = args[0];

        try (AiUnifiedSearchEngineAtlas engine = new AiUnifiedSearchEngineAtlas(mongoUri, dbName, apiKey)) {
            if ("search".equalsIgnoreCase(cmd)) {
                if (args.length < 2) {
                    System.err.println("Please provide a query string.");
                    return;
                }
                String query = args[1];
                String json = engine.searchAsJson(query);
                System.out.println(json); // print to stdout so callers can capture
            } else if ("export-json".equalsIgnoreCase(cmd)) {
                String outFile = args.length >= 2 ? args[1] : "./embedded_data.json";
                String json = engine.exportAllCollectionsAsJson();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                    System.out.println("Exported embedded JSON to: " + outFile);
                }
            } else {
                System.err.println("Unknown command: " + cmd);
            }
        }
    }
}
