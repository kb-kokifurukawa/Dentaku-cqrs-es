# Dentaku-cqrs-es

## 📐 System Architecture (CQRS / Event Sourcing)

このアプリケーションは、コマンド（更新）とクエリ（参照）の責務を完全に分離する **CQRS (Command Query Responsibility Segregation)** と、状態の変更履歴をすべて事実として記録する **Event Sourcing** アーキテクチャを採用しています。

```mermaid
graph TD
    subgraph Frontend
        UI[React Calculator UI]
    end

    subgraph BFF Layer
        BFF[Go GraphQL Server<br/>Port: 8080]
    end

    subgraph CQRS Write Side
        WriteAPI[Scala Write Server<br/>Pekko Actor / Port: 9000]
        WriteDB[(SQLite: write_side.db<br/>Event Journal)]
    end

    subgraph CQRS Read Side
        ReadAPI[Scala Read Server<br/>Pekko Streams / Port: 9001]
        ReadDB[(SQLite: read_side.db<br/>View Model)]
    end

    %% Write Flow (Command)
    UI -- "1. Mutation (PressDigit etc.)" --> BFF
    BFF -- "2. HTTP POST (Fire & Forget)" --> WriteAPI
    WriteAPI -- "3. Append Event (Calculated etc.)" --> WriteDB

    %% Projection Flow (Event Sourcing)
    WriteDB -. "4. Stream Events (Pekko Query)" .-> ReadAPI
    ReadAPI -- "5. UPDATE State" --> ReadDB

    %% Read Flow (Query)
    BFF -- "6. HTTP GET (Polling /state)" --> ReadAPI
    ReadAPI -- "SELECT" --> ReadDB
    BFF -- "7. Subscription Push" --> UI
    
    classDef frontend fill:#61dafb,stroke:#333,stroke-width:2px,color:#000;
    classDef bff fill:#00add8,stroke:#333,stroke-width:2px,color:#fff;
    classDef writeSide fill:#e32d26,stroke:#333,stroke-width:2px,color:#fff;
    classDef readSide fill:#42b883,stroke:#333,stroke-width:2px,color:#fff;
    classDef database fill:#f2a65a,stroke:#333,stroke-width:2px,color:#000;

    class UI frontend;
    class BFF bff;
    class WriteAPI writeSide;
    class ReadAPI readSide;
    class WriteDB,ReadDB database;