package org.allureIQ.models;

import com.mongodb.client.*;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🤖 AI Project Session Comparator
 * ----------------------------------------------
 * - Compares the last two sessions per subproject
 * - Works only for the active project/subproject
 * - Generates HTML summary with success rate and change delta
 * - Uses MongoConnector’s detected project/subproject
 */
public class ReportComparator {

    public static String compareLatestExecutions() {
        StringBuilder report = new StringBuilder();

        try {
            MongoDatabase db = MongoConnector.connect();
            MongoCollection<Document> col = db.getCollection("ai_executions");

            // 🔹 Get project and subproject names
            String projectName = getActiveField("projectName");
            String subProjectName = getActiveField("subProjectName");

            // 🔹 Fetch only this project/subproject
            FindIterable<Document> projectDocs = col.find(new Document("project", projectName)
                    .append("subproject", subProjectName));

            if (!projectDocs.iterator().hasNext()) {
                return "⚠️ No data found for project: " + projectName + " / " + subProjectName;
            }

            List<Document> sessions = new ArrayList<>();
            for (Document doc : projectDocs) {
                List<Document> sList = (List<Document>) doc.get("sessions");
                if (sList != null) sessions.addAll(sList);
            }

            if (sessions.isEmpty()) {
                return "⚠️ No sessions available for " + projectName + " / " + subProjectName;
            }

            // 🔹 Sort sessions by createdAt
            sessions.sort((a, b) -> b.getDate("createdAt").compareTo(a.getDate("createdAt")));

            if (sessions.size() < 2) {
                return "⚠️ Only one session found — no comparison available for " + projectName + "/" + subProjectName;
            }

            Document latestSession = sessions.get(0);
            Document prevSession = sessions.get(1);

            List<Document> latestEndpoints = (List<Document>) latestSession.get("endpoints");
            List<Document> prevEndpoints = (List<Document>) prevSession.get("endpoints");

            // 🔹 Calculate success rates
            double latestRate = calcSuccessRate(latestEndpoints);
            double prevRate = calcSuccessRate(prevEndpoints);
            double delta = latestRate - prevRate;

            long prevFails = countFailures(prevEndpoints);
            long latestFails = countFailures(latestEndpoints);

            report.append("<div style='font-family:Segoe UI, sans-serif;padding:20px;'>");
            report.append("<h3>📊 AI Execution Comparison — ").append(projectName)
                    .append(" / ").append(subProjectName).append("</h3>");
            report.append("<p>Comparing last two sessions:</p>");

            report.append(String.format(
                    "🕒 <b>Previous Session:</b> %s<br>🕒 <b>Latest Session:</b> %s<br><br>",
                    prevSession.getDate("createdAt"), latestSession.getDate("createdAt")
            ));

            report.append(String.format(
                    "📈 Success Rate: %.2f%% → %.2f%% (Δ %.2f%%)<br>❌ Fails: %d → %d<br><br>",
                    prevRate, latestRate, delta, prevFails, latestFails
            ));

            String summary = generateSummary(subProjectName, prevRate, latestRate, delta,
                    prevFails, latestFails,
                    prevSession.getDate("createdAt").toString(),
                    latestSession.getDate("createdAt").toString());

            report.append(summary);

            // 🔹 Compare endpoints
            compareEndpoints(prevEndpoints, latestEndpoints, report);

            report.append(generateErrorSummary(prevEndpoints, latestEndpoints));
            report.append("</div>");

        } catch (Exception e) {
            e.printStackTrace();
            report.append("❌ Error generating comparison: ").append(e.getMessage());
        }

        return report.toString();
    }

    // 🔸 Helper to get field from MongoConnector
    private static String getActiveField(String name) {
        try {
            java.lang.reflect.Field field = MongoConnector.class.getDeclaredField(name);
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    // 🔹 Calculate success percentage
    private static double calcSuccessRate(List<Document> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) return 0.0;
        long successCount = endpoints.stream()
                .filter(d -> {
                    int status = d.getInteger("status", 0);
                    return status >= 200 && status < 300;
                })
                .count();
        return (successCount * 100.0 / endpoints.size());
    }

    private static long countFailures(List<Document> endpoints) {
        if (endpoints == null) return 0;
        return endpoints.stream().filter(d -> d.getInteger("status", 0) >= 400).count();
    }

    // 🔹 Compare endpoints
    private static void compareEndpoints(List<Document> prev, List<Document> latest, StringBuilder report) {
        Set<String> prevSet = prev.stream()
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());

        Set<String> currSet = latest.stream()
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());

        Set<String> added = new HashSet<>(currSet);
        added.removeAll(prevSet);

        Set<String> removed = new HashSet<>(prevSet);
        removed.removeAll(currSet);

        report.append("<hr>");
        if (!added.isEmpty()) report.append("➕ <b>Added:</b> ").append(added).append("<br>");
        if (!removed.isEmpty()) report.append("➖ <b>Removed:</b> ").append(removed).append("<br>");

        Set<String> newFails = findNewFailures(prev, latest);
        if (!newFails.isEmpty()) report.append("❌ <b>New Failures:</b> ").append(newFails).append("<br>");
        else report.append("✅ No new failures detected.<br>");
        report.append("<hr>");
    }

    // 🔹 Identify new failures
    private static Set<String> findNewFailures(List<Document> prev, List<Document> latest) {
        Set<String> prevFails = getFailedEndpoints(prev);
        Set<String> currFails = getFailedEndpoints(latest);
        currFails.removeAll(prevFails);
        return currFails;
    }

    private static Set<String> getFailedEndpoints(List<Document> endpoints) {
        return endpoints.stream()
                .filter(d -> d.getInteger("status", 0) >= 400)
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());
    }

    // 🔹 Recurring failure summary
    private static String generateErrorSummary(List<Document> prev, List<Document> latest) {
        Set<String> prevFails = getFailedEndpoints(prev);
        Set<String> currFails = getFailedEndpoints(latest);
        Set<String> recurring = new HashSet<>(prevFails);
        recurring.retainAll(currFails);

        if (recurring.isEmpty())
            return "🪶 Summary: No recurring errors detected.<br>";
        else
            return "🪶 Summary: Recurring issues found → " + recurring + "<br>";
    }

    // 🔹 Paragraph summary
    private static String generateSummary(String subproject, double prevRate, double latestRate, double delta,
                                          long prevFails, long latestFails, String prevTime, String latestTime) {

        String trend = delta > 0 ? "improvement" : (delta < 0 ? "decline" : "stability");
        String color = delta > 0 ? "#2ECC71" : (delta < 0 ? "#E74C3C" : "#999");

        return String.format(
                "<p style='color:%s;'>📘 Between <b>%s</b> and <b>%s</b>, subproject <b>%s</b> showed %s.<br>" +
                        "Success changed from <b>%.2f%%</b> (%d fails) to <b>%.2f%%</b> (%d fails). Δ = %.2f%%.</p>",
                color, prevTime, latestTime, subproject, trend,
                prevRate, prevFails, latestRate, latestFails, delta
        );
    }
}
