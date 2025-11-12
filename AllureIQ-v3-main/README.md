# 🚀 AllureIQ Framework — AI-Powered API Automation

A complete AI-driven API Automation Platform that executes, analyzes, and reports API test results — intelligently learning from previous runs and providing actionable insights. Fully integrated with MongoDB, Allure, Spring Boot, and custom AI logic, it delivers test intelligence at scale.

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
        <version>3.1.3</version>
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

## ⚡ Build & Run Tests

1. Open a terminal in your project root directory.
2. Build your project and execute tests:

```xml
mvn clean install
```
## Serve the AI-powered Allure report:
```xml
allure serve ./allure-results
