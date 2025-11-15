package org.allureIQ.models;

import com.mongodb.client.*;
import org.bson.Document;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.util.*;

public class MongoConnector {

    private static final Dotenv dotenv = loadEnv();
    private static final String uri = dotenv.get("MONGO_URL");
    private static final String dbName = "LuffyFramework";
    private static MongoClient mongoClient = null;

    // 🔹 Automatically detect project & subproject
    private static final String projectName = System.getProperty("project.name",
            dotenv.get("PROJECT_NAME") != null
                    ? dotenv.get("PROJECT_NAME")
                    : new File(System.getProperty("user.dir")).getName());

    private static final String subProjectName = System.getProperty("subproject.name",
            dotenv.get("SUBPROJECT_NAME") != null
                    ? dotenv.get("SUBPROJECT_NAME")
                    : "DefaultSubproject");

    private static Dotenv loadEnv() {
        String rootPath = new File(System.getProperty("user.dir")).getAbsolutePath();
        String envPath = rootPath + File.separator + "ENV";
        System.out.println("🌍 Looking for .env in: " + envPath);

        return Dotenv.configure()
                .directory(envPath)
                .ignoreIfMissing()
                .load();
    }

    public static MongoDatabase connect() {
        if (mongoClient == null) {
            if (uri == null) {
                throw new RuntimeException("❌ Missing MONGO_URL in .env file!");
            }
            mongoClient = MongoClients.create(uri);
            System.out.println("🟢 MongoDB connected successfully.");
        }
        return mongoClient.getDatabase(dbName);
    }

    // ✅ Save AI summary report
    public static void saveReport(String testName, String aiSummary, String records) {
        try {
            MongoDatabase db = connect();
            MongoCollection<Document> collection = db.getCollection("ai_reports");

            Document doc = new Document("projectName", projectName)
                    .append("subproject", subProjectName)
                    .append("testName", testName)
                    .append("aiSummary", aiSummary)
                    .append("records", records)
                    .append("timestamp", System.currentTimeMillis());

            collection.insertOne(doc);
            System.out.println("✅ [" + projectName + "/" + subProjectName + "] AI Report saved successfully.");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save report to MongoDB: " + e.getMessage());
        }
    }

