Here is the updated and expanded README for your project, now including **PacketEvents** in the system requirements and translated entirely into English.

***

# Omni Translator Client

**Omni Translator Client** (Project ID: `Omni`) is a high-performance, modular translation plugin designed for Minecraft networks. It enables seamless real-time translation of in-game content such as chat messages, signs, inventories, and holograms, ensuring a localized experience for every player.

The project is built as a multi-module Maven project, featuring native integrations for **Spigot/Paper** servers and **Velocity** proxy servers.

## 🌟 Features

* **Real-Time Translation:** Dynamically translates content for each player based on their individual locale settings.
* **Comprehensive Module Coverage (Spigot):**
    * 💬 **Live Chat:** Intercepts and translates player chat and plugin-broadcasted messages.
    * 🪧 **Signs:** Provides dynamic translation for text written on signs.
    * ✨ **Holograms:** Compatible with popular hologram systems via packet interception.
    * 🎒 **Inventories/GUIs:** Translates item names and descriptions within server-side GUIs.
* **High Performance:** Utilizes efficient caching via `CaffeineCache` and asynchronous task execution to maintain server TPS.
* **Secure Communication:** Uses Protocol Buffers (`packets.proto`) for standardized data exchange and HMAC encryption (`HmacService`) for secure verification.

## 🏗️ Project Structure

The project is organized into three main modules as defined in the `pom.xml`:

1.  **`bridge`**:
    The core logic of the project. It contains shared services used by both the Proxy and Spigot modules, including network protocols (Protobuf), configuration management (`ConfigService`), caching, placeholder processing (`PlaceholderService`), and security utilities.
2.  **`proxy`**:
    The Velocity-specific plugin (`VelocityBridge`). It handles cross-server connections, RESTful services, and network-wide MotD (Message of the Day) management with GeoIP support.
3.  **`spigot`**:
    The backend plugin (`SpigotTranslator`) for individual game servers. It handles low-level packet manipulation for inventories, holograms, signs, and chat.

## 🛠️ System Requirements

* **Java:** Version 21 (Required by `maven.compiler.source` and `target`).
* **Server Software:**
    * **Velocity** (for the Proxy module).
    * **Spigot / Paper** (for the Spigot module).
* **Required Plugins:**
    * **PacketEvents** (Must be installed on the Spigot/Paper server).
* **Build Tool:** Apache Maven (3.6.0 or higher).

## 🚀 Installation & Build

Since this is a Maven project, you can compile all modules simultaneously.

1.  Clone the repository to your local machine.
2.  Navigate to the root directory (where the main `pom.xml` is located).
3.  Execute the following Maven command to build the project:

```bash
mvn clean install
```

4.  After a successful build, you will find the `.jar` files in the respective `target/` directories:
    * Copy `spigot/target/...jar` to the `plugins/` folder of your Spigot/Paper server.
    * Copy `proxy/target/...jar` to the `plugins/` folder of your Velocity proxy.
5.  Restart both the proxy and the backend servers.

## ⚙️ Dependencies & Technologies

* **PacketEvents:** Used for advanced packet manipulation and cross-version compatibility on the Spigot side.
* **Jackson-BOM (2.18.2):** Used for efficient JSON processing and data structuring.
* **Google Protocol Buffers (Protobuf):** Used for defining (`packets.proto`) and exchanging network packets like `TranslationRequest` and `ConfigRequestPacket`.
* **Caffeine:** A high-performance caching library used to reduce redundant translation API calls.

## 📝 Configuration

Upon the first launch, the plugin generates configuration files in the respective server folders.
The main configuration allows you to:
* Connect to external translation APIs (RESTful Translation Services).
* Define caching durations.
* Exclude specific words, placeholders, or player names from being translated using the `PlayernameProtector` and `WordProtector` services.

***
*This project (GroupId: `org.example`, ArtifactId: `Omni`) is currently under development in version `1.0-SNAPSHOT`.*
