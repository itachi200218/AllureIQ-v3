package org.allureIQ.AI;
import org.bson.types.ObjectId;

import io.qameta.allure.Allure;
import org.allureIQ.models.MongoConnector;
import org.bson.Document;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🧠 AI Summary Reporter (Per-Project Session View)
 * -------------------------------------------------
 * ✅ Shows only data of the active project (ignores old/other projects)
 * ✅ Compares latest 2 sessions per subproject
 * ✅ Unified HTML report attached to Allure
 */
public class AiSummaryReporter {
    // 🔥 ADD HERE (right under the class declaration)
    private static final List<String> COLLECTIONS = Arrays.asList(
            "ai_reports",
            "ai_executions",
            "ai_sessions"
    );
    private static final MongoConnector mongo = new MongoConnector();

    // =======================================================
//  🔍 PURE JAVA OFFLINE SEARCH (NO MONGO, NO API)
// =======================================================
    public static Map<String, List<Document>> searchOfflineJava(String keyword) {
        keyword = keyword.toLowerCase();

        Map<String, List<Document>> results = new LinkedHashMap<>();

        try {

            for (String col : COLLECTIONS) {
                try {
                    List<Document> docs = mongo.findAll(Document.class, col);

                    List<Document> matched = new ArrayList<>();

                    for (Document d : docs) {
                        String json = d.toJson().toLowerCase();
                        if (json.contains(keyword)) {
                            matched.add(d);
                        }
                    }

                    results.put(col, matched);

                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    private static final String projectName = System.getProperty("project.name",
            System.getenv("PROJECT_NAME") != null ? System.getenv("PROJECT_NAME") : null);

    public static void logSummary() {
        StringBuilder finalSummary = new StringBuilder();

        String activeProject = null;
        try {
            var db = MongoConnector.connect();
            var collection = db.getCollection("ai_executions");

            // 🧩 Determine if we are showing one project or all
            // 🧩 Determine active project dynamically
            // 🧩 Determine active project dynamically
            List<Document> projectDocs;

// 1️⃣ Try to read from env or system property first
            activeProject = projectName;

// 2️⃣ Auto-detect from local project folder if not set
            if (activeProject == null || activeProject.isEmpty()) {
                try {
                    File currentDir = new File(System.getProperty("user.dir"));
                    activeProject = currentDir.getName(); // e.g., "AllureIQ-v3-main"
                    System.out.println("📁 Auto-detected local project folder: " + activeProject);
                } catch (Exception e) {
                    System.out.println("⚠️ Failed to detect local folder, falling back to DB check.");
                }
            }

// 3️⃣ If still not detected, try to fetch the latest project name from MongoDB
            if (activeProject == null || activeProject.isEmpty()) {
                Document latestDoc = collection.find()
                        .sort(new Document("createdAt", -1))
                        .first();

                if (latestDoc != null && latestDoc.getString("project") != null) {
                    activeProject = latestDoc.getString("project");
                    System.out.println("🧠 Auto-detected latest active project from DB: " + activeProject);
                } else {
                    System.out.println("⚠️ No project detected. MongoDB empty or project field missing.");
                    activeProject = "UNKNOWN_PROJECT";
                }
            }

// 4️⃣ Get all records for this project or its subprojects
            projectDocs = collection.find(
                    new Document("$or", List.of(
                            new Document("project", activeProject),
                            new Document("projectName", activeProject),
                            new Document("subProject", activeProject),
                            new Document("project", new Document("$regex", activeProject)),
                            new Document("subProject", new Document("$regex", activeProject))
                    ))
            ).into(new ArrayList<>());

// 5️⃣ Logging
            if (projectDocs.isEmpty()) {
                System.out.println("⚠️ No sessions found for project: " + activeProject);
            } else {
                System.out.println("📊 Generating AI summary only for project: " + activeProject);
                System.out.println("📦 Total records found: " + projectDocs.size());
            }


            if (projectDocs.isEmpty()) {
                finalSummary.append("<div style='color:#D32F2F;font-weight:600;'>⚠️ No sessions found for project: ")
                        .append(activeProject).append("</div>");
            } else {
                finalSummary.append("<h2 style='color:#1565C0;'>🧠 Project Execution Summary — ")
                        .append(activeProject).append("</h2>")
                        .append("<hr style='border:1px solid #ccc;'>");

                // 🔹 Group by Project → Subproject
                Map<String, Map<String, List<Document>>> groupedByProject = projectDocs.stream()
                        .collect(Collectors.groupingBy(
                                d -> d.getString("project") != null ? d.getString("project") : "UnknownProject",
                                Collectors.groupingBy(
                                        d -> d.getString("subproject") != null ? d.getString("subproject") : "UnknownSubproject"
                                )
                        ));

                double totalWeightedSuccess = 0.0;
                long totalEndpoints = 0;
                int subprojectCount = 0;

                for (String proj : groupedByProject.keySet()) {
                    finalSummary.append("<div class='project-card'><h2>🚀 Project: ").append(proj).append("</h2>");

                    var subGroups = groupedByProject.get(proj);
                    for (String subproject : subGroups.keySet()) {
                        List<Document> docs = subGroups.get(subproject);

                        // Flatten sessions
                        List<Document> allSessions = new ArrayList<>();
                        for (Document doc : docs) {
                            List<Document> sessions = (List<Document>) doc.getOrDefault("sessions", List.of());
                            allSessions.addAll(sessions);
                        }

                        allSessions.sort(Comparator.comparing(s -> s.getString("createdAt"), Comparator.reverseOrder()));

                        finalSummary.append("<div class='subproject-card'>")
                                .append("<h3>📦 Subproject: ").append(subproject).append("</h3>");

                        if (allSessions.size() < 2) {
                            finalSummary.append("<p style='color:#E65100;'>⚠️ Only one session found — comparison unavailable.</p></div>");
                            continue;
                        }

                        List<Document> latestSessions = List.of(allSessions.get(0));
                        List<Document> prevSessions = List.of(allSessions.get(1));

                        String latestTime = allSessions.get(0).getString("createdAt");
                        String prevTime = allSessions.get(1).getString("createdAt");

                        String subReport = compareSessions(subproject, prevTime, latestTime, prevSessions, latestSessions);
                        finalSummary.append(subReport).append("</div>");

                        long latestTotal = countEndpoints(latestSessions);
                        long latestSuccess = countSuccessEndpoints(latestSessions);
                        double latestRate = latestTotal == 0 ? 0 : (latestSuccess * 100.0 / latestTotal);

                        totalWeightedSuccess += latestRate * latestTotal;
                        totalEndpoints += latestTotal;
                        subprojectCount++;
                    }
                    finalSummary.append("</div>");
                }

                double globalRate = totalEndpoints > 0 ? totalWeightedSuccess / totalEndpoints : 0.0;
                finalSummary.insert(0, String.format("""
                        <div class='global-summary'>
                            <h2>🌍 Global Summary</h2>
                            <p><b>Success Rate:</b> %.2f%%<br>
                            <b>Subprojects:</b> %d<br>
                            <b>Endpoints Processed:</b> %d<br></p>
                        </div>
                        """, globalRate, subprojectCount, totalEndpoints));
            }

        } catch (Exception e) {
            finalSummary.append("<div style='color:red;font-weight:bold;'>❌ Error generating summary: ")
                    .append(e.getMessage()).append("</div>");
            e.printStackTrace();
        }


        // STEP 0: Build offline MongoDB JSON dump for offline search
        Document offlineData = new Document();
        Map<String, List<Document>> offlineMap = new LinkedHashMap<>();

        for (String col : COLLECTIONS) {
            try {
                List<Document> docs = mongo.findAll(Document.class, col);

                for (Document d : docs) {
                    Object id = d.get("_id");
                    if (id instanceof ObjectId)
                        d.put("_id", ((ObjectId) id).toHexString());
                }

                offlineMap.put(col, docs);
            } catch (Exception ignored) {}
        }

        offlineData.put("results", offlineMap);

        String offlineJson = offlineData.toJson();

// =========================================================
//  YOUR ORIGINAL HTML + OFFLINE JSON INJECTION
// =========================================================

        String htmlReport = """
    <html>
    <head>
        <meta charset='UTF-8'>
        <style>
            body { font-family:'Segoe UI',sans-serif;background:#f5f8fa;padding:25px;line-height:1.6; }
            h2,h3 { color:#0D47A1;margin-bottom:8px; }
            .subproject-card {
                background:#ffffff;
                border-left:6px solid #4285F4;
                border-radius:10px;
                padding:15px 20px;
                margin-bottom:20px;
                box-shadow:0 4px 10px rgba(0,0,0,0.08);
                transition: transform 0.2s ease;
            }
            .subproject-card:hover { transform: translateY(-2px); }
            .global-summary {
                background:#E3F2FD;
                padding:18px;
                border-left:6px solid #1A73E8;
                border-radius:10px;
                margin-bottom:25px;
            }
            .summary-footer {
                background:#E8EAF6;
                padding:15px;
                border-left:6px solid #3F51B5;
                border-radius:10px;
                margin-top:30px;
                font-size:14px;
            }
            .ai-search {
                background:#fff;
                border-left:6px solid #6C63FF;
                border-radius:10px;
                padding:15px;
                margin-bottom:25px;
                box-shadow:0 4px 10px rgba(0,0,0,0.08);
            }
            p { margin:6px 0; }
            code { background:#f0f3f6;padding:2px 5px;border-radius:4px; }
            input,button { font-size:14px; }
        </style>
    </head>
    <body>

        <!-- PROJECT TITLE -->
        <h2>📊 AI Subproject Analysis — %s</h2>

        <!-- SEARCH BAR -->
        <div class='ai-search'>
            <h3>🔍 Search Historical AI Data</h3>
            <input id='searchInput' type='text' placeholder='Search keyword (e.g., login, error, endpoint)'
                style='padding:8px;width:60%%;border-radius:8px;border:1px solid #ccc;'>
            <button onclick='searchAIData()'
                style='padding:8px 15px;background-color:#0078D7;color:white;border:none;border-radius:8px;margin-left:10px;'>
                Search
            </button>
            <div id='searchResults' style='margin-top:20px;'></div>
        </div>

        %s

        <!-- 🔥 OFFLINE MONGODB JSON EMBEDDED HERE -->
       <script>
                               window.__OFFLINE_SEARCH_DATA__ = %s;
                       </script>
                
                       <script>
                           async function searchAIData() {
                               const query = document.getElementById("searchInput").value.trim();
                               const resultsDiv = document.getElementById("searchResults");
                
                               if (!query) {
                                   resultsDiv.innerHTML = "<p style='color:red;'>Please enter a search keyword.</p>";
                                   return;
                               }
                
                               resultsDiv.innerHTML = "<p>⏳ Searching...</p>";
                
                               const isLocal =
                                   window.location.hostname.includes("localhost") ||
                                   window.location.hostname.startsWith("192.");
                
                               const baseUrl = isLocal
                                   ? "http://localhost:8081"
                                   : "https://ai-allure-reuse-luffy.onrender.com";
                
                               try {
                                   const res = await fetch(`${baseUrl}/api/search?q=${encodeURIComponent(query)}`);
                                   if (!res.ok) throw new Error("Server not reachable");
                
                                   const data = await res.json();
                
                                   if (!data.results || Object.keys(data.results).length === 0) {
                                       resultsDiv.innerHTML =
                                           "<p>No results found for <b>" + query + "</b>.</p>";
                                       return;
                                   }
                
                                   let html = "";
                
                                   if (data.ai_summary) {
                                       html += `
                                           <div style="
                                               background:linear-gradient(135deg,#ede7f6,#f3e5f5);
                                               border-left:6px solid #7B1FA2;
                                               padding:15px 20px;
                                               border-radius:12px;
                                               box-shadow:0 3px 10px rgba(123,31,162,0.15);
                                               margin-bottom:20px;
                                               font-family:'Segoe UI',sans-serif;
                                           ">
                                               <h3 style="color:#4A148C;margin-bottom:10px;">🧠 AI Summary Insights</h3>
                                               <ul style="list-style:none;padding-left:0;margin:0;">
                                                   ${data.ai_summary.split('\\n').map(line => {
                                                       if (line.trim().startsWith('-')) {
                                                           return `<li style="margin-bottom:6px;">🔹 ${line.replace('-', '').trim()}</li>`;
                                                       } else if (line.trim().startsWith('*')) {
                                                           return `<li style="margin-bottom:6px;">✨ ${line.replace('*', '').trim()}</li>`;
                                                       } else {
                                                           return `<li style="margin-bottom:6px;">💡 ${line.trim()}</li>`;
                                                       }
                                                   }).join('')}
                                               </ul>
                                           </div>`;
                                   }
                
                                   for (const [collection, docs] of Object.entries(data.results)) {
                                       html += `<h4 style='color:#0078D7;'>📂 ${collection}</h4>`;
                                       if (docs.length === 0) {
                                           html += "<p><i>No entries found.</i></p>";
                                       } else {
                                           docs.forEach(doc => {
                                               html += `<pre style='background:#f8f9fa;padding:10px;border-radius:6px;border:1px solid #ddd;'>${JSON.stringify(doc,null,2)}</pre>`;
                                           });
                                       }
                                   }
                
                                   resultsDiv.innerHTML = html;
                
                               } catch (err) {
                                   resultsDiv.innerHTML =
                                       "<p style='color:orange;'>⚠️ Server offline — using offline search...</p>";
                                   runOfflineSearch(query);
                               }
                           }
                
                           <!-- ✅ ADDED HERE (ONLY THIS LINE WAS MISSING) -->
                         const OFFLINE_DATA = window.__OFFLINE_SEARCH_DATA__ || null;
                
                                            function runOfflineSearch(query) {
                                                const resultsDiv = document.getElementById("searchResults");
                                                const q = query.toLowerCase();
                
                                                if (!OFFLINE_DATA) {
                                                    resultsDiv.innerHTML += `<p style='color:red;'>❌ Offline database not embedded in report.</p>`;
                                                    return;
                                                }
                
                                                let matches = [];
                
                                                // ⭐ 1. If query matches collection name → return entire collection
                                                for (const [collectionName, docs] of Object.entries(OFFLINE_DATA.results)) {
                                                    if (collectionName.toLowerCase().includes(q)) {
                                                        matches.push(...docs);
                                                    }
                                                }
                
                                                // ⭐ 2. Fallback: search inside documents
                                                if (matches.length === 0) {
                                                    Object.values(OFFLINE_DATA.results).forEach(collection => {
                                                        collection.forEach(doc => {
                                                            const str = JSON.stringify(doc).toLowerCase();
                                                            if (str.includes(q)) {
                                                                matches.push(doc);
                                                            }
                                                        });
                                                    });
                                                }
                
                                                if (matches.length === 0) {
                                                    resultsDiv.innerHTML += `<p style='color:red;'>No offline matches for <b>${query}</b>.</p>`;
                                                    return;
                                                }
                
                                                let html = `<p style='color:green;'>Offline results found: ${matches.length}</p>`;
                
                                                matches.forEach((doc, i) => {
                                                    html += `
                                                        <div style="
                                                            background:#f1f1f1;
                                                            margin:10px 0;
                                                            padding:10px;
                                                            border-radius:6px;
                                                            font-family:monospace;
                                                            white-space:pre-wrap;
                                                        ">
                                                            <b>Result ${i + 1}</b><br>
                                                            ${JSON.stringify(doc, null, 2)}
                                                        </div>
                                                    `;
                                                });
                
                                                resultsDiv.innerHTML += html;
                                            }
                
                       </script>
                
    </body>
    </html>
""".formatted(projectName, finalSummary.toString(), offlineJson);

        // ✅ Step 3: Attach to Allure
        Allure.addAttachment("AI Unified Report (" + projectName + ")", "text/html",
                new ByteArrayInputStream(htmlReport.getBytes(StandardCharsets.UTF_8)), "html");

        System.out.println("✅ AI Unified HTML Summary generated for project: " + projectName);
    }

    // === Helper Methods ===

    private static String compareSessions(String subproject, String prevTime, String latestTime,
                                          List<Document> prevSessions, List<Document> latestSessions) {
        long prevTotal = countEndpoints(prevSessions);
        long latestTotal = countEndpoints(latestSessions);
        long prevSuccess = countSuccessEndpoints(prevSessions);
        long latestSuccess = countSuccessEndpoints(latestSessions);
        long prevFail = prevTotal - prevSuccess;
        long latestFail = latestTotal - latestSuccess;
        double prevRate = prevTotal == 0 ? 0 : (prevSuccess * 100.0 / prevTotal);
        double latestRate = latestTotal == 0 ? 0 : (latestSuccess * 100.0 / latestTotal);
        double delta = latestRate - prevRate;

        Set<String> prevEndpoints = extractEndpoints(prevSessions);
        Set<String> latestEndpoints = extractEndpoints(latestSessions);
        Set<String> added = new HashSet<>(latestEndpoints);
        added.removeAll(prevEndpoints);
        Set<String> removed = new HashSet<>(prevEndpoints);
        removed.removeAll(latestEndpoints);
        Set<String> newFails = findNewFailures(prevSessions, latestSessions);

        StringBuilder html = new StringBuilder();
        html.append(String.format("""
                <p>🕒 <b>Previous:</b> %s (%d endpoints)<br>
                🕒 <b>Latest:</b> %s (%d endpoints)</p>
                <p>📈 Success Rate: %.2f%% → %.2f%% (%+.2f%%)<br>
                ❌ Failures: %d → %d</p>
                """, prevTime, prevTotal, latestTime, latestTotal, prevRate, latestRate, delta, prevFail, latestFail));

        if (!added.isEmpty()) html.append("➕ Added: ").append(added).append("<br>");
        if (!removed.isEmpty()) html.append("➖ Removed: ").append(removed).append("<br>");
        if (!newFails.isEmpty()) html.append("❌ New Failures: ").append(newFails).append("<br>");
        if (added.isEmpty() && removed.isEmpty() && newFails.isEmpty())
            html.append("✅ Stable endpoints.<br>");

        html.append(generateParagraphSummary(subproject, prevRate, latestRate, delta, prevFail, latestFail, prevTime, latestTime));
        return html.toString();
    }

    private static Set<String> findNewFailures(List<Document> prev, List<Document> latest) {
        Set<String> prevFails = extractFailedEndpoints(prev);
        Set<String> currFails = extractFailedEndpoints(latest);
        currFails.removeAll(prevFails);
        return currFails;
    }

    private static long countEndpoints(List<Document> sessions) {
        return sessions.stream().mapToLong(s -> ((List<?>) s.getOrDefault("endpoints", List.of())).size()).sum();
    }

    private static long countSuccessEndpoints(List<Document> sessions) {
        return sessions.stream()
                .flatMap(s -> ((List<Document>) s.getOrDefault("endpoints", List.of())).stream())
                .filter(d -> {
                    int status = d.getInteger("status", 0);
                    return status >= 200 && status < 300;
                })
                .count();
    }

    private static Set<String> extractEndpoints(List<Document> sessions) {
        return sessions.stream()
                .flatMap(s -> ((List<Document>) s.getOrDefault("endpoints", List.of())).stream())
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());
    }

    private static Set<String> extractFailedEndpoints(List<Document> sessions) {
        return sessions.stream()
                .flatMap(s -> ((List<Document>) s.getOrDefault("endpoints", List.of())).stream())
                .filter(d -> d.getInteger("status", 0) >= 400)
                .map(d -> d.getString("method") + " " + d.getString("endpoint"))
                .collect(Collectors.toSet());
    }

    private static String generateParagraphSummary(String subproject, double prevRate, double latestRate, double delta,
                                                   long prevFails, long latestFails, String prevTime, String latestTime) {
        String trend = delta > 0 ? "improvement" : (delta < 0 ? "decline" : "no major change");
        String performance = delta > 0 ? "performed better" : (delta < 0 ? "performed worse" : "maintained stability");
        String color = delta > 0 ? "#1B5E20" : (delta < 0 ? "#C62828" : "#616161");

        return String.format("""
                <p style='color:%s;font-weight:600;'>
                🧾 <b>Summary:</b> Between <b>%s</b> and <b>%s</b>, subproject <b>%s</b> showed %s —
                from <b>%.2f%%</b> (%d fails) to <b>%.2f%%</b> (%d fails). It %s overall (Δ %+.2f%%).
                </p>
                """, color, prevTime, latestTime, subproject, trend, prevRate, prevFails, latestRate, latestFails, performance, delta);
    }
}