    // ✅ Save API execution with project + subproject
    public static void saveExecution(String method, String endpoint, String payload, String response, int status) {
        try {
            MongoDatabase db = connect();
            MongoCollection<Document> collection = db.getCollection("ai_executions");

            Document doc = new Document("projectName", projectName)
                    .append("subproject", subProjectName)
                    .append("method", method)
                    .append("endpoint", endpoint)
                    .append("payload", payload)
                    .append("response", response)
                    .append("status", status)
                    .append("timestamp", new Date().toString());

            collection.insertOne(doc);
            System.out.println("📩 [" + projectName + "/" + subProjectName + "] Saved execution → " + method + " " + endpoint);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save execution: " + e.getMessage());
        }
        String sessionId = UUID.randomUUID().toString();
        updateSessionHierarchy(sessionId);

    }
    // ✅ Create or update session hierarchy for project/subproject
    public static void updateSessionHierarchy(String sessionId) {
        try {
            MongoDatabase db = connect();
            MongoCollection<Document> sessionCollection = db.getCollection("ai_sessions");

            // Check if project already exists
            Document existingProject = sessionCollection.find(new Document("projectName", projectName)).first();

            if (existingProject == null) {
                // If project not found, create new entry
                Document newProject = new Document("projectName", projectName)
                        .append("createdAt", new Date())
                        .append("subprojects", new ArrayList<>(List.of(
                                new Document("name", subProjectName)
                                        .append("sessions", List.of(
                                                new Document("sessionId", sessionId)
                                                        .append("createdAt", new Date())
                                        ))
                        )));

                sessionCollection.insertOne(newProject);
                System.out.println("🆕 Created new project in ai_sessions → " + projectName + " / " + subProjectName);
            } else {
                // If project exists, check if subproject is already listed
                List<Document> subprojects = existingProject.getList("subprojects", Document.class, new ArrayList<>());
                Optional<Document> existingSub = subprojects.stream()
                        .filter(sp -> sp.getString("name").equals(subProjectName))
                        .findFirst();

                if (existingSub.isPresent()) {
                    // Add session to existing subproject
                    List<Document> sessions = existingSub.get().getList("sessions", Document.class, new ArrayList<>());
                    sessions.add(new Document("sessionId", sessionId).append("createdAt", new Date()));
                    existingSub.get().put("sessions", sessions);
                } else {
                    // Add new subproject under the same project
                    subprojects.add(new Document("name", subProjectName)
                            .append("sessions", List.of(
                                    new Document("sessionId", sessionId)
                                            .append("createdAt", new Date())
                            )));
                }

                // Update back in DB
                existingProject.put("subprojects", subprojects);
                sessionCollection.replaceOne(new Document("projectName", projectName), existingProject);
                System.out.println("📁 Updated ai_sessions for → " + projectName + " / " + subProjectName);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to update session hierarchy: " + e.getMessage());
        }
    }


    public static List<Document> getLastReports(int limit) {
        List<Document> reports = new ArrayList<>();
        try {
            MongoDatabase db = connect();
            MongoCollection<Document> collection = db.getCollection("ai_reports");

            FindIterable<Document> docs = collection.find(new Document("projectName", projectName)
                            .append("subproject", subProjectName))
                    .sort(new Document("timestamp", -1))
                    .limit(limit);

            for (Document d : docs) {
                reports.add(d);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch previous reports: " + e.getMessage());
        }
        return reports;
    }

    public static Document getLastReport(String testName) {
        try {
            MongoDatabase db = connect();
            MongoCollection<Document> collection = db.getCollection("ai_reports");

            return collection.find(new Document("testName", testName)
                            .append("projectName", projectName)
                            .append("subproject", subProjectName))
                    .sort(new Document("timestamp", -1))
                    .first();
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch last report for " + testName + ": " + e.getMessage());
            return null;
        }
    }

    public static Document[] getLastTwoReports() {
        List<Document> reports = getLastReports(2);
        Document last = reports.size() > 0 ? reports.get(0) : null;
        Document secondLast = reports.size() > 1 ? reports.get(1) : null;
        return new Document[]{last, secondLast};
    }

    public static List<Document> getRecentEndpoints(int limit) {
        List<Document> executions = new ArrayList<>();
        try {
            MongoDatabase db = connect();
            MongoCollection<Document> collection = db.getCollection("ai_executions");

            FindIterable<Document> docs = collection.find(new Document("projectName", projectName)
                            .append("subproject", subProjectName))
                    .sort(new Document("timestamp", -1))
                    .limit(limit);

            for (Document d : docs) {
                executions.add(d);
            }
            System.out.println("📦 [" + projectName + "/" + subProjectName + "] Retrieved " + executions.size() + " recent executions.");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch recent executions: " + e.getMessage());
        }
        return executions;
    }

    public static Document[] getLastTwoExecutions() {
        try {
            MongoDatabase db = connect();
            MongoCollection<Document> col = db.getCollection("ai_executions");

            FindIterable<Document> docs = col.find(new Document("projectName", projectName)
                            .append("subproject", subProjectName))
                    .sort(new Document("timestamp", -1))
                    .limit(2);

            Document latest = null;
            Document previous = null;
            int i = 0;
            for (Document d : docs) {
                if (i == 0) latest = d;
                else if (i == 1) previous = d;
                i++;
            }

            System.out.println("🧠 [" + projectName + "/" + subProjectName + "] Retrieved last two executions for comparison.");
            return new Document[]{latest, previous};
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch last two executions: " + e.getMessage());
            return new Document[]{null, null};
        }
    }
}
