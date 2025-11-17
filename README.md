## 🧠 AllureIQ Framework v3.2.6 — AI-Powered Unified Test Intelligence Platform  
**Developed by Adepu Chaitanya — SDET @ Cognizant**

### 🚀 Overview  
**AllureIQ Framework** is an advanced **AI-driven automation and reporting ecosystem** designed and developed by **Adepu Chaitanya**, currently working as a **Software Development Engineer in Test (SDET) at Cognizant**.  

This framework integrates **REST API testing**, **AI intelligence**, **real-time analytics**, and **CI/CD automation**, delivering a complete **end-to-end intelligent test and reporting solution**. It automatically analyzes test results, identifies trends, and produces **AI-generated summaries and insights** — just like an observability dashboard for QA.

---

### 🧩 Core Capabilities

#### 🧾 1. AI Test Intelligence Reports  
- Generates **AI summaries** after every test run.  
- Displays key stats like success rates, API performance, and error causes.  
- Detects recurring issues (e.g., malformed endpoints or missing validations).  
- Suggests fixes and best practices automatically.

#### 🧠 2. AI-Allure-Reuse Architecture  
- Built with **REST Assured**, **TestNG**, **Allure**, and **Spring Boot**.  
- AI compares test runs to show stability, performance, and delta metrics.  
- Integrates directly with MongoDB to store and retrieve AI-analyzed results.

---

### 🗂️ Project & Data Management  
- Each **user** can create **folders (projects)** with multiple **subprojects** and test runs.  
- User data and sessions are isolated.  
- Example:  
  - Folder `Test` → 3 subprojects (`UserAppTest`, `FoodFinderTest`, `SocialMediaTest`)  
  - Folder `Test2` → 6 subprojects (handled independently)  

Data stored in MongoDB:
- `ai_executions` → execution metadata  
- `ai_reports` → AI insights  
- `ai_context_logs` → payloads & error traces  

---

### 🔍 New Enhanced Search Bar (v3.2.6 Upgrade)

The search bar now supports a **serverless + hybrid search engine** with intelligent fallback and fuzzy matching.

⭐ Key Enhancements
- **No server required** — connects to **MongoDB Atlas directly through Java JAR**, without Spring Boot.
- **Local JSON caching** — stores search results locally to ensure the dashboard works even if MongoDB or server is unavailable.
- **Automatic fallback mode**  
  Cloud MongoDB → If unavailable → Use local JSON cache
- **Fuzzy matching** — supports smart partial and typo-based search for projects, endpoints, logs, and AI reports.
- **Real-time results** — instantly displays matches from MongoDB or cache.

This makes the AllureIQ dashboard more **fault-tolerant, faster, and usable in both online and offline modes**.

---

### 🔄 CI/CD Automation  
- Integrated **GitHub Actions** for continuous testing and deployment.  
- Auto-runs tests, generates Allure reports, and tags releases.  
- Published a Maven package:
  **`allureiq-framework` v3.2.6**  
  usable as a reusable dependency in any project.

```xml
<dependency>
  <groupId>com.allureiq</groupId>
  <artifactId>allureiq-framework</artifactId>
  <version>3.2.6</version>
</dependency>
```

---

### ⚙️ Tech Stack Overview

| Layer | Technologies |
|--------|---------------|
| **Automation & Testing** | Java, TestNG, REST Assured |
| **Reporting** | Allure Reports |
| **Backend** | Spring Boot, Maven |
| **Frontend** | HTML, CSS, JavaScript |
| **Database** | MongoDB |
| **AI & Logic Layer** | Custom AI using API keys |
| **CI/CD** | GitHub Actions |
| **Packaging** | JAR (Reusable Maven Dependency) |

---

### 📊 Example AI Report
> **AI Test Intelligence Report — Unified View**  
> 🕒 Generated: 2025-11-08T18:43:52  
> 🧾 Summary: 3 APIs tested → 66.67% success rate  
> 🧠 Root Cause: Null endpoint validation missing  
> 💡 Suggestion: Add validation and error handling in API layer  
> ✅ Stored successfully in MongoDB (`ai_reports`)

---

### 🧰 Highlights
- ✅ AI-generated test summaries & insights  
- ✅ REST Assured + TestNG automation base  
- ✅ Allure visual reporting integration  
- ✅ MongoDB for test persistence & analytics  
- ✅ Spring Boot + HTML/CSS/JS UI dashboard  
- ✅ CI/CD with GitHub Actions  
- ✅ Published reusable Maven package  
- ✅ Real-time AI search across logs  

---

## 📦 Maven Setup
```xml
<!-- ✅ Public GitHub Package Repository -->
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub itachi200218 Maven Packages</name>
        <url>https://maven.pkg.github.com/itachi200218/AllureIQ-v3</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.allureiq</groupId>
        <artifactId>allureiq-framework</artifactId>
        <version>3.2.6</version>
    </dependency>

    <dependency>
        <groupId>io.qameta.allure</groupId>
        <artifactId>allure-testng</artifactId>
        <version>2.21.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.qameta.allure</groupId>
            <artifactId>allure-maven</artifactId>
            <version>2.11.2</version>
            <executions>
                <execution>
                    <phase>verify</phase>
                    <goals>
                        <goal>serve</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>

```
---
### 📌 Maven GitHub Packages Setup

- 1️⃣ Create settings.xml in your .m2 folder:
- Windows → C:\Users\<you>\.m2\settings.xml
- Mac/Linux → ~/.m2/settings.xml

- 2️⃣ Create a Classic GitHub Token:
- GitHub → Settings → Developer Settings → Personal Access Tokens → Classic
-    Enable:
       ✔ read:packages
       ✔ write:packages

### 3️⃣ Paste this into settings.xml
    Replace:
-       YOUR_GITHUB_USERNAME → your GitHub username
-      YOUR_GITHUB_TOKEN → your classic PAT token
```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>

</settings>
```
---
## ⚡ Build & Run Tests

1. Open a terminal in your project root directory.
2. Build your project and execute tests:

```xml
mvn clean install
```
## Serve the AI-powered Allure report:
```xml
allure serve ./allure-results


---
### 👨‍💻 Author
**Adepu Chaitanya**  
**Role:** SDET (Software Development Engineer in Test)  
**Organization:** Cognizant Technology Solutions  
**Specialization:** Automation Frameworks, AI-driven Testing, CI/CD, Spring Boot, and Data Intelligence  
**Version:** `allureiq-framework v3.2.2`
